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

    @Test
    fun detectsAnimatedWebpHeader() {
        // RIFF (4) + size (4) + WEBP (4) + VP8X (4) + chunk_size (4) + flags (1)
        val header = ByteArray(21)
        System.arraycopy("RIFF".toByteArray(), 0, header, 0, 4)
        System.arraycopy("WEBP".toByteArray(), 0, header, 8, 4)
        System.arraycopy("VP8X".toByteArray(), 0, header, 12, 4)
        header[20] = 0x02.toByte() // Animation bit set
        assertTrue(BitmapDecoder.isAnimatedWebpHeader(header))

        header[20] = 0x12.toByte() // Animation bit + Alpha bit set
        assertTrue(BitmapDecoder.isAnimatedWebpHeader(header))
    }

    @Test
    fun detectsStaticWebpHeader() {
        val header = ByteArray(21)
        System.arraycopy("RIFF".toByteArray(), 0, header, 0, 4)
        System.arraycopy("WEBP".toByteArray(), 0, header, 8, 4)
        System.arraycopy("VP8X".toByteArray(), 0, header, 12, 4)
        header[20] = 0x10.toByte() // Only alpha bit set (static WebP with alpha)
        org.junit.Assert.assertFalse(BitmapDecoder.isAnimatedWebpHeader(header))

        System.arraycopy("VP8 ".toByteArray(), 0, header, 12, 4)
        org.junit.Assert.assertFalse(BitmapDecoder.isAnimatedWebpHeader(header))
    }

    @Test
    fun handlesTruncatedOrCorruptHeader() {
        org.junit.Assert.assertFalse(BitmapDecoder.isAnimatedWebpHeader(ByteArray(10)))
        org.junit.Assert.assertFalse(BitmapDecoder.isAnimatedWebpHeader(ByteArray(0)))
        org.junit.Assert.assertFalse(BitmapDecoder.isAnimatedWebpHeader("RANDOM_BYTES_HEADER_NOT_RIFF".toByteArray()))
    }
}
