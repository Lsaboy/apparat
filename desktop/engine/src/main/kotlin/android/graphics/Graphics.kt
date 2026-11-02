package android.graphics

import com.omniforge.apparat.desktop.platform.SkiaImages
import java.io.OutputStream

/**
 * Desktop stand-in for android.graphics.Bitmap: non-premultiplied ARGB pixels in an [IntArray].
 * Encoding goes through Skia (JPEG/PNG/WEBP), like the Android original.
 */
class Bitmap private constructor(val width: Int, val height: Int, internal var data: IntArray, val config: Config) {

    enum class Config { ARGB_8888, RGB_565, ALPHA_8, HARDWARE, RGBA_F16 }

    enum class CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }

    var isRecycled: Boolean = false
        private set

    /** The raw pixel array (row by row, ARGB). */
    val pixels: IntArray get() = data

    fun recycle() {
        isRecycled = true
    }

    fun hasAlpha(): Boolean {
        for (p in data) if ((p ushr 24) != 0xFF) return true
        return false
    }

    fun getPixel(x: Int, y: Int): Int = data[y * width + x]

    fun setPixel(x: Int, y: Int, color: Int) {
        data[y * width + x] = color
    }

    fun getPixels(out: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) System.arraycopy(data, (y + row) * width + x, out, offset + row * stride, w)
    }

    fun setPixels(src: IntArray, offset: Int, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) System.arraycopy(src, offset + row * stride, data, (y + row) * width + x, w)
    }

    fun eraseColor(color: Int) = data.fill(color)

    fun copy(config: Config, isMutable: Boolean): Bitmap = Bitmap(width, height, data.copyOf(), config)

    /** Writes the bitmap as [format]. [quality] (0-100) applies to JPEG and lossy WEBP. */
    fun compress(format: CompressFormat, quality: Int, stream: OutputStream): Boolean = runCatching {
        stream.write(SkiaImages.encode(this, format, quality))
        true
    }.getOrDefault(false)

    companion object {
        @JvmStatic
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap {
            require(width > 0 && height > 0) { "width and height must be > 0" }
            return Bitmap(width, height, IntArray(width * height), config)
        }

        @JvmStatic
        fun createBitmap(pixels: IntArray, width: Int, height: Int, config: Config): Bitmap =
            Bitmap(width, height, pixels.copyOf(width * height), config)

        /** Wraps [pixels] without copying. */
        @JvmStatic
        fun wrap(pixels: IntArray, width: Int, height: Int): Bitmap = Bitmap(width, height, pixels, Config.ARGB_8888)

        @JvmStatic
        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap =
            if (src.width == dstWidth && src.height == dstHeight) src else SkiaImages.scale(src, dstWidth, dstHeight)
    }
}

/** Desktop stand-in for android.graphics.Canvas over a [Bitmap] (only what the app uses). */
class Canvas(private val bitmap: Bitmap) {
    fun drawColor(color: Int) {
        val a = color ushr 24
        if (a == 0xFF) { bitmap.eraseColor(color); return }
        val d = bitmap.pixels
        for (i in d.indices) d[i] = Color.blend(color, d[i])
    }

    fun drawBitmap(src: Bitmap, left: Float, top: Float, paint: Paint?) {
        val dst = bitmap.pixels
        val sp = src.pixels
        val ox = left.toInt()
        val oy = top.toInt()
        val filter = paint?.colorFilter
        for (y in 0 until src.height) {
            val dy = y + oy
            if (dy < 0 || dy >= bitmap.height) continue
            for (x in 0 until src.width) {
                val dx = x + ox
                if (dx < 0 || dx >= bitmap.width) continue
                var c = sp[y * src.width + x]
                if (filter != null) c = filter.apply(c)
                val di = dy * bitmap.width + dx
                dst[di] = Color.blend(c, dst[di])
            }
        }
    }
}

