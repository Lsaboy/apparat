package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.DesktopMediaTools
import com.omniforge.apparat.desktop.engine.VideoTurn
import com.omniforge.apparat.desktop.platform.FfmpegEnv
import com.omniforge.apparat.desktop.platform.HwAccel
import com.omniforge.apparat.desktop.platform.HwApi
import com.omniforge.apparat.desktop.platform.HwBackend
import com.omniforge.apparat.desktop.platform.HwDeviceType
import com.omniforge.apparat.desktop.platform.MediaProbe
import com.omniforge.apparat.engine.video.EncodeJob
import com.omniforge.apparat.engine.video.EncodeLevel
import com.omniforge.apparat.engine.video.EncodePlan
import com.omniforge.apparat.engine.video.PlanArgs
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEncoding
import com.omniforge.apparat.engine.video.VideoEngine
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HwAccelTest {
    private val ctx = Context.app

    private fun backend(api: HwApi = HwApi.VAAPI, device: String = "/dev/dri/renderD999") = HwBackend(
        api = api,
        deviceType = if (api == HwApi.NVENC) HwDeviceType.CUDA else HwDeviceType.VAAPI,
        device = device,
        env = FfmpegEnv.NONE,
        encoders = mapOf(VideoCodecChoice.H264 to emptyList(), VideoCodecChoice.HEVC to emptyList()),
        fullGpu = true,
        description = "Test GPU"
    )

    private fun source(codec: String = "h264", w: Int = 1920, h: Int = 1080, rotation: Int = 0, bitrate: Long? = 8_000_000) =
        MediaProbe(10.0, bitrate, "mp4", codec, w, h, rotation, 30.0, "aac", 2, 48000)

    @Before @After fun reset() {
        HwAccel.overrideForTests = null
        HwAccel.resetSessionForTests()
    }

    @Test fun planOrderFullThenDecodeThenEncodeThenCpu() {
        val b = backend()
        val levels = VideoEncoding.plans(VideoCodecChoice.HEVC, EncodeJob(source(), 1280, 720, allowFullGpu = true), b)
            .map { it.level to it.codec }
        assertEquals(
            listOf(
                EncodeLevel.FULL_GPU to VideoCodecChoice.HEVC,
                EncodeLevel.GPU_DECODE to VideoCodecChoice.HEVC,
                EncodeLevel.GPU_ENCODE to VideoCodecChoice.HEVC,
                EncodeLevel.SOFTWARE to VideoCodecChoice.HEVC,
                EncodeLevel.SOFTWARE to VideoCodecChoice.H264
            ),
            levels
        )
    }

    @Test fun noFullGpuWithOtherFiltersOrUnknownSourceCodec() {
        val b = backend()
        val noFull = VideoEncoding.plans(VideoCodecChoice.H264, EncodeJob(source(), 1280, 720), b).map { it.level }
        assertEquals(listOf(EncodeLevel.GPU_DECODE, EncodeLevel.GPU_ENCODE, EncodeLevel.SOFTWARE), noFull)
        val prores = VideoEncoding.plans(VideoCodecChoice.H264, EncodeJob(source("prores"), 1280, 720, allowFullGpu = true), b).map { it.level }
        assertEquals(listOf(EncodeLevel.GPU_ENCODE, EncodeLevel.SOFTWARE), prores)
    }

    @Test fun sizeGuardsSkipHardware() {
        val b = backend()
        // Over 4096 wide: H.264 hardware encoders refuse it.
        assertEquals(listOf(EncodeLevel.SOFTWARE), VideoEncoding.plans(VideoCodecChoice.H264, EncodeJob(source(w = 7680, h = 4320), 7680, 4320), b).map { it.level })
        // Tiny: below the encoders' minimum.
        assertEquals(listOf(EncodeLevel.SOFTWARE), VideoEncoding.plans(VideoCodecChoice.H264, EncodeJob(source(w = 96, h = 64), 96, 64), b).map { it.level })
        assertTrue(VideoEncoding.sizeOk(VideoCodecChoice.HEVC, 7680, 4320))
    }

    @Test fun unsupportedCodecGoesStraightToCpu() {
        val levels = VideoEncoding.plans(VideoCodecChoice.AV1, EncodeJob(source(), 1280, 720), backend()).map { it.level }
        assertTrue(levels.all { it == EncodeLevel.SOFTWARE })
    }

    @Test fun vaapiArgs() {
        val b = backend(device = "/dev/dri/renderD128")
        val full = PlanArgs(EncodePlan(VideoCodecChoice.H264, b, EncodeLevel.FULL_GPU), EncodeJob(source(), 1280, 720, allowFullGpu = true))
        assertEquals(
            listOf("-init_hw_device", "vaapi=va:/dev/dri/renderD128", "-filter_hw_device", "va",
                "-hwaccel", "vaapi", "-hwaccel_device", "va", "-hwaccel_output_format", "vaapi", "-noautorotate"),
            full.input
        )
        assertEquals("scale_vaapi=w=1280:h=720:format=nv12", full.videoFilter(listOf("scale=1280:720")))
        assertEquals("h264_vaapi", full.encoder[1])
        assertFalse(full.needsRotationRemux)

        val encodeOnly = PlanArgs(EncodePlan(VideoCodecChoice.H264, b, EncodeLevel.GPU_ENCODE), EncodeJob(source(), 1280, 720))
        assertEquals(listOf("-init_hw_device", "vaapi=va:/dev/dri/renderD128", "-filter_hw_device", "va"), encodeOnly.input)
        assertEquals("scale=1280:720,format=nv12,hwupload", encodeOnly.videoFilter(listOf("scale=1280:720")))
        assertEquals("format=nv12,hwupload", encodeOnly.videoFilter(emptyList()))
        assertEquals(",format=nv12,hwupload", encodeOnly.complexSuffix)
    }

    @Test fun nvencArgsNeedNoUpload() {
        val b = backend(HwApi.NVENC)
        val args = PlanArgs(EncodePlan(VideoCodecChoice.HEVC, b, EncodeLevel.GPU_DECODE), EncodeJob(source(), 1280, 720))
        assertEquals(listOf("-hwaccel", "cuda"), args.input)
        assertNull(args.videoFilter(emptyList()))
        assertEquals("", args.complexSuffix)
        assertTrue(args.encoder.containsAll(listOf("hevc_nvenc", "-tag:v", "hvc1")))
    }

    @Test fun fullGpuOnRotatedSourceScalesStoredFrameAndRemuxes() {
        // Portrait phone video: stored 1920x1080 with rotation 90, shown 1080x1920; target shown 720x1280.
        val args = PlanArgs(EncodePlan(VideoCodecChoice.H264, backend(), EncodeLevel.FULL_GPU), EncodeJob(source(rotation = 90), 720, 1280, allowFullGpu = true))
        assertTrue(args.needsRotationRemux)
        assertEquals("scale_vaapi=w=1280:h=720:format=nv12", args.videoFilter(emptyList()))
    }

    @Test fun hardwareBitrateNeverExceedsSource() {
        val plan = EncodePlan(VideoCodecChoice.H264, backend(), EncodeLevel.GPU_ENCODE)
        // 1080p30 at 0.12 bpp ≈ 7.5 Mb/s, but the source only has 2 Mb/s.
        val capped = PlanArgs(plan, EncodeJob(source(bitrate = 2_000_000), 1920, 1080)).hwBitrate
        assertTrue("capped $capped", capped in 2_000_000..2_300_000)
        val requested = PlanArgs(plan, EncodeJob(source(), 1280, 720, bitrate = 1_500_000)).hwBitrate
        assertEquals(1_500_000, requested)
        assertTrue(PlanArgs(plan, EncodeJob(source(bitrate = 50_000), 1920, 1080)).hwBitrate >= 300_000)
    }

    /** A VA-API device that doesn't exist: every GPU attempt fails and the CPU still delivers. */
    @Test fun brokenGpuFallsBackToCpuAndIsRemembered() = runBlocking {
        val b = backend()
        HwAccel.overrideForTests = b
        val out = VideoEngine.shrinkVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoShrinkPreset.MAX_SHRINK).getOrThrow()
        val p = MediaProbe.of(out)!!
        assertEquals("h264", p.videoCodec)
        assertEquals("aac", p.audioCodec)
        assertEquals(EncodeLevel.SOFTWARE, VideoEncoding.lastPlan?.level)
        // The full-GPU path is not retried on the next video.
        assertTrue(HwAccel.hasPathFailed(b, "full:h264"))
        VideoEngine.shrinkVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoShrinkPreset.MAX_SHRINK).getOrThrow()
        // Two failed GPU encodes in a row: hardware is skipped for that codec from now on.
        assertFalse(HwAccel.usable(b, VideoCodecChoice.H264))
        assertTrue(VideoEncoding.plans(VideoCodecChoice.H264, EncodeJob(source(), 1280, 720), b).all { !it.isHardware })
    }

    @Test fun brokenGpuRotatedTransformStillCorrect() = runBlocking {
        HwAccel.overrideForTests = backend()
        val out = DesktopMediaTools.transformVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoTurn.CW90, flipHorizontal = true, flipVertical = false, speed = 2f).getOrThrow()
        val p = MediaProbe.of(out)!!
        assertEquals(360, p.displayWidth); assertEquals(640, p.displayHeight)
        assertEquals(2.0, p.durationSec, 0.4)
    }

    @Test fun disabledSettingMeansCpu() {
        HwAccel.overrideForTests = backend()
        HwAccel.enabled = false
        try {
            assertNull(HwAccel.backend())
            assertTrue(HwAccel.summary().startsWith("Off"))
        } finally {
            HwAccel.enabled = true
        }
    }
}
