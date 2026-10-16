package com.omniforge.app.engine.video

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.collect.ImmutableList
import com.omniforge.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Target for Video Shrinker. [shortSide] is the output's smaller dimension (so portrait and
 * landscape clips get the same treatment); [bitrate] is the requested H.264 bitrate at that size.
 */
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
 * All video work runs on Media3 Transformer (hardware codecs, no bundled FFmpeg).
 * With no effects, Transformer copies streams instead of re-encoding (trim, mute, remux, AAC rip).
 */
@OptIn(UnstableApi::class)
object VideoEngine {

    const val MP4_MIME = "video/mp4"

    suspend fun shrinkVideo(
        context: Context,
        inputUri: Uri,
        preset: VideoShrinkPreset = VideoShrinkPreset.DISCORD_FAST,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val info = readVideoInfo(context, inputUri)
        val effects = mutableListOf<Effect>()
        var bitrate = preset.bitrate
        if (info != null && info.shortSide > preset.shortSide) {
            val scale = preset.shortSide.toFloat() / info.shortSide
            effects += Presentation.createForWidthAndHeight(
                even(info.width * scale), even(info.height * scale), Presentation.LAYOUT_SCALE_TO_FIT
            )
        } else if (info != null) {
            // Already small: keep size, just re-encode at a lower bitrate.
            val pixelRatio = (info.width.toFloat() * info.height) / (preset.shortSide * preset.shortSide * 16f / 9f)
            bitrate = (preset.bitrate * pixelRatio).roundToInt()
        }
        info?.bitrate?.takeIf { it > 0 }?.let { bitrate = min(bitrate, (it * 0.8f).roundToInt()) }
        bitrate = max(bitrate, 200_000)

        val output = FileUtils.createTempOutputFile(context, "shrunk", ".mp4")
        val item = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
            .setEffects(Effects(emptyList(), effects))
            .build()
        onProgress(0f, "Compressing video (${preset.label})...")
        runTransformer(context, item, output, forceVideoEncode = true, videoBitrate = bitrate) { p ->
            onProgress(p, "Compressing video... ${(p * 100).toInt()}%")
        }
        output
    }

    /** Re-packages any supported input (MKV, WEBM, MOV, 3GP…) as an H.264/AAC MP4. */
    suspend fun convertToMp4(
        context: Context,
        inputUri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val output = FileUtils.createTempOutputFile(context, "converted", ".mp4")
        val item = EditedMediaItem.Builder(MediaItem.fromUri(inputUri)).build()
        onProgress(0f, "Converting to MP4...")
        runTransformer(
            context, item, output,
            videoMimeType = MimeTypes.VIDEO_H264,
            audioMimeType = MimeTypes.AUDIO_AAC
        ) { p -> onProgress(p, "Converting to MP4... ${(p * 100).toInt()}%") }
        output
    }

