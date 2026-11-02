package com.omniforge.apparat.engine.video

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.FfmpegException
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class VideoShrinkPreset(val label: String, val shortSide: Int, val bitrate: Int) {
    DISCORD_FAST("Discord / Social (Compact 720p)", 720, 2_500_000),
    WHATSAPP("WhatsApp / Messaging (480p)", 480, 1_200_000),
    HIGH_QUALITY("Light Compress (1080p)", 1080, 5_000_000),
    MAX_SHRINK("Extreme Shrink (360p)", 360, 600_000)
}

enum class AudioOutputFormat(val label: String, val extension: String, val mimeType: String) {
    M4A("M4A (AAC)", ".m4a", "audio/mp4"),
    WAV("WAV", ".wav", "audio/wav")
}

/**
 * Desktop video engine: the same functions as the Android one (Media3 Transformer), running the
 * bundled FFmpeg instead. Streams are copied whenever possible (trim, mute, AAC rip).
 */
object VideoEngine {

    const val MP4_MIME = "video/mp4"

    /** Progress callbacks go to the UI thread, at most ~8 times a second. */
    private class Progress(private val sink: (Float, String) -> Unit) {
        private var last = 0L
        fun post(p: Float, message: String, force: Boolean = false) {
            val now = System.nanoTime()
            if (!force && now - last < 120_000_000L) return
            last = now
            java.awt.EventQueue.invokeLater { sink(p, message) }
        }
    }

    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun probe(uri: Uri): MediaProbe =
        MediaProbe.of(uri.file) ?: throw IllegalStateException("This file isn't a video that can be read (it may be damaged).")

    private fun even(value: Float): Int = max(2, (value / 2).roundToInt() * 2)

    /** Encoder args for [codec] at [bitrate] (bits/s), or plain quality-based H.264. */
    private fun videoEncoderArgs(codec: VideoCodecChoice, bitrate: Int?): List<String> {
        val encoder = codec.ffmpegEncoder ?: throw IllegalStateException("${codec.shortName} encoding isn't available on this computer.")
        val args = mutableListOf("-c:v", encoder)
        when (encoder) {
            "libx264" -> args += listOf("-preset", "medium", "-profile:v", "high")
            "libx265" -> args += listOf("-preset", "medium", "-tag:v", "hvc1")
            "libsvtav1" -> args += listOf("-preset", "8")
            "libaom-av1" -> args += listOf("-cpu-used", "6", "-row-mt", "1")
        }
        if (bitrate != null) {
            args += listOf("-b:v", bitrate.toString(), "-maxrate", (bitrate * 3 / 2).toString(), "-bufsize", (bitrate * 2).toString())
        } else if (encoder == "libx264") {
            args += listOf("-crf", "20")
        }
        args += listOf("-pix_fmt", "yuv420p")
        return args
    }

    private val mp4Flags = listOf("-movflags", "+faststart")

    suspend fun shrinkVideo(
        context: Context,
        inputUri: Uri,
        preset: VideoShrinkPreset = VideoShrinkPreset.DISCORD_FAST,
        codec: VideoCodecChoice = VideoCodecChoice.H264,
        onCodecUsed: (VideoCodecChoice) -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        withContext(Dispatchers.IO) { VideoDecodeSupport.requireDecodable(context, inputUri) }
        val info = probe(inputUri)
        val progress = Progress(onProgress)
        val shortSide = min(info.displayWidth, info.displayHeight)
        var bitrate = preset.bitrate
        val filters = mutableListOf<String>()
        if (shortSide > preset.shortSide) {
            val scale = preset.shortSide.toFloat() / shortSide
            filters += "scale=${even(info.displayWidth * scale)}:${even(info.displayHeight * scale)}:flags=lanczos"
        } else {
            val pixelRatio = (info.displayWidth.toFloat() * info.displayHeight) / (preset.shortSide * preset.shortSide * 16f / 9f)
            bitrate = (preset.bitrate * pixelRatio).roundToInt()
            // Odd sizes can't be encoded as yuv420p.
            if (info.displayWidth % 2 != 0 || info.displayHeight % 2 != 0) filters += "scale=trunc(iw/2)*2:trunc(ih/2)*2"
        }
        info.bitrate?.takeIf { it > 0 }?.let { bitrate = min(bitrate, (it * 0.8f).roundToInt()) }
        bitrate = max(bitrate, 200_000)

        val output = FileUtils.createTempOutputFile(context, "shrunk", ".mp4")
        progress.post(0f, "Compressing video (${preset.label})...", force = true)
        val used = encodeWithFallback(codec, progress) { attempt ->
            val args = mutableListOf("-i", inputUri.file.absolutePath, "-map", "0:v:0", "-map", "0:a:0?")
            if (filters.isNotEmpty()) args += listOf("-vf", filters.joinToString(","))
            args += videoEncoderArgs(attempt, VideoCodecChoice.scaledBitrate(bitrate, attempt))
            args += listOf("-c:a", "aac", "-b:a", "128k", "-ac", "2") + mp4Flags + output.absolutePath
            runToFile(args, output, info.durationSec) { p ->
                progress.post(p, "Compressing video (${attempt.shortName})... ${(p * 100).toInt()}%")
            }
        }
        onCodecUsed(used)
        output
    }

