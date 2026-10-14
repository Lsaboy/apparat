package com.omniforge.app.engine.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.omniforge.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object UtilityEngine {

    /**
     * Generates a high-resolution QR code bitmap 100% offline using ZXing.
     */
    fun generateQrCode(text: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        return bitmap
    }

    /**
     * Saves QR Bitmap to temporary PNG file for sharing or downloading.
     */
    suspend fun saveQrToFile(context: Context, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val file = FileUtils.createTempOutputFile(context, "qrcode", ".png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    }

    /**
     * Calculates MD5, SHA-1, SHA-256, or SHA-512 checksum of any file without reading it into memory all at once.
     */
    suspend fun calculateChecksum(
        context: Context,
        uri: Uri,
        algorithm: String = "SHA-256"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(8192)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            } ?: throw IllegalStateException("Could not read input file")

            val hashBytes = digest.digest()
            hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Base64 encode and decode
     */
    fun encodeBase64(input: String): String =
        Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    fun decodeBase64(input: String): Result<String> = runCatching {
        String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8)
    }

    /**
     * Prettifies or minifies JSON strings.
     */
    fun formatJson(jsonString: String, pretty: Boolean = true): Result<String> = runCatching {
        val trimmed = jsonString.trim()
        val indent = if (pretty) 4 else 0
        if (trimmed.startsWith("{")) {
            org.json.JSONObject(trimmed).toString(indent)
        } else if (trimmed.startsWith("[")) {
            org.json.JSONArray(trimmed).toString(indent)
        } else {
            throw IllegalArgumentException("Not a valid JSON object or array")
        }
    }

    /**
     * Converts text casing.
     */
    enum class CaseMode(val label: String) {
        UPPERCASE("UPPERCASE"),
        LOWERCASE("lowercase"),
        TITLE_CASE("Title Case"),
        CAMEL_CASE("camelCase"),
        SNAKE_CASE("snake_case"),
        KEBAB_CASE("kebab-case")
    }

    fun convertCase(text: String, mode: CaseMode): String {
        val words = text.trim().split(Regex("[\\s_\\-]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return text
        return when (mode) {
            CaseMode.UPPERCASE -> text.uppercase()
            CaseMode.LOWERCASE -> text.lowercase()
            CaseMode.TITLE_CASE -> words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            CaseMode.CAMEL_CASE -> words.mapIndexed { index, w ->
                if (index == 0) w.lowercase() else w.replaceFirstChar { it.uppercase() }
            }.joinToString("")
            CaseMode.SNAKE_CASE -> words.joinToString("_") { it.lowercase() }
            CaseMode.KEBAB_CASE -> words.joinToString("-") { it.lowercase() }
        }
    }
}
