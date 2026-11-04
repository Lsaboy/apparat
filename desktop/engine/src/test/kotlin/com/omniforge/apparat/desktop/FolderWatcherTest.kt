package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.BatchItemState
import com.omniforge.apparat.desktop.engine.BatchOperation
import com.omniforge.apparat.desktop.engine.FolderWatcher
import com.omniforge.apparat.desktop.engine.WatchConfig
import com.omniforge.apparat.desktop.engine.WatchEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Collections

class FolderWatcherTest {
    private val ctx = Context.app

    private fun dirs(name: String): Pair<File, File> {
        val input = File(TestMedia.dir, "$name-in").apply { deleteRecursively(); mkdirs() }
        val output = File(input, "Processed")
        return input to output
    }

    @Test fun processesNewFilesOnceAndLeavesOldOnesAlone() = runBlocking {
        val (input, output) = dirs("watch")
        TestMedia.jpeg.copyTo(File(input, "old.jpg"))
        val events = Collections.synchronizedList(mutableListOf<WatchEvent>())
        val cfg = WatchConfig(input, output, BatchOperation.STRIP_EXIF, pollMs = 100, settleMs = 300)
        val job = launch(Dispatchers.IO) { FolderWatcher.watch(ctx, cfg) { events += it } }
        delay(400)
        TestMedia.jpeg.copyTo(File(input, "new.jpg"))
        TestMedia.jpeg.copyTo(File(input, ".hidden.jpg"))
        TestMedia.jpeg.copyTo(File(input, "half.jpg.part"))
        TestMedia.mp4.copyTo(File(input, "clip.mp4")) // not a photo
        withTimeout(20_000) { while (events.none { it is WatchEvent.Finished }) delay(50) }
        delay(800)
        job.cancelAndJoin()
        assertEquals(WatchEvent.Started(1), events.first())
        val finished = events.filterIsInstance<WatchEvent.Finished>()
        assertEquals(listOf("new.jpg"), finished.map { it.file.name })
        assertTrue(finished[0].state is BatchItemState.Done)
        assertEquals(listOf("new_clean.jpg"), output.listFiles()!!.map { it.name }.filter { !it.startsWith(".") })

        // Restart with "include existing": the ledger stops new.jpg being redone; old.jpg gets done now.
        events.clear()
        val job2 = launch(Dispatchers.IO) { FolderWatcher.watch(ctx, cfg.copy(includeExisting = true)) { events += it } }
        withTimeout(20_000) { while (events.none { it is WatchEvent.Finished }) delay(50) }
        delay(800)
        job2.cancelAndJoin()
        assertEquals(listOf("old.jpg"), events.filterIsInstance<WatchEvent.Finished>().map { it.file.name })
    }

    @Test fun refusesOutputSameAsInput() {
        val (input, _) = dirs("watch-same")
        assertTrue(runCatching { FolderWatcher.validate(WatchConfig(input, File(input.path), BatchOperation.STRIP_EXIF)) }.isFailure)
        assertTrue(runCatching { FolderWatcher.validate(WatchConfig(File(input, "missing"), File(input, "out"), BatchOperation.STRIP_EXIF)) }.isFailure)
    }
}
