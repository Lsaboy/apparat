package com.omniforge.apparat.desktop

import com.omniforge.apparat.desktop.platform.DesktopPaths
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Keeps one Apparat window: opening files with Apparat while it's running hands them to the
 * running window (over a localhost socket) instead of starting a second copy.
 */
object SingleInstance {
    private val portFile = File(DesktopPaths.cacheDir, "instance.port")
    private const val HELLO = "apparat-v1"

    /** Sends [files] to a running instance. True if one took them (this process should exit). */
    fun handOff(files: List<File>): Boolean = runCatching {
        val port = portFile.readText().trim().toInt()
        Socket().use { s ->
            s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 700)
            s.soTimeout = 2000
            val out = s.getOutputStream().bufferedWriter()
            out.write(HELLO + "\n")
            files.forEach { out.write(it.absolutePath + "\n") }
            out.write("\n")
            out.flush()
            s.getInputStream().bufferedReader().readLine() == "ok"
        }
    }.getOrDefault(false)

    /** Listens for hand-offs; [onFiles] is called on a background thread (empty list = just focus). */
    fun listen(onFiles: (List<File>) -> Unit) {
        val server = runCatching { ServerSocket(0, 10, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { portFile.writeText(server.localPort.toString()) }
        thread(isDaemon = true, name = "single-instance") {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                runCatching {
                    socket.use { s ->
                        s.soTimeout = 2000
                        val reader = s.getInputStream().bufferedReader()
                        if (reader.readLine() != HELLO) return@use
                        val files = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.map(::File).toList()
                        s.getOutputStream().write("ok\n".toByteArray())
                        onFiles(files)
                    }
                }
            }
        }
    }
}
