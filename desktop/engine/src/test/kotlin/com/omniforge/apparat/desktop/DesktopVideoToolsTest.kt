package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.CropAspect
import com.omniforge.apparat.desktop.engine.CropRect
import com.omniforge.apparat.desktop.engine.DesktopVideoTools
import com.omniforge.apparat.desktop.engine.FrameFormat
import com.omniforge.apparat.desktop.engine.JoinMode
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipFile
import javax.imageio.ImageIO

class DesktopVideoToolsTest {
    private val ctx = Context.app

    @Test fun centeredCropFitsInside() {
        assertEquals(CropRect(140, 0, 360, 360), DesktopVideoTools.centeredCrop(640, 360, CropAspect.SQUARE))
        val p = DesktopVideoTools.centeredCrop(640, 360, CropAspect.PORTRAIT)
        assertEquals(360, p.height); assertEquals(202, p.width); assertEquals(218, p.x)
        val l = DesktopVideoTools.centeredCrop(360, 640, CropAspect.LANDSCAPE)
        assertEquals(360, l.width); assertEquals(202, l.height)
    }

    @Test fun frameTimesAreSpreadOut() {
        val t = DesktopVideoTools.frameTimes(10.05, 5)
        assertEquals(5, t.size); assertEquals(1.0, t[0], 1e-9); assertEquals(9.0, t[4], 1e-9)
        assertEquals("1:05:09", DesktopVideoTools.timestamp(3909.4)); assertEquals("2:03", DesktopVideoTools.timestamp(123.9))
    }

    @Test fun matchingClipsJoinLosslessly() = runBlocking {
        val (out, mode) = DesktopVideoTools.joinVideos(ctx, listOf(TestMedia.uri(TestMedia.mp4), TestMedia.uri(TestMedia.mp4))).getOrThrow()
        assertEquals(JoinMode.LOSSLESS, mode)
        val p = MediaProbe.of(out)!!
        assertEquals(8.0, p.durationSec, 0.3); assertTrue(p.hasAudio); assertEquals(640, p.width)
    }

    @Test fun mixedClipsAreFittedAndReencoded() = runBlocking {
        val clips = listOf(TestMedia.mp4, TestMedia.webm, TestMedia.rotatedMp4).map { TestMedia.uri(it) }
        val (out, mode) = DesktopVideoTools.joinVideos(ctx, clips).getOrThrow()
        assertEquals(JoinMode.REENCODED, mode)
        val p = MediaProbe.of(out)!!
        assertEquals(640, p.width); assertEquals(360, p.height); assertEquals("h264", p.videoCodec)
        assertTrue("silence added for the clip without sound", p.hasAudio)
        assertEquals(9.0, p.durationSec, 0.4)
    }

    @Test fun cropsToSquare() = runBlocking {
        val rect = DesktopVideoTools.centeredCrop(640, 360, CropAspect.SQUARE)
        val p = MediaProbe.of(DesktopVideoTools.cropVideo(ctx, TestMedia.uri(TestMedia.mp4), rect).getOrThrow())!!
        assertEquals(360, p.width); assertEquals(360, p.height); assertTrue(p.hasAudio)
        // Portrait source: the crop is in displayed pixels.
        val r = DesktopVideoTools.cropVideo(ctx, TestMedia.uri(TestMedia.rotatedMp4), CropRect(0, 0, 360, 360)).getOrThrow()
        val q = MediaProbe.of(r)!!
        assertEquals(360, q.displayWidth); assertEquals(360, q.displayHeight)
    }

    @Test fun changesResolutionAndFps() = runBlocking {
        val p = MediaProbe.of(DesktopVideoTools.changeResolution(ctx, TestMedia.uri(TestMedia.mp4), 180, 15.0).getOrThrow())!!
        assertEquals(320, p.width); assertEquals(180, p.height); assertEquals(15.0, p.fps!!, 0.1)
        assertEquals(4.0, p.durationSec, 0.3)
        assertTrue(DesktopVideoTools.changeResolution(ctx, TestMedia.uri(TestMedia.mp4), 360, 25.0).isFailure)
    }

    @Test fun extractsFramesToZip() = runBlocking {
        val zip = DesktopVideoTools.extractFrames(ctx, TestMedia.uri(TestMedia.mp4), 5, FrameFormat.PNG).getOrThrow()
        ZipFile(zip).use { z ->
            val names = z.entries().toList().map { it.name }
            assertEquals(5, names.size)
            assertTrue(names.first(), names.first().startsWith("clip_1_0-00") && names.first().endsWith(".png"))
            val img = ImageIO.read(z.getInputStream(z.entries().nextElement()))
            assertEquals(640, img.width)
        }
    }

    @Test fun makesContactSheet() = runBlocking {
        val sheet = DesktopVideoTools.contactSheet(ctx, TestMedia.uri(TestMedia.mp4), 3, 2, width = 1200).getOrThrow()
        val img = ImageIO.read(sheet)
        assertEquals(1200, img.width)
        assertTrue(img.height in 400..900)
    }
}
