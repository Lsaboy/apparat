package com.omniforge.apparat.engine.image

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.omniforge.apparat.core.util.CodecSupport
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The one place images are decoded. Uses [ImageDecoder], which
 * - decodes straight to the requested size (no full-resolution bitmap for a 200 MP photo),
 * - applies the EXIF orientation, so portrait photos come out upright,
 * - reads HEIC/HEIF (API 28+), AVIF (API 31+) and WEBP as well as JPEG/PNG.
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
            if (!CodecSupport.canDecodeAvif && isAvif(context, uri)) {
                throw IllegalArgumentException("AVIF photos need Android 12 or newer. Convert it to JPEG on another device first.", e)
            }
            throw IllegalArgumentException("Could not read this image. The file may be damaged or in an unsupported format.", e)
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Could not open the selected image. Pick it again.", e)
        }
    }

    private fun isAvif(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return isAvifType(mime, uri.lastPathSegment)
    }

    /** True when [mime] or the name's extension says AVIF. */
    fun isAvifType(mime: String?, name: String?): Boolean {
        val lowerMime = mime?.lowercase()
        return lowerMime == "image/avif" || lowerMime == "image/avif-sequence" ||
            name?.substringAfterLast('.', "")?.lowercase() == "avif"
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

    /** The pixel budget [decode] will actually use on this phone for [maxPixels]. */
    fun effectivePixelBudget(context: Context, maxPixels: Long = DEFAULT_MAX_PIXELS): Long =
        if (isLowRam(context)) min(maxPixels, LOW_RAM_MAX_PIXELS) else maxPixels

    /** Width × height from the file header (no pixels decoded), or null if unreadable. */
    fun pixelCount(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toLong() * bounds.outHeight else null
        }
    }.getOrNull() ?: headerPixelCount(context, uri)

    private class HeaderRead : RuntimeException()

    /**
     * Fallback for formats BitmapFactory can't size on some phones (HEIC, AVIF): ImageDecoder reads the
     * header, and the listener aborts before any pixels are decoded.
     */
    private fun headerPixelCount(context: Context, uri: Uri): Long? {
        var pixels: Long? = null
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { _, info, _ ->
                if (info.size.width > 0 && info.size.height > 0) {
                    pixels = info.size.width.toLong() * info.size.height
                }
                throw HeaderRead()
            }.recycle()
        } catch (_: Throwable) {
            // HeaderRead is the expected way out; anything else means unreadable.
        }
        return pixels
    }

    private fun isLowRam(context: Context): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true

    /**
     * Quickly checks whether the image at [uri] is animated (e.g. GIF, animated WebP, animated HEIF/APNG).
     * Uses MIME/extension checks and WebP header byte inspection to avoid allocating large bitmaps.
     */
    fun isAnimated(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)?.lowercase()
        val path = uri.path?.lowercase() ?: ""
        if (mime == "image/gif" || path.endsWith(".gif")) return true
        if (mime == "image/heic-sequence" || mime == "image/heif-sequence" || mime == "image/avif-sequence") return true
        if (mime == "image/jpeg" || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
            mime == "image/bmp" || path.endsWith(".bmp")) {
            return false
        }

        // Fast path for WebP: VP8X chunk byte 20 bit 1 is ANIMATION_FLAG
        if (mime == "image/webp" || path.endsWith(".webp")) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val header = ByteArray(21)
                    var readTotal = 0
                    while (readTotal < 21) {
                        val count = stream.read(header, readTotal, 21 - readTotal)
                        if (count < 0) break
                        readTotal += count
                    }
                    if (readTotal == 21) {
                        return isAnimatedWebpHeader(header)
                    }
                }
            } catch (_: Exception) {
                // fallback below
            }
        }

        // Fallback: Safe downscaled decode with ImageDecoder
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val drawable = ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                decoder.setTargetSize(1, 1)
            }
            drawable is android.graphics.drawable.AnimatedImageDrawable
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Inspects the first 21 bytes of a WebP header to determine if the ANIMATION flag is set.
     */
    fun isAnimatedWebpHeader(header: ByteArray): Boolean {
        if (header.size < 21) return false
        val isRiff = header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                     header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte()
        val isWebp = header[8] == 'W'.code.toByte() && header[9] == 'E'.code.toByte() &&
                     header[10] == 'B'.code.toByte() && header[11] == 'P'.code.toByte()
        if (isRiff && isWebp) {
            val isVp8x = header[12] == 'V'.code.toByte() && header[13] == 'P'.code.toByte() &&
                         header[14] == '8'.code.toByte() && header[15] == 'X'.code.toByte()
            if (isVp8x) {
                val flags = header[20].toInt()
                val animationFlag = 0x02
                return (flags and animationFlag) != 0
            }
        }
        return false
    }
}
