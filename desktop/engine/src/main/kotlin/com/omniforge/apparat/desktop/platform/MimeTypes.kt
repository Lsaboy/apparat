package com.omniforge.apparat.desktop.platform

/** File-extension based MIME types (desktop files carry no MIME of their own). */
object MimeTypes {
    private val byExt = mapOf(
        "pdf" to "application/pdf",
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "jpe" to "image/jpeg", "jfif" to "image/jpeg",
        "png" to "image/png", "webp" to "image/webp", "gif" to "image/gif", "bmp" to "image/bmp",
        "heic" to "image/heic", "heif" to "image/heif", "avif" to "image/avif",
        "tif" to "image/tiff", "tiff" to "image/tiff", "ico" to "image/x-icon", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm",
        "mov" to "video/quicktime", "3gp" to "video/3gpp", "3g2" to "video/3gpp2", "avi" to "video/x-msvideo",
        "wmv" to "video/x-ms-wmv", "flv" to "video/x-flv", "ts" to "video/mp2t", "mts" to "video/mp2t",
        "m2ts" to "video/mp2t", "mpg" to "video/mpeg", "mpeg" to "video/mpeg", "ogv" to "video/ogg",
        "m4a" to "audio/mp4", "aac" to "audio/aac", "mp3" to "audio/mpeg", "wav" to "audio/wav",
        "ogg" to "audio/ogg", "opus" to "audio/opus", "flac" to "audio/flac", "wma" to "audio/x-ms-wma",
        "srt" to "application/x-subrip", "vtt" to "text/vtt", "txt" to "text/plain", "json" to "application/json",
        "zip" to "application/zip", "xml" to "text/xml", "csv" to "text/csv", "html" to "text/html"
    )

    fun forExtension(ext: String): String? = byExt[ext.lowercase().removePrefix(".")]

    fun forName(name: String): String? = forExtension(name.substringAfterLast('.', ""))

    /** Extensions matching a picker MIME filter such as "image/ *" or "application/pdf"; null = anything. */
    fun extensionsFor(mime: String): Set<String>? {
        val m = mime.lowercase().trim()
        if (m == "*/*" || m.isEmpty()) return null
        if (m.endsWith("/*")) {
            val prefix = m.removeSuffix("*")
            return byExt.filterValues { it.startsWith(prefix) }.keys
        }
        val exact = byExt.filterValues { it == m }.keys
        return when {
            exact.isNotEmpty() -> exact
            m == "text/plain" -> setOf("txt", "srt", "vtt")
            else -> null
        }
    }
}
