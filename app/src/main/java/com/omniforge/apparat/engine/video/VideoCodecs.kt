package com.omniforge.apparat.engine.video

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.omniforge.apparat.core.util.CodecSupport
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Output video codec for tools that re-encode into MP4. [bitrateFactor] scales the H.264 bitrate:
 * newer codecs reach the same quality at a smaller size. VP9 is left out on purpose, because it needs
 * a WEBM container and every output here is MP4.
 */
enum class VideoCodecChoice(val label: String, val shortName: String, val mimeType: String, val bitrateFactor: Float) {
    H264("H.264 (plays everywhere)", "H.264", "video/avc", 1.0f),
    HEVC("HEVC / H.265 (smaller)", "HEVC", CodecSupport.MIME_HEVC, 0.6f),
    AV1("AV1 (smallest, newer players only)", "AV1", CodecSupport.MIME_AV1, 0.5f);

    companion object {
        /** Lowest bitrate any shrink will ask for, whatever the codec. */
        const val MIN_BITRATE = 200_000

        /** H.264 always comes first and is always there; the others only when the phone has a hardware encoder. */
        fun available(canEncodeHevc: Boolean, canEncodeAv1: Boolean): List<VideoCodecChoice> = buildList {
            add(H264)
            if (canEncodeHevc) add(HEVC)
            if (canEncodeAv1) add(AV1)
        }

        /** The codecs this device can encode in hardware. */
        fun availableOnDevice(): List<VideoCodecChoice> =
            available(CodecSupport.canEncodeHevc, CodecSupport.canEncodeAv1)

        /** Scales an H.264 bitrate for [codec], never going below [MIN_BITRATE]. */
        fun scaledBitrate(h264Bitrate: Int, codec: VideoCodecChoice): Int =
            max((h264Bitrate * codec.bitrateFactor).roundToInt(), MIN_BITRATE)
    }
}

/** Checks whether the phone has any decoder for a file's video track, so tools can say so up front. */
object VideoDecodeSupport {

    /** Readable name for a video track MIME, used in error messages. */
    fun friendlyCodecName(mime: String): String = when (mime.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC (H.265)"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/x-vnd.on2.vp8" -> "VP8"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/mpeg2" -> "MPEG-2"
        else -> mime.substringAfter('/').uppercase()
    }

    /** The message shown when no decoder exists for [mime]. */
    fun unsupportedMessage(mime: String): String =
        "This phone can't play ${friendlyCodecName(mime)} video, so it can't be converted here."

    /** MIME of the first video track, or null if it can't be read (the export then reports its own error). */
    fun videoTrackMime(context: Context, uri: Uri): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount)
                .mapNotNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                .firstOrNull { it.startsWith("video/") }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** True if any decoder (hardware or software) handles [mime]. Unknown results count as supported. */
    fun canDecode(mime: String): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    }.getOrDefault(true)

    /** Throws a clear error when the video track uses a codec this phone can't decode. */
    fun requireDecodable(context: Context, uri: Uri) {
        val mime = videoTrackMime(context, uri) ?: return
        if (!canDecode(mime)) throw IllegalStateException(unsupportedMessage(mime))
    }
}
