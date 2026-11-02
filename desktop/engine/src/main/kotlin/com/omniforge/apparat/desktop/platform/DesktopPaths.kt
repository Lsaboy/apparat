package com.omniforge.apparat.desktop.platform

import java.io.File

/** Where the desktop app keeps its cache, settings and default output folder, per OS. */
object DesktopPaths {
    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")
    private val home = File(System.getProperty("user.home"))

    private fun env(name: String): File? = System.getenv(name)?.takeIf { it.isNotBlank() }?.let(::File)

    /** Overridable for tests and portable runs: -Dapparat.home=/some/dir */
    private val override: File? = System.getProperty("apparat.home")?.let(::File)

    val cacheDir: File by lazy {
        (override?.let { File(it, "cache") }
            ?: if (isWindows) File(env("LOCALAPPDATA") ?: File(home, "AppData/Local"), "Apparat/cache")
            else File(env("XDG_CACHE_HOME") ?: File(home, ".cache"), "apparat")).apply { mkdirs() }
    }

    val configDir: File by lazy {
        (override?.let { File(it, "config") }
            ?: if (isWindows) File(env("APPDATA") ?: File(home, "AppData/Roaming"), "Apparat")
            else File(env("XDG_CONFIG_HOME") ?: File(home, ".config"), "apparat")).apply { mkdirs() }
    }

    val dataDir: File by lazy {
        (override?.let { File(it, "data") }
            ?: if (isWindows) File(env("LOCALAPPDATA") ?: File(home, "AppData/Local"), "Apparat/data")
            else File(env("XDG_DATA_HOME") ?: File(home, ".local/share"), "apparat")).apply { mkdirs() }
    }

    /** The user's Downloads folder (XDG user dir on Linux, %USERPROFILE%\Downloads on Windows). */
    val downloadsDir: File by lazy {
        override?.let { return@lazy File(it, "Downloads") }
        if (!isWindows) xdgUserDir("DOWNLOAD")?.let { return@lazy it }
        File(home, "Downloads")
    }

    /** Reads XDG_<name>_DIR from ~/.config/user-dirs.dirs (what `xdg-user-dir` does). */
    private fun xdgUserDir(name: String): File? = runCatching {
        System.getenv("XDG_${name}_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val file = File(env("XDG_CONFIG_HOME") ?: File(home, ".config"), "user-dirs.dirs")
        if (!file.exists()) return null
        val line = file.readLines().firstOrNull { it.trim().startsWith("XDG_${name}_DIR=") } ?: return null
        val raw = line.substringAfter('=').trim().removeSurrounding("\"")
        val path = raw.replace("\$HOME", home.path)
        File(path).takeIf { it.path != home.path }
    }.getOrNull()
}
