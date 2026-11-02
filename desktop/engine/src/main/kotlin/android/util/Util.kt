package android.util

/** Desktop stand-in for android.util.Log, printing to stderr. */
object Log {
    @JvmStatic fun d(tag: String, msg: String, t: Throwable? = null): Int = print("D", tag, msg, t)
    @JvmStatic fun i(tag: String, msg: String, t: Throwable? = null): Int = print("I", tag, msg, t)
    @JvmStatic fun w(tag: String, msg: String, t: Throwable? = null): Int = print("W", tag, msg, t)
    @JvmStatic fun e(tag: String, msg: String, t: Throwable? = null): Int = print("E", tag, msg, t)

    private fun print(level: String, tag: String, msg: String, t: Throwable?): Int {
        if (level == "D" && System.getProperty("apparat.debug") == null) return 0
        System.err.println("$level/$tag: $msg" + (t?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
        return 0
    }
}

/**
 * Desktop stand-in for android.util.Base64 with the same flags and lenient decoding
 * (whitespace ignored, padding optional, IllegalArgumentException on bad input).
 */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8
    const val NO_CLOSE = 16

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = String(encode(input, flags), Charsets.US_ASCII)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray {
        var encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) encoder = encoder.withoutPadding()
        val raw = encoder.encodeToString(input)
        if (flags and NO_WRAP != 0) return raw.toByteArray(Charsets.US_ASCII)
        // Android wraps at 76 characters and ends with a newline.
        val nl = if (flags and CRLF != 0) "\r\n" else "\n"
        val sb = StringBuilder()
        raw.chunked(76).forEach { sb.append(it).append(nl) }
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = decode(str.toByteArray(Charsets.US_ASCII), flags)

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray {
        val cleaned = StringBuilder(input.size)
        for (b in input) {
            val c = (b.toInt() and 0xFF).toChar()
            if (!c.isWhitespace()) cleaned.append(c)
        }
        var s = cleaned.toString().trimEnd('=')
        val urlSafe = flags and URL_SAFE != 0
        for (c in s) {
            val ok = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                (if (urlSafe) c == '-' || c == '_' else c == '+' || c == '/')
            if (!ok) throw IllegalArgumentException("bad base-64")
        }
        if (s.length % 4 == 1) throw IllegalArgumentException("bad base-64")
        while (s.length % 4 != 0) s += "="
        val decoder = if (urlSafe) java.util.Base64.getUrlDecoder() else java.util.Base64.getDecoder()
        return try { decoder.decode(s) } catch (e: IllegalArgumentException) { throw IllegalArgumentException("bad base-64", e) }
    }
}
