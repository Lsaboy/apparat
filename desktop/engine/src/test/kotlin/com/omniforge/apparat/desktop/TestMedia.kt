package com.omniforge.apparat.desktop

import android.net.Uri
import com.omniforge.apparat.desktop.platform.Ffmpeg
import kotlinx.coroutines.runBlocking
import java.io.File

/** Generates small media files with the bundled FFmpeg, once per test run. */
object TestMedia {
    val dir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "apparat-test-media").apply { deleteRecursively(); mkdirs() }
    }

    private fun make(name: String, vararg args: String): File {
        val f = File(dir, name)
        if (!f.exists()) runBlocking { Ffmpeg.run(args.toList() + f.absolutePath) }
        return f
    }

    /** 4 s, 640x360, 25 fps H.264 + stereo AAC sine. */
    val mp4: File by lazy {
        make(
            "clip.mp4", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=4",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=4:sample_rate=44100",
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-ac", "2", "-shortest"
        )
    }

    /** 3 s 1280x720 VP9 + Opus WEBM (needs a real conversion to MP4). */
    val webm: File by lazy {
        make(
            "clip.webm", "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=30:duration=3",
            "-f", "lavfi", "-i", "sine=frequency=300:duration=3",
            "-c:v", "libvpx-vp9", "-b:v", "1M", "-deadline", "realtime", "-cpu-used", "8", "-c:a", "libopus", "-shortest"
        )
    }

    /** Portrait phone-style clip: 360x640 frames with rotate metadata of 90°. */
    val rotatedMp4: File by lazy {
        val plain = make("plain.mp4", "-f", "lavfi", "-i", "testsrc2=size=640x360:rate=25:duration=2", "-c:v", "libx264", "-pix_fmt", "yuv420p")
        val f = File(dir, "rotated.mp4")
        if (!f.exists()) runBlocking { Ffmpeg.run(listOf("-display_rotation:v:0", "90", "-i", plain.absolutePath, "-c", "copy", f.absolutePath)) }
        f
    }

    val jpeg: File by lazy { make("photo.jpg", "-f", "lavfi", "-i", "testsrc2=size=1600x1200", "-frames:v", "1", "-q:v", "2") }
    val png: File by lazy { make("photo.png", "-f", "lavfi", "-i", "testsrc2=size=800x600", "-frames:v", "1") }

    fun uri(f: File): Uri = Uri.fromFile(f)
}
