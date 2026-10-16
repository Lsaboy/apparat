package com.omniforge.app.engine.image

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The one place images are decoded. Uses [ImageDecoder], which
 * - decodes straight to the requested size (no full-resolution bitmap for a 200 MP photo),
 * - applies the EXIF orientation, so portrait photos come out upright,
 * - reads HEIC/HEIF and WEBP as well as JPEG/PNG.
 */
object BitmapDecoder {

    /** 24 MP ≈ 96 MB as ARGB_8888. Plenty for print-quality output. */
    const val DEFAULT_MAX_PIXELS = 24_000_000L
    private const val LOW_RAM_MAX_PIXELS = 12_000_000L

    /**
     * Decodes [uri] so that its longest side is at most [maxDimension] and its pixel count at most
     * [maxPixels] (lower on low-RAM devices). Aspect ratio is kept; images are never upscaled.
     * The result is a software bitmap (safe for getPixels, Canvas and PDFBox).
     */
    fun decode(
        context: Context,
        uri: Uri,
        maxDimension: Int = Int.MAX_VALUE,
        maxPixels: Long = DEFAULT_MAX_PIXELS
    ): Bitmap {
        val pixelBudget = if (isLowRam(context)) min(maxPixels, LOW_RAM_MAX_PIXELS) else maxPixels
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return try {
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = targetSize(info.size.width, info.size.height, maxDimension, pixelBudget)
                if (w != info.size.width || h != info.size.height) decoder.setTargetSize(w, h)
            }
        } catch (e: ImageDecoder.DecodeException) {
            throw IllegalArgumentException("Could not read this image. The file may be damaged or in an unsupported format.", e)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Could not open the selected image. Pick it again.", e)
        }
    }

    /** Largest size ≤ the source that fits both limits, keeping aspect ratio. */
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

    private fun isLowRam(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
}
