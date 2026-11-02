package com.omniforge.apparat.core.util

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.desktop.platform.DesktopPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One file to save: [name] is the suggested display name, extension included. */
data class SaveItem(val file: File, val mimeType: String, val name: String)

/** Where a save went, for the message shown to the user. */
data class SaveOutcome(val savedCount: Int, val total: Int, val where: String, val fellBack: Boolean = false) {
    val message: String
        get() = when {
            savedCount == 0 -> "Couldn't save. Check that there's free space and try again."
            total == 1 && fellBack -> "Your chosen folder isn't available. Saved to $where instead."
            total == 1 -> "Saved to $where"
            savedCount == total && fellBack -> "Your chosen folder isn't available. Saved $total files to $where instead."
            savedCount == total -> "Saved $total files to $where"
            else -> "Saved $savedCount of $total files to $where"
        }
}

/**
 * Desktop version of OutputSaver (same API). The default destination is Downloads/<folder> or a
 * folder picked in Settings; if that folder disappears or becomes read-only, saves fall back to
 * Downloads/Apparat and say so. Existing files are never overwritten: a number is added instead.
 */
object OutputSaver {

    const val DEFAULT_FOLDER = "Apparat"

    sealed interface Destination {
        val label: String

        data class Downloads(val folder: String) : Destination {
            override val label get() = "Downloads/$folder"
        }

        data class Tree(val uri: Uri, override val label: String) : Destination
    }

    fun sanitizeFolderName(input: String): String {
        val cleaned = input
            .replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimStart('.')
            .trimEnd('.', ' ')
            .take(60)
            .trim()
        return cleaned.ifEmpty { DEFAULT_FOLDER }
    }

    fun destination(context: Context): Destination {
        val settings = AppSettings(context)
        val tree = settings.saveTreeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (tree != null && canWriteTree(tree)) return Destination.Tree(tree, treeLabel(context, tree))
        return Destination.Downloads(settings.saveFolderName)
    }

    fun isPickedFolderUnavailable(context: Context): Boolean {
        val tree = AppSettings(context).saveTreeUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return !canWriteTree(tree)
    }

    fun setDefaultTree(context: Context, tree: Uri): Boolean {
        if (!canWriteTree(tree)) return false
        AppSettings(context).saveTreeUri = tree.toString()
        return true
    }

    fun useDownloads(context: Context, folder: String) {
        val settings = AppSettings(context)
        settings.saveTreeUri = null
        settings.saveFolderName = sanitizeFolderName(folder)
    }

    private fun canWriteTree(tree: Uri): Boolean = tree.file.isDirectory && tree.file.canWrite()

    /** A short name for a folder: the path with the home folder shown as "~". */
    fun treeLabel(context: Context, tree: Uri): String {
        val path = tree.file.absolutePath
        val home = System.getProperty("user.home")
        val shown = if (path.startsWith(home)) "~" + path.removePrefix(home) else path
        return shown.ifBlank { "Chosen folder" }
    }

    /** Where Downloads/[folder] actually is (shown in Settings). */
    fun downloadsFolder(folder: String): File = File(DesktopPaths.downloadsDir, folder)

    suspend fun saveToDefault(context: Context, items: List<SaveItem>, onProgress: (Int, Int) -> Unit = { _, _ -> }): SaveOutcome =
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context)
            val lostPickedFolder = isPickedFolderUnavailable(context)
            if (lostPickedFolder) settings.saveTreeUri = null
            when (val dest = destination(context)) {
                is Destination.Downloads -> saveToDir(downloadsFolder(dest.folder), dest.label, items, onProgress, fellBack = lostPickedFolder)
                is Destination.Tree -> {
                    val outcome = saveToDir(dest.uri.file, dest.label, items, onProgress, fellBack = false)
                    if (outcome.savedCount > 0) outcome
                    else saveToDir(downloadsFolder(settings.saveFolderName), "Downloads/${settings.saveFolderName}", items, onProgress, fellBack = true)
                }
            }
        }

    suspend fun saveToPickedFolder(context: Context, tree: Uri, items: List<SaveItem>, onProgress: (Int, Int) -> Unit = { _, _ -> }): SaveOutcome =
        withContext(Dispatchers.IO) { saveToDir(tree.file, treeLabel(context, tree), items, onProgress, fellBack = false) }

    /** Writes one file to the path the user chose in the "Save as" dialog (they confirmed any overwrite there). */
    suspend fun saveToDocument(context: Context, file: File, target: Uri): SaveOutcome = withContext(Dispatchers.IO) {
        val dest = target.file
        val ok = runCatching {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, ".${dest.name}.part")
            Files.copy(file.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            dest.length() == file.length()
        }.getOrDefault(false)
        SaveOutcome(if (ok) 1 else 0, 1, dest.name)
    }

    private suspend fun saveToDir(
        dir: File, label: String, items: List<SaveItem>, onProgress: (Int, Int) -> Unit, fellBack: Boolean
    ): SaveOutcome {
        var saved = 0
        items.forEachIndexed { i, item ->
            if (FileUtils.copyToFolder(item.file, dir, item.name) != null) saved++
            withContext(Dispatchers.Main) { onProgress(i + 1, items.size) }
        }
        lastSaveFolder = dir
        return SaveOutcome(saved, items.size, label, fellBack)
    }

    /** The folder of the most recent save (the window offers to open it). */
    @Volatile
    var lastSaveFolder: File? = null
        private set
}
