package com.omniforge.apparat.engine.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCodecChoiceTest {

    @Test
    fun `only H264 when no hardware encoders`() {
        assertEquals(listOf(VideoCodecChoice.H264), VideoCodecChoice.available(false, false))
    }

    @Test
    fun `all codecs in order when both encoders exist`() {
        assertEquals(
            listOf(VideoCodecChoice.H264, VideoCodecChoice.HEVC, VideoCodecChoice.AV1),
            VideoCodecChoice.available(true, true)
        )
    }

    @Test
    fun `AV1 without HEVC keeps H264 first`() {
        assertEquals(listOf(VideoCodecChoice.H264, VideoCodecChoice.AV1), VideoCodecChoice.available(false, true))
    }

    @Test
    fun `bitrate scales per codec`() {
        assertEquals(2_000_000, VideoCodecChoice.scaledBitrate(2_000_000, VideoCodecChoice.H264))
        assertEquals(1_200_000, VideoCodecChoice.scaledBitrate(2_000_000, VideoCodecChoice.HEVC))
        assertEquals(1_000_000, VideoCodecChoice.scaledBitrate(2_000_000, VideoCodecChoice.AV1))
    }

    @Test
    fun `bitrate never drops below the floor`() {
        assertEquals(VideoCodecChoice.MIN_BITRATE, VideoCodecChoice.scaledBitrate(250_000, VideoCodecChoice.AV1))
    }

    @Test
    fun `friendly codec names`() {
        assertEquals("H.264", VideoDecodeSupport.friendlyCodecName("video/avc"))
        assertEquals("HEVC (H.265)", VideoDecodeSupport.friendlyCodecName("video/hevc"))
        assertEquals("AV1", VideoDecodeSupport.friendlyCodecName("video/av01"))
        assertEquals("VP9", VideoDecodeSupport.friendlyCodecName("video/x-vnd.on2.vp9"))
        assertEquals("Dolby Vision", VideoDecodeSupport.friendlyCodecName("video/dolby-vision"))
        assertEquals("FOO", VideoDecodeSupport.friendlyCodecName("video/foo"))
    }

    @Test
    fun `unsupported message names the codec`() {
        assertEquals(
            "This phone can't play VP9 video, so it can't be converted here.",
            VideoDecodeSupport.unsupportedMessage("video/x-vnd.on2.vp9")
        )
    }
}