    suspend fun burnSubtitles(
        context: Context,
        videoUri: Uri,
        subtitleUri: Uri,
        fontSize: Int = 20,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(subtitleUri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("Could not read subtitle file")
        }
        val cues = SubtitleParser.parse(text)
        if (cues.isEmpty()) throw IllegalArgumentException("No subtitles found. Use a .srt or .vtt file.")

        val output = FileUtils.createTempOutputFile(context, "captioned", ".mp4")
        val overlay = SubtitleOverlay(cues, fontSize)
        val item = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of(overlay)))))
            .build()
        onProgress(0f, "Burning subtitles...")
        runTransformer(context, item, output, forceVideoEncode = true) { p ->
            onProgress(p, "Burning subtitles... ${(p * 100).toInt()}%")
        }
        output
    }

    suspend fun extractAudio(
        context: Context,
        videoUri: Uri,
        targetFormat: AudioOutputFormat,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        val output = FileUtils.createTempOutputFile(context, "extracted_audio", targetFormat.extension)
        when (targetFormat) {
            AudioOutputFormat.M4A -> {
                val item = EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setRemoveVideo(true).build()
                onProgress(0f, "Extracting audio...")
                runTransformer(context, item, output, audioMimeType = MimeTypes.AUDIO_AAC) { p ->
                    onProgress(p, "Extracting audio... ${(p * 100).toInt()}%")
                }
            }
            AudioOutputFormat.WAV -> withContext(Dispatchers.IO) {
                decodeToWav(context, videoUri, output) { p -> onProgress(p, "Decoding audio... ${(p * 100).toInt()}%") }
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
        val output = FileUtils.createTempOutputFile(context, "muted", ".mp4")
        val item = EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setRemoveAudio(true).build()
        runTransformer(context, item, output) { p -> onProgress(p, "Removing audio... ${(p * 100).toInt()}%") }
        output
    }

    /**
     * Cuts [startSec, startSec + durationSec]. Streams are copied; trim optimisation only
     * re-encodes the few frames before the first keyframe so the cut stays accurate.
     */
    suspend fun losslessTrim(
        context: Context,
        videoUri: Uri,
        startSec: Float,
        durationSec: Float,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = runCatchingCancellable {
        require(startSec >= 0f) { "Start time can't be negative" }
        require(durationSec > 0f) { "Duration must be more than 0 seconds" }
        val info = readVideoInfo(context, videoUri)
        if (info != null && info.durationMs > 0 && startSec * 1000 >= info.durationMs) {
            throw IllegalArgumentException("Start time is past the end of the video (${info.durationMs / 1000f}s)")
        }
        val startMs = (startSec * 1000).toLong()
        val endMs = startMs + (durationSec * 1000).toLong()
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(if (info != null && info.durationMs in 1..endMs) C.TIME_END_OF_SOURCE else endMs)
                    .build()
            )
            .build()
        val output = FileUtils.createTempOutputFile(context, "trimmed", ".mp4")
        runTransformer(context, EditedMediaItem.Builder(mediaItem).build(), output, trimOptimization = true) { p ->
            onProgress(p, "Trimming... ${(p * 100).toInt()}%")
        }
        output
    }

    /** Pulls frames with MediaMetadataRetriever and writes them with our own GIF encoder. */
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
            val output = FileUtils.createTempOutputFile(context, "animated", ".gif")
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val startMs = (startSec * 1000).toLong()
                val endMs = if (durationMs > 0) min(durationMs, startMs + (durationSec * 1000).toLong()) else startMs + (durationSec * 1000).toLong()
                if (endMs <= startMs) throw IllegalArgumentException("Start time is past the end of the video")

                val info = readVideoInfo(retriever)
                val outW = if (info != null) min(width, info.width) else width
                val outH = if (info != null) max(2, (outW.toFloat() * info.height / info.width).roundToInt()) else outW * 9 / 16

                val frameDelayMs = 1000 / fps.coerceIn(1, 50)
                val frameCount = max(1, ((endMs - startMs) / frameDelayMs).toInt())
                FileOutputStream(output).buffered().use { out ->
                    val encoder = GifEncoder(out, outW, outH, frameDelayMs)
                    for (i in 0 until frameCount) {
                        coroutineContext.ensureActive()
                        val timeUs = (startMs + i.toLong() * frameDelayMs) * 1000
                        val frame = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, outW, outH)
                            ?: continue
                        val sized = if (frame.width != outW || frame.height != outH) {
                            Bitmap.createScaledBitmap(frame, outW, outH, true).also { if (it != frame) frame.recycle() }
                        } else frame
                        encoder.addFrame(sized)
                        sized.recycle()
                        withContext(Dispatchers.Main) {
                            onProgress((i + 1f) / frameCount, "Encoding GIF frame ${i + 1} of $frameCount...")
                        }
                    }
                    encoder.finish()
                }
            } catch (t: Throwable) {
                output.delete()
                throw t
            } finally {
                retriever.release()
            }
            output
        }
    }

    // ---------------------------------------------------------------- internals

    private data class VideoInfo(val width: Int, val height: Int, val durationMs: Long, val bitrate: Int?) {
        val shortSide get() = min(width, height)
    }

    /** Displayed size (rotation applied), duration and bitrate, or null when unreadable. */
    private fun readVideoInfo(context: Context, uri: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            readVideoInfo(retriever)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun readVideoInfo(retriever: MediaMetadataRetriever): VideoInfo? {
        val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
        val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
        val rotated = rotation % 180 != 0
        return VideoInfo(if (rotated) h else w, if (rotated) w else h, duration, bitrate)
    }

    private fun even(value: Float): Int = max(2, (value / 2).roundToInt() * 2)

    /**
     * Runs one Transformer export on the main looper and suspends until it finishes.
     * Cancelling the coroutine cancels the export and deletes the partial output.
     */
    private suspend fun runTransformer(
        context: Context,
        item: EditedMediaItem,
        output: File,
        videoMimeType: String? = null,
        audioMimeType: String? = null,
        forceVideoEncode: Boolean = false,
        videoBitrate: Int? = null,
        trimOptimization: Boolean = false,
        onProgress: (Float) -> Unit
    ): Unit = withContext(Dispatchers.Main) {
        val builder = Transformer.Builder(context.applicationContext)
        if (forceVideoEncode || videoMimeType != null) builder.setVideoMimeType(videoMimeType ?: MimeTypes.VIDEO_H264)
        audioMimeType?.let { builder.setAudioMimeType(it) }
        if (videoBitrate != null) {
            builder.setEncoderFactory(
                DefaultEncoderFactory.Builder(context.applicationContext)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                    .setEnableFallback(true)
                    .build()
            )
        }
        if (trimOptimization) builder.experimentalSetTrimOptimizationEnabled(true)

        val progressHolder = ProgressHolder()
        var poller: kotlinx.coroutines.Job? = null
        try {
        suspendCancellableCoroutine { cont ->
            val transformer = builder.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    output.delete()
                    if (cont.isActive) cont.resumeWithException(friendlyError(exportException))
                }
            }).build()

            poller = launch {
                while (true) {
                    if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    delay(250)
                }
            }
            cont.invokeOnCancellation {
                // invokeOnCancellation may run off the main thread; Transformer must be touched on its looper.
                android.os.Handler(transformer.applicationLooper).post {
                    transformer.cancel()
                    output.delete()
                }
            }
            val shouldToneMap = (forceVideoEncode || videoMimeType != null || item.effects.videoEffects.isNotEmpty()) && !item.removeVideo
            val composition = Composition.Builder(EditedMediaItemSequence(item)).apply {
                if (shouldToneMap) {
                    setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                }
            }.build()
            transformer.start(composition, output.absolutePath)
        }
        } finally {
            poller?.cancel()
        }
        onProgress(1f)
    }

    private fun friendlyError(e: ExportException): Exception {
        val reason = when (e.errorCode) {
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_DECODER_INIT_FAILED -> "This phone can't decode this video format."
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED -> "This phone's video encoder doesn't support these settings."
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            ExportException.ERROR_CODE_IO_NO_PERMISSION -> "Could not read the selected file. Pick it again."
            ExportException.ERROR_CODE_MUXING_FAILED -> "Could not write the output file."
            else -> "Processing failed (${e.errorCodeName})."
        }
        return IllegalStateException(reason, e)
    }

    /**
     * Decodes the first audio track to 16-bit PCM and writes a WAV file.
     * The header is written last, once the real data size is known.
     */
    private suspend fun decodeToWav(context: Context, uri: Uri, output: File, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IllegalArgumentException("Selected file contains no audio track")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloatPcm = false
            var dataBytes = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            RandomAccessFile(output, "rw").use { raf ->
                raf.setLength(0)
                raf.write(ByteArray(44)) // header placeholder
                var chunk = ByteArray(0)
                var shortChunk = ByteArray(0)
                while (!outputDone) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buffer = codec.getInputBuffer(inIndex)!!
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else {
                                AudioFormat.ENCODING_PCM_16BIT
                            }
                            isFloatPcm = (encoding == AudioFormat.ENCODING_PCM_FLOAT)
                        }
                        outIndex >= 0 -> {
                            if (info.size > 0) {
                                val buffer = codec.getOutputBuffer(outIndex)!!
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                if (isFloatPcm) {
                                    val floatBuf = buffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                                    val numFloats = floatBuf.remaining()
                                    val requiredBytes = numFloats * 2
                                    if (shortChunk.size < requiredBytes) shortChunk = ByteArray(requiredBytes)
                                    val outBuf = ByteBuffer.wrap(shortChunk).order(ByteOrder.LITTLE_ENDIAN)
                                    outBuf.clear()
                                    while (floatBuf.hasRemaining()) {
                                        val sample = floatBuf.get()
                                        val s16 = (sample.coerceIn(-1.0f, 1.0f) * 32767.0f).roundToInt().toShort()
                                        outBuf.putShort(s16)
                                    }
                                    raf.write(shortChunk, 0, requiredBytes)
                                    dataBytes += requiredBytes
                                } else {
                                    if (chunk.size < info.size) chunk = ByteArray(info.size)
                                    buffer.get(chunk, 0, info.size)
                                    raf.write(chunk, 0, info.size)
                                    dataBytes += info.size
                                }
                                if (durationUs > 0) {
                                    val p = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                                    withContext(Dispatchers.Main) { onProgress(p) }
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
                if (dataBytes > 0xFFFFFFFFL - 36) throw IllegalStateException("Audio is too long for a WAV file. Use M4A instead.")
                raf.seek(0)
                raf.write(wavHeader(dataBytes, sampleRate, channels))
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun wavHeader(dataBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort())
            putInt(sampleRate); putInt(byteRate); putShort((channels * bitsPerSample / 8).toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray()); putInt(dataBytes.toInt())
        }.array()
    }

    /** Like runCatching, but lets coroutine cancellation propagate. */
    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