    suspend fun convertToMp4(
        context: Context,
        inputUri: Uri,
        codec: VideoCodecChoice = VideoCodecChoice.H264,
        onCodecUsed: (VideoCodecChoice) -> Unit = {},
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        withContext(Dispatchers.IO) { VideoDecodeSupport.requireDecodable(context, inputUri) }
        val info = probe(inputUri)
        val progress = Progress(onProgress)
        val output = FileUtils.createTempOutputFile(context, "converted", ".mp4")
        progress.post(0f, "Converting to MP4...", force = true)
        val used = encodeWithFallback(codec, progress) { attempt ->
            val args = mutableListOf("-i", inputUri.file.absolutePath, "-map", "0:v:0", "-map", "0:a:0?")
            if (info.displayWidth % 2 != 0 || info.displayHeight % 2 != 0) args += listOf("-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2")
            args += videoEncoderArgs(attempt, null)
            args += listOf("-c:a", "aac", "-b:a", "192k") + mp4Flags + output.absolutePath
            runToFile(args, output, info.durationSec) { p ->
                progress.post(p, "Converting to MP4 (${attempt.shortName})... ${(p * 100).toInt()}%")
            }
        }
        onCodecUsed(used)
        output
    }

    /** Runs [export] in [codec]; if HEVC/AV1 fails in FFmpeg, runs it once more as H.264. */
    private suspend fun encodeWithFallback(
        codec: VideoCodecChoice,
        progress: Progress,
        export: suspend (VideoCodecChoice) -> Unit
    ): VideoCodecChoice {
        try {
            export(codec)
            return codec
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            if (codec == VideoCodecChoice.H264 || e !is FfmpegException && e !is IllegalStateException) throw e
        }
        progress.post(0f, "${codec.shortName} encoder failed, retrying as H.264...", force = true)
        export(VideoCodecChoice.H264)
        return VideoCodecChoice.H264
    }

