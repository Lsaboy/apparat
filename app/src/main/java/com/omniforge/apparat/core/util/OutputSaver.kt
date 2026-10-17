package com.omniforge.apparat.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.omniforge.apparat.core.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

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
 * The one place outputs leave the app. The default destination comes from Settings: a subfolder of
 * Downloads (through MediaStore, no permission needed) or a folder the user picked with the system
 * folder picker (a persisted tree URI). If that folder is gone or its permission was revoked, saves
 * fall back to Downloads/Apparat and say so.
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

    /**
     * Makes a user-typed folder name safe: no path separators or "..", only characters that also
     * work on Windows when the phone is plugged into a PC. Empty results become [DEFAULT_FOLDER].
     */
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

    /**
     * The current default destination. A picked folder the app can no longer write to (deleted,
     * permission revoked, restored from a backup without the permission) resolves to Downloads.
     * The setting is only cleared when a save actually falls back, so the user is told.
     */
    fun destination(context: Context): Destination {
        val settings = AppSettings(context)
        val tree = settings.saveTreeUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (tree != null && canWriteTree(context, tree)) return Destination.Tree(tree, treeLabel(context, tree))
        return Destination.Downloads(settings.saveFolderName)
    }

    /** True when a folder was picked in Settings but can't be used any more. */
    fun isPickedFolderUnavailable(context: Context): Boolean {
        val tree = AppSettings(context).saveTreeUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return false
        return !canWriteTree(context, tree)
    }

    /** Remembers a folder picked with OpenDocumentTree as the default, releasing the previous one. */
    fun setDefaultTree(context: Context, tree: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching { context.contentResolver.takePersistableUriPermission(tree, flags) }.isSuccess
        if (!ok || !canWriteTree(context, tree)) return false
        val settings = AppSettings(context)
        settings.saveTreeUri?.let { old -> if (old != tree.toString()) releaseTree(context, Uri.parse(old)) }
        settings.saveTreeUri = tree.toString()
        return true
    }

    /** Back to Downloads/[folder]; releases a picked folder's permission. */
    fun useDownloads(context: Context, folder: String) {
        val settings = AppSettings(context)
        settings.saveTreeUri?.let { releaseTree(context, Uri.parse(it)) }
        settings.saveTreeUri = null
        settings.saveFolderName = sanitizeFolderName(folder)
    }

    private fun releaseTree(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(tree, flags) }
    }

    private fun canWriteTree(context: Context, tree: Uri): Boolean {
        val granted = context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!granted) return false
        return runCatching {
            context.contentResolver.query(rootDocument(tree), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }

    private fun rootDocument(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** A short human name for a picked folder, e.g. "Work" or "SD card › Scans". */
    fun treeLabel(context: Context, tree: Uri): String {
        val name = runCatching {
            context.contentResolver.query(rootDocument(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() }
            ?: DocumentsContract.getTreeDocumentId(tree).substringAfter(':').substringAfterLast('/').ifBlank { "Chosen folder" }
    }

    // ------------------------------------------------------------ saving

    /** Saves to the default destination (Settings). */
    suspend fun saveToDefault(context: Context, items: List<SaveItem>, onProgress: (Int, Int) -> Unit = { _, _ -> }): SaveOutcome =
        withContext(Dispatchers.IO) {
            val settings = AppSettings(context)
            val lostPickedFolder = isPickedFolderUnavailable(context)
            if (lostPickedFolder) settings.saveTreeUri = null
            when (val dest = destination(context)) {
                is Destination.Downloads -> saveToDownloads(context, items, dest.folder, onProgress, fellBack = lostPickedFolder)
                is Destination.Tree -> {
                    val outcome = saveToTree(context, dest.uri, dest.label, items, onProgress)
                    if (outcome.savedCount > 0) outcome
                    else saveToDownloads(context, items, settings.saveFolderName, onProgress, fellBack = true)
                }
            }
        }

    /** Saves into a folder picked just for this save (OpenDocumentTree result; no persisted grant needed). */
    suspend fun saveToPickedFolder(context: Context, tree: Uri, items: List<SaveItem>, onProgress: (Int, Int) -> Unit = { _, _ -> }): SaveOutcome =
        withContext(Dispatchers.IO) { saveToTree(context, tree, treeLabel(context, tree), items, onProgress) }

    /** Writes one file into a document the user just created with the system "Save as" picker. */
    suspend fun saveToDocument(context: Context, file: File, target: Uri): SaveOutcome = withContext(Dispatchers.IO) {
        val ok = copyInto(context, file, target)
        val name = runCatching { FileUtils.getFileName(context, target) }.getOrDefault(file.name)
        if (!ok) runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
        SaveOutcome(if (ok) 1 else 0, 1, name)
    }

    private suspend fun saveToDownloads(
        context: Context, items: List<SaveItem>, folder: String, onProgress: (Int, Int) -> Unit, fellBack: Boolean
    ): SaveOutcome {
        var saved = 0
        items.forEachIndexed { i, item ->
            if (FileUtils.saveToDownloads(context, item.file, item.mimeType, item.name, folder) != null) saved++
            withContext(Dispatchers.Main) { onProgress(i + 1, items.size) }
        }
        return SaveOutcome(saved, items.size, "Downloads/$folder", fellBack)
    }

    private suspend fun saveToTree(
        context: Context, tree: Uri, label: String, items: List<SaveItem>, onProgress: (Int, Int) -> Unit
    ): SaveOutcome {
        val parent = runCatching { rootDocument(tree) }.getOrNull() ?: return SaveOutcome(0, items.size, label)
        var saved = 0
        items.forEachIndexed { i, item ->
            val doc = runCatching {
                DocumentsContract.createDocument(context.contentResolver, parent, item.mimeType, item.name)
            }.getOrNull()
            if (doc != null) {
                if (copyInto(context, item.file, doc)) saved++
                else runCatching { DocumentsContract.deleteDocument(context.contentResolver, doc) }
            }
            withContext(Dispatchers.Main) { onProgress(i + 1, items.size) }
        }
        return SaveOutcome(saved, items.size, label)
    }

    private fun copyInto(context: Context, file: File, target: Uri): Boolean = try {
        context.contentResolver.openOutputStream(target, "wt")?.use { out ->
            FileInputStream(file).use { it.copyTo(out, 64 * 1024) }
            true
        } ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
