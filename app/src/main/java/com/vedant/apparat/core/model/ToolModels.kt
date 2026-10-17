package com.vedant.apparat.core.model

import androidx.compose.ui.graphics.Color
import com.vedant.apparat.core.theme.AccentImage
import com.vedant.apparat.core.theme.AccentPdf
import com.vedant.apparat.core.theme.AccentUtility
import com.vedant.apparat.core.theme.AccentVideo
import java.io.File

enum class ToolCategory(val displayName: String, val color: Color) {
    PDF("PDF Tools", AccentPdf),
    VIDEO("Video & Audio", AccentVideo),
    IMAGE("Photo & Image", AccentImage),
    UTILITIES("Quick Utilities", AccentUtility)
}

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val route: String,
    val badge: String? = null,
    val keywords: List<String> = emptyList(),
    /** Fits a quick-action card and a launcher shortcut's short label (~12 chars). */
    val shortTitle: String = title
)

sealed interface ProcessState {
    data object Idle : ProcessState
    data class Processing(val progress: Float = 0f, val message: String = "Processing...") : ProcessState
    data class Success(val outputFile: File, val message: String = "Operation completed successfully!") : ProcessState
    data class Error(val error: String) : ProcessState
}

val ALL_TOOLS = listOf(
    // PDF Tools
    ToolItem(
        id = "pdf_unlock",
        shortTitle = "Unlock PDF",
        title = "Remove PDF Password",
        description = "Remove the password or printing/copying restrictions from a PDF you can open.",
        category = ToolCategory.PDF,
        route = "pdf_unlock",
        keywords = listOf("password", "decrypt", "unlock", "security", "protect", "restriction", "pin", "credentials", "unprotect")
    ),
    ToolItem(
        id = "pdf_shrink",
        shortTitle = "Compress PDF",
        title = "Compress PDF",
        description = "Make PDFs smaller by re-saving the photos and scans inside them.",
        category = ToolCategory.PDF,
        route = "pdf_shrink",
        keywords = listOf("compress", "shrink", "reduce", "size", "downsample", "dpi", "optimize", "kb", "mb", "small")
    ),
    ToolItem(
        id = "pdf_split",
        shortTitle = "Split PDF",
        title = "Split PDF",
        description = "Split into multiple PDFs by custom ranges (e.g. 1-3, 4-6) or page intervals.",
        category = ToolCategory.PDF,
        route = "pdf_split",
        badge = "Multi-Split",
        keywords = listOf("split", "separate", "cut", "extract", "range", "interval", "divide", "pages", "break")
    ),
    ToolItem(
        id = "pdf_merge",
        shortTitle = "Merge PDFs",
        title = "Merge PDFs",
        description = "Combine several PDFs into one, in the order you choose. Handles password-protected files.",
        category = ToolCategory.PDF,
        route = "pdf_merge",
        keywords = listOf("merge", "combine", "join", "append", "bills", "invoices", "kyc", "documents", "attach", "one pdf")
    ),
    ToolItem(
        id = "pdf_to_images",
        shortTitle = "PDF to Images",
        title = "PDF to Images",
        description = "Save PDF pages as JPEG or PNG images: all pages or a range, at 150 or 300 DPI.",
        category = ToolCategory.PDF,
        route = "pdf_to_images",
        keywords = listOf("convert", "export", "render", "extract", "png", "jpg", "jpeg", "pages", "photo", "picture")
    ),
    ToolItem(
        id = "images_to_pdf",
        shortTitle = "Photos to PDF",
        title = "Photos to PDF",
        description = "Combine photos into one A4 PDF, in the order you choose.",
        category = ToolCategory.PDF,
        route = "images_to_pdf",
        keywords = listOf("combine", "merge", "join", "photos", "pictures", "images", "scan", "jpg", "png", "convert")
    ),

    // Video Tools
    ToolItem(
        id = "video_shrink",
        shortTitle = "Shrink Video",
        title = "Video Shrinker",
        description = "Compress videos for Discord, WhatsApp, Email, or custom resolutions.",
        category = ToolCategory.VIDEO,
        route = "video_shrink",
        keywords = listOf("compress", "shrink", "reduce size", "resize", "scale", "mb", "gb", "whatsapp", "discord", "email", "bitrate", "resolution", "720p", "1080p")
    ),
    ToolItem(
        id = "video_trim",
        shortTitle = "Trim Video",
        title = "Trim & Mute",
        description = "Cut a clip or remove the sound, without re-encoding the video.",
        category = ToolCategory.VIDEO,
        route = "video_trim",
        badge = "Fast",
        keywords = listOf("cut", "trim", "crop", "clip", "snip", "mute", "silence", "remove audio", "shorten", "fast")
    ),
    ToolItem(
        id = "video_caption",
        shortTitle = "Subtitles",
        title = "Burn Subtitles & Captions",
        description = "Burn .srt or .vtt subtitles into a video, with adjustable text size.",
        category = ToolCategory.VIDEO,
        route = "video_caption",
        keywords = listOf("subtitles", "captions", "srt", "vtt", "burn", "hardcode", "transcript", "text", "timing")
    ),
    ToolItem(
        id = "video_to_gif",
        shortTitle = "Video to GIF",
        title = "Video to GIF",
        description = "Turn a short video clip into an animated GIF.",
        category = ToolCategory.VIDEO,
        route = "video_to_gif",
        keywords = listOf("gif", "animation", "loop", "meme", "sticker", "convert", "clip")
    ),
    ToolItem(
        id = "video_convert",
        shortTitle = "To MP4",
        title = "Convert to MP4",
        description = "Turn MKV, WEBM, MOV or 3GP videos into an MP4 that plays everywhere.",
        category = ToolCategory.VIDEO,
        route = "video_convert",
        keywords = listOf("convert", "mp4", "mkv", "webm", "mov", "3gp", "transcode", "remux", "format")
    ),
    ToolItem(
        id = "audio_extract",
        shortTitle = "Get Audio",
        title = "Extract Audio from Video",
        description = "Save the sound from a video as M4A (small) or WAV (uncompressed).",
        category = ToolCategory.VIDEO,
        route = "audio_extract",
        keywords = listOf("extract", "audio", "sound", "music", "m4a", "wav", "mp3", "voice", "strip video", "soundtrack")
    ),

    // Image Tools
    ToolItem(
        id = "image_shrink",
        shortTitle = "Shrink Photo",
        title = "Shrink Image",
        description = "Shrink a photo under a size limit (e.g. 50 KB for portals), or set quality and resolution yourself.",
        category = ToolCategory.IMAGE,
        route = "image_shrink",
        keywords = listOf("compress", "shrink", "reduce size", "resize", "scale", "kb", "mb", "jpg", "jpeg", "png", "webp", "photo", "picture", "quality", "downscale", "dimen", "portal", "upload", "limit", "target", "under", "50kb", "100kb", "aadhaar", "pan", "passport", "ssc", "upsc", "form")
    ),
    ToolItem(
        id = "document_scan",
        shortTitle = "Scan Doc",
        title = "Document & Receipt Enhancer",
        description = "Turn photos of documents and receipts into clean, high-contrast scans.",
        category = ToolCategory.IMAGE,
        route = "document_scan",
        badge = "Scan",
        keywords = listOf("scan", "scanner", "receipt", "document", "b&w", "black and white", "contrast", "threshold", "invoice", "paper", "clean")
    ),
    ToolItem(
        id = "palette_extractor",
        shortTitle = "Palette",
        title = "Palette & Color Extractor",
        description = "Extract dominant colors and HEX codes directly from photos.",
        category = ToolCategory.IMAGE,
        route = "palette_extractor",
        badge = "Palette",
        keywords = listOf("palette", "color", "hex", "swatch", "dominant", "picker", "theme", "extract")
    ),
    ToolItem(
        id = "image_convert",
        shortTitle = "Convert Photo",
        title = "Convert Image",
        description = "Convert photos between JPEG, PNG and WEBP (HEIC photos too).",
        category = ToolCategory.IMAGE,
        route = "image_convert",
        keywords = listOf("convert", "format", "jpg", "jpeg", "png", "webp", "extension", "transcode")
    ),
    ToolItem(
        id = "exif_strip",
        shortTitle = "Strip EXIF",
        title = "EXIF Metadata Stripper",
        description = "Inspect hidden camera/GPS info and strip all location & metadata before sharing.",
        category = ToolCategory.IMAGE,
        route = "exif_strip",
        badge = "Privacy",
        keywords = listOf("exif", "metadata", "privacy", "gps", "location", "camera", "strip", "remove", "clean", "sanitize")
    ),

    // Utility Tools
    ToolItem(
        id = "qr_studio",
        shortTitle = "QR Code",
        title = "QR Code Studio",
        description = "Generate high-res QR codes for Wi-Fi, URLs, UPI and text.",
        category = ToolCategory.UTILITIES,
        route = "qr_studio",
        keywords = listOf("qr", "barcode", "matrix", "wifi", "url", "link", "generator", "create", "code")
    ),
    ToolItem(
        id = "hash_verifier",
        shortTitle = "Checksum",
        title = "Checksum & Hash Verifier",
        description = "Compute MD5, SHA-1, SHA-256 and SHA-512 for a file or text, and check against an expected hash.",
        category = ToolCategory.UTILITIES,
        route = "hash_verifier",
        badge = "SHA-256",
        keywords = listOf("checksum", "hash", "md5", "sha1", "sha256", "sha512", "integrity", "verify", "digest", "compare")
    ),
    ToolItem(
        id = "json_tool",
        shortTitle = "JSON",
        title = "JSON Formatter & Validator",
        description = "Prettify, validate or minify JSON without changing any values.",
        category = ToolCategory.UTILITIES,
        route = "json_tool",
        keywords = listOf("json", "format", "prettify", "minify", "validate", "parse", "syntax", "indent", "beautify")
    ),
    ToolItem(
        id = "base64_studio",
        shortTitle = "Base64",
        title = "Base64 Studio",
        description = "Encode or decode Base64; decoded PDFs and images can be saved as files.",
        category = ToolCategory.UTILITIES,
        route = "base64_studio",
        keywords = listOf("base64", "encode", "decode", "b64", "text", "binary", "ascii", "string")
    )
)

