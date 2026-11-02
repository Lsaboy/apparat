package com.omniforge.apparat.desktop

import com.omniforge.apparat.desktop.platform.DesktopPaths
import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * "Job finished" notifications. Linux: notify-send, else the freedesktop notification service
 * through gdbus (works inside Flatpak). Windows: a tray balloon (shown as a toast on Windows 10/11).
 */
object DesktopNotifications {

    /** Called when the user should be brought back to a tool (window raised, tool opened). */
    @Volatile
    var onOpenRoute: (String) -> Unit = {}

    private var trayIcon: TrayIcon? = null

    val isSupported: Boolean
        get() = if (DesktopPaths.isWindows) SystemTray.isSupported() else true

    private fun exec(vararg cmd: String): Boolean = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    fun show(title: String, body: String, route: String?) {
        Thread {
            if (DesktopPaths.isWindows || DesktopPaths.isMac) showTray(title, body, route)
            else {
                exec("notify-send", "--app-name=Apparat", "--icon=apparat", title, body) ||
                    exec(
                        "gdbus", "call", "--session", "--dest", "org.freedesktop.Notifications",
                        "--object-path", "/org/freedesktop/Notifications", "--method", "org.freedesktop.Notifications.Notify",
                        "Apparat", "0", "apparat", title, body, "[]", "{}", "6000"
                    ) || showTray(title, body, route)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun showTray(title: String, body: String, route: String?): Boolean = runCatching {
        if (!SystemTray.isSupported()) return false
        val icon = trayIcon ?: run {
            val image = AppIcons.trayImage()
            TrayIcon(image, "Apparat").also {
                it.isImageAutoSize = true
                it.addActionListener { _ -> route?.let(onOpenRoute) }
                SystemTray.getSystemTray().add(it)
                trayIcon = it
            }
        }
        icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
        true
    }.getOrDefault(false)
}
