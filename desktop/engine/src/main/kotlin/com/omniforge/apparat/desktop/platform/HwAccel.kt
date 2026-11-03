package com.omniforge.apparat.desktop.platform

import com.omniforge.apparat.engine.video.VideoCodecChoice
import java.io.File
import java.util.concurrent.TimeUnit

/** A hardware video encoder family in the bundled FFmpeg. [suffix] completes "h264_", "hevc_", "av1_". */
enum class HwApi(val label: String, val suffix: String) {
    VAAPI("VA-API", "vaapi"),
    NVENC("NVIDIA NVENC", "nvenc"),
    QSV("Intel Quick Sync", "qsv"),
    D3D12VA("Direct3D 12", "d3d12va"),
    MF("Media Foundation", "mf")
}

/** The FFmpeg hardware device used for decoding (and, for VA-API/D3D12, for uploading frames). */
enum class HwDeviceType(val ffName: String, val alias: String) {
    VAAPI("vaapi", "va"),
    CUDA("cuda", "cu"),
    D3D11VA("d3d11va", "dx"),
    D3D12VA("d3d12va", "d12")
}

/**
 * One working hardware path found by [HwAccel]'s probe: which encoders work ([encoders], with any
 * extra args they needed, e.g. "-low_power 1"), on which device, with which library environment.
 * [fullGpu]: decode, scale and encode can all stay on the GPU (VA-API, CUDA).
 */
data class HwBackend(
    val api: HwApi,
    val deviceType: HwDeviceType,
    val device: String?,
    val env: FfmpegEnv,
    val encoders: Map<VideoCodecChoice, List<String>>,
    val fullGpu: Boolean,
    val description: String
) {
    fun supports(codec: VideoCodecChoice) = codec in encoders

    fun encoderName(codec: VideoCodecChoice): String = when (codec) {
        VideoCodecChoice.H264 -> "h264"
        VideoCodecChoice.HEVC -> "hevc"
        VideoCodecChoice.AV1 -> "av1"
    } + "_" + api.suffix

    /** Global options: they create the device; they go before every input. */
    fun globalArgs(): List<String> = when (deviceType) {
        HwDeviceType.VAAPI -> listOf("-init_hw_device", "vaapi=${deviceType.alias}" + (device?.let { ":$it" } ?: ""), "-filter_hw_device", deviceType.alias)
        HwDeviceType.D3D12VA -> listOf("-init_hw_device", "d3d12va=${deviceType.alias}", "-filter_hw_device", deviceType.alias)
        HwDeviceType.CUDA, HwDeviceType.D3D11VA -> emptyList()
    }

    /**
     * Input options for hardware decoding (before the first -i only). Without [full], decoded frames
     * come back to memory, so the normal filters work and FFmpeg falls back to software decoding by
     * itself if the GPU can't decode the file. With [full] frames stay on the GPU (no autorotate:
     * the rotation is put back by a remux afterwards).
     */
    fun decodeArgs(full: Boolean): List<String> {
        val args = mutableListOf("-hwaccel", deviceType.ffName)
        if (deviceType == HwDeviceType.VAAPI || deviceType == HwDeviceType.D3D12VA) args += listOf("-hwaccel_device", deviceType.alias)
        if (full) {
            args += listOf("-hwaccel_output_format", deviceType.ffName, "-noautorotate")
        }
        return args
    }

    /** Filters that move software frames to the encoder's device, or null if the encoder takes them as is. */
    val uploadSuffix: String?
        get() = if (api == HwApi.VAAPI || api == HwApi.D3D12VA) "format=nv12,hwupload" else null

    /** A GPU-side scale to [w]x[h] (also converts 10-bit to 8-bit), for the full-GPU path. */
    fun fullScale(w: Int, h: Int): String = when (deviceType) {
        HwDeviceType.CUDA -> "scale_cuda=w=$w:h=$h:format=nv12"
        else -> "scale_vaapi=w=$w:h=$h:format=nv12"
    }

    /** Encoder options for [codec] at [bitrate] bits/s. */
    fun encoderArgs(codec: VideoCodecChoice, bitrate: Int, full: Boolean): List<String> {
        val rate = listOf("-b:v", bitrate.toString(), "-maxrate", (bitrate.toLong() * 3 / 2).toString(), "-bufsize", (bitrate.toLong() * 2).toString())
        val args = mutableListOf("-c:v", encoderName(codec))
        args += when (api) {
            HwApi.VAAPI -> listOf("-rc_mode", "VBR") + rate
            HwApi.NVENC -> listOf("-preset", "p5", "-tune", "hq", "-rc", "vbr") + rate + (if (full) emptyList() else listOf("-pix_fmt", "yuv420p"))
            HwApi.QSV -> listOf("-preset", "medium") + rate + listOf("-pix_fmt", "nv12")
            HwApi.MF -> listOf("-hw_encoding", "1", "-b:v", bitrate.toString(), "-pix_fmt", "nv12")
            HwApi.D3D12VA -> rate
        }
        if (codec == VideoCodecChoice.HEVC) args += listOf("-tag:v", "hvc1")
        args += encoders[codec].orEmpty()
        return args
    }
}

