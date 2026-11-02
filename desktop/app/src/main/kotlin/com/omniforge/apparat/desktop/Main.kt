package com.omniforge.apparat.desktop

import android.content.Context
import androidx.activity.compose.BackDispatcher
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.core.theme.ApparatTheme
import com.omniforge.apparat.core.util.FileUtils
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import kotlin.concurrent.thread

const val APP_VERSION = "1.5.0"

fun main(args: Array<String>) {
    val appStartTime = System.currentTimeMillis()
    // Wayland/X11 scaling is picked up by Skia; keep text antialiasing crisp on Linux.
    System.setProperty("awt.useSystemAAFontSettings", "lcd")
    if (args.any { it == "--version" }) { println("Apparat $APP_VERSION"); return }
    if (args.any { it == "--self-test" }) kotlin.system.exitProcess(runSelfTest())
    setLinuxWindowClass("apparat")

    val initialFiles = args.filterNot { it.startsWith("-") }.map(::File).filter { it.isFile }
    // Already running? Give it the files (or just bring it forward) and stop here.
    if (System.getProperty("apparat.multiInstance") == null && SingleInstance.handOff(initialFiles)) return

    val context = Context.app
    ToastHost.install()
    ToolJobs.onActivityCreated(context, restoring = false)
    thread(isDaemon = true, name = "cache-cleanup") { FileUtils.cleanStaleCache(context, appStartTime = appStartTime) }
    // Unpack FFmpeg in the background so the first video job starts quickly.
    thread(isDaemon = true, name = "ffmpeg-warmup") { runCatching { com.omniforge.apparat.desktop.platform.Ffmpeg.isAvailable } }

    val appSettings = AppSettings(context)
    val controller = AppController()
    val windowPrefs = context.getSharedPreferences("desktop_window", Context.MODE_PRIVATE)

    application {
        var iconVariant by remember { mutableStateOf(appSettings.appIconVariant) }
        AppSettings.onIconVariantChanged = { iconVariant = it }
        var confirmQuit by remember { mutableStateOf(false) }

        val windowState = rememberWindowState(
            placement = if (windowPrefs.getBoolean("maximized", false)) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = if (windowPrefs.contains("x")) WindowPosition(windowPrefs.getInt("x", 0).dp, windowPrefs.getInt("y", 0).dp) else WindowPosition.PlatformDefault,
            size = DpSize(windowPrefs.getInt("w", 560).dp, windowPrefs.getInt("h", 900).dp)
        )

        fun saveWindow() {
            val e = windowPrefs.edit().putBoolean("maximized", windowState.placement == WindowPlacement.Maximized)
            if (windowState.placement == WindowPlacement.Floating) {
                e.putInt("w", windowState.size.width.value.toInt()).putInt("h", windowState.size.height.value.toInt())
                val pos = windowState.position
                if (pos is WindowPosition.Absolute) e.putInt("x", pos.x.value.toInt()).putInt("y", pos.y.value.toInt())
            }
            e.apply()
        }

        fun quit() {
            saveWindow()
            exitApplication()
        }

        Window(
            onCloseRequest = { if (ToolJobs.running.isNotEmpty()) confirmQuit = true else quit() },
            state = windowState,
            title = "Apparat",
            icon = remember(iconVariant) { AppIcons.painter(iconVariant) },
            onPreviewKeyEvent = { event ->
                val isBack = event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft))
                if (isBack) BackDispatcher.dispatch() || controller.pop() else false
            }
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(400, 560)
                // Mouse "back" side button goes back too.
                java.awt.Toolkit.getDefaultToolkit().addAWTEventListener({ e ->
                    if (e is java.awt.event.MouseEvent && e.id == java.awt.event.MouseEvent.MOUSE_RELEASED && e.button == 4) {
                        java.awt.EventQueue.invokeLater { BackDispatcher.dispatch() || controller.pop() }
                    }
                }, java.awt.AWTEvent.MOUSE_EVENT_MASK)
                DesktopDialogs.owner = window
                DesktopNotifications.onOpenRoute = { route ->
                    window.isVisible = true
                    window.toFront()
                    if (route in TOOL_ROUTES) controller.navigate(route)
                }
                // Drop files (or text) anywhere on the window.
                window.contentPane.dropTarget = object : DropTarget() {
                    @Synchronized
                    override fun drop(evt: DropTargetDropEvent) {
                        runCatching {
                            evt.acceptDrop(DnDConstants.ACTION_COPY)
                            val t = evt.transferable
                            when {
                                t.isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val files = t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                                    controller.handleFiles(files)
                                }
                                t.isDataFlavorSupported(DataFlavor.stringFlavor) -> {
                                    val text = t.getTransferData(DataFlavor.stringFlavor) as String
                                    val asFiles = text.lines().filter { it.startsWith("file:") }
                                        .mapNotNull { runCatching { File(java.net.URI(it.trim())) }.getOrNull() }
                                    if (asFiles.isNotEmpty()) controller.handleFiles(asFiles) else controller.handleText(text)
                                }
                            }
                            evt.dropComplete(true)
                        }.onFailure { evt.dropComplete(false) }
                    }
                }
                if (initialFiles.isNotEmpty()) controller.handleFiles(initialFiles)
                SingleInstance.listen { files ->
                    java.awt.EventQueue.invokeLater {
                        if (window.extendedState and java.awt.Frame.ICONIFIED != 0) window.extendedState = java.awt.Frame.NORMAL
                        window.toFront()
                        window.requestFocus()
                        if (files.isNotEmpty()) controller.handleFiles(files)
                    }
                }
            }

            ApparatDesktopApp(controller, appSettings)

            if (confirmQuit) {
                ApparatTheme(themeStyle = appSettings.themeStyle, accentColor = appSettings.currentAccentColor) {
                    AlertDialog(
                        onDismissRequest = { confirmQuit = false },
                        title = { Text("Still working") },
                        text = { Text("${ToolJobs.running.values.joinToString()} hasn't finished. Quit anyway? The unfinished result will be lost.") },
                        confirmButton = { TextButton(onClick = { quit() }) { Text("Quit") } },
                        dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep working") } }
                    )
                }
            }
        }
    }
}

/**
 * On X11 the window class is what docks use to match a window to its .desktop file (and icon).
 * Java derives it from the main class name; set it to the app's name instead.
 */
private fun setLinuxWindowClass(name: String) {
    if (com.omniforge.apparat.desktop.platform.DesktopPaths.isWindows || com.omniforge.apparat.desktop.platform.DesktopPaths.isMac) return
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, name)
    }
}
