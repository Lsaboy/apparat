package com.omniforge.apparat.desktop.platform

import android.graphics.Bitmap
import java.awt.image.BufferedImage

object AwtImages {
    /** [bitmap] flattened onto white as an opaque RGB image (for JPEG). */
    fun toRgbOnWhite(bitmap: Bitmap): BufferedImage {
        val w = bitmap.width
        val h = bitmap.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val src = bitmap.pixels
        val row = IntArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src[y * w + x]
                val a = c ushr 24
                row[x] = if (a == 0xFF) c else {
                    fun ch(s: Int) = (s * a + 255 * (255 - a)) / 255
                    (ch((c shr 16) and 0xFF) shl 16) or (ch((c shr 8) and 0xFF) shl 8) or ch(c and 0xFF)
                }
            }
            out.setRGB(0, y, w, 1, row, 0, w)
        }
        return out
    }

    fun toBitmap(image: BufferedImage): Bitmap {
        val w = image.width
        val h = image.height
        val px = IntArray(w * h)
        image.getRGB(0, 0, w, h, px, 0, w)
        return Bitmap.wrap(px, w, h)
    }
}
