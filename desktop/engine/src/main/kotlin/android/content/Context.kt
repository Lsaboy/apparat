package android.content

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.desktop.platform.MimeTypes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/**
 * Desktop stand-in for android.content.Context: one app-wide instance ([Context.app]) that knows
 * the app's cache/config folders and hands out the few services the shared screens use.
 */
open class Context internal constructor() {
    open val applicationContext: Context get() = this
    val packageName: String = "com.omniforge.apparat"
    val cacheDir: File get() = DesktopPaths.cacheDir
    val filesDir: File get() = DesktopPaths.dataDir
    val contentResolver: ContentResolver = ContentResolver()

    fun getSystemService(name: String): Any? = when (name) {
        CLIPBOARD_SERVICE -> ClipboardManager
        else -> null
    }

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferences.named(name)

    /** No services on desktop: jobs run in-process while the window is open. */
    fun startService(intent: Intent): Any? = null

    fun startActivity(intent: Intent) {}

    companion object {
        const val CLIPBOARD_SERVICE = "clipboard"
        const val ACTIVITY_SERVICE = "activity"
        const val NOTIFICATION_SERVICE = "notification"
        const val MODE_PRIVATE = 0

        /** The single application context. */
        val app: Context = Context()
    }
}

class ContentResolver internal constructor() {
    fun openInputStream(uri: Uri): InputStream? = runCatching { FileInputStream(uri.file) }.getOrNull()
        ?: throw java.io.FileNotFoundException(uri.file.path)

    fun openOutputStream(uri: Uri): OutputStream? = openOutputStream(uri, "w")

    /** "wa" appends; any other mode truncates (as "w"/"wt" do on Android). */
    fun openOutputStream(uri: Uri, mode: String): OutputStream? {
        uri.file.parentFile?.mkdirs()
        return FileOutputStream(uri.file, mode.contains('a'))
    }

    fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = uri.file
        if (mode.contains('w')) {
            if (!f.exists() || !f.canWrite()) return null
        } else if (!f.canRead()) {
            throw java.io.FileNotFoundException(f.path)
        }
        return ParcelFileDescriptor(f)
    }

    fun getType(uri: Uri): String? = MimeTypes.forName(uri.file.name)
}

class Intent(val action: String? = null) {
    constructor(context: Context, cls: Class<*>) : this(null)

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    }
}

class ActivityNotFoundException(message: String? = null) : RuntimeException(message)

/**
 * Desktop stand-in for SharedPreferences: a properties file per name in the app's config folder.
 * Writes are applied immediately (apply() and commit() both save synchronously; files are tiny).
 */
class SharedPreferences private constructor(private val file: File) {
    private val props = Properties()

    init {
        if (file.exists()) runCatching { file.inputStream().use { props.load(it) } }
    }

    @Synchronized fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def
    @Synchronized fun getBoolean(key: String, def: Boolean): Boolean = props.getProperty(key)?.toBooleanStrictOrNull() ?: def
    @Synchronized fun getInt(key: String, def: Int): Int = props.getProperty(key)?.toIntOrNull() ?: def
    @Synchronized fun getLong(key: String, def: Long): Long = props.getProperty(key)?.toLongOrNull() ?: def
    @Synchronized fun getFloat(key: String, def: Float): Float = props.getProperty(key)?.toFloatOrNull() ?: def
    @Synchronized fun getStringSet(key: String, def: Set<String>?): Set<String>? =
        props.getProperty(key)?.let { raw -> if (raw.isEmpty()) emptySet() else raw.split(SET_SEPARATOR).toSet() } ?: def
    @Synchronized fun contains(key: String): Boolean = props.containsKey(key)

    fun edit(): Editor = Editor()

    inner class Editor internal constructor() {
        private val changes = LinkedHashMap<String, String?>()
        private var clearAll = false

        fun putString(key: String, value: String?) = apply { changes[key] = value }
        fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value.toString() }
        fun putInt(key: String, value: Int) = apply { changes[key] = value.toString() }
        fun putLong(key: String, value: Long) = apply { changes[key] = value.toString() }
        fun putFloat(key: String, value: Float) = apply { changes[key] = value.toString() }
        fun putStringSet(key: String, value: Set<String>?) = apply { changes[key] = value?.joinToString(SET_SEPARATOR) }
        fun remove(key: String) = apply { changes[key] = null }
        fun clear() = apply { clearAll = true }

        fun apply() { commit() }

        fun commit(): Boolean = synchronized(this@SharedPreferences) {
            if (clearAll) props.clear()
            for ((k, v) in changes) if (v == null) props.remove(k) else props.setProperty(k, v)
            runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.outputStream().use { props.store(it, null) }
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }.isSuccess
        }
    }

    companion object {
        private const val SET_SEPARATOR = "\u001F"
        private val cache = HashMap<String, SharedPreferences>()

        @Synchronized
        fun named(name: String): SharedPreferences =
            cache.getOrPut(name) { SharedPreferences(File(DesktopPaths.configDir, "$name.properties")) }
    }
}
