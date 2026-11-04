package com.omniforge.apparat.desktop.engine

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

/** A watch folder: files that land in [input] get [operation] and the result goes to [output]. */
data class WatchConfig(
    val input: File,
    val output: File,
    val operation: BatchOperation,
    val options: BatchOptions = BatchOptions(),
    /** Also process files already in the folder when watching starts. */
    val includeExisting: Boolean = false,
    val pollMs: Long = 2_000,
    /** A file must keep the same size and date this long before it's picked up (still copying otherwise). */
    val settleMs: Long = 3_000
)

sealed interface WatchEvent {
    /** Watching began; [ignored] files already there are left alone. */
    data class Started(val ignored: Int) : WatchEvent
    data class Working(val file: File, val progress: Float) : WatchEvent
    /** [state] is [BatchItemState.Done] or [BatchItemState.Failed]. */
    data class Finished(val file: File, val state: BatchItemState) : WatchEvent
}

/**
 * Polls a folder (top level only, so an output folder inside it is never re-read) and runs a
 * batch operation on each new file once it has stopped growing. Files already handled are
 * listed in a small ledger in the output folder, so restarting doesn't redo them; a file replaced
 * with a new version (different size or date) is processed again.
 */
object FolderWatcher {

    const val LEDGER_NAME = ".apparat-watch-done"

    private val partialSuffixes = listOf(".part", ".crdownload", ".download", ".tmp", ".partial", ".!qb", ".opdownload")

    /** Files in [dir] that [op] takes, oldest first; hidden and half-downloaded files are left out. */
    internal fun candidates(dir: File, op: BatchOperation): List<File> =
        (dir.listFiles() ?: emptyArray()).filter { f ->
            val n = f.name.lowercase()
            f.isFile && !n.startsWith(".") && !n.startsWith("~$") &&
                partialSuffixes.none { n.endsWith(it) } && op.input.accepts(f)
        }.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })

    internal fun key(f: File) = "${f.absolutePath}\t${f.length()}\t${f.lastModified()}"

    /** Whether something else still has [f] open for writing (Windows locks it; elsewhere this is just a read check). */
    private fun readable(f: File) = runCatching { RandomAccessFile(f, "r").use { it.read() }; true }.getOrDefault(false)

    /** Checks the folders; throws [IllegalArgumentException] with a message for the user. */
    fun validate(config: WatchConfig) {
        val inDir = config.input.absoluteFile
        val outDir = config.output.absoluteFile
        require(inDir.isDirectory) { "The watched folder doesn't exist: ${inDir.path}" }
        require(inDir.canonicalPath != outDir.canonicalPath) { "Choose a different output folder: results saved into the watched folder would be processed again." }
    }

    /** Watches until cancelled. [onEvent] is called from the watcher's coroutine. */
    suspend fun watch(context: Context, config: WatchConfig, onEvent: (WatchEvent) -> Unit) {
        validate(config)
        config.output.mkdirs()
        require(config.output.isDirectory && config.output.canWrite()) { "Can't write to ${config.output.path}" }
        val ledgerFile = File(config.output, LEDGER_NAME)
        val done = HashSet<String>(runCatching { ledgerFile.readLines() }.getOrDefault(emptyList()).filter { it.isNotBlank() })
        val ignored = HashSet<String>()
        if (!config.includeExisting) candidates(config.input, config.operation).forEach { ignored += key(it) }
        onEvent(WatchEvent.Started(ignored.size))

        // path -> (size/date key last seen, when it was first seen with that key)
        val seen = HashMap<String, Pair<String, Long>>()
        while (true) {
            coroutineContext.ensureActive()
            val now = System.currentTimeMillis()
            val present = candidates(config.input, config.operation)
            seen.keys.retainAll(present.map { it.absolutePath }.toSet())
            for (f in present) {
                val k = key(f)
                if (k in done || k in ignored) continue
                val last = seen[f.absolutePath]
                if (last == null || last.first != k) { seen[f.absolutePath] = k to now; continue }
                if (now - last.second < config.settleMs || !readable(f)) continue
                coroutineContext.ensureActive()
                var final: BatchItemState = BatchItemState.Failed("Not processed")
                BatchRunner.run(context, config.operation, config.options, listOf(f), config.output) { _, s ->
                    when (s) {
                        is BatchItemState.Running -> onEvent(WatchEvent.Working(f, s.progress))
                        is BatchItemState.Done, is BatchItemState.Failed -> final = s
                        else -> {}
                    }
                }
                // Failed files go in the ledger too, so a broken file isn't retried every few seconds.
                done += k
                runCatching { ledgerFile.appendText(k + "\n") }
                seen.remove(f.absolutePath)
                onEvent(WatchEvent.Finished(f, final))
            }
            delay(config.pollMs)
        }
    }
}
