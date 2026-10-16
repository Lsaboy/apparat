package com.omniforge.app.engine.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.omniforge.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object UtilityEngine {

    /**
     * Generates a high-resolution QR code bitmap 100% offline using ZXing.
     * Uses a single bulk setPixels() call on ARGB_8888 avoiding hundreds of thousands of JNI crossings.
     */
    fun generateQrCode(text: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val offset = y * size
            for (x in 0 until size) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
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
     * Calculates MD5, SHA-1, SHA-256, and/or SHA-512 checksums in a single streaming pass
     * with a 64KB buffer, throttled progress notifications (>= 100ms), and cancellation safety.
     */
    suspend fun calculateChecksums(
        context: Context,
        uri: Uri,
        algorithms: List<String> = listOf("MD5", "SHA-1", "SHA-256", "SHA-512"),
        onProgress: (Float) -> Unit = {}
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val digests = algorithms.associateWith { MessageDigest.getInstance(it) }
            val buffer = ByteArray(65536)
            val fileSize = runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            }.getOrNull()?.takeIf { it > 0L } ?: 0L

            var bytesReadTotal = 0L
            var lastProgressTime = 0L

            context.contentResolver.openInputStream(uri)?.use { stream ->
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    coroutineContext.ensureActive()
                    for (d in digests.values) {
                        d.update(buffer, 0, read)
                    }
                    bytesReadTotal += read

                    val now = SystemClock.uptimeMillis()
                    if (now - lastProgressTime >= 100L) {
                        lastProgressTime = now
                        val progress = if (fileSize > 0L) (bytesReadTotal.toFloat() / fileSize).coerceIn(0f, 1f) else 0f
                        onProgress(progress)
                    }
                }
            } ?: throw IllegalStateException("Could not read input file")

            onProgress(1f)
            digests.mapValues { (_, d) ->
                d.digest().joinToString("") { "%02x".format(it) }
            }
        }
    }

    /**
     * Calculates MD5, SHA-1, SHA-256, or SHA-512 checksum of any file.
     * Delegates to single-pass [calculateChecksums].
     */
    suspend fun calculateChecksum(
        context: Context,
        uri: Uri,
        algorithm: String = "SHA-256"
    ): Result<String> = calculateChecksums(context, uri, listOf(algorithm)).map {
        it[algorithm] ?: throw IllegalStateException("Checksum $algorithm missing")
    }

    /**
     * Base64 encode and decode with URL-safe option and strict UTF-8 validation.
     */
    fun encodeBase64(input: String, urlSafe: Boolean = false): String {
        val flags = Base64.NO_WRAP or (if (urlSafe) Base64.URL_SAFE else 0)
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), flags)
    }

    fun decodeBase64(input: String, urlSafe: Boolean = false): Result<String> = runCatching {
        val clean = input.trim()
        if (clean.isEmpty()) return@runCatching ""

        val primaryFlags = if (urlSafe) Base64.URL_SAFE else Base64.DEFAULT
        val fallbackFlags = if (urlSafe) Base64.DEFAULT else Base64.URL_SAFE

        val bytes = try {
            Base64.decode(clean, primaryFlags)
        } catch (_: IllegalArgumentException) {
            Base64.decode(clean, fallbackFlags)
        }

        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("Decoded ${bytes.size} bytes of binary data (not valid UTF-8 text).")
        }
    }

    /**
     * Prettifies or minifies JSON strings with line and column reporting on syntax errors.
     */
    fun formatJson(jsonString: String, pretty: Boolean = true): Result<String> = runCatching {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("JSON input is empty")
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw IllegalArgumentException("JSON must start with '{' or '[' (root must be an object or array)")
        }

        try {
            val indent = if (pretty) 4 else 0
            if (trimmed.startsWith("{")) {
                org.json.JSONObject(trimmed).toString(indent)
            } else {
                org.json.JSONArray(trimmed).toString(indent)
            }
        } catch (e: org.json.JSONException) {
            val message = e.message ?: "Invalid JSON syntax"
            val match = Regex("""at character\s+(\d+)""").find(message)
            if (match != null) {
                val charIndex = match.groupValues[1].toIntOrNull() ?: 0
                val safeIndex = charIndex.coerceIn(0, trimmed.length)
                val prefix = trimmed.take(safeIndex)
                val lines = prefix.split('\n')
                val line = lines.size
                val column = lines.last().length + 1
                val cleanDesc = message.substringBefore(" at character")
                throw IllegalArgumentException("Line $line, Col $column: $cleanDesc")
            } else {
                throw IllegalArgumentException(message)
            }
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
