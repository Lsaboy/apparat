package android.os

import java.io.Closeable
import java.io.File

/** Desktop stand-in: the app never runs on an Android API level, so SDK_INT is 0. */
object Build {
    object VERSION {
        @JvmField val SDK_INT: Int = 0
        @JvmField val RELEASE: String = System.getProperty("os.version") ?: ""
    }

    object VERSION_CODES {
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
        const val VANILLA_ICE_CREAM = 35
    }

    @JvmField val MANUFACTURER: String = System.getProperty("os.name") ?: "Desktop"
    @JvmField val MODEL: String = System.getProperty("os.arch") ?: ""
}

/** Desktop stand-in for a file descriptor: just the file. */
class ParcelFileDescriptor(val file: File) : Closeable {
    val statSize: Long get() = file.length()
    /** On desktop this is the file itself (MediaMetadataRetriever accepts it). */
    val fileDescriptor: File get() = file
    override fun close() {}

    companion object {
        const val MODE_READ_ONLY = 0x10000000
        const val MODE_READ_WRITE = 0x30000000

        @JvmStatic
        fun open(file: File, mode: Int): ParcelFileDescriptor {
            if (!file.exists()) throw java.io.FileNotFoundException(file.path)
            return ParcelFileDescriptor(file)
        }
    }
}

object SystemClock {
    @JvmStatic fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
    @JvmStatic fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
}

class Bundle
