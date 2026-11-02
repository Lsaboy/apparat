package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.AudioConvertFormat
import com.omniforge.apparat.desktop.engine.DesktopMediaTools
import com.omniforge.apparat.desktop.engine.VideoTurn
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMediaToolsTest {
    private val ctx = Context.app

    @Test fun convertsAudioToEveryFormat() = runBlocking {
        val expected = mapOf(
            AudioConvertFormat.MP3 to "mp3", AudioConvertFormat.M4A to "aac", AudioConvertFormat.OPUS to "opus",
            AudioConvertFormat.FLAC to "flac", AudioConvertFormat.WAV to "pcm_s16le"
        )
        for ((format, codec) in expected) {
            val out = DesktopMediaTools.convertAudio(ctx, TestMedia.uri(TestMedia.webm), format).getOrThrow()
            val p = MediaProbe.of(out)!!
            assertEquals(format.name, codec, p.audioCodec)
            assertFalse(p.hasVideo)
            assertEquals(3.0, p.durationSec, 0.3)
        }
    }

    @Test fun normalizeKeepsNormalSampleRate() = runBlocking {
        val out = DesktopMediaTools.convertAudio(ctx, TestMedia.uri(TestMedia.mp4), AudioConvertFormat.MP3, normalize = true).getOrThrow()
        assertEquals(44100, MediaProbe.of(out)!!.audioSampleRate)
    }

    @Test fun silentVideoCantBeConverted() = runBlocking {
        assertTrue(DesktopMediaTools.convertAudio(ctx, TestMedia.uri(TestMedia.rotatedMp4), AudioConvertFormat.MP3).isFailure)
    }

    @Test fun rotationAloneIsLossless() = runBlocking {
        val out = DesktopMediaTools.transformVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoTurn.CW90, false, false, 1f).getOrThrow()
        val p = MediaProbe.of(out)!!
        assertEquals("stream copied, frames unchanged", 640, p.width)
        assertEquals(360, p.displayWidth); assertEquals(640, p.displayHeight)
        assertTrue(p.hasAudio)
        // Turning the already-rotated portrait clip right again makes it upside-down landscape.
        val again = DesktopMediaTools.transformVideo(ctx, TestMedia.uri(out), VideoTurn.CW90, false, false, 1f).getOrThrow()
        assertEquals(640, MediaProbe.of(again)!!.displayWidth)
    }

    @Test fun flipAndSpeedReencode() = runBlocking {
        val out = DesktopMediaTools.transformVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoTurn.CCW90, true, false, 2f).getOrThrow()
        val p = MediaProbe.of(out)!!
        assertEquals(360, p.width); assertEquals(640, p.height); assertEquals(0, p.rotation)
        assertEquals(2.0, p.durationSec, 0.3)
        assertTrue(p.hasAudio)
        val slow = DesktopMediaTools.transformVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoTurn.NONE, false, false, 0.25f).getOrThrow()
        assertEquals(16.0, MediaProbe.of(slow)!!.durationSec, 0.8)
    }

    @Test fun atempoChainStaysInRange() {
        assertEquals("atempo=2.0000,atempo=2.0000", DesktopMediaTools.atempoChain(4.0))
        assertEquals("atempo=0.5000,atempo=0.5000", DesktopMediaTools.atempoChain(0.25))
        assertEquals("atempo=1.5000", DesktopMediaTools.atempoChain(1.5))
    }

    @Test fun nothingToDoIsAnError() = runBlocking {
        assertTrue(DesktopMediaTools.transformVideo(ctx, TestMedia.uri(TestMedia.mp4), VideoTurn.NONE, false, false, 1f).isFailure)
    }
}
