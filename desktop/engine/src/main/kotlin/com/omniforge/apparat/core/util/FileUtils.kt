package com.omniforge.apparat.core.util

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.desktop.platform.DesktopShell
import com.omniforge.apparat.desktop.platform.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.DecimalFormat

/**
 * Desktop version of the Android app's FileUtils, with the same functions. Inputs are local
 * files; "share" reveals the output in the file manager, "open" uses the default app.
 */
object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups]
    }

    fun getFileName(context: Context, uri: Uri): String = uri.file.name.takeIf { it.isNotBlank() } ?: "unnamed_file"

    fun getFileSize(context: Context, uri: Uri): Long = runCatching { uri.file.length() }.getOrDefault(0L)

    /** A private copy of [uri] in the cache, owned (and deleted) by the caller. */
    fun copyUriToCache(context: Context, uri: Uri, targetFileName: String? = null): File {
        val name = targetFileName ?: getFileName(context, uri)
        val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
        val cacheFile = File(context.cacheDir, "input_${System.currentTimeMillis()}_${System.nanoTime() % 100000}$ext")
        try {
            Files.copy(uri.file.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return cacheFile
        } catch (e: Exception) {
            cacheFile.delete()
            throw IllegalStateException("Could not read ${uri.file.name}. Pick it again.", e)
        }
    }

    fun createTempOutputFile(context: Context, prefix: String, extensionWithDot: String): File {
        val dir = File(context.cacheDir, "outputs").apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}_${System.nanoTime() % 100000}$extensionWithDot")
    }

    fun getContentUri(context: Context, file: File): Uri = Uri.fromFile(file)

    /** MIME type of an output file, from its extension (outputs always have one). */
    fun mimeForFile(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "txt" -> "text/plain"
        else -> MimeTypes.forExtension(file.extension) ?: "application/octet-stream"
    }

    /** "<base of [originalName]><suffix>.<ext of [output]>", e.g. report.pdf + "_compressed" → report_compressed.pdf */
    fun outputName(originalName: String?, fallbackBase: String, suffix: String, output: File): String {
        val base = originalName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: fallbackBase
        val ext = output.extension.ifEmpty { "bin" }
        return "$base$suffix.$ext"
    }

    /** Desktop "share": shows the file in the file manager so it can be dragged or attached anywhere. */
    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Output file does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        if (!DesktopShell.reveal(file)) Toast.makeText(context, "Could not open the file manager", Toast.LENGTH_SHORT).show()
    }

    fun shareFiles(context: Context, files: List<File>, mimeType: String) {
        val existing = files.filter { it.exists() && it.length() > 0L }
        if (existing.isEmpty()) {
            Toast.makeText(context, "Output file does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        shareFile(context, existing.first(), mimeType)
    }

    fun openFile(context: Context, file: File, mimeType: String? = null) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Output file does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        if (!DesktopShell.open(file)) Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }

    /** [dir]/[name], or "name (2).ext", "name (3).ext"… if taken. */
    fun uniqueFile(dir: File, name: String): File {
        val safe = name.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").ifBlank { "output" }
        var candidate = File(dir, safe)
        if (!candidate.exists()) return candidate
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "").let { if (it.isEmpty() || it == safe) "" else ".$it" }
        var n = 2
        while (candidate.exists()) candidate = File(dir, "$base ($n)$ext").also { n++ }
        return candidate
    }

    /**
     * Copies [file] into Downloads/[subfolder] (never overwriting: a number is added). Returns the
     * saved file's Uri, or null on failure.
     */
    suspend fun saveToDownloads(
        context: Context,
        file: File,
        mimeType: String,
        displayName: String,
        subfolder: String = "Apparat"
    ): Uri? = withContext(Dispatchers.IO) { copyToFolder(file, File(DesktopPaths.downloadsDir, subfolder), displayName) }

    /** Copies [file] into [dir] as [displayName] (made unique). Returns the new file's Uri or null. */
    fun copyToFolder(file: File, dir: File, displayName: String): Uri? = runCatching {
        dir.mkdirs()
        val target = uniqueFile(dir, displayName)
        val tmp = File(dir, ".${target.name}.part")
        try {
            Files.copy(file.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        Uri.fromFile(target)
    }.getOrNull()

    /**
     * Replaces [originalUri]'s file with [newFile]. The new content is written next to the
     * original and moved over it in one step, so the original is never half-written. Returns
     * false (original untouched) when the folder isn't writable.
     */
    suspend fun overwriteOriginal(context: Context, originalUri: Uri, newFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!newFile.exists() || newFile.length() == 0L) return@withContext false
        val original = originalUri.file
        val dir = original.parentFile ?: return@withContext false
        if (!original.exists() || !original.canWrite() || !dir.canWrite()) return@withContext false
        val tmp = File(dir, ".${original.name}.apparat-${System.nanoTime()}")
        try {
            Files.copy(newFile.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (tmp.length() != newFile.length()) return@withContext false
            try {
                Files.move(tmp.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            original.length() == newFile.length()
        } catch (_: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    fun isImageFormatMatching(mime: String?, fileName: String, target: com.omniforge.apparat.engine.image.ImageOutputFormat): Boolean {
        val lowerMime = mime?.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (target) {
            com.omniforge.apparat.engine.image.ImageOutputFormat.JPEG -> lowerMime in listOf("image/jpeg", "image/jpg") || ext in listOf("jpg", "jpeg", "jpe")
            com.omniforge.apparat.engine.image.ImageOutputFormat.PNG -> lowerMime == "image/png" || ext == "png"
            com.omniforge.apparat.engine.image.ImageOutputFormat.WEBP -> lowerMime == "image/webp" || ext == "webp"
            com.omniforge.apparat.engine.image.ImageOutputFormat.HEIC ->
                lowerMime in listOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence") || ext in listOf("heic", "heif")
        }
    }

    fun isImageFormatMatching(context: Context, uri: Uri, target: com.omniforge.apparat.engine.image.ImageOutputFormat): Boolean =
        isImageFormatMatching(context.contentResolver.getType(uri), getFileName(context, uri), target)

    fun isVideoMp4(mime: String?, fileName: String): Boolean {
        val lowerMime = mime?.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return lowerMime == "video/mp4" || ext in listOf("mp4", "m4v")
    }

    fun isVideoMp4(context: Context, uri: Uri): Boolean = isVideoMp4(context.contentResolver.getType(uri), getFileName(context, uri))

    fun cleanStaleCacheDir(
        cacheDir: File,
        maxAgeMs: Long = 24 * 60 * 60 * 1000L,
        appStartTime: Long = System.currentTimeMillis()
    ) {
        runCatching {
            File(cacheDir, "pdfbox").listFiles()?.forEach { if (it.lastModified() < appStartTime) it.deleteRecursively() }
        }
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                val name = file.name
                if ((name.startsWith("input_") || name.startsWith("decrypted_") || name.startsWith("backup_") || name.startsWith("work_")) &&
                    file.lastModified() < appStartTime
                ) file.deleteRecursively()
            }
        }
        val cutoff = System.currentTimeMillis() - maxAgeMs
        runCatching {
            File(cacheDir, "outputs").listFiles()?.forEach { if (it.lastModified() < cutoff) it.deleteRecursively() }
        }
    }

    fun cleanStaleCache(
        context: Context,
        maxAgeMs: Long = 24 * 60 * 60 * 1000L,
        appStartTime: Long = System.currentTimeMillis()
    ) = cleanStaleCacheDir(context.cacheDir, maxAgeMs, appStartTime)
}
