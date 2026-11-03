package com.omniforge.apparat.engine.video

import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.FfmpegEnv
import com.omniforge.apparat.desktop.platform.FfmpegException
import com.omniforge.apparat.desktop.platform.HwAccel
import com.omniforge.apparat.desktop.platform.HwBackend
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** How much of a video encode runs on the graphics card. */
enum class EncodeLevel {
    /** Decode, scale and encode on the GPU; frames never come back to memory. */
    FULL_GPU,
    /** GPU decode, CPU filters, GPU encode. */
    GPU_DECODE,
    /** CPU decode and filters, GPU encode. */
    GPU_ENCODE,
    /** x264 / x265 / SVT-AV1. */
    SOFTWARE
}

data class EncodePlan(val codec: VideoCodecChoice, val backend: HwBackend?, val level: EncodeLevel) {
    val isHardware: Boolean get() = backend != null && level != EncodeLevel.SOFTWARE

    /** e.g. "H.264, VA-API" or "HEVC, CPU". */
    val label: String get() = codec.shortName + ", " + (if (isHardware) backend!!.api.label else "CPU")
}

/**
 * What one encode needs: the [source], the output size as displayed ([outWidth] x [outHeight],
 * even), and an H.264-equivalent [bitrate] (null: quality-based). [allowFullGpu]: the only video
 * filter is a resize, so the full-GPU path can be used.
 */
data class EncodeJob(
    val source: MediaProbe,
    val outWidth: Int,
    val outHeight: Int,
    val bitrate: Int? = null,
    val allowFullGpu: Boolean = false,
    val fps: Double? = source.fps
)

/** The FFmpeg arguments for one [plan]. */
class PlanArgs(val plan: EncodePlan, val job: EncodeJob) {
    private val backend: HwBackend? = plan.backend?.takeIf { plan.isHardware }
    private val full = plan.level == EncodeLevel.FULL_GPU

    val env: FfmpegEnv = backend?.env ?: FfmpegEnv.NONE

    /** Device options; before every input. */
    val global: List<String> = backend?.globalArgs().orEmpty()

    /** Decoder options for the first input. */
    val decode: List<String> = when (plan.level) {
        EncodeLevel.FULL_GPU -> backend?.decodeArgs(full = true).orEmpty()
        EncodeLevel.GPU_DECODE -> backend?.decodeArgs(full = false).orEmpty()
        else -> emptyList()
    }

    /** Everything that goes before the first "-i". */
    val input: List<String> = global + decode

    /** Appended to the last filter of a -filter_complex graph (moves frames to the GPU if needed). */
    val complexSuffix: String = backend?.takeIf { !full }?.uploadSuffix?.let { ",$it" } ?: ""

    /** The full-GPU path ignores the rotation flag; it is put back by a remux afterwards. */
    val needsRotationRemux: Boolean = full && job.source.rotation % 360 != 0

