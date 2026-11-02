package com.omniforge.apparat.core.util

import com.omniforge.apparat.desktop.platform.Ffmpeg

/**
 * What the bundled FFmpeg can encode. Desktop encodes in software, so a codec is offered whenever
 * the FFmpeg build has an encoder for it. HEIC/AVIF stills are not offered (no encoder bundled).
 */
object CodecSupport {
    const val MIME_HEVC = "video/hevc"
    const val MIME_AV1 = "video/av01"
    const val MIME_VP9 = "video/x-vnd.on2.vp9"
    const val MIME_HEIC = "image/vnd.android.heic"

    val canEncodeHevc: Boolean by lazy { "libx265" in Ffmpeg.encoders }
    val canEncodeAv1: Boolean by lazy { Ffmpeg.encoders.any { it in setOf("libsvtav1", "libaom-av1", "librav1e") } }
    val canEncodeVp9: Boolean by lazy { "libvpx-vp9" in Ffmpeg.encoders }

    val canEncodeHeic: Boolean get() = false

    /** HEIC/AVIF photos are opened through FFmpeg when Skia can't read them. */
    val canDecodeHeic: Boolean get() = true
    val canDecodeAvif: Boolean get() = true

    val canEncodeAvif: Boolean get() = false
}
