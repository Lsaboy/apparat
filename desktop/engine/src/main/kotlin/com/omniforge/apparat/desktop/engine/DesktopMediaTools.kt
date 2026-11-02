package com.omniforge.apparat.desktop.engine

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.Locale

/** Output of the desktop-only Audio Converter. */
enum class AudioConvertFormat(val label: String, val extension: String, val mimeType: String, val lossless: Boolean) {
    MP3("MP3", ".mp3", "audio/mpeg", false),
    M4A("M4A", ".m4a", "audio/mp4", false),
    OPUS("Opus", ".opus", "audio/opus", false),
    FLAC("FLAC", ".flac", "audio/flac", true),
    WAV("WAV", ".wav", "audio/wav", true)
}

/** A quarter turn, half turn or none, as the user sees it (clockwise). */
enum class VideoTurn(val label: String, val clockwiseDegrees: Int) {
    NONE("None", 0),
    CW90("90° right", 90),
    R180("180°", 180),
    CCW90("90° left", 270)
}

/**
 * Engines for tools that only exist on desktop, where the bundled FFmpeg makes them cheap.
 * Same conventions as the shared engines: Result<File> in the app's output cache, progress as
 * (0..1, message), cancellation stops FFmpeg.
 */
object DesktopMediaTools {

    val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 4f)

    private suspend fun <T> catching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun probe(uri: Uri): MediaProbe =
        MediaProbe.of(uri.file) ?: throw IllegalStateException("This file can't be read (it may be damaged).")

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.4f", v)

    private suspend fun runInto(args: List<String>, output: File, duration: Double, onProgress: (Float) -> Unit) {
        try {
            Ffmpeg.run(args + output.absolutePath, duration, onProgress)
            check(output.isFile && output.length() > 0) { "FFmpeg produced no output." }
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    /**
     * Converts the first audio track of [uri] (audio or video file) to [format]. [bitrateKbps]
     * applies to MP3/M4A/Opus. [normalize] levels loudness to podcast/streaming level (-16 LUFS).
     */
    suspend fun convertAudio(
        context: Context,
        uri: Uri,
        format: AudioConvertFormat,
        bitrateKbps: Int = 192,
        normalize: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = catching {
        val info = probe(uri)
        if (!info.hasAudio) throw IllegalStateException("This file has no sound to convert.")
        val output = FileUtils.createTempOutputFile(context, "converted_audio", format.extension)
        val args = mutableListOf("-i", uri.file.absolutePath, "-map", "0:a:0", "-vn", "-sn", "-dn", "-map_metadata", "0")
        if (normalize) args += listOf("-af", "loudnorm=I=-16:TP=-1.5:LRA=11")
        val kbps = bitrateKbps.coerceIn(32, 320)
        args += when (format) {
            AudioConvertFormat.MP3 -> listOf("-c:a", "libmp3lame", "-b:a", "${kbps}k", "-id3v2_version", "3")
            AudioConvertFormat.M4A -> listOf("-c:a", "aac", "-b:a", "${kbps}k", "-movflags", "+faststart")
            AudioConvertFormat.OPUS -> listOf("-c:a", "libopus", "-b:a", "${kbps.coerceAtMost(256)}k")
            AudioConvertFormat.FLAC -> listOf("-c:a", "flac")
            AudioConvertFormat.WAV -> listOf("-c:a", "pcm_s16le")
        }
        // loudnorm resamples to 192 kHz internally; bring it back to a normal rate.
        if (normalize) args += listOf("-ar", (info.audioSampleRate ?: 48000).coerceAtMost(48000).toString())
        onProgress(0f, "Converting to ${format.label}...")
        runInto(args, output, info.durationSec) { p -> onProgress(p, "Converting to ${format.label}... ${(p * 100).toInt()}%") }
        output
    }

    /** atempo filters for [speed] (each stage limited to 0.5..2 for the best quality). */
    internal fun atempoChain(speed: Double): String {
        val stages = mutableListOf<Double>()
        var s = speed
        while (s > 2.0) { stages += 2.0; s /= 2.0 }
        while (s < 0.5) { stages += 0.5; s /= 0.5 }
        stages += s
        return stages.joinToString(",") { "atempo=${fmt(it)}" }
    }

    /**
     * Rotates/flips and/or changes the speed of a video. A pure rotation with normal speed is
     * lossless (only the rotation tag changes, no re-encoding); anything else re-encodes to H.264.
     */
    suspend fun transformVideo(
        context: Context,
        uri: Uri,
        turn: VideoTurn,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        speed: Float,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = catching {
        val info = probe(uri)
        if (!info.hasVideo) throw IllegalStateException("This file has no video track.")
        val noChange = turn == VideoTurn.NONE && !flipHorizontal && !flipVertical && speed == 1f
        if (noChange) throw IllegalArgumentException("Choose a rotation, a flip or a different speed first.")
        val output = FileUtils.createTempOutputFile(context, "transformed", ".mp4")

        if (!flipHorizontal && !flipVertical && speed == 1f) {
            // Lossless: rewrite the display-rotation tag (stored counter-clockwise).
            val ccw = ((info.rotation - turn.clockwiseDegrees) % 360 + 360) % 360
            onProgress(0f, "Rotating (no re-encoding)...")
            runInto(
                listOf("-display_rotation:v:0", ccw.toString(), "-i", uri.file.absolutePath, "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy", "-movflags", "+faststart"),
                output, info.durationSec
            ) { p -> onProgress(p, "Rotating... ${(p * 100).toInt()}%") }
            return@catching output
        }

        val v = mutableListOf<String>()
        when (turn) {
            VideoTurn.CW90 -> v += "transpose=clock"
            VideoTurn.CCW90 -> v += "transpose=cclock"
            VideoTurn.R180 -> v += listOf("hflip", "vflip")
            VideoTurn.NONE -> {}
        }
        if (flipHorizontal) v += "hflip"
        if (flipVertical) v += "vflip"
        if (speed != 1f) v += "setpts=PTS/${fmt(speed.toDouble())}"
        v += "scale=trunc(iw/2)*2:trunc(ih/2)*2"
        val args = mutableListOf("-i", uri.file.absolutePath, "-map", "0:v:0", "-map", "0:a:0?", "-vf", v.joinToString(","))
        if (speed != 1f && info.hasAudio) args += listOf("-af", atempoChain(speed.toDouble()))
        args += listOf(
            "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-metadata:s:v:0", "rotate=0"
        )
        val outDuration = info.durationSec / speed
        onProgress(0f, "Processing video...")
        runInto(args, output, outDuration) { p -> onProgress(p, "Processing video... ${(p * 100).toInt()}%") }
        output
    }
}
