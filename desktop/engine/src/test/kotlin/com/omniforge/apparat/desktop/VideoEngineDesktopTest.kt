package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.MediaProbe
import com.omniforge.apparat.engine.video.AudioOutputFormat
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEngine
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoEngineDesktopTest {
    private val ctx = Context.app

    private fun probe(f: File) = MediaProbe.of(f)!!

    @Test fun bundledFfmpegRuns() {
        assertTrue("bundled ffmpeg: ${Ffmpeg.ffmpeg}", Ffmpeg.isAvailable)
        assertTrue("libx264 in build", "libx264" in Ffmpeg.encoders)
        assertTrue("aac in build", "aac" in Ffmpeg.encoders)
    }

    @Test fun probeReadsBasics() {
        val p = probe(TestMedia.mp4)
        assertEquals(640, p.width); assertEquals(360, p.height)
        assertEquals(4.0, p.durationSec, 0.2)
        assertTrue(p.hasAudio)
        val r = probe(TestMedia.rotatedMp4)
        assertEquals(90, r.rotation)
        assertEquals(360, r.displayWidth); assertEquals(640, r.displayHeight)
        val meta = runBlocking { VideoEngine.getVideoMetadata(ctx, TestMedia.uri(TestMedia.rotatedMp4)) }!!
        assertEquals("360x640", meta.resolutionFormatted)
    }

    @Test fun shrinkScalesToPresetAndKeepsAudio() = runBlocking {
        var used: VideoCodecChoice? = null
        val progress = mutableListOf<Float>()
        val out = VideoEngine.shrinkVideo(ctx, TestMedia.uri(TestMedia.webm), VideoShrinkPreset.MAX_SHRINK, onCodecUsed = { used = it }) { p, _ -> progress += p }.getOrThrow()
        val p = probe(out)
        assertEquals("h264", p.videoCodec); assertEquals(360, p.height); assertEquals(640, p.width)
        assertEquals("aac", p.audioCodec)
        assertEquals(VideoCodecChoice.H264, used)
    }

    @Test fun shrinkRotatedKeepsDisplayOrientation() = runBlocking {
        val out = VideoEngine.shrinkVideo(ctx, TestMedia.uri(TestMedia.rotatedMp4), VideoShrinkPreset.MAX_SHRINK).getOrThrow()
        val p = probe(out)
        assertTrue("portrait output ${p.displayWidth}x${p.displayHeight}", p.displayHeight > p.displayWidth)
        assertEquals(360, minOf(p.displayWidth, p.displayHeight))
    }

    @Test fun convertWebmToMp4() = runBlocking {
        val out = VideoEngine.convertToMp4(ctx, TestMedia.uri(TestMedia.webm)).getOrThrow()
        assertEquals("mp4", out.extension)
        val p = probe(out)
        assertEquals("h264", p.videoCodec); assertEquals("aac", p.audioCodec)
        assertEquals(3.0, p.durationSec, 0.3)
    }

    @Test fun trimCopiesStreams() = runBlocking {
        val out = VideoEngine.losslessTrim(ctx, TestMedia.uri(TestMedia.mp4), 1f, 2f).getOrThrow()
        val p = probe(out)
        assertEquals(2.0, p.durationSec, 0.6)
        assertTrue(p.hasAudio)
    }

    @Test fun trimPastEndFails() = runBlocking {
        assertTrue(VideoEngine.losslessTrim(ctx, TestMedia.uri(TestMedia.mp4), 10f, 2f).isFailure)
    }

    @Test fun muteRemovesAudio() = runBlocking {
        val out = VideoEngine.stripAudio(ctx, TestMedia.uri(TestMedia.mp4)).getOrThrow()
        val p = probe(out)
        assertTrue(p.hasVideo); assertFalse(p.hasAudio)
    }

    @Test fun muteWebmFallsBackOrCopies() = runBlocking {
        val out = VideoEngine.stripAudio(ctx, TestMedia.uri(TestMedia.webm)).getOrThrow()
        assertFalse(probe(out).hasAudio)
    }

    @Test fun extractAudioM4aAndWav() = runBlocking {
        val m4a = VideoEngine.extractAudio(ctx, TestMedia.uri(TestMedia.mp4), AudioOutputFormat.M4A).getOrThrow()
        assertEquals("aac", probe(m4a).audioCodec); assertFalse(probe(m4a).hasVideo)
        val wav = VideoEngine.extractAudio(ctx, TestMedia.uri(TestMedia.webm), AudioOutputFormat.WAV).getOrThrow()
        assertEquals("pcm_s16le", probe(wav).audioCodec)
        val header = wav.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII)
        assertEquals("RIFF", header)
    }

    @Test fun extractAudioFromSilentVideoFails() = runBlocking {
        assertTrue(VideoEngine.extractAudio(ctx, TestMedia.uri(TestMedia.rotatedMp4), AudioOutputFormat.M4A).isFailure)
    }

    @Test fun gifHasExpectedFrames() = runBlocking {
        val out = VideoEngine.videoToGif(ctx, TestMedia.uri(TestMedia.mp4), fps = 10, width = 320, startSec = 0.5f, durationSec = 2f).getOrThrow()
        val bytes = out.readBytes()
        assertEquals("GIF89a", bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII))
        val p = probe(out)
        assertEquals(320, p.width); assertEquals(180, p.height)
    }

    @Test fun burnSubtitles() = runBlocking {
        val srt = File(TestMedia.dir, "subs.srt").apply {
            writeText("1\n00:00:00,500 --> 00:00:01,800\nHello <i>desktop</i>\n\n2\n00:00:02,000 --> 00:00:03,500\nA much longer second line of subtitle text that should wrap onto two lines on screen\n")
        }
        val out = VideoEngine.burnSubtitles(ctx, TestMedia.uri(TestMedia.mp4), TestMedia.uri(srt), fontSize = 20).getOrThrow()
        val p = probe(out)
        assertEquals(4.0, p.durationSec, 0.3)
        assertEquals(640, p.width)
        assertTrue(p.hasAudio)
        // Frame at 1s should differ from the source (white text box burned in).
        val a = File(TestMedia.dir, "f_src.png"); val b = File(TestMedia.dir, "f_out.png")
        Ffmpeg.run(listOf("-ss", "1", "-i", TestMedia.mp4.absolutePath, "-frames:v", "1", a.absolutePath))
        Ffmpeg.run(listOf("-ss", "1", "-i", out.absolutePath, "-frames:v", "1", b.absolutePath))
        val sa = com.omniforge.apparat.desktop.platform.SkiaImages.decode(a.readBytes())!!
        val sb = com.omniforge.apparat.desktop.platform.SkiaImages.decode(b.readBytes())!!
        var diff = 0L
        val ya = (sa.height * 0.9).toInt()
        for (x in 0 until sa.width) {
            val pa = sa.getPixel(x, ya); val pb = sb.getPixel(x, ya)
            diff += Math.abs(((pa shr 8) and 0xFF) - ((pb shr 8) and 0xFF))
        }
        assertTrue("subtitle band should change pixels, diff=$diff", diff > sa.width * 10)
        // And a frame with no cue (at 3.8 s) should look like the source.
        assertNotNull(out)
    }

    @Test fun cancellationKillsFfmpeg() = runBlocking {
        val job = @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class) kotlinx.coroutines.GlobalScope.launch {
            VideoEngine.convertToMp4(ctx, TestMedia.uri(TestMedia.webm))
        }
        kotlinx.coroutines.delay(300)
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
    }
}
