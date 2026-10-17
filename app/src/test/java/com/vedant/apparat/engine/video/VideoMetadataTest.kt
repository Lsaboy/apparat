package com.vedant.apparat.engine.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMetadataTest {

    @Test
    fun testDurationFormatted() {
        val metaZero = VideoMetadata(1920, 1080, durationMs = 0L)
        assertEquals("0:00", metaZero.durationFormatted)

        val metaSubSecond = VideoMetadata(1920, 1080, durationMs = 500L)
        assertEquals("0:00", metaSubSecond.durationFormatted)

        val meta59s = VideoMetadata(1920, 1080, durationMs = 59_000L)
        assertEquals("0:59", meta59s.durationFormatted)

        val meta65s = VideoMetadata(1920, 1080, durationMs = 65_000L)
        assertEquals("1:05", meta65s.durationFormatted)

        val metaLong = VideoMetadata(1920, 1080, durationMs = 754_000L) // 12m 34s
        assertEquals("12:34", metaLong.durationFormatted)
    }

    @Test
    fun testResolutionFormatted() {
        val metaLandscape = VideoMetadata(1920, 1080, durationMs = 1000L)
        assertEquals("1920x1080", metaLandscape.resolutionFormatted)

        val metaPortrait = VideoMetadata(1080, 1920, durationMs = 1000L)
        assertEquals("1080x1920", metaPortrait.resolutionFormatted)

        val metaSquare = VideoMetadata(720, 720, durationMs = 1000L)
        assertEquals("720x720", metaSquare.resolutionFormatted)
    }
}
