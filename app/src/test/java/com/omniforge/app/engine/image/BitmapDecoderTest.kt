package com.omniforge.app.engine.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapDecoderTest {

    @Test
    fun keepsSmallImagesUntouched() {
        assertEquals(800 to 600, BitmapDecoder.targetSize(800, 600, 1920, BitmapDecoder.DEFAULT_MAX_PIXELS))
    }

    @Test
    fun maxDimensionIsExactNotPowerOfTwo() {
        // 4000x3000 at 1920 must give exactly 1920 on the long side (old inSampleSize gave 2000).
        assertEquals(1920 to 1440, BitmapDecoder.targetSize(4000, 3000, 1920, BitmapDecoder.DEFAULT_MAX_PIXELS))
        assertEquals(1440 to 1920, BitmapDecoder.targetSize(3000, 4000, 1920, BitmapDecoder.DEFAULT_MAX_PIXELS))
    }

    @Test
    fun pixelBudgetCapsHugePhotos() {
        // 200 MP (16320x12240) must fit in 24 MP.
        val (w, h) = BitmapDecoder.targetSize(16320, 12240, Int.MAX_VALUE, 24_000_000L)
        assertTrue(w.toLong() * h <= 24_010_000L)
        assertEquals(16320.0 / 12240.0, w.toDouble() / h, 0.01)
    }
}
