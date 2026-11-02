package com.omniforge.apparat.desktop.platform

import java.awt.Desktop
import java.io.File

/** Opening files and folders with the system's default apps. */
object DesktopShell {

    private fun exec(vararg cmd: String): Boolean = runCatching {
        ProcessBuilder(*cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        true
    }.getOrDefault(false)

    /** Opens [file] in its default app. */
    fun open(file: File): Boolean {
        if (!file.exists()) return false
        if (!DesktopPaths.isWindows && !DesktopPaths.isMac && exec("xdg-open", file.absolutePath)) return true
        return runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file); true
            } else false
        }.getOrDefault(false) || (DesktopPaths.isWindows && exec("explorer", file.absolutePath))
    }

    /** Shows [file] selected in the file manager (or opens its folder where selecting isn't possible). */
    fun reveal(file: File): Boolean {
        if (DesktopPaths.isWindows) {
            val ok = runCatching {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    Desktop.getDesktop().browseFileDirectory(file); true
                } else false
            }.getOrDefault(false)
            return ok || exec("explorer.exe", "/select,${file.absolutePath}") || open(file.parentFile ?: return false)
        }
        if (DesktopPaths.isMac) return exec("open", "-R", file.absolutePath)
        // FileManager1 D-Bus interface (Nautilus, Dolphin, Nemo, Thunar...) selects the file.
        val uri = file.toURI().toString().replace("file:/", "file:///").replace("file:////", "file:///")
        if (exec(
                "dbus-send", "--session", "--dest=org.freedesktop.FileManager1", "--type=method_call",
                "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems",
                "array:string:$uri", "string:"
            )
        ) return true
        return open(file.parentFile ?: return false)
    }
}
