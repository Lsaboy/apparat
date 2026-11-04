package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.AudioConvertFormat
import com.omniforge.apparat.desktop.engine.BatchItemState
import com.omniforge.apparat.desktop.engine.BatchOperation
import com.omniforge.apparat.desktop.engine.BatchOptions
import com.omniforge.apparat.desktop.engine.BatchRunner
import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.engine.image.ImageOutputFormat
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import kotlinx.coroutines.runBlocking
import java.io.File

/** Command names for --batch, e.g. `apparat --batch compress-pdf ...`. */
private val COMMANDS = mapOf(
    "compress-pdf" to BatchOperation.COMPRESS_PDF,
    "shrink-photo" to BatchOperation.SHRINK_PHOTO,
    "photo-under-kb" to BatchOperation.PHOTO_UNDER_KB,
    "convert-image" to BatchOperation.CONVERT_IMAGE,
    "strip-exif" to BatchOperation.STRIP_EXIF,
    "shrink-video" to BatchOperation.SHRINK_VIDEO,
    "convert-video" to BatchOperation.CONVERT_VIDEO,
    "convert-audio" to BatchOperation.CONVERT_AUDIO
)

val CLI_HELP = """
    Apparat $APP_VERSION

    apparat [FILE|FOLDER...]           open the app (files go to the matching tool, folders to Batch)
    apparat --self-test                check that every engine works on this computer
    apparat --version
    apparat --batch COMMAND [OPTIONS] FILE|FOLDER...
        COMMAND: ${COMMANDS.keys.joinToString(", ")}
        --out DIR        where results go (default: Downloads/Apparat/Batch)
        --kb N           photo-under-kb limit (default 200)
        --format F       convert-image: jpeg|png|webp; convert-audio: mp3|m4a|opus|flac|wav
        --preset P       shrink-video: 360|480|720|1080 (default 720)
        --bitrate N      convert-audio kbps (default 192)
    Examples:
        apparat --batch compress-pdf ~/Scans
        apparat --batch photo-under-kb --kb 50 --out ~/upload passport.jpg sign.png
    On Windows, run commands with apparat-cli.exe (next to Apparat.exe) to see the output.

    Shortcuts in the app: Ctrl+O open, Ctrl+S save, Ctrl+Shift+S save as, Ctrl+F search tools,
    Esc or Alt+Left back.
""".trimIndent()

/** `--batch ...`: runs a batch without a window and prints one line per file. Exit code 1 if any failed. */
fun runCommandLineBatch(args: List<String>): Int {
    val rest = args.dropWhile { it != "--batch" }.drop(1).toMutableList()
    val op = COMMANDS[rest.removeFirstOrNull()] ?: run { System.err.println(CLI_HELP); return 2 }
    var out = File(DesktopPaths.downloadsDir, "Apparat/Batch")
    var options = BatchOptions()
    val inputs = mutableListOf<File>()
    val it = rest.iterator()
    while (it.hasNext()) {
        when (val a = it.next()) {
            "--out" -> out = File(it.next())
            "--kb" -> options = options.copy(photoLimitKb = it.next().toInt())
            "--bitrate" -> options = options.copy(audioBitrateKbps = it.next().toInt())
            "--preset" -> {
                val p = it.next().removeSuffix("p").toInt()
                options = options.copy(videoPreset = VideoShrinkPreset.entries.first { e -> e.shortSide == p })
            }
            "--format" -> {
                val f = it.next().lowercase()
                options = options.copy(
                    imageFormat = ImageOutputFormat.entries.firstOrNull { e -> e.name.lowercase() == f || (f == "jpg" && e == ImageOutputFormat.JPEG) } ?: options.imageFormat,
                    audioFormat = AudioConvertFormat.entries.firstOrNull { e -> e.name.lowercase() == f } ?: options.audioFormat
                )
            }
            else -> inputs += File(a)
        }
    }
    val files = inputs.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList() else listOf(f) }
    if (files.isEmpty()) { System.err.println("No input files.\n\n$CLI_HELP"); return 2 }
    println("${op.label}: ${files.size} file(s) → $out")
    val summary = runBlocking {
        BatchRunner.run(Context.app, op, options, files, out) { i, s ->
            val name = files[i].name
            when (s) {
                is BatchItemState.Done -> println("  ok      $name → ${s.output.name}")
                is BatchItemState.Failed -> println("  failed  $name: ${s.message}")
                BatchItemState.Skipped -> println("  skipped $name")
                else -> {}
            }
        }
    }
    println("Done: ${summary.done}, failed: ${summary.failed}, skipped: ${summary.skipped}")
    return if (summary.failed > 0) 1 else 0
}
