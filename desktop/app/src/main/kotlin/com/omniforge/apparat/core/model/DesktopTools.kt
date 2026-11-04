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
    ),
    ToolItem(
        id = "watch_folder",
        shortTitle = "Watch Folder",
        title = "Watch Folder",
        description = "Pick a folder; every PDF, photo, video or audio file dropped into it is processed automatically.",
        category = ToolCategory.UTILITIES,
        route = "watch_folder",
        badge = "Desktop",
        keywords = listOf("watch", "folder", "auto", "automatic", "hot folder", "monitor", "drop", "inbox", "batch", "background")
    ),
    ToolItem(
        id = "pdf_pages",
        shortTitle = "Page Tools",
        title = "Page Tools: Reorder, Rotate & Number",
        description = "See every page, then reorder, rotate or delete pages, add page numbers or a watermark.",
        category = ToolCategory.PDF,
        route = "pdf_pages",
        badge = "Desktop",
        keywords = listOf("page", "pages", "reorder", "rearrange", "move", "rotate", "delete", "remove", "number", "numbering", "watermark", "stamp", "organize", "thumbnails")
    ),
    ToolItem(
        id = "video_join",
        shortTitle = "Join Videos",
        title = "Join Videos",
        description = "Join clips into one video, in the order you choose. Lossless when the clips match.",
        category = ToolCategory.VIDEO,
        route = "video_join",
        badge = "Desktop",
        keywords = listOf("join", "merge", "combine", "concat", "concatenate", "append", "stitch", "clips")
    ),
    ToolItem(
        id = "video_crop",
        shortTitle = "Crop Video",
        title = "Crop Video",
        description = "Crop a video to square, 9:16 for stories, 16:9, 4:5 or 4:3, centred.",
        category = ToolCategory.VIDEO,
        route = "video_crop",
        badge = "Desktop",
        keywords = listOf("crop", "square", "vertical", "portrait", "story", "reel", "tiktok", "9:16", "16:9", "4:5", "aspect", "ratio")
    ),
    ToolItem(
        id = "video_resize",
        shortTitle = "Resize Video",
        title = "Resolution & Frame Rate",
        description = "Change a video's resolution (4K to 360p) or frame rate.",
        category = ToolCategory.VIDEO,
        route = "video_resize",
        badge = "Desktop",
        keywords = listOf("resolution", "resize", "scale", "downscale", "1080p", "720p", "4k", "fps", "frame rate", "framerate", "60", "30")
    ),
    ToolItem(
        id = "video_frames",
        shortTitle = "Frames",
        title = "Frames & Contact Sheet",
        description = "Save evenly spaced frames from a video as images, or one contact-sheet picture of thumbnails.",
        category = ToolCategory.VIDEO,
        route = "video_frames",
        badge = "Desktop",
        keywords = listOf("frame", "frames", "extract", "screenshot", "still", "thumbnail", "contact sheet", "storyboard", "grid", "snapshot", "jpg", "png")
    )
)

val ALL_TOOLS: List<ToolItem> = ANDROID_TOOLS + DESKTOP_ONLY_TOOLS
