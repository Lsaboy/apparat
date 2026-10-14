package com.omniforge.app.engine.video

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.omniforge.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class VideoOutputFormat(val extension: String, val mimeType: String, val codecArgs: String) {
    MP4(".mp4", "video/mp4", "-c:v libx264 -preset veryfast -crf 23 -c:a aac"),
    MKV(".mkv", "video/x-matroska", "-c:v libx264 -preset veryfast -crf 23 -c:a aac"),
    WEBM(".webm", "video/webm", "-c:v libvpx-vp9 -crf 30 -b:v 0 -c:a libopus"),
    GIF(".gif", "image/gif", "-vf \"fps=12,scale=480:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse\"")
}

enum class VideoShrinkPreset(val label: String, val scaleFilter: String, val crf: Int) {
    DISCORD_FAST("Discord / Social (Compact 720p)", "scale=-2:720", 28),
    WHATSAPP("WhatsApp / Messaging (480p)", "scale=-2:480", 30),
    HIGH_QUALITY("Light Compress (1080p)", "scale=-2:1080", 24),
    MAX_SHRINK("Extreme Shrink (360p)", "scale=-2:360", 32)
}

enum class AudioOutputFormat(val extension: String, val mimeType: String, val ffmpegArgs: String) {
    MP3(".mp3", "audio/mpeg", "-vn -c:a libmp3lame -q:a 2"),
    M4A(".m4a", "audio/mp4", "-vn -c:a aac -b:a 192k"),
    WAV(".wav", "audio/wav", "-vn -c:a pcm_s16le"),
    FLAC(".flac", "audio/flac", "-vn -c:a flac")
}

object VideoEngine {

    /**
     * Shrinks video using FFmpeg with selected preset, resolution scaling, and CRF.
     */
    suspend fun shrinkVideo(
        context: Context,
        inputUri: Uri,
        preset: VideoShrinkPreset = VideoShrinkPreset.DISCORD_FAST,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "shrunk", ".mp4")

            try {
                val cmd = "-y -i \"${cachedInput.absolutePath}\" -vf \"${preset.scaleFilter}\" -c:v libx264 -preset veryfast -crf ${preset.crf} -c:a aac -b:a 128k \"${outputFile.absolutePath}\""
                onProgress(0.2f, "Encoding video with ${preset.label}...")

                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    throw IllegalStateException("FFmpeg failed: ${session.failStackTrace ?: session.allLogsAsString}")
                }
                outputFile
            } finally {
                cachedInput.delete()
            }
        }
    }

    /**
     * Converts video to different container / format (MP4, MKV, WEBM, GIF).
     */
    suspend fun convertFormat(
        context: Context,
        inputUri: Uri,
        targetFormat: VideoOutputFormat,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "converted", targetFormat.extension)

            try {
                val cmd = "-y -i \"${cachedInput.absolutePath}\" ${targetFormat.codecArgs} \"${outputFile.absolutePath}\""
                onProgress(0.2f, "Transcoding to ${targetFormat.name}...")

                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    throw IllegalStateException("Transcoding failed: ${session.failStackTrace ?: session.allLogsAsString}")
                }
                outputFile
            } finally {
                cachedInput.delete()
            }
        }
    }

    /**
     * Hardcodes / burns subtitles (.srt or .vtt) into video.
     */
    suspend fun burnSubtitles(
        context: Context,
        videoUri: Uri,
        subtitleUri: Uri,
        fontSize: Int = 20,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedVideo = FileUtils.copyUriToCache(context, videoUri)
            val cachedSub = FileUtils.copyUriToCache(context, subtitleUri, "subtitles.srt")
            val outputFile = FileUtils.createTempOutputFile(context, "captioned", ".mp4")

            try {
                // Escape colons and backslashes for FFmpeg subtitles filter
                val subPathEscaped = cachedSub.absolutePath.replace(":", "\\:").replace("\\", "/")
                val filter = "subtitles='$subPathEscaped':force_style='FontSize=$fontSize,PrimaryColour=&H00FFFFFF&,OutlineColour=&H00000000&,BorderStyle=3'"
                val cmd = "-y -i \"${cachedVideo.absolutePath}\" -vf \"$filter\" -c:v libx264 -preset veryfast -crf 23 -c:a copy \"${outputFile.absolutePath}\""

                onProgress(0.2f, "Burning subtitles into video track...")
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    throw IllegalStateException("Subtitle burn failed: ${session.failStackTrace ?: session.allLogsAsString}")
                }
                outputFile
            } finally {
                cachedVideo.delete()
                cachedSub.delete()
            }
        }
    }

    /**
     * Extracts audio from video directly into MP3, M4A, WAV, or FLAC.
     */
    suspend fun extractAudio(
        context: Context,
        videoUri: Uri,
        targetFormat: AudioOutputFormat
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedVideo = FileUtils.copyUriToCache(context, videoUri)
            val outputFile = FileUtils.createTempOutputFile(context, "extracted_audio", targetFormat.extension)

            try {
                val cmd = "-y -i \"${cachedVideo.absolutePath}\" ${targetFormat.ffmpegArgs} \"${outputFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    throw IllegalStateException("Audio extraction failed: ${session.failStackTrace ?: session.allLogsAsString}")
                }
                outputFile
            } finally {
                cachedVideo.delete()
            }
        }
    }

    /**
     * Instantly strips audio track from video (lossless stream copy, 0 re-encoding!).
     */
    suspend fun stripAudio(
        context: Context,
        videoUri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedVideo = FileUtils.copyUriToCache(context, videoUri)
            val outputFile = FileUtils.createTempOutputFile(context, "muted", ".mp4")

            try {
                val cmd = "-y -i \"${cachedVideo.absolutePath}\" -an -c:v copy \"${outputFile.absolutePath}\""
                val session = FFmpegKit.execute(cmd)
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    throw IllegalStateException("Mute failed: ${session.failStackTrace ?: session.allLogsAsString}")
                }
                outputFile
            } finally {
                cachedVideo.delete()
            }
        }
    }
}
