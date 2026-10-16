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
     * Strips data URL prefix if present (e.g. data:image/png;base64,....).
     */
    fun stripDataUrlPrefix(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val commaIndex = trimmed.indexOf(',')
            if (commaIndex != -1) {
                return trimmed.substring(commaIndex + 1).trim()
            }
        }
        return trimmed
    }

    /**
     * Base64 encode with URL-safe option.
     */
    fun encodeBase64(input: String, urlSafe: Boolean = false): String {
        val flags = Base64.NO_WRAP or (if (urlSafe) Base64.URL_SAFE else 0)
        return Base64.encodeToString(input.toByteArray(Charsets.UTF_8), flags)
    }

    /**
     * Decodes Base64 to raw bytes, supporting optional data-URL prefix and automatic flag fallback.
     */
    fun decodeBase64ToBytes(input: String, urlSafe: Boolean = false): Result<ByteArray> = runCatching {
        val clean = stripDataUrlPrefix(input)
        if (clean.isEmpty()) return@runCatching ByteArray(0)

        val primaryFlags = if (urlSafe) Base64.URL_SAFE else Base64.DEFAULT
        val fallbackFlags = if (urlSafe) Base64.DEFAULT else Base64.URL_SAFE

        try {
            Base64.decode(clean, primaryFlags)
        } catch (_: IllegalArgumentException) {
            Base64.decode(clean, fallbackFlags)
        }
    }

    /**
     * Decodes Base64 to UTF-8 text with strict validation. Throws if binary data is detected.
     */
    fun decodeBase64(input: String, urlSafe: Boolean = false): Result<String> = runCatching {
        val bytes = decodeBase64ToBytes(input, urlSafe).getOrThrow()
        if (bytes.isEmpty()) return@runCatching ""

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
     * Detects MIME type and file extension from magic bytes for common formats (PNG, JPEG, WebP, GIF, PDF).
     * Returns null if bytes are insufficient or unrecognized.
     */
    fun detectBinaryType(bytes: ByteArray): Pair<String, String>? {
        if (bytes.size < 12) return null

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        ) {
            return Pair("image/png", ".png")
        }

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return Pair("image/jpeg", ".jpg")
        }

        // WebP: RIFF....WEBP (bytes 0..3 == RIFF and bytes 8..11 == WEBP)
        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() && bytes[9] == 0x42.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        ) {
            return Pair("image/webp", ".webp")
        }

        // GIF: GIF87a or GIF89a
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte() &&
            (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) &&
            bytes[5] == 0x61.toByte()
        ) {
            return Pair("image/gif", ".gif")
        }

        // PDF: %PDF-
        if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte() && bytes[4] == 0x2D.toByte()
        ) {
            return Pair("application/pdf", ".pdf")
        }

        return null
    }

    /**
     * Escapes special characters for ZXing Wi-Fi QR format (\, ;, ,, :, ").
     * Critical: \ must be escaped FIRST to prevent double-escaping.
     */
    fun escapeWifiField(input: String): String =
        input.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")

    /**
     * Formats Wi-Fi configuration into standard ZXing QR payload.
     */
    fun formatWifiQr(ssid: String, pass: String, sec: String, hidden: Boolean = false): String {
        if (ssid.isBlank()) return ""
        val secType = when (sec) {
            "WEP" -> "WEP"
            "nopass" -> "nopass"
            else -> "WPA"
        }
        val pPart = if (secType != "nopass" && pass.isNotEmpty()) "P:${escapeWifiField(pass)};" else ""
        val hPart = if (hidden) "H:true;" else ""
        return "WIFI:T:$secType;S:${escapeWifiField(ssid)};$pPart$hPart;"
    }

    /**
     * Formats NPCI-compliant UPI Payment QR payload.
     */
    fun formatUpiQr(vpa: String, name: String, amount: String = "", note: String = ""): String {
        if (vpa.isBlank()) return ""
        val encode = { param: String ->
            try {
                java.net.URLEncoder.encode(param, "UTF-8").replace("+", "%20")
            } catch (_: Exception) {
                param
            }
        }
        return buildString {
            append("upi://pay?")
            append("pa=").append(encode(vpa.trim()))
            if (name.isNotBlank()) {
                append("&pn=").append(encode(name.trim()))
            }
            if (amount.isNotBlank()) {
                append("&am=").append(encode(amount.trim()))
            }
            append("&cu=INR")
            if (note.isNotBlank()) {
                append("&tn=").append(encode(note.trim()))
            }
        }
    }

    /**
     * Formats vCard 3.0 QR payload.
     */
    fun formatVCardQr(name: String, phone: String, email: String): String {
        if (name.isBlank() && phone.isBlank() && email.isBlank()) return ""
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            if (name.isNotBlank()) appendLine("FN:${name.trim().replace(";", "\\;").replace(",", "\\,")}")
            if (phone.isNotBlank()) appendLine("TEL;TYPE=CELL:${phone.trim()}")
            if (email.isNotBlank()) appendLine("EMAIL;TYPE=INTERNET:${email.trim()}")
            append("END:VCARD")
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
