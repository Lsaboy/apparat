package com.vedant.apparat.core.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

object FileUtils {

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups]
    }

    fun getFileName(context: Context, uri: Uri): String = runCatching {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        result?.takeIf { it.isNotBlank() } ?: "unnamed_file"
    }.getOrDefault("unnamed_file")

    fun getFileSize(context: Context, uri: Uri): Long = runCatching {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0) {
                        return@runCatching it.getLong(index)
                    }
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { File(it).length() }?.let { return@runCatching it }
        }
        0L
    }.getOrDefault(0L)

    fun copyUriToCache(context: Context, uri: Uri, targetFileName: String? = null): File {
        val name = targetFileName ?: getFileName(context, uri)
        val ext = name.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
        val cacheFile = File(context.cacheDir, "input_${System.currentTimeMillis()}$ext")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open input stream for $uri")
            return cacheFile
        } catch (e: Exception) {
            cacheFile.delete()
            throw e
        }
    }

    fun createTempOutputFile(context: Context, prefix: String, extensionWithDot: String): File {
        val dir = File(context.cacheDir, "outputs").apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}$extensionWithDot")
    }

    fun getContentUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Output file does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri("Shared File", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share output").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun openFile(context: Context, file: File, mimeType: String? = null) {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Output file does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, file)
            val extension = file.extension.lowercase()
            val resolvedMime = mimeType?.takeIf { it.isNotBlank() }
                ?: (if (extension.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) else null)
                ?: when (extension) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "pdf" -> "application/pdf"
                    "mp4" -> "video/mp4"
                    "m4a" -> "audio/mp4"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    else -> "*/*"
                }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, resolvedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Output File", contentUri)
            }
            val chooser = Intent.createChooser(viewIntent, "Open with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Permission denied opening file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open file: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    const val DOWNLOAD_SUBFOLDER = "Apparat"

    /** Copies [file] into Downloads/Apparat on the IO dispatcher. Returns null on failure. */
    suspend fun saveToDownloads(context: Context, file: File, mimeType: String, displayName: String): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_SUBFOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) }.getOrNull()
            ?: return@withContext null

        try {
            val out = resolver.openOutputStream(itemUri) ?: throw java.io.IOException("Could not open output")
            out.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            }
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
            itemUri
        } catch (t: Throwable) {
            // Don't leave a half-written pending entry behind (also on cancellation).
            runCatching { resolver.delete(itemUri, null, null) }
            if (t is kotlinx.coroutines.CancellationException) throw t
            null
        }
    }

    /**
     * Replaces the content behind [originalUri] with [newFile]. Returns true only when the new
     * content was written and checked. A false return means the original is unchanged, or was put
     * back from a backup if the write failed halfway.
     *
     * Opening a content URI for writing truncates it immediately, and there is no atomic replace for
     * picker/MediaStore URIs, so the original is first backed up to cache and restored on failure.
     */
    suspend fun overwriteOriginal(context: Context, originalUri: Uri, newFile: File): Boolean = withContext(Dispatchers.IO) {
        if (!newFile.exists() || newFile.length() == 0L) return@withContext false
        val resolver = context.contentResolver

        // 1. Can we write at all? "rw" doesn't truncate, so a refusal costs nothing.
        val writable = runCatching { resolver.openFileDescriptor(originalUri, "rw")?.use { true } ?: false }.getOrDefault(false)
        if (!writable) return@withContext false

        // 2. Back up the original (needs the space; without a backup we won't risk it).
        val backup = File(context.cacheDir, "backup_${System.currentTimeMillis()}_${System.nanoTime()}")
        try {
            val originalSize = runCatching { resolver.openFileDescriptor(originalUri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
            if (originalSize > 0 && context.cacheDir.usableSpace < originalSize + originalSize / 5) return@withContext false
            val copied = runCatching {
                resolver.openInputStream(originalUri)?.use { input ->
                    FileOutputStream(backup).use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (!copied) return@withContext false

            // 3. Write, then check the size that actually landed.
            val written = try {
                resolver.openOutputStream(originalUri, "wt")?.use { output ->
                    FileInputStream(newFile).use { input -> input.copyTo(output) }
                } != null
            } catch (e: Exception) {
                false
            }
            val landedSize = runCatching { resolver.openFileDescriptor(originalUri, "r")?.use { it.statSize } }.getOrNull() ?: -1L
            if (written && (landedSize < 0 || landedSize == newFile.length())) return@withContext true

            // 4. Something went wrong after truncation: put the original back.
            runCatching {
                resolver.openOutputStream(originalUri, "wt")?.use { output ->
                    FileInputStream(backup).use { input -> input.copyTo(output) }
                }
            }
            false
        } finally {
            backup.delete()
        }
    }

    fun isImageFormatMatching(mime: String?, fileName: String, target: com.vedant.apparat.engine.image.ImageOutputFormat): Boolean {
        val lowerMime = mime?.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (target) {
            com.vedant.apparat.engine.image.ImageOutputFormat.JPEG -> lowerMime in listOf("image/jpeg", "image/jpg") || ext in listOf("jpg", "jpeg", "jpe")
            com.vedant.apparat.engine.image.ImageOutputFormat.PNG -> lowerMime == "image/png" || ext == "png"
            com.vedant.apparat.engine.image.ImageOutputFormat.WEBP -> lowerMime == "image/webp" || ext == "webp"
        }
    }

    fun isImageFormatMatching(context: Context, uri: Uri, target: com.vedant.apparat.engine.image.ImageOutputFormat): Boolean {
        return isImageFormatMatching(context.contentResolver.getType(uri), getFileName(context, uri), target)
    }

    fun isVideoMp4(mime: String?, fileName: String): Boolean {
        val lowerMime = mime?.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return lowerMime == "video/mp4" || ext in listOf("mp4", "m4v")
    }

    fun isVideoMp4(context: Context, uri: Uri): Boolean {
        return isVideoMp4(context.contentResolver.getType(uri), getFileName(context, uri))
    }

    fun cleanStaleCacheDir(
        cacheDir: File,
        maxAgeMs: Long = 24 * 60 * 60 * 1000L,
        appStartTime: Long = System.currentTimeMillis()
    ) {
        // 1. PDFBox scratch files (created prior to this process start)
        runCatching {
            File(cacheDir, "pdfbox").listFiles()?.forEach { file ->
                if (file.lastModified() < appStartTime) {
                    file.deleteRecursively()
                }
            }
        }

        // 2. Decrypted PDFs and copied input files
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                val name = file.name
                if ((name.startsWith("input_") || name.startsWith("decrypted_") || name.startsWith("backup_")) && file.lastModified() < appStartTime) {
                    file.delete()
                }
            }
        }

        // 3. Stale outputs older than maxAgeMs (default 24h)
        val cutoff = System.currentTimeMillis() - maxAgeMs
        runCatching {
            File(cacheDir, "outputs").listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.deleteRecursively()
                }
            }
        }
    }

    /**
     * Cleans stale temp files from cacheDir.
     * Guards against deleting active files by checking lastModified against appStartTime and maxAgeMs.
     */
    fun cleanStaleCache(
        context: Context,
        maxAgeMs: Long = 24 * 60 * 60 * 1000L,
        appStartTime: Long = System.currentTimeMillis()
    ) {
        val cacheDir = context.cacheDir ?: return
        cleanStaleCacheDir(cacheDir, maxAgeMs, appStartTime)
    }
}

