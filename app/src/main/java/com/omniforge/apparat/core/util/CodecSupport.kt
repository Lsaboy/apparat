package com.omniforge.apparat.core.util

import android.media.MediaCodecList
import android.os.Build

/**
 * What this device can actually decode/encode. Tools use it to show a format only when it will work;
 * formats the phone can't handle are hidden rather than greyed out. Results are cached per process.
 */
object CodecSupport {
    const val MIME_HEVC = "video/hevc"
    const val MIME_AV1 = "video/av01"
    const val MIME_VP9 = "video/x-vnd.on2.vp9"
    const val MIME_HEIC = "image/vnd.android.heic"

    private fun hasCodec(mime: String, encoder: Boolean, hardwareOnly: Boolean): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder == encoder &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                (!hardwareOnly || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.isHardwareAccelerated && !info.isSoftwareOnly))
        }
    }.getOrDefault(false)

    /** Video encoders are only offered when hardware-accelerated; software encoding is far too slow on a phone. */
    val canEncodeHevc: Boolean by lazy { hasCodec(MIME_HEVC, encoder = true, hardwareOnly = true) }
    val canEncodeAv1: Boolean by lazy { hasCodec(MIME_AV1, encoder = true, hardwareOnly = true) }
    val canEncodeVp9: Boolean by lazy { hasCodec(MIME_VP9, encoder = true, hardwareOnly = true) }

    /** HEIC stills are HEVC-coded, so HeifWriter needs a HEVC (or dedicated HEIC) encoder, API 28+. */
    val canEncodeHeic: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (hasCodec(MIME_HEIC, encoder = true, hardwareOnly = false) || canEncodeHevc)
    }

    /** ImageDecoder reads HEIC from API 28 and AVIF from API 31. */
    val canDecodeHeic: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val canDecodeAvif: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Android has no public AVIF encoder; stays false unless an engine adds one. */
    val canEncodeAvif: Boolean get() = false
}