/**
 * Finds a working hardware video encoder once per run (in the background, at start-up) and keeps
 * track of what failed this session. Everything falls back to software (x264/x265/SVT-AV1).
 *
 * Linux: VA-API on each /dev/dri/renderD* node (AMD, Intel; also NVIDIA's decode-only VA driver,
 * which fails the encode probe), then NVENC. The bundled libva is older than many distros' Mesa
 * drivers expect, so the system's libva is tried first, then the bundled one with the usual driver
 * folders. Windows: NVENC, Quick Sync, Direct3D 12, then Media Foundation (the bundled FFmpeg has no
 * AMF: AMD cards encode through D3D12 or Media Foundation).
 *
 * APPARAT_HWACCEL=off turns it off; =vaapi|nvenc|qsv|d3d12va|mf allows only that API.
 * APPARAT_VAAPI_DEVICE=/dev/dri/renderD129 picks the VA-API node.
 */
object HwAccel {

    /** The "Hardware video" setting. */
    @Volatile
    var enabled = true

    /** Tests: a backend to use instead of the detected one. */
    @Volatile
    internal var overrideForTests: HwBackend? = null

    private val envChoice: String = System.getenv("APPARAT_HWACCEL")?.trim()?.lowercase().orEmpty()

    val disabledByEnv: Boolean get() = envChoice in setOf("off", "0", "false", "none", "no", "software")

    private val forcedApi: HwApi? get() = HwApi.entries.firstOrNull { it.suffix == envChoice }

    class Detection(val backend: HwBackend?, val notes: List<String>, val reason: String?)

    private val detectionLazy = lazy { detect() }
    private val detection: Detection by detectionLazy

    /** The detection result if it has finished, without waiting for it. */
    fun detectedOrNull(): Detection? = if (detectionLazy.isInitialized()) detection else null

    /** Starts detection in the background (it takes a second or two). */
    fun warmUp() {
        if (disabledByEnv || detectionLazy.isInitialized()) return
        Thread({ runCatching { detection } }, "apparat-hw-probe").apply { isDaemon = true; start() }
    }

    /** The backend to use now, or null for software. Blocks on the first call until detection is done. */
    fun backend(): HwBackend? {
        if (!enabled || disabledByEnv) return null
        overrideForTests?.let { return it }
        return detection.backend
    }

