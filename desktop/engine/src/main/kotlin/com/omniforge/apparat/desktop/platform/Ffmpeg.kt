package com.omniforge.apparat.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** An FFmpeg run that failed; [message] is a readable summary, [log] the end of FFmpeg's output. */
class FfmpegException(message: String, val log: String) : RuntimeException(message)

/**
 * Runs the FFmpeg/FFprobe executables bundled with the app (bytedeco's GPL build: libx264, AAC,
 * and decoders for practically everything). Set APPARAT_FFMPEG / APPARAT_FFPROBE to use other
 * binaries; if the bundled ones can't be unpacked, the ones on PATH are used.
 */
object Ffmpeg {

    private fun bundled(cls: Class<*>): String? = runCatching {
        System.setProperty("org.bytedeco.javacpp.cachedir", File(DesktopPaths.cacheDir, "native").path)
        org.bytedeco.javacpp.Loader.load(cls)
    }.getOrNull()?.takeIf { File(it).canExecute() }

    val ffmpeg: String by lazy {
        System.getenv("APPARAT_FFMPEG")?.takeIf { File(it).canExecute() }
            ?: bundled(org.bytedeco.ffmpeg.ffmpeg::class.java)
            ?: "ffmpeg"
    }

    val ffprobe: String by lazy {
        System.getenv("APPARAT_FFPROBE")?.takeIf { File(it).canExecute() }
            ?: bundled(org.bytedeco.ffmpeg.ffprobe::class.java)
            ?: "ffprobe"
    }

    /** True when FFmpeg starts at all (used for a clear message if it's missing). */
    val isAvailable: Boolean by lazy {
        runCatching {
            val p = ProcessBuilder(ffmpeg, "-hide_banner", "-version").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor() == 0
        }.getOrDefault(false)
    }

