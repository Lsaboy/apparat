package com.omniforge.app.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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

    fun getFileName(context: Context, uri: Uri): String {
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
        return result ?: "unnamed_file"
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0) {
                        return it.getLong(index)
                    }
                }
            }
        } else if (uri.scheme == "file") {
            uri.path?.let { File(it).length() }?.let { return it }
        }
        return 0L
    }

    fun copyUriToCache(context: Context, uri: Uri, targetFileName: String? = null): File {
        val name = targetFileName ?: getFileName(context, uri)
        val cacheFile = File(context.cacheDir, "input_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open input stream for $uri")
        return cacheFile
    }

    fun createTempOutputFile(context: Context, prefix: String, extensionWithDot: String): File {
        val dir = File(context.cacheDir, "outputs").apply { mkdirs() }
        return File(dir, "${prefix}_${System.currentTimeMillis()}$extensionWithDot")
    }

    fun shareFile(context: Context, file: File, mimeType: String = "*/*") {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share output")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun saveToDownloads(context: Context, file: File, mimeType: String, displayName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OmniForge")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val itemUri = resolver.insert(collection, contentValues) ?: return null

        resolver.openOutputStream(itemUri)?.use { out ->
            FileInputStream(file).use { input ->
                input.copyTo(out)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }

        return itemUri
    }
}
