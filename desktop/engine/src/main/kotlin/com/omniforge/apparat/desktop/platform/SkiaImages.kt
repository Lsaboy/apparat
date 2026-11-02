package com.omniforge.apparat.desktop.platform

import android.graphics.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bridges the desktop [Bitmap] to Skia for decoding, scaling and encoding. */
object SkiaImages {

    private fun info(w: Int, h: Int) = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)

    fun toImage(bitmap: Bitmap): Image {
        val buf = ByteBuffer.allocate(bitmap.width * bitmap.height * 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.asIntBuffer().put(bitmap.pixels)
        return Image.makeRaster(info(bitmap.width, bitmap.height), buf.array(), bitmap.width * 4)
    }

    fun fromImage(image: Image): Bitmap {
        val w = image.width
        val h = image.height
        val bytes = ByteArray(w * h * 4)
        val bmp = org.jetbrains.skia.Bitmap()
        bmp.allocPixels(info(w, h))
        try {
            check(image.readPixels(bmp, 0, 0)) { "Could not read image pixels" }
            val raw = bmp.readPixels(info(w, h), w * 4, 0, 0) ?: error("Could not read image pixels")
            System.arraycopy(raw, 0, bytes, 0, minOf(raw.size, bytes.size))
        } finally {
            bmp.close()
        }
        val ints = IntArray(w * h)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints)
        return Bitmap.wrap(ints, w, h)
    }

    /** Decodes JPEG/PNG/WEBP/GIF/BMP/ICO (whatever Skia reads), or null. Orientation is not applied. */
    fun decode(bytes: ByteArray): Bitmap? = runCatching {
        Image.makeFromEncoded(bytes).use { fromImage(it) }
    }.getOrNull()

    /** Width and height from the header, without decoding pixels, or null. */
    fun size(bytes: ByteArray): Pair<Int, Int>? = runCatching {
        org.jetbrains.skia.Codec.makeFromData(Data.makeFromBytes(bytes)).use { it.width to it.height }
    }.getOrNull()

    /** High-quality resize (mipmapped linear sampling: good for downscaling). */
    fun scale(src: Bitmap, w: Int, h: Int): Bitmap {
        val image = toImage(src)
        try {
            val surface = Surface.makeRaster(info(w, h).withColorAlphaType(ColorAlphaType.PREMUL))
            surface.use {
                it.canvas.drawImageRect(
                    image,
                    Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
                    Rect.makeWH(w.toFloat(), h.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                    Paint(),
                    true
                )
                return it.makeImageSnapshot().use { snap -> fromImage(snap) }
            }
        } finally {
            image.close()
        }
    }

    fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val q = quality.coerceIn(0, 100)
        toImage(bitmap).use { image ->
            val data = when (format) {
                Bitmap.CompressFormat.JPEG -> image.encodeToData(EncodedImageFormat.JPEG, q)
                Bitmap.CompressFormat.PNG -> image.encodeToData(EncodedImageFormat.PNG, 100)
                Bitmap.CompressFormat.WEBP_LOSSLESS -> image.encodeToData(EncodedImageFormat.WEBP, 100)
                Bitmap.CompressFormat.WEBP, Bitmap.CompressFormat.WEBP_LOSSY ->
                    image.encodeToData(EncodedImageFormat.WEBP, q.coerceAtMost(99))
            } ?: error("Could not encode image as $format")
            return data.use { it.bytes }
        }
    }
}