    /** Encoders compiled into the FFmpeg build, e.g. "libx264", "libx265", "libsvtav1". */
    val encoders: Set<String> by lazy {
        runCatching {
            val p = ProcessBuilder(ffmpeg, "-hide_banner", "-encoders").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out.lines().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2 && parts[0].length == 6 && parts[0][0] in "VAS") parts[1] else null
            }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Runs ffmpeg with [args] (no "-y"/progress flags needed: they are added). [durationSec] lets
     * [onProgress] report 0..1 from FFmpeg's progress output. Cancelling the coroutine kills FFmpeg.
     */
    suspend fun run(
        args: List<String>,
        durationSec: Double? = null,
        onProgress: (Float) -> Unit = {}
    ): Unit = withContext(Dispatchers.IO) {
        val command = listOf(ffmpeg, "-hide_banner", "-nostdin", "-y", "-progress", "pipe:1", "-nostats") + args
        val process = ProcessBuilder(command).start()
        val log = ArrayDeque<String>()
        val errReader = Thread {
            runCatching {
                process.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(log) {
                        log.addLast(line)
                        if (log.size > 60) log.removeFirst()
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        val job = coroutineContext
        try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { process.descendants().forEach { it.destroyForcibly() }; process.destroyForcibly() }
                Thread {
                    runCatching {
                        process.inputStream.bufferedReader().forEachLine { line ->
                            if (durationSec != null && durationSec > 0 && line.startsWith("out_time_us=")) {
                                val us = line.substringAfter('=').trim().toLongOrNull()
                                if (us != null && us >= 0) onProgress((us / 1_000_000.0 / durationSec).toFloat().coerceIn(0f, 1f))
                            }
                        }
                    }
                    process.waitFor()
                    errReader.join(2000)
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }.apply { isDaemon = true; start() }
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        job.ensureActive()
        val code = process.waitFor()
        if (code != 0) {
            val tail = synchronized(log) { log.joinToString("\n") }
            throw FfmpegException(summarize(tail), tail)
        }
        onProgress(1f)
    }

    /** Runs a short command and returns stdout (stderr discarded). Throws on a non-zero exit. */
    fun capture(command: List<String>): String {
        val p = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val out = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) throw FfmpegException("Could not read this media file.", out)
        return out
    }

    /** Turns FFmpeg's last lines into something a person can act on. */
    internal fun summarize(log: String): String {
        val l = log.lowercase()
        return when {
            "invalid data found" in l || "moov atom not found" in l -> "This file isn't a video FFmpeg can read (it may be damaged)."
            "does not contain any stream" in l || "matches no streams" in l -> "This file has no stream of the kind needed (e.g. no audio track)."
            "no such file" in l -> "Could not open the selected file. Pick it again."
            "permission denied" in l -> "Permission denied reading or writing the file."
            "no space left" in l -> "Not enough free disk space."
            "unknown encoder" in l || "encoder not found" in l -> "This FFmpeg build has no encoder for the chosen format."
            else -> "FFmpeg failed: " + (log.lines().lastOrNull { it.isNotBlank() }?.trim() ?: "unknown error")
        }
    }
}

/** Basic facts about a media file from ffprobe. */
data class MediaProbe(
    val durationSec: Double,
    val bitrate: Long?,
    val formatName: String?,
    val videoCodec: String?,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val fps: Double?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val audioSampleRate: Int?
) {
    val hasVideo: Boolean get() = videoCodec != null && width > 0
    val hasAudio: Boolean get() = audioCodec != null
    /** Width/height as displayed (rotation applied). */
    val displayWidth: Int get() = if (rotation % 180 != 0) height else width
    val displayHeight: Int get() = if (rotation % 180 != 0) width else height

    companion object {
        private val cache = object : LinkedHashMap<String, Pair<Long, MediaProbe?>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, MediaProbe?>>?) = size > 32
        }

        fun of(file: File): MediaProbe? {
            val key = file.absolutePath
            val stamp = file.lastModified() xor file.length()
            synchronized(cache) { cache[key]?.let { if (it.first == stamp) return it.second } }
            val probe = runCatching { parse(probeRaw(file)) }.getOrNull()
            synchronized(cache) { cache[key] = stamp to probe }
            return probe
        }

        private fun probeRaw(file: File): Map<String, String> {
            val out = Ffmpeg.capture(
                listOf(
                    Ffmpeg.ffprobe, "-v", "error", "-of", "flat=s=_",
                    "-show_entries",
                    "format=duration,bit_rate,format_name:stream=index,codec_type,codec_name,width,height,avg_frame_rate,channels,sample_rate,duration:stream_tags=rotate:stream_side_data=rotation",
                    file.absolutePath
                )
            )
            return out.lines().mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i) to line.substring(i + 1).trim().removeSurrounding("\"")
            }.toMap()
        }

        internal fun parse(m: Map<String, String>): MediaProbe {
            val streams = m.keys.mapNotNull { Regex("""^streams_stream_(\d+)_""").find(it)?.groupValues?.get(1)?.toInt() }.toSortedSet()
            fun s(i: Int, k: String) = m["streams_stream_${i}_$k"]
            val v = streams.firstOrNull { s(it, "codec_type") == "video" && s(it, "codec_name") !in setOf("mjpeg", "png", "bmp") }
                ?: streams.firstOrNull { s(it, "codec_type") == "video" }
            val a = streams.firstOrNull { s(it, "codec_type") == "audio" }
            val rotation = v?.let { idx ->
                val sideKey = m.keys.firstOrNull { it.startsWith("streams_stream_${idx}_side_data_list") && it.endsWith("_rotation") }
                (sideKey?.let { m[it] } ?: s(idx, "tags_rotate"))?.toDoubleOrNull()?.toInt()
            } ?: 0
            val fps = v?.let { s(it, "avg_frame_rate") }?.let { r ->
                val (n, d) = r.split('/').let { (it.getOrNull(0)?.toDoubleOrNull() ?: 0.0) to (it.getOrNull(1)?.toDoubleOrNull() ?: 1.0) }
                if (d > 0 && n > 0) n / d else null
            }
            val duration = m["format_duration"]?.toDoubleOrNull()
                ?: v?.let { s(it, "duration") }?.toDoubleOrNull()
                ?: a?.let { s(it, "duration") }?.toDoubleOrNull() ?: 0.0
            return MediaProbe(
                durationSec = duration,
                bitrate = m["format_bit_rate"]?.toLongOrNull(),
                formatName = m["format_format_name"],
                videoCodec = v?.let { s(it, "codec_name") },
                width = v?.let { s(it, "width") }?.toIntOrNull() ?: 0,
                height = v?.let { s(it, "height") }?.toIntOrNull() ?: 0,
                rotation = ((rotation % 360) + 360) % 360,
                fps = fps,
                audioCodec = a?.let { s(it, "codec_name") },
                audioChannels = a?.let { s(it, "channels") }?.toIntOrNull(),
                audioSampleRate = a?.let { s(it, "sample_rate") }?.toIntOrNull()
            )
        }
    }
}
