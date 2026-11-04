package com.omniforge.apparat.desktop.engine

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.FfmpegEnv
import com.omniforge.apparat.desktop.platform.HwAccel
import com.omniforge.apparat.desktop.platform.MediaProbe
import com.omniforge.apparat.engine.video.EncodeJob
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEncoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A crop in displayed pixels (after the rotation flag is applied), from the top-left corner. */
data class CropRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** Aspect presets for the crop tool (null ratio: free, use the rectangle as given). */
enum class CropAspect(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1", 1, 1),
    PORTRAIT("9:16", 9, 16),
    LANDSCAPE("16:9", 16, 9),
    FOUR_FIVE("4:5", 4, 5),
    FOUR_THREE("4:3", 4, 3)
}

enum class FrameFormat(val label: String, val extension: String) { JPG("JPG", ".jpg"), PNG("PNG", ".png") }

/** How a join was done. */
enum class JoinMode { LOSSLESS, REENCODED }

/**
 * Desktop-only video tools: join, crop, frames / contact sheet, resolution and frame rate.
 * Same conventions as [DesktopMediaTools].
 */
object DesktopVideoTools {

    /** Output heights offered by Change Resolution (the source size is always also allowed). */
    val HEIGHTS = listOf(2160, 1440, 1080, 720, 480, 360)
    val FRAME_RATES = listOf(24.0, 25.0, 30.0, 50.0, 60.0)

    private suspend fun <T> catching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun probe(uri: Uri): MediaProbe =
        MediaProbe.of(uri.file) ?: throw IllegalStateException("${uri.file.name} can't be read (it may be damaged).")

    private fun even(v: Int) = max(2, v / 2 * 2)

    private val mp4Out = listOf("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-metadata:s:v:0", "rotate=0")

