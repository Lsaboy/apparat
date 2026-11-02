package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.BatchItemState
import com.omniforge.apparat.desktop.engine.BatchOperation
import com.omniforge.apparat.desktop.engine.BatchOptions
import com.omniforge.apparat.desktop.engine.BatchRunner
import com.omniforge.apparat.desktop.platform.SkiaImages
import com.omniforge.apparat.engine.image.ImageOutputFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BatchRunnerTest {
    private val ctx = Context.app

    @Test fun convertsEveryPhotoSkipsOthersAndNeverOverwrites() = runBlocking {
        val out = File(TestMedia.dir, "batch-out").apply { deleteRecursively(); mkdirs() }
        File(out, "photo.webp").writeText("existing") // must not be overwritten
        val broken = File(TestMedia.dir, "broken.jpg").apply { writeText("not an image") }
        val files = listOf(TestMedia.jpeg, TestMedia.png, TestMedia.mp4, broken)
        val states = HashMap<Int, BatchItemState>()
        val summary = BatchRunner.run(ctx, BatchOperation.CONVERT_IMAGE, BatchOptions(imageFormat = ImageOutputFormat.WEBP), files, out) { i, s -> states[i] = s }
        assertEquals(2, summary.done); assertEquals(1, summary.failed); assertEquals(1, summary.skipped)
        assertTrue(states[2] is BatchItemState.Skipped)
        assertTrue(states[3] is BatchItemState.Failed)
        val names = out.listFiles()!!.map { it.name }.sorted()
        assertEquals(listOf("photo (2).webp", "photo (3).webp", "photo.webp").sorted(), names)
        assertEquals("existing", File(out, "photo.webp").readText())
        assertTrue(SkiaImages.size(File(out, "photo (2).webp").readBytes()) != null)
    }

    @Test fun shrinksVideosAndAudio() = runBlocking {
        val out = File(TestMedia.dir, "batch-av").apply { deleteRecursively(); mkdirs() }
        val s1 = BatchRunner.run(ctx, BatchOperation.CONVERT_AUDIO, BatchOptions(), listOf(TestMedia.mp4, TestMedia.webm), out) { _, _ -> }
        assertEquals(2, s1.done)
        assertTrue(File(out, "clip.mp3").isFile)
        assertTrue(File(out, "clip (2).mp3").isFile)
    }
}