    /** Runs FFmpeg writing [output]; deletes it on failure or cancellation. */
    private suspend fun runToFile(args: List<String>, output: File, durationSec: Double?, onProgress: (Float) -> Unit) {
        try {
            Ffmpeg.run(args, durationSec, onProgress)
            if (!output.exists() || output.length() == 0L) throw IllegalStateException("FFmpeg produced no output.")
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    suspend fun burnSubtitles(
        context: Context,
        videoUri: Uri,
        subtitleUri: Uri,
        fontSize: Int = 20,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val text = withContext(Dispatchers.IO) {
            runCatching { subtitleUri.file.readBytes().toString(Charsets.UTF_8) }.getOrNull()
                ?: throw IllegalStateException("Could not read subtitle file")
        }
        val cues = SubtitleParser.parse(text)
        if (cues.isEmpty()) throw IllegalArgumentException("No subtitles found. Use a .srt or .vtt file.")
        withContext(Dispatchers.IO) { VideoDecodeSupport.requireDecodable(context, videoUri) }
        val info = probe(videoUri)
        val progress = Progress(onProgress)
        val output = FileUtils.createTempOutputFile(context, "captioned", ".mp4")
        val workDir = File(context.cacheDir, "work_subs_${System.nanoTime()}").apply { mkdirs() }
        try {
            progress.post(0f, "Preparing subtitles...", force = true)
            val w = even(info.displayWidth.toFloat())
            val h = even(info.displayHeight.toFloat())
            val list = withContext(Dispatchers.IO) { writeCueImages(cues, SubtitleRenderer(w, h, fontSize), workDir) }
            progress.post(0f, "Burning subtitles...", force = true)
            val args = listOf(
                "-i", videoUri.file.absolutePath,
                "-f", "concat", "-safe", "0", "-i", list.absolutePath,
                "-filter_complex",
                "[0:v]scale=$w:$h,format=yuv420p[base];[1:v]format=rgba[subs];[base][subs]overlay=0:0:eof_action=pass:format=auto[v]",
                "-map", "[v]", "-map", "0:a:0?"
            ) + videoEncoderArgs(VideoCodecChoice.H264, null) + listOf("-c:a", "aac", "-b:a", "192k") + mp4Flags + output.absolutePath
            runToFile(args, output, info.durationSec) { p -> progress.post(p, "Burning subtitles... ${(p * 100).toInt()}%") }
            output
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** One PNG per cue plus a blank, sequenced with an FFmpeg concat list by duration. */
    private fun writeCueImages(cues: List<SubtitleCue>, renderer: SubtitleRenderer, dir: File): File {
        val blank = File(dir, "blank.png").also { renderer.renderBlank(it) }
        val sb = StringBuilder("ffconcat version 1.0\n")
        fun entry(file: File, seconds: Double) {
            if (seconds <= 0.0005) return
            sb.append("file '").append(file.absolutePath.replace("'", "'\\''")).append("'\n")
            sb.append("duration ").append("%.3f".format(java.util.Locale.ROOT, seconds)).append('\n')
        }
        var cursorUs = 0L
        cues.forEachIndexed { i, cue ->
            val start = max(cue.startUs, cursorUs)
            if (cue.endUs <= start) return@forEachIndexed
            entry(blank, (start - cursorUs) / 1e6)
            val png = File(dir, "cue_$i.png").also { renderer.renderPng(cue.text, it) }
            entry(png, (cue.endUs - start) / 1e6)
            cursorUs = cue.endUs
        }
        entry(blank, 1.0)
        // The concat demuxer ignores the last entry's duration: end on the blank frame.
        sb.append("file '").append(blank.absolutePath.replace("'", "'\\''")).append("'\n")
        return File(dir, "subs.ffconcat").apply { writeText(sb.toString()) }
    }

    suspend fun extractAudio(
        context: Context,
        videoUri: Uri,
        targetFormat: AudioOutputFormat,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val info = probe(videoUri)
        if (!info.hasAudio) throw IllegalStateException("This video has no sound to extract.")
        val progress = Progress(onProgress)
        val output = FileUtils.createTempOutputFile(context, "extracted_audio", targetFormat.extension)
        val input = listOf("-i", videoUri.file.absolutePath, "-map", "0:a:0", "-vn", "-sn", "-dn")
        when (targetFormat) {
            AudioOutputFormat.M4A -> {
                progress.post(0f, "Extracting audio...", force = true)
                val codecArgs = if (info.audioCodec == "aac") listOf("-c:a", "copy") else listOf("-c:a", "aac", "-b:a", "192k")
                runToFile(input + codecArgs + mp4Flags + output.absolutePath, output, info.durationSec) { p ->
                    progress.post(p, "Extracting audio... ${(p * 100).toInt()}%")
                }
            }
            AudioOutputFormat.WAV -> {
                progress.post(0f, "Decoding audio...", force = true)
                runToFile(input + listOf("-c:a", "pcm_s16le", output.absolutePath), output, info.durationSec) { p ->
                    progress.post(p, "Decoding audio... ${(p * 100).toInt()}%")
                }
            }
        }
        output
    }

    /** Removes the audio track. No re-encoding: the video stream is copied. */
    suspend fun stripAudio(
        context: Context,
        videoUri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val info = probe(videoUri)
        val progress = Progress(onProgress)
        val output = FileUtils.createTempOutputFile(context, "muted", ".mp4")
        val base = listOf("-i", videoUri.file.absolutePath, "-map", "0:v:0", "-an", "-sn", "-dn")
        try {
            runToFile(base + listOf("-c:v", "copy") + mp4Flags + output.absolutePath, output, info.durationSec) { p ->
                progress.post(p, "Removing audio... ${(p * 100).toInt()}%")
            }
        } catch (e: FfmpegException) {
            // The video codec can't go into MP4 as is (e.g. some MKV/WEBM streams): re-encode.
            runToFile(base + videoEncoderArgs(VideoCodecChoice.H264, null) + mp4Flags + output.absolutePath, output, info.durationSec) { p ->
                progress.post(p, "Removing audio (re-encoding)... ${(p * 100).toInt()}%")
            }
        }
        output
    }

    /** Cuts [startSec, startSec + durationSec] by copying streams (cut lands on the keyframe at or before the start). */
    suspend fun losslessTrim(
        context: Context,
        videoUri: Uri,
        startSec: Float,
        durationSec: Float,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        require(startSec >= 0f) { "Start time can't be negative" }
        require(durationSec > 0f) { "Duration must be more than 0 seconds" }
        val info = probe(videoUri)
        if (info.durationSec > 0 && startSec >= info.durationSec) {
            throw IllegalArgumentException("Start time is past the end of the video (${"%.1f".format(info.durationSec)}s)")
        }
        val progress = Progress(onProgress)
        val clipLength = if (info.durationSec > 0) min(durationSec.toDouble(), info.durationSec - startSec) else durationSec.toDouble()
        val output = FileUtils.createTempOutputFile(context, "trimmed", ".mp4")
        val base = listOf(
            "-ss", "%.3f".format(java.util.Locale.ROOT, startSec), "-i", videoUri.file.absolutePath,
            "-t", "%.3f".format(java.util.Locale.ROOT, durationSec), "-map", "0:v:0?", "-map", "0:a:0?", "-sn", "-dn"
        )
        try {
            runToFile(base + listOf("-c", "copy") + mp4Flags + output.absolutePath, output, clipLength) { p ->
                progress.post(p, "Trimming... ${(p * 100).toInt()}%")
            }
        } catch (e: FfmpegException) {
            runToFile(
                base + videoEncoderArgs(VideoCodecChoice.H264, null) + listOf("-c:a", "aac", "-b:a", "192k") + mp4Flags + output.absolutePath,
                output, clipLength
            ) { p -> progress.post(p, "Trimming (re-encoding)... ${(p * 100).toInt()}%") }
        }
        output
    }

    /** Pulls frames with FFmpeg and writes them with the app's own GIF encoder (as on Android). */
    suspend fun videoToGif(
        context: Context,
        videoUri: Uri,
        fps: Int = 12,
        width: Int = 480,
        startSec: Float = 0f,
        durationSec: Float = 6f,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        withContext(Dispatchers.IO) {
            val info = probe(videoUri)
            val durationMs = (info.durationSec * 1000).toLong()
            val startMs = (startSec * 1000).toLong()
            val endMs = if (durationMs > 0) min(durationMs, startMs + (durationSec * 1000).toLong()) else startMs + (durationSec * 1000).toLong()
            if (endMs <= startMs) throw IllegalArgumentException("Start time is past the end of the video")
            val outW = if (info.hasVideo) min(width, info.displayWidth) else width
            val outH = if (info.hasVideo) max(2, (outW.toFloat() * info.displayHeight / info.displayWidth).roundToInt()) else outW * 9 / 16
            val safeFps = fps.coerceIn(1, 50)
            val frameDelayMs = 1000 / safeFps
            val frameCount = max(1, ((endMs - startMs) / frameDelayMs).toInt())
            val progress = Progress(onProgress)
            val output = FileUtils.createTempOutputFile(context, "animated", ".gif")
            val command = listOf(
                Ffmpeg.ffmpeg, "-hide_banner", "-nostdin", "-loglevel", "error",
                "-ss", "%.3f".format(java.util.Locale.ROOT, startMs / 1000.0), "-i", videoUri.file.absolutePath,
                "-t", "%.3f".format(java.util.Locale.ROOT, (endMs - startMs) / 1000.0),
                "-vf", "fps=$safeFps,scale=$outW:$outH:flags=lanczos", "-frames:v", frameCount.toString(),
                "-an", "-f", "rawvideo", "-pix_fmt", "argb", "pipe:1"
            )
            val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            try {
                FileOutputStream(output).buffered().use { out ->
                    val encoder = GifEncoder(out, outW, outH, frameDelayMs)
                    val frameBytes = ByteArray(outW * outH * 4)
                    val pixels = IntArray(outW * outH)
                    val input = DataInputStream(process.inputStream.buffered(1 shl 20))
                    var written = 0
                    while (written < frameCount) {
                        coroutineContext.ensureActive()
                        try { input.readFully(frameBytes) } catch (_: EOFException) { break }
                        ByteBuffer.wrap(frameBytes).asIntBuffer().get(pixels)
                        encoder.addFrame(pixels)
                        written++
                        progress.post(written.toFloat() / frameCount, "Encoding GIF frame $written of $frameCount...")
                    }
                    if (written == 0) throw IllegalStateException("Could not read frames from this video.")
                    encoder.finish()
                }
                process.waitFor()
            } catch (t: Throwable) {
                output.delete()
                throw t
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
            progress.post(1f, "GIF ready", force = true)
            output
        }
    }

    suspend fun getVideoMetadata(context: Context, uri: Uri): VideoMetadata? = withContext(Dispatchers.IO) {
        val p = MediaProbe.of(uri.file) ?: return@withContext null
        if (p.displayWidth <= 0 || p.displayHeight <= 0) return@withContext null
        VideoMetadata(
            width = p.displayWidth,
            height = p.displayHeight,
            durationMs = (p.durationSec * 1000).toLong(),
            rotation = p.rotation,
            mimeType = com.omniforge.apparat.desktop.platform.MimeTypes.forName(uri.file.name)
        )
    }
}

data class VideoMetadata(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int = 0,
    val mimeType: String? = null
) {
    val durationFormatted: String
        get() {
            val totalSec = durationMs / 1000
            return "%d:%02d".format(totalSec / 60, totalSec % 60)
        }

    val resolutionFormatted: String
        get() = "${width}x${height}"
}
