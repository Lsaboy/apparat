package com.omniforge.apparat.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.MediaProbe
import com.omniforge.apparat.desktop.platform.SkiaImages
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Desktop version of the Android BitmapDecoder (same functions). Decodes with Skia (JPEG, PNG,
 * WEBP, GIF, BMP) and falls back to FFmpeg for HEIC/HEIF/AVIF/TIFF. EXIF orientation is applied,
 * so portrait photos come out upright, and the result is scaled to the requested limits.
 */
object BitmapDecoder {

    const val DEFAULT_MAX_PIXELS = 24_000_000L

    fun decode(
        context: Context,
        uri: Uri,
        maxDimension: Int = Int.MAX_VALUE,
        maxPixels: Long = DEFAULT_MAX_PIXELS
    ): Bitmap {
        val file = uri.file
        val bytes = try {
            file.readBytes()
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Could not open the selected image. Pick it again.", e)
        }
        // Skia applies the EXIF orientation while decoding; FFmpeg applies HEIF/TIFF rotation itself.
        val decoded = SkiaImages.decode(bytes) ?: decodeWithFfmpeg(context, file)
            ?: throw IllegalArgumentException("Could not read this image. The file may be damaged or in an unsupported format.")
        val (w, h) = targetSize(decoded.width, decoded.height, maxDimension, maxPixels)
        return if (w != decoded.width || h != decoded.height) Bitmap.createScaledBitmap(decoded, w, h, true) else decoded
    }

    private fun decodeWithFfmpeg(context: Context, file: File): Bitmap? = runCatching {
        val out = File(context.cacheDir, "work_decode_${System.nanoTime()}.png")
        try {
            runBlocking { Ffmpeg.run(listOf("-i", file.absolutePath, "-frames:v", "1", "-update", "1", out.absolutePath)) }
            SkiaImages.decode(out.readBytes())
        } finally {
            out.delete()
        }
    }.getOrNull()

    /** EXIF orientation 1..8 (1 = upright) or 1 when unknown. */
    fun orientationOf(file: File): Int = runCatching {
        ImageMetadataReader.readMetadata(file).getFirstDirectoryOfType(ExifIFD0Directory::class.java)
            ?.takeIf { it.containsTag(ExifIFD0Directory.TAG_ORIENTATION) }
            ?.getInt(ExifIFD0Directory.TAG_ORIENTATION)
    }.getOrNull() ?: 1

    /** Rotates/flips pixels so that EXIF [orientation] becomes 1. */
    fun applyOrientation(src: Bitmap, orientation: Int): Bitmap {
        if (orientation !in 2..8) return src
        val w = src.width
        val h = src.height
        val swap = orientation >= 5
        val ow = if (swap) h else w
        val oh = if (swap) w else h
        val s = src.pixels
        val d = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val (nx, ny) = when (orientation) {
                2 -> (w - 1 - x) to y
                3 -> (w - 1 - x) to (h - 1 - y)
                4 -> x to (h - 1 - y)
                5 -> y to x
                6 -> (h - 1 - y) to x
                7 -> (h - 1 - y) to (w - 1 - x)
                else -> y to (w - 1 - x) // 8
            }
            d[ny * ow + nx] = s[y * w + x]
        }
        return Bitmap.wrap(d, ow, oh)
    }

    fun isAvifType(mime: String?, name: String?): Boolean {
        val lowerMime = mime?.lowercase()
        return lowerMime == "image/avif" || lowerMime == "image/avif-sequence" ||
            name?.substringAfterLast('.', "")?.lowercase() == "avif"
    }

    fun targetSize(width: Int, height: Int, maxDimension: Int, maxPixels: Long): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        var scale = 1.0
        val longest = max(width, height)
        if (longest > maxDimension) scale = min(scale, maxDimension.toDouble() / longest)
        val pixels = width.toLong() * height
        if (pixels > maxPixels) scale = min(scale, sqrt(maxPixels.toDouble() / pixels))
        if (scale >= 1.0) return width to height
        return max(1, (width * scale).roundToInt().coerceAtMost(width)) to
            max(1, (height * scale).roundToInt().coerceAtMost(height))
    }

    /** Desktops have plenty of memory: the budget is what was asked for. */
    fun effectivePixelBudget(context: Context, maxPixels: Long = DEFAULT_MAX_PIXELS): Long = maxPixels

    /** Width × height from the file header (no pixels decoded), or null if unreadable. */
    fun pixelCount(context: Context, uri: Uri): Long? = runCatching {
        val file = uri.file
        val header = file.inputStream().use { it.readNBytes(min(file.length(), 4L * 1024 * 1024).toInt()) }
        SkiaImages.size(header)?.let { (w, h) -> w.toLong() * h }
            ?: MediaProbe.of(file)?.takeIf { it.width > 0 }?.let { it.width.toLong() * it.height }
    }.getOrNull()

    fun isAnimated(context: Context, uri: Uri): Boolean {
        val path = uri.path.lowercase()
        if (path.endsWith(".gif")) return true
        if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".bmp")) return false
        if (path.endsWith(".webp")) {
            runCatching {
                uri.file.inputStream().use { s ->
                    val header = s.readNBytes(21)
                    if (header.size == 21) return isAnimatedWebpHeader(header)
                }
            }
        }
        return false
    }

    fun isAnimatedWebpHeader(header: ByteArray): Boolean {
        if (header.size < 21) return false
        val isRiff = header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
        val isWebp = header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
        if (isRiff && isWebp) {
            val isVp8x = header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()
            if (isVp8x) return (header[20].toInt() and 0x02) != 0
        }
        return false
    }
}
