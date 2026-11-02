package com.omniforge.apparat.desktop

import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.desktop.platform.MimeTypes
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.TimeUnit
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Native open/save/folder dialogs. Files use the system dialog through AWT (GTK on Linux,
 * the Windows common dialog on Windows). Folders use zenity/kdialog on Linux when installed,
 * otherwise Swing's chooser with the system look.
 */
object DesktopDialogs {

    /** The main window, set at start-up; dialogs are modal to it. */
    @Volatile
    var owner: Frame? = null

    private var lastDir: File? = null

    init {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }

    private fun startDir(): File = lastDir?.takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))

    private fun extensionsFor(mimes: List<String>): Set<String>? {
        val sets = mimes.map { MimeTypes.extensionsFor(it) }
        if (sets.any { it == null } || sets.isEmpty()) return null
        return sets.flatMap { it!! }.toSet()
    }

    private fun dialog(title: String, mode: Int, mimes: List<String>): FileDialog {
        val d = FileDialog(owner, title, mode)
        d.directory = startDir().absolutePath
        val exts = extensionsFor(mimes)
        if (exts != null) {
            d.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in exts }
            // Windows ignores FilenameFilter; a wildcard list in the name field filters there.
            if (DesktopPaths.isWindows && mode == FileDialog.LOAD) d.file = exts.joinToString(";") { "*.$it" }
        }
        return d
    }

    private fun titleFor(mimes: List<String>): String {
        val m = mimes.firstOrNull().orEmpty()
        return when {
            m == "application/pdf" -> "Choose a PDF"
            m.startsWith("image/") -> "Choose an image"
            m.startsWith("video/") -> "Choose a video"
            m.startsWith("audio/") -> "Choose an audio file"
            else -> "Choose a file"
        }
    }

    fun openFile(mimes: List<String>): File? {
        val d = dialog(titleFor(mimes), FileDialog.LOAD, mimes)
        d.isMultipleMode = false
        d.isVisible = true
        val file = d.files.firstOrNull() ?: return null
        lastDir = file.parentFile
        return file.takeIf { it.isFile }
    }

    fun openFiles(mimes: List<String>): List<File> {
        val d = dialog(titleFor(mimes).replace("a ", "").replace("an ", "") + "s", FileDialog.LOAD, mimes)
        d.isMultipleMode = true
        d.isVisible = true
        val files = d.files.filter { it.isFile }
        files.firstOrNull()?.let { lastDir = it.parentFile }
        return files
    }

    /** Save dialog with [suggestedName]; the extension implied by [mimeType] is kept. */
    fun saveFile(suggestedName: String, mimeType: String): File? {
        val d = FileDialog(owner, "Save as", FileDialog.SAVE)
        d.directory = (lastDir ?: DesktopPaths.downloadsDir.takeIf { it.isDirectory } ?: startDir()).absolutePath
        d.file = suggestedName
        d.isVisible = true
        val dir = d.directory ?: return null
        var name = d.file ?: return null
        val wantedExt = suggestedName.substringAfterLast('.', "")
        if (wantedExt.isNotEmpty() && !name.lowercase().endsWith(".${wantedExt.lowercase()}")) name += ".$wantedExt"
        val file = File(dir, name)
        lastDir = file.parentFile
        return file
    }

    fun chooseFolder(start: File?): File? {
        val from = start?.takeIf { it.isDirectory } ?: startDir()
        if (!DesktopPaths.isWindows && !DesktopPaths.isMac) {
            linuxFolderPicker(from)?.let { return it.takeIf { f -> f.isDirectory }?.also { lastDir = it } }
        }
        val chooser = JFileChooser(from).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a folder"
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.also { lastDir = it } else null
    }

    /** zenity (GNOME) or kdialog (KDE) if installed. Returns null when cancelled or unavailable. */
    private fun linuxFolderPicker(start: File): File? {
        val commands = listOf(
            listOf("zenity", "--file-selection", "--directory", "--title=Choose a folder", "--filename=${start.absolutePath}/"),
            listOf("kdialog", "--getexistingdirectory", start.absolutePath, "--title", "Choose a folder")
        )
        for (cmd in commands) {
            val process = runCatching { ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start() }.getOrNull() ?: continue
            // Keep the window painting while the external dialog is open.
            val loop = java.awt.Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop()
            var output = ""
            Thread {
                output = runCatching { process.inputStream.bufferedReader().readText().trim() }.getOrDefault("")
                process.waitFor(10, TimeUnit.MINUTES)
                loop.exit()
            }.start()
            loop.enter()
            return if (process.exitValue() == 0 && output.isNotEmpty()) File(output) else null
        }
        return null
    }
}
