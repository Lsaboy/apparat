package com.omniforge.apparat.core.model

/**
 * The desktop tool list: every Android tool (ANDROID_TOOLS is the Android app's ALL_TOOLS, renamed
 * when the desktop build copies ToolModels.kt) followed by tools that only exist on desktop.
 * Home, search and the shared screens all read ALL_TOOLS, so desktop-only tools appear there
 * without any change to the Android app.
 */
val DESKTOP_ONLY_TOOLS: List<ToolItem> = listOf(
    ToolItem(
        id = "batch_process",
        shortTitle = "Batch",
        title = "Batch Processor",
        description = "Compress, shrink or convert many PDFs, photos, videos or audio files at once, straight into a folder.",
        category = ToolCategory.UTILITIES,
        route = "batch_process",
        badge = "Desktop",
        keywords = listOf("batch", "bulk", "many", "multiple", "folder", "all", "mass", "queue", "compress", "convert", "shrink")
    ),
    ToolItem(
        id = "audio_convert",
        shortTitle = "Audio Convert",
        title = "Audio Converter",
        description = "Convert audio (or a video's sound) to MP3, M4A, Opus, FLAC or WAV, with optional volume levelling.",
        category = ToolCategory.VIDEO,
        route = "audio_convert",
        badge = "Desktop",
        keywords = listOf("audio", "convert", "mp3", "m4a", "aac", "opus", "ogg", "flac", "wav", "music", "podcast", "normalize", "loudness", "volume")
    ),
    ToolItem(
        id = "video_rotate",
        shortTitle = "Rotate Video",
        title = "Rotate, Flip & Speed",
        description = "Rotate or flip a video, or speed it up / slow it down (sound kept in tune).",
        category = ToolCategory.VIDEO,
        route = "video_rotate",
        badge = "Desktop",
        keywords = listOf("rotate", "flip", "mirror", "sideways", "upside down", "speed", "fast", "slow", "timelapse", "slow motion", "90", "180")
    )
)

val ALL_TOOLS: List<ToolItem> = ANDROID_TOOLS + DESKTOP_ONLY_TOOLS
