package android.net

import java.io.File

/**
 * Desktop stand-in for android.net.Uri. On desktop every picked item is a local file, so a Uri
 * simply wraps an absolute [File]. Shared screens treat it as an opaque handle, as on Android.
 */
class Uri private constructor(val file: File) {
    val scheme: String get() = "file"
    val path: String get() = file.path
    val lastPathSegment: String get() = file.name
    val authority: String? get() = null
    val host: String? get() = null

    override fun toString(): String = file.toURI().toString()
    override fun equals(other: Any?): Boolean = other is Uri && other.file == file
    override fun hashCode(): Int = file.hashCode()

    companion object {
        @JvmStatic
        fun fromFile(file: File): Uri = Uri(file.absoluteFile)

        @JvmStatic
        fun parse(uriString: String): Uri =
            if (uriString.startsWith("file:")) Uri(File(java.net.URI(uriString)).absoluteFile)
            else Uri(File(uriString).absoluteFile)
    }
}

/** The file behind a desktop [Uri]. */
fun Uri.toFile(): File = file