    /** The -vf value for software filters [swChain] (null: none needed). */
    fun videoFilter(swChain: List<String>): String? {
        if (full && backend != null) {
            val swap = job.source.rotation % 180 != 0
            return backend.fullScale(if (swap) job.outHeight else job.outWidth, if (swap) job.outWidth else job.outHeight)
        }
        val chain = swChain + listOfNotNull(backend?.uploadSuffix)
        return chain.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    fun vfArgs(swChain: List<String>): List<String> = videoFilter(swChain)?.let { listOf("-vf", it) } ?: emptyList()

    val encoder: List<String>
        get() = backend?.encoderArgs(plan.codec, hwBitrate, full) ?: softwareEncoder()

    /**
     * Hardware encoders have no quality mode that behaves the same everywhere, so they always get a
     * bitrate: the requested one, or ~0.12 bits per pixel (H.264), never more than the source has.
     */
    val hwBitrate: Int
        get() {
            val codec = plan.codec
            job.bitrate?.let { return VideoCodecChoice.scaledBitrate(it, codec) }
            val fps = (job.fps ?: 30.0).coerceIn(1.0, 120.0)
            var rate = 0.12 * job.outWidth * job.outHeight * fps * codec.bitrateFactor
            val srcFactor = when (job.source.videoCodec) {
                "h264" -> 1.0
                "hevc", "vp9" -> 0.6
                "av1" -> 0.5
                else -> null
            }
            val src = job.source.bitrate?.takeIf { it > 0 }
            if (srcFactor != null && src != null) rate = min(rate, src / srcFactor * codec.bitrateFactor * 1.1)
            return max(rate.roundToLong(), 300_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    private fun softwareEncoder(): List<String> {
        val codec = plan.codec
        val encoder = codec.ffmpegEncoder ?: throw IllegalStateException("${codec.shortName} encoding isn't available on this computer.")
        val args = mutableListOf("-c:v", encoder)
        when (encoder) {
            "libx264" -> args += listOf("-preset", "medium", "-profile:v", "high")
            "libx265" -> args += listOf("-preset", "medium", "-tag:v", "hvc1")
            "libsvtav1" -> args += listOf("-preset", "8")
            "libaom-av1" -> args += listOf("-cpu-used", "6", "-row-mt", "1")
        }
        val bitrate = job.bitrate?.let { VideoCodecChoice.scaledBitrate(it, codec) }
        if (bitrate != null) {
            args += listOf("-b:v", bitrate.toString(), "-maxrate", (bitrate.toLong() * 3 / 2).toString(), "-bufsize", (bitrate.toLong() * 2).toString())
        } else if (encoder == "libx264") {
            args += listOf("-crf", "20")
        }
        args += listOf("-pix_fmt", "yuv420p")
        return args
    }
}

/**
 * Picks how to encode a video and falls back step by step: full GPU, GPU decode + encode, CPU decode
 * + GPU encode, CPU in the chosen codec, CPU H.264. What failed is remembered for the session, so
 * a broken path is tried once, not on every video.
 */
object VideoEncoding {

    /** The plan of the last successful encode (self-test, logs). */
    @Volatile
    var lastPlan: EncodePlan? = null
        private set

    /** Source codecs worth trying to decode on the GPU. */
    private val gpuDecodable = setOf("h264", "hevc", "vp9", "av1", "vp8", "mpeg2video")

    /** Sizes every hardware encoder handles (H.264 tops out at 4096 on most chips). */
    internal fun sizeOk(codec: VideoCodecChoice, w: Int, h: Int): Boolean {
        val maxSide = if (codec == VideoCodecChoice.H264) 4096 else 8192
        return min(w, h) >= 128 && max(w, h) <= maxSide
    }

    fun plans(codec: VideoCodecChoice, job: EncodeJob, backend: HwBackend?): List<EncodePlan> = buildList {
        val src = job.source.videoCodec.orEmpty()
        if (backend != null && HwAccel.usable(backend, codec) && sizeOk(codec, job.outWidth, job.outHeight)) {
            val decodable = src in gpuDecodable
            if (job.allowFullGpu && backend.fullGpu && decodable && !HwAccel.hasPathFailed(backend, "full:$src")) {
                add(EncodePlan(codec, backend, EncodeLevel.FULL_GPU))
            }
            if (decodable && !HwAccel.hasPathFailed(backend, "decode:$src")) add(EncodePlan(codec, backend, EncodeLevel.GPU_DECODE))
            add(EncodePlan(codec, backend, EncodeLevel.GPU_ENCODE))
        }
        if (codec.ffmpegEncoder != null) add(EncodePlan(codec, null, EncodeLevel.SOFTWARE))
        if (codec != VideoCodecChoice.H264) add(EncodePlan(VideoCodecChoice.H264, null, EncodeLevel.SOFTWARE))
    }

    /** Errors no other plan can fix (bad input, full disk). Hardware errors can mention missing devices, so only CPU runs count. */
    private fun isFatal(t: Throwable, plan: EncodePlan): Boolean {
        val m = t.message.orEmpty()
        if ("Not enough free disk space" in m) return true
        if (plan.isHardware) return false
        return t is FfmpegException && ("Could not open the selected file" in m || "Permission denied" in m || "isn't a video FFmpeg can read" in m)
    }

    /**
     * Runs [run] for each plan until one works and returns it. [run] writes the file it is given
     * (not always [output]: the full-GPU path writes a temporary file and remuxes it).
     */
    suspend fun encode(
        codec: VideoCodecChoice,
        job: EncodeJob,
        output: File,
        backend: HwBackend? = HwAccel.backend(),
        onAttempt: (plan: EncodePlan, retry: Boolean) -> Unit = { _, _ -> },
        run: suspend (PlanArgs, File) -> Unit
    ): EncodePlan {
        val plans = plans(codec, job, backend)
        if (plans.isEmpty()) throw IllegalStateException("${codec.shortName} encoding isn't available on this computer.")
        val failed = mutableListOf<EncodePlan>()
        var lastError: Throwable? = null
        for ((index, plan) in plans.withIndex()) {
            onAttempt(plan, index > 0)
            val args = PlanArgs(plan, job)
            val target = if (args.needsRotationRemux) File(output.parentFile, output.nameWithoutExtension + ".gpu.mp4") else output
            try {
                run(args, target)
                if (args.needsRotationRemux) {
                    restoreRotation(target, output, job.source.rotation)
                    target.delete()
                }
                if (!output.isFile || output.length() == 0L) throw IllegalStateException("FFmpeg produced no output.")
            } catch (c: CancellationException) {
                target.delete(); output.delete()
                throw c
            } catch (t: Throwable) {
                target.delete(); output.delete()
                System.err.println("Apparat: video encode with ${describe(plan)} failed: ${t.message}")
                if (isFatal(t, plan)) throw t
                lastError = t
                failed += plan
                continue
            }
            remember(plan, failed, job.source.videoCodec.orEmpty())
            lastPlan = plan
            System.err.println("Apparat: video encoded with ${describe(plan)}")
            return plan
        }
        throw lastError ?: IllegalStateException("Video encoding failed.")
    }

    fun describe(plan: EncodePlan): String = plan.label + when (plan.level) {
        EncodeLevel.FULL_GPU -> " (full GPU)"
        EncodeLevel.GPU_DECODE -> " (GPU decode)"
        EncodeLevel.GPU_ENCODE -> " (CPU decode)"
        EncodeLevel.SOFTWARE -> ""
    }

    /** What the failures before a success say about this computer. */
    private fun remember(success: EncodePlan, failed: List<EncodePlan>, src: String) {
        for (f in failed) {
            val b = f.backend ?: continue
            when (f.level) {
                EncodeLevel.FULL_GPU -> HwAccel.markPathFailed(b, "full:$src")
                EncodeLevel.GPU_DECODE -> if (success.level == EncodeLevel.GPU_ENCODE) HwAccel.markPathFailed(b, "decode:$src")
                EncodeLevel.GPU_ENCODE -> HwAccel.recordFailure(b, f.codec)
                EncodeLevel.SOFTWARE -> Unit
            }
        }
        if (success.isHardware) HwAccel.recordSuccess(success.backend!!, success.codec)
    }

    /** Copies [from] to [to] with the display rotation of the source (the full-GPU path drops it). */
    private suspend fun restoreRotation(from: File, to: File, rotation: Int) {
        Ffmpeg.run(
            listOf("-display_rotation:v:0", rotation.toString(), "-i", from.absolutePath, "-map", "0", "-c", "copy", "-movflags", "+faststart", to.absolutePath)
        )
    }

    /** Output size as displayed for a source scaled by [scale], both sides even. */
    fun evenSize(width: Int, height: Int, scale: Float = 1f): Pair<Int, Int> =
        max(2, (width * scale / 2).roundToInt() * 2) to max(2, (height * scale / 2).roundToInt() * 2)
}
