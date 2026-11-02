package com.omniforge.apparat.engine.video

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.CodecSupport
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlin.math.max
import kotlin.math.roundToInt

/** Output video codec (desktop: software encoders in the bundled FFmpeg). */
enum class VideoCodecChoice(val label: String, val shortName: String, val mimeType: String, val bitrateFactor: Float) {
    H264("H.264 (plays everywhere)", "H.264", "video/avc", 1.0f),
    HEVC("HEVC / H.265 (smaller)", "HEVC", CodecSupport.MIME_HEVC, 0.6f),
    AV1("AV1 (smallest, newer players only)", "AV1", CodecSupport.MIME_AV1, 0.5f);

    /** FFmpeg encoder for this codec, or null if the bundled build has none. */
    val ffmpegEncoder: String?
        get() = when (this) {
            H264 -> "libx264".takeIf { it in Ffmpeg.encoders } ?: "h264"
            HEVC -> "libx265".takeIf { it in Ffmpeg.encoders }
            AV1 -> listOf("libsvtav1", "libaom-av1", "librav1e").firstOrNull { it in Ffmpeg.encoders }
        }

    companion object {
        const val MIN_BITRATE = 200_000

        fun available(canEncodeHevc: Boolean, canEncodeAv1: Boolean): List<VideoCodecChoice> = buildList {
            add(H264)
            if (canEncodeHevc) add(HEVC)
            if (canEncodeAv1) add(AV1)
        }

        fun availableOnDevice(): List<VideoCodecChoice> = available(CodecSupport.canEncodeHevc, CodecSupport.canEncodeAv1)

        fun scaledBitrate(h264Bitrate: Int, codec: VideoCodecChoice): Int =
            max((h264Bitrate * codec.bitrateFactor).roundToInt(), MIN_BITRATE)
    }
}

/** FFmpeg decodes practically everything, so this mostly reports unreadable files clearly. */
object VideoDecodeSupport {

    fun friendlyCodecName(mime: String): String = when (mime.lowercase()) {
        "video/avc", "h264" -> "H.264"
        "video/hevc", "hevc" -> "HEVC (H.265)"
        "video/av01", "av1" -> "AV1"
        "video/x-vnd.on2.vp9", "vp9" -> "VP9"
        "video/x-vnd.on2.vp8", "vp8" -> "VP8"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es", "mpeg4" -> "MPEG-4"
        "video/3gpp", "h263" -> "H.263"
        "video/mpeg2", "mpeg2video" -> "MPEG-2"
        else -> mime.substringAfter('/').uppercase()
    }

    fun unsupportedMessage(mime: String): String =
        "This computer can't decode ${friendlyCodecName(mime)} video, so it can't be converted here."

    fun videoTrackMime(context: Context, uri: Uri): String? = MediaProbe.of(uri.file)?.videoCodec

    fun canDecode(mime: String): Boolean = true

    fun requireDecodable(context: Context, uri: Uri) {
        if (!Ffmpeg.isAvailable) throw IllegalStateException("FFmpeg is missing, so video tools can't run. Reinstall Apparat.")
        val probe = MediaProbe.of(uri.file) ?: throw IllegalStateException("This file isn't a video that can be read (it may be damaged).")
        if (!probe.hasVideo) throw IllegalStateException("This file has no video track.")
    }
}
