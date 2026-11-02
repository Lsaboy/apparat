package com.omniforge.apparat.desktop.engine

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.platform.MimeTypes
import com.omniforge.apparat.engine.image.ImageEngine
import com.omniforge.apparat.engine.image.ImageOutputFormat
import com.omniforge.apparat.engine.pdf.PdfEngine
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEngine
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext

/** What kind of files an operation takes. */
enum class BatchInput(val mimes: List<String>) {
    PDF(listOf("application/pdf")),
    IMAGE(listOf("image/*")),
    VIDEO(listOf("video/*")),
    AUDIO_OR_VIDEO(listOf("audio/*", "video/*"));

    fun accepts(file: File): Boolean {
        val mime = MimeTypes.forName(file.name) ?: return false
        return mimes.any { m -> if (m.endsWith("/*")) mime.startsWith(m.removeSuffix("*")) else mime == m }
    }
}

/** One operation applied to every file in a batch, with the same engines as the single-file tools. */
enum class BatchOperation(val label: String, val input: BatchInput, val suffix: String) {
    COMPRESS_PDF("Compress PDFs", BatchInput.PDF, "_compressed"),
    SHRINK_PHOTO("Shrink photos", BatchInput.IMAGE, "_shrunk"),
    PHOTO_UNDER_KB("Photos under a size limit", BatchInput.IMAGE, "_limit"),
    CONVERT_IMAGE("Convert photos", BatchInput.IMAGE, ""),
    STRIP_EXIF("Remove photo metadata", BatchInput.IMAGE, "_clean"),
    SHRINK_VIDEO("Shrink videos", BatchInput.VIDEO, "_shrunk"),
    CONVERT_VIDEO("Convert videos to MP4", BatchInput.VIDEO, ""),
    CONVERT_AUDIO("Convert audio", BatchInput.AUDIO_OR_VIDEO, "")
}

/** Settings for the operations that have any (the rest ignore them). */
data class BatchOptions(
    val pdfQuality: Float = 0.6f,
    val photoMaxDimension: Int = 1920,
    val photoQuality: Int = 80,
    val photoLimitKb: Int = 200,
    val imageFormat: ImageOutputFormat = ImageOutputFormat.JPEG,
    val videoPreset: VideoShrinkPreset = VideoShrinkPreset.DISCORD_FAST,
    val audioFormat: AudioConvertFormat = AudioConvertFormat.MP3,
    val audioBitrateKbps: Int = 192
)

sealed interface BatchItemState {
    data object Waiting : BatchItemState
    data class Running(val progress: Float) : BatchItemState
    data class Done(val output: File, val originalSize: Long) : BatchItemState
    data class Failed(val message: String) : BatchItemState
    data object Skipped : BatchItemState
}

data class BatchSummary(val done: Int, val failed: Int, val skipped: Int, val savedBytes: Long, val outputDir: File)

object BatchRunner {

    /**
     * Runs [operation] on each of [files] in order and writes the results into [outputDir]
     * (existing files are never overwritten). [onItem] reports each file's state as it changes.
     * One failing file doesn't stop the batch; cancelling does.
     */
    suspend fun run(
        context: Context,
        operation: BatchOperation,
        options: BatchOptions,
        files: List<File>,
        outputDir: File,
        onItem: (index: Int, state: BatchItemState) -> Unit
    ): BatchSummary {
        outputDir.mkdirs()
        require(outputDir.isDirectory && outputDir.canWrite()) { "Can't write to ${outputDir.path}" }
        var done = 0
        var failed = 0
        var skipped = 0
        var saved = 0L
        for ((i, file) in files.withIndex()) {
            coroutineContext.ensureActive()
            if (!file.isFile || !operation.input.accepts(file)) {
                skipped++
                onItem(i, BatchItemState.Skipped)
                continue
            }
            onItem(i, BatchItemState.Running(0f))
            val result = try {
                process(context, operation, options, Uri.fromFile(file)) { p -> onItem(i, BatchItemState.Running(p)) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Result.failure(t)
            }
            result.fold(
                onSuccess = { temp ->
                    val name = FileUtils.outputName(file.name, "file", operation.suffix, temp)
                    val target = FileUtils.copyToFolder(temp, outputDir, name)
                    temp.delete()
                    if (target == null) {
                        failed++
                        onItem(i, BatchItemState.Failed("Couldn't write to the output folder"))
                    } else {
                        done++
                        saved += file.length() - target.file.length()
                        onItem(i, BatchItemState.Done(target.file, file.length()))
                    }
                },
                onFailure = {
                    failed++
                    onItem(i, BatchItemState.Failed(it.localizedMessage ?: it.javaClass.simpleName))
                }
            )
        }
        return BatchSummary(done, failed, skipped, saved, outputDir)
    }

    private suspend fun process(
        context: Context,
        op: BatchOperation,
        o: BatchOptions,
        uri: Uri,
        onProgress: (Float) -> Unit
    ): Result<File> = when (op) {
        BatchOperation.COMPRESS_PDF -> PdfEngine.compressPdf(context, uri, o.pdfQuality)
        BatchOperation.SHRINK_PHOTO -> ImageEngine.shrinkImage(context, uri, o.photoQuality, o.photoMaxDimension, stripExif = true)
        BatchOperation.PHOTO_UNDER_KB -> ImageEngine.shrinkToTargetKb(context, uri, o.photoLimitKb, maxDimension = 4096)
        BatchOperation.CONVERT_IMAGE -> ImageEngine.convertFormat(context, uri, o.imageFormat)
        BatchOperation.STRIP_EXIF -> ImageEngine.stripExif(context, uri)
        BatchOperation.SHRINK_VIDEO -> VideoEngine.shrinkVideo(context, uri, o.videoPreset, VideoCodecChoice.H264) { p, _ -> onProgress(p) }
        BatchOperation.CONVERT_VIDEO -> VideoEngine.convertToMp4(context, uri) { p, _ -> onProgress(p) }
        BatchOperation.CONVERT_AUDIO -> DesktopMediaTools.convertAudio(context, uri, o.audioFormat, o.audioBitrateKbps) { p, _ -> onProgress(p) }
    }
}
