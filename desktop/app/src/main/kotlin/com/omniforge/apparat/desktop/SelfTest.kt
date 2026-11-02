package com.omniforge.apparat.desktop

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.CodecSupport
import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.MediaProbe
import com.omniforge.apparat.engine.common.UtilityEngine
import com.omniforge.apparat.engine.image.ImageEngine
import com.omniforge.apparat.engine.image.ImageOutputFormat
import com.omniforge.apparat.engine.pdf.PdfEngine
import com.omniforge.apparat.engine.video.AudioOutputFormat
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEngine
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * `Apparat --self-test`: checks, without a window, that the installed app's engines work on this
 * computer (bundled FFmpeg, PDF, images). Prints one line per check; exit code 0 when all pass.
 * Useful after installing on a new system: `/opt/apparat/bin/Apparat --self-test`,
 * `flatpak run com.omniforge.Apparat --self-test`, or `Apparat.exe --self-test` on Windows.
 */
fun runSelfTest(): Int {
    val ctx = Context.app
    val dir = File(DesktopPaths.cacheDir, "self-test").apply { deleteRecursively(); mkdirs() }
    var failed = 0
    fun check(name: String, block: () -> String) {
        val result = runCatching(block)
        result.onSuccess { println("PASS  $name: $it") }
        result.onFailure { failed++; println("FAIL  $name: ${it.javaClass.simpleName}: ${it.message}") }
    }
    println("Apparat $APP_VERSION self-test (${System.getProperty("os.name")} ${System.getProperty("os.arch")}, Java ${System.getProperty("java.version")})")
    println("cache: ${DesktopPaths.cacheDir}  downloads: ${DesktopPaths.downloadsDir}")

    check("ffmpeg") {
        require(Ffmpeg.isAvailable) { "not runnable (${Ffmpeg.bundleProblem})" }
        "${Ffmpeg.ffmpeg} ${if (File(Ffmpeg.ffmpeg).isAbsolute) "(bundled)" else "(system)"}"
    }
    check("encoders") {
        val codecs = VideoCodecChoice.availableOnDevice().joinToString { it.shortName }
        require("libx264" in Ffmpeg.encoders) { "libx264 missing" }
        "$codecs; HEVC=${CodecSupport.canEncodeHevc} AV1=${CodecSupport.canEncodeAv1}"
    }
    val video = File(dir, "test.mp4")
    check("make test video") {
        runBlocking {
            Ffmpeg.run(listOf("-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=2", "-f", "lavfi", "-i", "sine=duration=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", video.absolutePath))
        }
        "${video.length()} bytes"
    }
    check("video shrink") {
        val out = runBlocking { VideoEngine.shrinkVideo(ctx, Uri.fromFile(video), VideoShrinkPreset.MAX_SHRINK).getOrThrow() }
        val p = MediaProbe.of(out)!!
        "${p.width}x${p.height} ${p.videoCodec}/${p.audioCodec}"
    }
    check("audio extract") {
        val out = runBlocking { VideoEngine.extractAudio(ctx, Uri.fromFile(video), AudioOutputFormat.M4A).getOrThrow() }
        "${out.length()} bytes ${MediaProbe.of(out)?.audioCodec}"
    }
    check("gif") {
        val out = runBlocking { VideoEngine.videoToGif(ctx, Uri.fromFile(video), durationSec = 1f).getOrThrow() }
        "${out.length()} bytes"
    }
    val png = File(dir, "test.png")
    check("image") {
        val qr = UtilityEngine.generateQrCode("apparat self-test", 400)
        png.outputStream().use { qr.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val webp = runBlocking { ImageEngine.convertFormat(ctx, Uri.fromFile(png), ImageOutputFormat.WEBP).getOrThrow() }
        "png ${png.length()} B -> webp ${webp.length()} B"
    }
    check("pdf") {
        val pdf = runBlocking { PdfEngine.imagesToPdf(ctx, listOf(Uri.fromFile(png), Uri.fromFile(png))).getOrThrow() }
        val parts = runBlocking { PdfEngine.multiSplitPdf(ctx, Uri.fromFile(pdf), listOf(1..1, 2..2)).getOrThrow() }
        "${pdf.length()} B, split into ${parts.size}"
    }
    dir.deleteRecursively()
    println(if (failed == 0) "ALL CHECKS PASSED" else "$failed CHECK(S) FAILED")
    return if (failed == 0) 0 else 1
}