    /** A multi-line report for the self-test and logs. */
    fun report(): String {
        val d = detection
        return buildString {
            append(summary()).append('\n')
            d.backend?.let { b ->
                append("  API: ${b.api.label}, device: ${b.device ?: "default"}, full GPU path: ${b.fullGpu}\n")
                b.encoders.forEach { (codec, extra) -> append("  ${b.encoderName(codec)}${if (extra.isEmpty()) "" else " " + extra.joinToString(" ")}\n") }
                if (b.env.libDirs.isNotEmpty() || b.env.vars.isNotEmpty()) append("  env: libs=${b.env.libDirs.joinToString { it.path }} vars=${b.env.vars}\n")
            }
            d.notes.forEach { append("  - ").append(it).append('\n') }
        }.trimEnd()
    }

    /** One line for the settings screen. */
    fun summary(): String {
        if (disabledByEnv) return "Off (APPARAT_HWACCEL=$envChoice)"
        if (!enabled) return "Off: videos are encoded on the CPU"
        val d = detectedOrNull() ?: return "Checking the graphics card..."
        val b = d.backend ?: return "No hardware encoder found, using the CPU" + (d.reason?.let { ": $it" } ?: "")
        return "Using ${b.description} (${b.api.label}): " + b.encoders.keys.joinToString { it.shortName }
    }

    // ---- Session bookkeeping: what failed on this computer since the app started ----

    private val pathFailed = mutableSetOf<Pair<HwApi, String>>()
    private val failures = mutableMapOf<Pair<HwApi, VideoCodecChoice>, Int>()

    /** False once an encoder has failed twice in a row this session (then software is used directly). */
    fun usable(backend: HwBackend, codec: VideoCodecChoice): Boolean =
        backend.supports(codec) && synchronized(failures) { (failures[backend.api to codec] ?: 0) < 2 }

    fun recordFailure(backend: HwBackend, codec: VideoCodecChoice) {
        synchronized(failures) { failures.merge(backend.api to codec, 1) { a, b -> a + b } }
    }

    fun recordSuccess(backend: HwBackend, codec: VideoCodecChoice) {
        synchronized(failures) { failures.remove(backend.api to codec) }
    }

    /** Remembers that one way of using [backend] failed, e.g. "full:hevc" (full-GPU path for HEVC sources). */
    fun markPathFailed(backend: HwBackend, key: String) {
        synchronized(pathFailed) { pathFailed += backend.api to key }
    }

    fun hasPathFailed(backend: HwBackend, key: String): Boolean =
        synchronized(pathFailed) { (backend.api to key) in pathFailed }

    internal fun resetSessionForTests() {
        synchronized(failures) { failures.clear() }
        synchronized(pathFailed) { pathFailed.clear() }
    }

    // ---- Detection ----

    private val probeCodecs = listOf(VideoCodecChoice.H264, VideoCodecChoice.HEVC, VideoCodecChoice.AV1)

    private val deviceFailure = listOf(
        "Device creation failed", "Failed to set value", "for option 'init_hw_device'", "No VA display found",
        "as DRM device node", "Cannot load libcuda", "Could not dynamically load CUDA",
        "Failed to initialise VAAPI connection", "Cannot load nvcuda", "Failed to create Direct3D device",
        "Failed to create D3D12"
    )

    private class ProbeResult(val ok: Boolean, val output: String)

    private val workDir: File get() = File(DesktopPaths.cacheDir, "hw").apply { mkdirs() }
    private val sample: File get() = File(workDir, "sample.mp4")