class Paint {
    var colorFilter: ColorMatrixColorFilter? = null
    var isAntiAlias: Boolean = true
    var isFilterBitmap: Boolean = true
}

/** 4x5 colour matrix, same layout and semantics as android.graphics.ColorMatrix. */
class ColorMatrix(values: FloatArray? = null) {
    val array: FloatArray = values?.copyOf(20) ?: floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    fun setSaturation(sat: Float) {
        val invSat = 1 - sat
        val r = 0.213f * invSat
        val g = 0.715f * invSat
        val b = 0.072f * invSat
        val m = floatArrayOf(
            r + sat, g, b, 0f, 0f,
            r, g + sat, b, 0f, 0f,
            r, g, b + sat, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        m.copyInto(array)
    }

    /** this = [post] × this */
    fun postConcat(post: ColorMatrix) {
        val a = post.array
        val b = array.copyOf()
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = 0f
                for (k in 0 until 4) v += a[row * 5 + k] * b[k * 5 + col]
                if (col == 4) v += a[row * 5 + 4]
                out[row * 5 + col] = v
            }
        }
        out.copyInto(array)
    }
}

class ColorMatrixColorFilter(matrix: ColorMatrix) {
    private val m = matrix.array.copyOf()

    fun apply(color: Int): Int {
        val a = (color ushr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        fun ch(i: Int): Int = (m[i] * r + m[i + 1] * g + m[i + 2] * b + m[i + 3] * a + m[i + 4]).toInt().coerceIn(0, 255)
        return (ch(15) shl 24) or (ch(0) shl 16) or (ch(5) shl 8) or ch(10)
    }
}

class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f)

object Color {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val GRAY: Int = 0xFF888888.toInt()
    const val DKGRAY: Int = 0xFF444444.toInt()
    const val LTGRAY: Int = 0xFFCCCCCC.toInt()
    const val RED: Int = 0xFFFF0000.toInt()
    const val GREEN: Int = 0xFF00FF00.toInt()
    const val BLUE: Int = 0xFF0000FF.toInt()
    const val TRANSPARENT: Int = 0

    @JvmStatic fun alpha(color: Int): Int = color ushr 24
    @JvmStatic fun red(color: Int): Int = (color shr 16) and 0xFF
    @JvmStatic fun green(color: Int): Int = (color shr 8) and 0xFF
    @JvmStatic fun blue(color: Int): Int = color and 0xFF
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** "#RRGGBB" or "#AARRGGBB"; throws IllegalArgumentException like Android. */
    @JvmStatic
    fun parseColor(colorString: String): Int {
        if (colorString.startsWith("#")) {
            val hex = colorString.substring(1)
            val v = hex.toLongOrNull(16) ?: throw IllegalArgumentException("Unknown color")
            return when (hex.length) {
                6 -> (v or 0xFF000000L).toInt()
                8 -> v.toInt()
                else -> throw IllegalArgumentException("Unknown color")
            }
        }
        return when (colorString.lowercase()) {
            "black" -> BLACK; "white" -> WHITE; "gray", "grey" -> GRAY; "red" -> RED
            "green" -> GREEN; "blue" -> BLUE; "transparent" -> TRANSPARENT
            else -> throw IllegalArgumentException("Unknown color")
        }
    }

    /** Source-over blend of [src] onto [dst] (both non-premultiplied ARGB). */
    internal fun blend(src: Int, dst: Int): Int {
        val sa = src ushr 24
        if (sa == 0xFF) return src
        if (sa == 0) return dst
        val da = dst ushr 24
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun mix(s: Int, d: Int): Int = ((s * sa + d * da * (255 - sa) / 255) / outA).coerceIn(0, 255)
        return (outA shl 24) or
            (mix((src shr 16) and 0xFF, (dst shr 16) and 0xFF) shl 16) or
            (mix((src shr 8) and 0xFF, (dst shr 8) and 0xFF) shl 8) or
            mix(src and 0xFF, dst and 0xFF)
    }
}