    private suspend fun runInto(args: List<String>, output: File, duration: Double, env: FfmpegEnv = FfmpegEnv.NONE, onProgress: (Float) -> Unit) {
        try {
            Ffmpeg.run(args + output.absolutePath, duration, env, onProgress)
            check(output.isFile && output.length() > 0) { "FFmpeg produced no output." }
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    // ---------------------------------------------------------------- join

    /** True when [clips] can be joined by copying streams (same codecs, size, rate and audio layout). */
    internal fun canJoinLosslessly(clips: List<MediaProbe>): Boolean {
        val first = clips.firstOrNull() ?: return false
        if (first.videoCodec !in setOf("h264", "hevc")) return false
        return clips.all { c ->
            c.hasVideo && c.videoCodec == first.videoCodec && c.width == first.width && c.height == first.height &&
                c.rotation == first.rotation && abs((c.fps ?: 0.0) - (first.fps ?: 0.0)) < 0.01 &&
                c.formatName?.contains("mp4") == true && c.hasAudio == first.hasAudio &&
                (!c.hasAudio || (c.audioCodec == "aac" && c.audioCodec == first.audioCodec &&
                    c.audioChannels == first.audioChannels && c.audioSampleRate == first.audioSampleRate))
        }
    }

    private fun concatPath(file: File): String = file.absolutePath.replace('\\', '/').replace("'", "'\\''")

    /**
     * Joins [uris] in order into one MP4. Matching clips (e.g. from the same camera) are joined
     * losslessly; otherwise every clip is fitted (letterboxed) to the first clip's size and frame rate
     * and re-encoded to H.264. Clips without sound get silence.
     */
    suspend fun joinVideos(
        context: Context,
        uris: List<Uri>,
        forceReencode: Boolean = false,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Pair<File, JoinMode>> = catching {
        if (uris.size < 2) throw IllegalArgumentException("Pick at least two videos to join.")
        val clips = uris.map { probe(it) }
        clips.forEachIndexed { i, c -> if (!c.hasVideo) throw IllegalStateException("${uris[i].file.name} has no video track.") }
        val total = clips.sumOf { it.durationSec }
        val output = FileUtils.createTempOutputFile(context, "joined", ".mp4")

        if (!forceReencode && canJoinLosslessly(clips)) {
            val list = File(context.cacheDir, "join_${System.nanoTime()}.txt")
            try {
                list.writeText("ffconcat version 1.0\n" + uris.joinToString("") { "file '${concatPath(it.file)}'\n" })
                onProgress(0f, "Joining (no re-encoding)...")
                runInto(
                    listOf("-f", "concat", "-safe", "0", "-i", list.absolutePath, "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy", "-movflags", "+faststart"),
                    output, total
                ) { p -> onProgress(p, "Joining... ${(p * 100).toInt()}%") }
            } finally {
                list.delete()
            }
            return@catching output to JoinMode.LOSSLESS
        }

        val first = clips.first()
        val w = even(first.displayWidth)
        val h = even(first.displayHeight)
        val fps = (first.fps ?: 30.0).coerceIn(1.0, 120.0)
        val withAudio = clips.any { it.hasAudio }
        val fpsText = String.format(Locale.ROOT, "%.3f", fps)
        val job = EncodeJob(first, w, h, fps = fps)
        val backend = withContext(Dispatchers.IO) { HwAccel.backend() }
        VideoEncoding.encode(
            VideoCodecChoice.H264, job, output, backend,
            onAttempt = { plan, retry -> onProgress(0f, if (retry) "Joining: retrying with ${plan.label}..." else "Joining (${plan.label})...") }
        ) { plan, target ->
            val graph = StringBuilder()
            clips.forEachIndexed { i, c ->
                graph.append("[$i:v]scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=$fpsText,format=yuv420p[v$i];")
                if (withAudio) {
                    if (c.hasAudio) graph.append("[$i:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo[a$i];")
                    else graph.append("anullsrc=r=48000:cl=stereo,atrim=duration=${String.format(Locale.ROOT, "%.3f", c.durationSec)},aformat=sample_fmts=fltp[a$i];")
                }
            }
            clips.indices.forEach { i -> graph.append("[v$i]"); if (withAudio) graph.append("[a$i]") }
            graph.append("concat=n=${clips.size}:v=1:a=${if (withAudio) 1 else 0}")
            graph.append(if (withAudio) "[vc][a]" else "[vc]")
            graph.append(";[vc]null${plan.complexSuffix}[v]")
            // Device options only: GPU decoding is set per input and can't feed the software scaler for every clip.
            val args = plan.global + uris.flatMap { listOf("-i", it.file.absolutePath) } +
                listOf("-filter_complex", graph.toString(), "-map", "[v]") + (if (withAudio) listOf("-map", "[a]") else emptyList())
            runInto(args + plan.encoder + mp4Out, target, total, plan.env) { p ->
                onProgress(p, "Joining (${plan.plan.label})... ${(p * 100).toInt()}%")
            }
        }
        output to JoinMode.REENCODED
    }

    // ---------------------------------------------------------------- crop

    /** The largest centred rectangle of [aspect] inside a [width] x [height] picture, even sides. */
    fun centeredCrop(width: Int, height: Int, aspect: CropAspect): CropRect {
        val target = aspect.w.toDouble() / aspect.h
        var cw = width
        var ch = (width / target).roundToInt()
        if (ch > height) {
            ch = height
            cw = (height * target).roundToInt()
        }
        cw = even(min(cw, width)); ch = even(min(ch, height))
        return CropRect(((width - cw) / 2) and 1.inv(), ((height - ch) / 2) and 1.inv(), cw, ch)
    }

    /** [rect] clamped into a [width] x [height] picture, with even size and position. */
    internal fun clampCrop(rect: CropRect, width: Int, height: Int): CropRect {
        val w = even(rect.width.coerceIn(2, width))
        val h = even(rect.height.coerceIn(2, height))
        val x = (rect.x.coerceIn(0, width - w)) / 2 * 2
        val y = (rect.y.coerceIn(0, height - h)) / 2 * 2
        return CropRect(x, y, w, h)
    }

    /** Crops [uri] to [rect] (displayed pixels) and re-encodes to H.264, keeping the sound. */
    suspend fun cropVideo(
        context: Context,
        uri: Uri,
        rect: CropRect,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = catching {
        val info = probe(uri)
        if (!info.hasVideo) throw IllegalStateException("This file has no video track.")
        val r = clampCrop(rect, info.displayWidth, info.displayHeight)
        if (r.width >= info.displayWidth - 1 && r.height >= info.displayHeight - 1) {
            throw IllegalArgumentException("The crop covers the whole picture. Make it smaller first.")
        }
        val output = FileUtils.createTempOutputFile(context, "cropped", ".mp4")
        val job = EncodeJob(info, r.width, r.height)
        val backend = withContext(Dispatchers.IO) { HwAccel.backend() }
        VideoEncoding.encode(
            VideoCodecChoice.H264, job, output, backend,
            onAttempt = { plan, retry -> onProgress(0f, if (retry) "Cropping: retrying with ${plan.label}..." else "Cropping (${plan.label})...") }
        ) { plan, target ->
            val args = plan.input + listOf("-i", uri.file.absolutePath, "-map", "0:v:0", "-map", "0:a:0?") +
                plan.vfArgs(listOf("crop=${r.width}:${r.height}:${r.x}:${r.y}"))
            runInto(args + plan.encoder + mp4Out, target, info.durationSec, plan.env) { p ->
                onProgress(p, "Cropping (${plan.plan.label})... ${(p * 100).toInt()}%")
            }
        }
        output
    }

    // ---------------------------------------------------------------- resolution / frame rate

    /**
     * Re-encodes [uri] to H.264 at [height] displayed pixels (null: unchanged; width follows the
     * aspect ratio) and/or [fps] (null: unchanged).
     */
    suspend fun changeResolution(
        context: Context,
        uri: Uri,
        height: Int?,
        fps: Double?,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = catching {
        val info = probe(uri)
        if (!info.hasVideo) throw IllegalStateException("This file has no video track.")
        val sameHeight = height == null || height == info.displayHeight
        val sameFps = fps == null || (info.fps != null && abs(info.fps - fps) < 0.01)
        if (sameHeight && sameFps) throw IllegalArgumentException("Choose a different resolution or frame rate first.")
        val (outW, outH) = if (height == null) VideoEncoding.evenSize(info.displayWidth, info.displayHeight)
        else VideoEncoding.evenSize(info.displayWidth, info.displayHeight, height.toFloat() / info.displayHeight)
        val outFps = fps ?: info.fps
        // The frame rate is an output option, so the only filter is the resize and the full-GPU path stays open.
        val job = EncodeJob(info, outW, outH, allowFullGpu = true, fps = outFps)
        val output = FileUtils.createTempOutputFile(context, "resized", ".mp4")
        val backend = withContext(Dispatchers.IO) { HwAccel.backend() }
        VideoEncoding.encode(
            VideoCodecChoice.H264, job, output, backend,
            onAttempt = { plan, retry -> onProgress(0f, if (retry) "Converting: retrying with ${plan.label}..." else "Converting (${plan.label})...") }
        ) { plan, target ->
            val rate = if (fps != null) listOf("-r", String.format(Locale.ROOT, "%.3f", fps)) else emptyList()
            val args = plan.input + listOf("-i", uri.file.absolutePath, "-map", "0:v:0", "-map", "0:a:0?") +
                plan.vfArgs(listOf("scale=$outW:$outH")) + rate
            runInto(args + plan.encoder + mp4Out, target, info.durationSec, plan.env) { p ->
                onProgress(p, "Converting (${plan.plan.label})... ${(p * 100).toInt()}%")
            }
        }
        output
    }

    // ---------------------------------------------------------------- frames

    /** [count] evenly spaced times inside a [duration]-second clip (middle of each slice). */
    internal fun frameTimes(duration: Double, count: Int): List<Double> {
        val n = count.coerceAtLeast(1)
        val usable = max(duration - 0.05, 0.0)
        return List(n) { i -> (i + 0.5) * usable / n }
    }

    private suspend fun grabFrame(uri: Uri, time: Double, maxWidth: Int?, out: File) {
        val scale = maxWidth?.let { listOf("-vf", "scale='min($it,iw)':-2") }.orEmpty()
        val quality = if (out.extension == "jpg") listOf("-q:v", "2") else emptyList()
        Ffmpeg.run(
            listOf("-ss", String.format(Locale.ROOT, "%.3f", time), "-i", uri.file.absolutePath, "-frames:v", "1", "-an") +
                scale + quality + out.absolutePath
        )
        if (!out.isFile || out.length() == 0L) throw IllegalStateException("Could not read a frame at ${timestamp(time)}.")
    }

    internal fun timestamp(seconds: Double): String {
        val s = seconds.toInt()
        return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
    }

    /** Saves [count] evenly spaced frames of [uri] as [format] images, packed into one ZIP. */
    suspend fun extractFrames(
        context: Context,
        uri: Uri,
        count: Int,
        format: FrameFormat,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = catching {
        val info = probe(uri)
        if (!info.hasVideo) throw IllegalStateException("This file has no video track.")
        val n = count.coerceIn(1, 500)
        val output = FileUtils.createTempOutputFile(context, "frames", ".zip")
        val work = File(context.cacheDir, "frames_${System.nanoTime()}").apply { mkdirs() }
        try {
            val times = frameTimes(info.durationSec, n)
            val digits = n.toString().length
            val base = uri.file.nameWithoutExtension
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                times.forEachIndexed { i, t ->
                    onProgress(i.toFloat() / n, "Saving frame ${i + 1} of $n...")
                    val f = File(work, "f$i${format.extension}")
                    grabFrame(uri, t, null, f)
                    val name = "${base}_${(i + 1).toString().padStart(digits, '0')}_${timestamp(t).replace(':', '-')}${format.extension}"
                    zip.putNextEntry(ZipEntry(name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    f.delete()
                }
            }
            onProgress(1f, "Done")
            output
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * One JPG with [columns] x [rows] evenly spaced frames of [uri], each labelled with its time,
     * under a header with the file name, length and size. [width] is the sheet's width in pixels.
     */
    suspend fun contactSheet(
        context: Context,
        uri: Uri,
        columns: Int,
        rows: Int,
        width: Int = 1920,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = catching {
        val info = probe(uri)
        if (!info.hasVideo) throw IllegalStateException("This file has no video track.")
        val cols = columns.coerceIn(1, 12)
        val rws = rows.coerceIn(1, 20)
        val n = cols * rws
        val gap = max(4, width / 240)
        val tileW = (width - gap * (cols + 1)) / cols
        val tileH = max(1, (tileW.toDouble() * info.displayHeight / max(1, info.displayWidth)).roundToInt())
        val header = max(40, width / 30)
        val sheetH = header + gap + rws * (tileH + gap)
        val output = FileUtils.createTempOutputFile(context, "contact_sheet", ".jpg")
        val work = File(context.cacheDir, "sheet_${System.nanoTime()}").apply { mkdirs() }
        try {
            val sheet = BufferedImage(width, sheetH, BufferedImage.TYPE_INT_RGB)
            val g = sheet.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.color = Color(0x16, 0x16, 0x16); g.fillRect(0, 0, width, sheetH)
                g.color = Color(0xEE, 0xEE, 0xEE)
                g.font = Font(Font.SANS_SERIF, Font.BOLD, header * 4 / 10)
                val sizeMb = uri.file.length() / 1_048_576.0
                val title = "${uri.file.name}   ·   ${timestamp(info.durationSec)}   ·   ${info.displayWidth}x${info.displayHeight}" +
                    String.format(Locale.ROOT, "   ·   %.1f MB", sizeMb)
                g.drawString(title, gap * 2, header * 2 / 3)
                val label = Font(Font.SANS_SERIF, Font.BOLD, max(10, tileH / 9))
                frameTimes(info.durationSec, n).forEachIndexed { i, t ->
                    onProgress(i.toFloat() / n, "Reading frame ${i + 1} of $n...")
                    val f = File(work, "t$i.png")
                    grabFrame(uri, t, tileW * 2, f)
                    val img = ImageIO.read(f) ?: throw IllegalStateException("Could not read a frame at ${timestamp(t)}.")
                    val x = gap + (i % cols) * (tileW + gap)
                    val y = header + gap + (i / cols) * (tileH + gap)
                    g.drawImage(img, x, y, tileW, tileH, null)
                    f.delete()
                    val text = timestamp(t)
                    g.font = label
                    val fm = g.fontMetrics
                    val tx = x + tileW - fm.stringWidth(text) - gap
                    val ty = y + tileH - gap
                    g.stroke = BasicStroke(max(2f, label.size / 6f))
                    val outline = g.font.createGlyphVector(g.fontRenderContext, text).getOutline(tx.toFloat(), ty.toFloat())
                    g.color = Color(0, 0, 0, 200); g.draw(outline)
                    g.color = Color.WHITE; g.fill(outline)
                }
            } finally {
                g.dispose()
            }
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            try {
                val param = writer.defaultWriteParam.apply {
                    compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = 0.9f
                }
                ImageIO.createImageOutputStream(output).use { ios ->
                    writer.output = ios
                    writer.write(null, javax.imageio.IIOImage(sheet, null, null), param)
                }
            } finally {
                writer.dispose()
            }
            onProgress(1f, "Done")
            output
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            work.deleteRecursively()
        }
    }

    /** Rows needed to show about [frames] tiles in [columns] columns. */
    fun rowsFor(frames: Int, columns: Int): Int = ceil(frames.toDouble() / columns).toInt().coerceAtLeast(1)
}