    private fun probe(args: List<String>, env: FfmpegEnv): ProbeResult {
        val command = listOf(Ffmpeg.ffmpeg, "-hide_banner", "-nostdin", "-y", "-v", "verbose") + args
        val process = try {
            Ffmpeg.process(command, env).redirectErrorStream(true).start()
        } catch (t: Throwable) {
            return ProbeResult(false, t.toString())
        }
        val out = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (out.length < 200_000) synchronized(out) { out.append(line).append('\n') }
                }
            }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
        reader.join(2000)
        val text = synchronized(out) { out.toString() }
        return ProbeResult(finished && process.exitValue() == 0, if (finished) text else "$text\n(timed out)")
    }

    private fun encodeTest(backend: HwBackend, codec: VideoCodecChoice, extra: List<String>, toFile: Boolean): ProbeResult {
        val b = backend.copy(encoders = mapOf(codec to extra))
        val args = b.globalArgs() + listOf("-f", "lavfi", "-i", "testsrc2=size=640x480:rate=30", "-frames:v", "8") +
            (b.uploadSuffix?.let { listOf("-vf", it) } ?: emptyList()) +
            b.encoderArgs(codec, 2_000_000, full = false) +
            if (toFile) listOf("-f", "mp4", sample.absolutePath) else listOf("-f", "null", "-")
        return probe(args, b.env)
    }

    private fun fullTest(backend: HwBackend): Boolean {
        if (!sample.isFile) return false
        val args = backend.globalArgs() + backend.decodeArgs(full = true) + listOf("-i", sample.absolutePath, "-vf", backend.fullScale(320, 240)) +
            backend.encoderArgs(VideoCodecChoice.H264, 1_000_000, full = true) + listOf("-f", "null", "-")
        return probe(args, backend.env).ok
    }

    private fun describe(api: HwApi, output: String): String {
        output.lineSequence().firstOrNull { "VAAPI driver:" in it }?.let { line ->
            val s = line.substringAfter("VAAPI driver:").let { if (" for " in it) it.substringAfter(" for ") else it }.substringBefore(" (").trim()
            if (s.isNotEmpty()) return s
        }
        Regex("""< (.+) > has Compute SM""").find(output)?.let { return it.groupValues[1].trim() }
        Regex("""Using device [0-9a-fA-F]{4}:[0-9a-fA-F]{4} \((.+)\)""").find(output)?.let { return it.groupValues[1].trim() }
        Regex("""MFT name: '(.+?)'""").find(output)?.let { return it.groupValues[1].trim() }
        if (api == HwApi.NVENC && !DesktopPaths.isWindows) {
            runCatching {
                File("/proc/driver/nvidia/gpus").listFiles()?.firstNotNullOfOrNull { gpu ->
                    File(gpu, "information").takeIf { it.isFile }?.readLines()?.firstOrNull { it.startsWith("Model:") }?.substringAfter(':')?.trim()
                }
            }.getOrNull()?.let { return it }
        }
        return when (api) {
            HwApi.VAAPI -> "the graphics card"
            HwApi.NVENC -> "the NVIDIA card"
            HwApi.QSV -> "Intel graphics"
            else -> "the graphics card"
        }
    }

    private fun hint(output: String): String? {
        val l = output.lowercase()
        return when {
            "required nvenc api version" in l || "driver does not support the required nvenc" in l ->
                "the NVIDIA driver is too old for this FFmpeg (update to 570 or newer)"
            "vainitialize failed" in l || "failed to initialise vaapi" in l || "va_openDriver() returns".lowercase() in l && "returns 0" !in l ->
                "the VA-API driver didn't load (install mesa-va-drivers; on Fedora mesa-va-drivers-freeworld)"
            "no usable encoding entrypoint" in l || "no usable encoding profile" in l ->
                "the graphics card can't encode this format"
            "cannot load libcuda" in l || "could not dynamically load cuda" in l -> null
            else -> null
        }
    }

    /** Tests every encoder of [base]; returns the backend with the codecs that worked, or null. */
    private fun tryBackend(base: HwBackend, notes: MutableList<String>): HwBackend? {
        val working = linkedMapOf<VideoCodecChoice, List<String>>()
        var description: String? = null
        for (codec in probeCodecs) {
            if (base.encoderName(codec) !in Ffmpeg.encoders) continue
            val toFile = codec == VideoCodecChoice.H264
            var result = encodeTest(base, codec, emptyList(), toFile)
            var extra = emptyList<String>()
            if (!result.ok && base.api == HwApi.VAAPI && "No usable encoding entrypoint" in result.output) {
                // Some AMD/Intel chips only have the low-power encoder for some formats.
                extra = listOf("-low_power", "1")
                result = encodeTest(base, codec, extra, toFile)
            }
            if (result.ok) {
                working[codec] = extra
                if (description == null) description = describe(base.api, result.output)
            } else {
                val why = hint(result.output) ?: result.output.lineSequence().lastOrNull { it.isNotBlank() }?.trim()?.take(160)
                notes += "${base.encoderName(codec)} on ${base.device ?: base.deviceType.ffName}: failed${why?.let { " ($it)" } ?: ""}"
                if (deviceFailure.any { it in result.output }) break
            }
        }
        if (working.isEmpty()) return null
        val found = base.copy(encoders = working, description = description ?: base.description)
        val full = base.fullGpu && VideoCodecChoice.H264 in working && fullTest(found)
        return found.copy(fullGpu = full)
    }

    private fun detect(): Detection {
        val notes = mutableListOf<String>()
        if (disabledByEnv) return Detection(null, notes, "turned off")
        val started = System.nanoTime()
        val result = runCatching {
            if (!Ffmpeg.isAvailable) return@runCatching Detection(null, notes, "FFmpeg is missing")
            val hwEncoders = Ffmpeg.encoders.filter { name -> HwApi.entries.any { name.endsWith("_" + it.suffix) } }
            if (hwEncoders.isEmpty()) return@runCatching Detection(null, notes, "this FFmpeg build has no hardware encoders")
            val candidates = if (DesktopPaths.isWindows) windowsCandidates() else linuxCandidates(notes)
            val allowed = forcedApi?.let { api -> candidates.filter { it.api == api } } ?: candidates
            var best: HwBackend? = null
            for (c in allowed) {
                if (best != null && best.api == c.api && best.device == c.device) continue // same device, other env: already works
                val found = tryBackend(c, notes) ?: continue
                if (best == null || found.encoders.size > best.encoders.size) best = found
                if (DesktopPaths.isWindows) break
                if (best.encoders.size == probeCodecs.size) break
            }
            val reason = if (best != null) null else notes.firstNotNullOfOrNull { n -> n.substringAfter("(", "").substringBeforeLast(")").takeIf { it.isNotBlank() } }
                ?: if (allowed.isEmpty()) "no graphics device found" else "no encoder passed the test"
            Detection(best, notes, if (best == null) reason else null)
        }.getOrElse { Detection(null, notes + "probe crashed: $it", "the check failed") }
        val ms = (System.nanoTime() - started) / 1_000_000
        System.err.println("Apparat: hardware video ${result.backend?.let { "${it.api.label} on ${it.description}: ${it.encoders.keys.joinToString { c -> c.shortName }}, full GPU ${it.fullGpu}" } ?: "unavailable (${result.reason})"} [${ms} ms]")
        result.notes.forEach { System.err.println("Apparat:   $it") }
        return result
    }

    private fun windowsCandidates(): List<HwBackend> {
        fun b(api: HwApi, type: HwDeviceType, full: Boolean = false) = HwBackend(api, type, null, FfmpegEnv.NONE, emptyMap(), full, api.label)
        return listOf(
            b(HwApi.NVENC, HwDeviceType.CUDA),
            b(HwApi.QSV, HwDeviceType.D3D11VA),
            b(HwApi.D3D12VA, HwDeviceType.D3D12VA),
            b(HwApi.MF, HwDeviceType.D3D11VA)
        )
    }

    private fun linuxCandidates(notes: MutableList<String>): List<HwBackend> {
        val list = mutableListOf<HwBackend>()
        val forcedNode = System.getenv("APPARAT_VAAPI_DEVICE")?.takeIf { it.isNotBlank() }
        val all = forcedNode?.let { listOf(File(it)) }
            ?: File("/dev/dri").listFiles { f -> f.name.startsWith("renderD") }?.sortedBy { it.name }?.take(4).orEmpty()
        val usable = all.filter { it.canRead() && it.canWrite() }
        if (all.isNotEmpty() && usable.isEmpty()) {
            notes += "${all.joinToString { it.path }} can't be opened: add yourself to the render and video groups " +
                "(sudo usermod -aG render,video \$USER) and log in again"
        } else if (all.isEmpty()) {
            notes += "no /dev/dri render node (no GPU driver loaded, or a sandbox without --device=dri)"
        }
        val envs = vaapiEnvs()
        for (node in usable) for (env in envs) {
            list += HwBackend(HwApi.VAAPI, HwDeviceType.VAAPI, node.path, env, emptyMap(), fullGpu = true, description = HwApi.VAAPI.label)
        }
        list += HwBackend(HwApi.NVENC, HwDeviceType.CUDA, null, FfmpegEnv.NONE, emptyMap(), fullGpu = true, description = HwApi.NVENC.label)
        return list
    }

    /**
     * Library environments for VA-API, best first. The bundled libva (2.14) can't load the newer
     * Mesa drivers on current distros; the system's libva (symlinked into a private folder, so
     * nothing else from the system shadows the bundle) can.
     */
    private fun vaapiEnvs(): List<FfmpegEnv> {
        val exeDir = File(Ffmpeg.ffmpeg).absoluteFile.parentFile
        val bundlesLibva = exeDir?.listFiles()?.any { it.name.startsWith("libva.so") } == true
        if (!bundlesLibva) return listOf(FfmpegEnv.NONE)
        val envs = mutableListOf<FfmpegEnv>()
        systemLibvaDir()?.let { envs += FfmpegEnv(libDirs = listOf(it)) }
        val inherited = System.getenv("LIBVA_DRIVERS_PATH")
        val driverDirs = listOf(
            "/usr/lib/x86_64-linux-gnu/dri", "/usr/lib64/dri", "/usr/lib/dri",
            "/usr/lib/x86_64-linux-gnu/GL/default/lib/dri", "/usr/lib/x86_64-linux-gnu/GL/lib/dri"
        ).filter { File(it).isDirectory }
        envs += when {
            !inherited.isNullOrBlank() -> FfmpegEnv.NONE
            driverDirs.isNotEmpty() -> FfmpegEnv(vars = mapOf("LIBVA_DRIVERS_PATH" to driverDirs.joinToString(":")))
            else -> FfmpegEnv.NONE
        }
        return envs
    }

    private val systemLibs = listOf("libva.so.2", "libva-drm.so.2", "libva-x11.so.2", "libdrm.so.2")
    private val requiredLibs = setOf("libva.so.2", "libva-drm.so.2")

    private fun findSystemLib(name: String): File? {
        runCatching {
            val p = ProcessBuilder("/sbin/ldconfig", "-p").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(5, TimeUnit.SECONDS)
            out.lineSequence().filter { it.trim().startsWith("$name ") && "x86-64" in it }
                .map { File(it.substringAfter("=>").trim()) }.firstOrNull { it.isFile }
        }.getOrNull()?.let { return it }
        return listOf("/usr/lib/x86_64-linux-gnu", "/lib/x86_64-linux-gnu", "/usr/lib64", "/lib64", "/usr/lib")
            .map { File(it, name) }.firstOrNull { it.isFile }
    }

    private fun systemLibvaDir(): File? = runCatching {
        val found = systemLibs.associateWith { findSystemLib(it) }
        if (requiredLibs.any { found[it] == null }) return@runCatching null
        val dir = File(workDir, "system-libva")
        dir.deleteRecursively()
        dir.mkdirs()
        for ((name, file) in found) {
            if (file == null) continue
            java.nio.file.Files.createSymbolicLink(File(dir, name).toPath(), file.canonicalFile.toPath())
        }
        dir
    }.getOrNull()
}
