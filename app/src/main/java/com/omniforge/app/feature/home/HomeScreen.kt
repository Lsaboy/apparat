package com.omniforge.app.feature.home

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.model.ToolCategory
import com.omniforge.app.core.model.ToolItem
import com.omniforge.app.core.settings.AppSettings
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.BraunSegmentedControl
import com.omniforge.app.core.ui.FirstBootAppearanceDialog
import com.omniforge.app.core.util.FileUtils

val ALL_TOOLS = listOf(
    // PDF Tools
    ToolItem(
        id = "pdf_unlock",
        title = "Remove PDF Password",
        description = "Enter password once to decrypt and save an unlocked, unrestricted PDF.",
        category = ToolCategory.PDF,
        route = "pdf_unlock",
        keywords = listOf("password", "decrypt", "unlock", "security", "protect", "restriction", "pin", "credentials", "unprotect")
    ),
    ToolItem(
        id = "pdf_shrink",
        title = "Compress PDF",
        description = "Downsample embedded images and clean redundant streams to reduce size.",
        category = ToolCategory.PDF,
        route = "pdf_shrink",
        keywords = listOf("compress", "shrink", "reduce", "size", "downsample", "dpi", "optimize", "kb", "mb", "small")
    ),
    ToolItem(
        id = "pdf_split",
        title = "Split PDF",
        description = "Split into multiple PDFs by custom ranges (e.g. 1-3, 4-6) or page intervals.",
        category = ToolCategory.PDF,
        route = "pdf_split",
        badge = "Multi-Split",
        keywords = listOf("split", "separate", "cut", "extract", "range", "interval", "divide", "pages", "break")
    ),
    ToolItem(
        id = "pdf_to_images",
        title = "PDF to Images",
        description = "Render every page of a PDF into high-res PNG images.",
        category = ToolCategory.PDF,
        route = "pdf_to_images",
        keywords = listOf("convert", "export", "render", "extract", "png", "jpg", "jpeg", "pages", "photo", "picture")
    ),
    ToolItem(
        id = "images_to_pdf",
        title = "Photos to PDF",
        description = "Combine multiple pictures into a clean multi-page PDF document.",
        category = ToolCategory.PDF,
        route = "images_to_pdf",
        keywords = listOf("combine", "merge", "join", "photos", "pictures", "images", "scan", "jpg", "png", "convert")
    ),

    // Video Tools
    ToolItem(
        id = "video_shrink",
        title = "Video Shrinker",
        description = "Compress videos for Discord, WhatsApp, Email, or custom resolutions.",
        category = ToolCategory.VIDEO,
        route = "video_shrink",
        keywords = listOf("compress", "shrink", "reduce size", "resize", "scale", "mb", "gb", "whatsapp", "discord", "email", "bitrate", "resolution", "720p", "1080p")
    ),
    ToolItem(
        id = "video_trim",
        title = "Trim & Mute",
        description = "Cut a clip or remove sound. Near-lossless fast trimming without quality loss.",
        category = ToolCategory.VIDEO,
        route = "video_trim",
        badge = "Fast",
        keywords = listOf("cut", "trim", "crop", "clip", "snip", "mute", "silence", "remove audio", "shorten", "fast")
    ),
    ToolItem(
        id = "video_caption",
        title = "Burn Subtitles",
        description = "Permanently hardcode an SRT subtitle file into your video stream.",
        category = ToolCategory.VIDEO,
        route = "video_caption",
        keywords = listOf("subtitles", "srt", "caption", "burn", "hardcode", "cc", "closed captions", "text", "overlay")
    ),
    ToolItem(
        id = "video_to_gif",
        title = "Video to GIF",
        description = "Transform video clips into animated GIFs with custom FPS and looping.",
        category = ToolCategory.VIDEO,
        route = "video_to_gif",
        keywords = listOf("gif", "animated", "animation", "loop", "convert", "clip", "fps", "meme")
    ),
    ToolItem(
        id = "video_convert",
        title = "Video Transcoder",
        description = "Transcode any video into standard MP4, WebM, or 3GP format.",
        category = ToolCategory.VIDEO,
        route = "video_convert",
        keywords = listOf("convert", "transcode", "format", "mp4", "webm", "mkv", "avi", "mov", "encoding", "codec")
    ),
    ToolItem(
        id = "audio_extract",
        title = "Extract Audio",
        description = "Rip the soundtrack from any video into a crisp M4A or MP3 file.",
        category = ToolCategory.VIDEO,
        route = "audio_extract",
        keywords = listOf("extract", "audio", "sound", "music", "song", "rip", "mp3", "m4a", "soundtrack", "speech")
    ),

    // Image Tools
    ToolItem(
        id = "image_shrink",
        title = "Compress Photo",
        description = "Reduce photo size with granular quality control, resolution limits, and metadata stripping.",
        category = ToolCategory.IMAGE,
        route = "image_shrink",
        keywords = listOf("compress", "shrink", "reduce size", "resize", "scale", "kb", "mb", "jpg", "jpeg", "png", "webp", "photo", "picture", "quality", "downscale", "dimen")
    ),
    ToolItem(
        id = "image_convert",
        title = "Image Converter",
        description = "Convert between PNG, JPEG, and modern efficient WebP formats.",
        category = ToolCategory.IMAGE,
        route = "image_convert",
        keywords = listOf("convert", "format", "png", "jpg", "jpeg", "webp", "transcode", "transparent")
    ),
    ToolItem(
        id = "exif_stripper",
        title = "EXIF Cleaner",
        description = "Strip GPS coordinates, device fingerprints, and timestamps for privacy.",
        category = ToolCategory.IMAGE,
        route = "exif_strip",
        keywords = listOf("exif", "metadata", "gps", "location", "privacy", "clean", "remove", "device", "camera", "strip")
    ),
    ToolItem(
        id = "document_scan",
        title = "Document Scanner",
        description = "Auto-detect document edges, straighten perspective, and enhance contrast.",
        category = ToolCategory.IMAGE,
        route = "document_scan",
        badge = "Camera",
        keywords = listOf("scan", "camera", "document", "receipt", "paper", "detect", "crop", "deskew", "contrast")
    ),
    ToolItem(
        id = "palette_extractor",
        title = "Palette Extractor",
        description = "Sample dominant colors from any image with exact HEX and RGB codes.",
        category = ToolCategory.IMAGE,
        route = "palette_extractor",
        keywords = listOf("palette", "colors", "extract", "hex", "rgb", "dominant", "sample", "picker", "design", "swatch")
    ),

    // Utilities
    ToolItem(
        id = "qr_studio",
        title = "QR Code Studio",
        description = "Generate customizable QR codes with colored pixels, logos, and high error correction.",
        category = ToolCategory.UTILITIES,
        route = "qr_studio",
        keywords = listOf("qr", "barcode", "generate", "code", "link", "url", "wifi", "vcard", "scan")
    ),
    ToolItem(
        id = "hash_verifier",
        title = "File Checksum",
        description = "Calculate and verify MD5, SHA-1, and SHA-256 integrity hashes for any file.",
        category = ToolCategory.UTILITIES,
        route = "hash_verifier",
        keywords = listOf("hash", "checksum", "sha256", "md5", "sha1", "verify", "integrity", "corrupt", "fingerprint")
    ),
    ToolItem(
        id = "json_formatter",
        title = "JSON Formatter",
        description = "Beautify, validate, and minify messy JSON text with error diagnostics.",
        category = ToolCategory.UTILITIES,
        route = "json_tool",
        keywords = listOf("json", "format", "beautify", "validate", "minify", "lint", "prettify", "parse")
    ),
    ToolItem(
        id = "base64_studio",
        title = "Base64 Studio",
        description = "Encode or decode strings, images, and raw binary data to Base64 format.",
        category = ToolCategory.UTILITIES,
        route = "base64_studio",
        keywords = listOf("base64", "encode", "decode", "binary", "data uri", "string", "ascii")
    )
)

data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val color: Color
)

val QUICK_ACTIONS = listOf(
    QuickActionItem("Unlock PDF", "Remove Passwords", "pdf_unlock", Icons.Default.LockOpen, AccentPdf),
    QuickActionItem("Shrink Photo", "Reduce KB/MB", "image_shrink", Icons.Default.Compress, AccentImage),
    QuickActionItem("Trim Video", "Near-Lossless Cut", "video_trim", Icons.Default.ContentCut, AccentVideo),
    QuickActionItem("QR Generator", "Links & Wi-Fi", "qr_studio", Icons.Default.QrCode, AccentUtility)
)

enum class BraunCategoryTab(val label: String, val category: ToolCategory?) {
    ALL("ALL", null),
    DOCS("DOCS", ToolCategory.PDF),
    VIDEO("VIDEO", ToolCategory.VIDEO),
    GRAPHICS("GRAPHICS", ToolCategory.IMAGE),
    UTILITY("UTILITY", ToolCategory.UTILITIES)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appSettings: AppSettings,
    onNavigateToTool: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    sharedUri: Uri? = null,
    sharedMimeType: String? = null,
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    var isGridView by remember { mutableStateOf(appSettings.isGridView) }
    var showFirstBootDialog by remember { mutableStateOf(!appSettings.hasSeenAppearancePrompt) }

    if (showFirstBootDialog) {
        FirstBootAppearanceDialog(
            appSettings = appSettings,
            onDismiss = {
                showFirstBootDialog = false
                onThemeChanged()
            }
        )
    }

    val compatibleCategory = remember(sharedMimeType) {
        when {
            sharedMimeType == null -> null
            sharedMimeType == "application/pdf" -> ToolCategory.PDF
            sharedMimeType.startsWith("video/") -> ToolCategory.VIDEO
            sharedMimeType.startsWith("image/") -> ToolCategory.IMAGE
            else -> null
        }
    }

    LaunchedEffect(compatibleCategory) {
        if (compatibleCategory != null) {
            selectedCategory = compatibleCategory
        }
    }

    val filteredTools = remember(searchQuery, selectedCategory, compatibleCategory) {
        ALL_TOOLS.filter { tool ->
            val matchesCategory = when {
                compatibleCategory != null -> tool.category == compatibleCategory
                selectedCategory != null -> tool.category == selectedCategory
                else -> true
            }

            val query = searchQuery.trim().lowercase()
            val matchesSearch = query.isEmpty() ||
                tool.title.lowercase().contains(query) ||
                tool.description.lowercase().contains(query) ||
                tool.keywords.any { it.lowercase().contains(query) }

            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Precision Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(BraunOrange, shape = RoundedCornerShape(4.5.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APPARAT",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                        Text(
                            text = "Mod. 2026 // 20 Instruments • 100% Offline",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Action buttons (View mode toggle + Settings)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isGridView = !isGridView
                                appSettings.isGridView = isGridView
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                contentDescription = "Toggle Grid or List View",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Customization & Settings",
                                tint = BraunOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Braun Hardware Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "> SEARCH INSTRUMENT OR FORMAT...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(BraunOrange),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Category Rocker Switch (English technical labels)
                if (compatibleCategory == null) {
                    Spacer(modifier = Modifier.height(10.dp))

                    val activeTab = BraunCategoryTab.entries.find { it.category == selectedCategory } ?: BraunCategoryTab.ALL
                    BraunSegmentedControl(
                        options = BraunCategoryTab.entries,
                        selectedOption = activeTab,
                        onOptionSelected = { tab ->
                            selectedCategory = tab.category
                        },
                        labelForOption = { it.label }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Contextual Share Banner
            if (sharedUri != null) {
                item {
                    val sharedName = FileUtils.getFileName(context, sharedUri)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, BraunOrange.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BraunOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = BraunOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Incoming File: $sharedName",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Select instrument to process:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }

                            if (filteredTools.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filteredTools) { tool ->
                                        AssistChip(
                                            onClick = { onNavigateToTool(tool.id) },
                                            label = { Text(tool.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = getCategoryIcon(tool.id, tool.category),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = tool.category.color
                                                )
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Shortcuts Row
            if (searchQuery.isEmpty() && selectedCategory == null && sharedUri == null) {
                item {
                    Column {
                        Text(
                            text = "QUICK SHORTCUTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            items(QUICK_ACTIONS) { action ->
                                QuickActionCard(action = action, onClick = { onNavigateToTool(action.route) })
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            searchQuery.isNotEmpty() -> "SEARCH RESULTS (${filteredTools.size})"
                            selectedCategory != null -> "${selectedCategory?.displayName?.uppercase()} (${filteredTools.size})"
                            else -> "ALL INSTRUMENTS (${filteredTools.size})"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 1.sp
                        )
                    )

                    Text(
                        text = if (isGridView) "GRID" else "LIST",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Tools Layout (Grid vs List)
            if (isGridView) {
                items(filteredTools.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            ToolGridCard(tool = pair[0], onClick = { onNavigateToTool(pair[0].route) })
                        }
                        if (pair.size > 1) {
                            Box(modifier = Modifier.weight(1f)) {
                                ToolGridCard(tool = pair[1], onClick = { onNavigateToTool(pair[1].route) })
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(filteredTools) { tool ->
                    ToolListCard(tool = tool, onClick = { onNavigateToTool(tool.route) })
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun QuickActionCard(
    action: QuickActionItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(136.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(action.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(9.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ToolGridCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    val icon = getCategoryIcon(tool.id, tool.category)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tool.category.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tool.category.color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (tool.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tool.category.color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tool.badge.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = tool.category.color,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToolListCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    val icon = getCategoryIcon(tool.id, tool.category)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tool.category.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tool.category.color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tool.badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tool.category.color.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tool.badge.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = tool.category.color,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun getCategoryHeaderIcon(category: ToolCategory): ImageVector = when (category) {
    ToolCategory.PDF -> Icons.Default.PictureAsPdf
    ToolCategory.VIDEO -> Icons.Default.Movie
    ToolCategory.IMAGE -> Icons.Default.Image
    ToolCategory.UTILITIES -> Icons.Default.Build
}

fun getCategoryIcon(id: String, category: ToolCategory): ImageVector = when {
    id.contains("password") || id.contains("unlock") -> Icons.Default.LockOpen
    id.contains("shrink") || id.contains("compress") -> Icons.Default.Compress
    id.contains("caption") || id.contains("subtitle") -> Icons.Default.Subtitles
    id.contains("trim") || id.contains("mute") -> Icons.Default.ContentCut
    id.contains("gif") -> Icons.Default.Gif
    id.contains("convert") -> Icons.Default.Transform
    id.contains("audio") -> Icons.Default.Audiotrack
    id.contains("scan") -> Icons.Default.DocumentScanner
    id.contains("palette") -> Icons.Default.ColorLens
    id.contains("exif") -> Icons.Default.Shield
    id.contains("qr") -> Icons.Default.QrCode
    id.contains("hash") -> Icons.Default.Fingerprint
    id.contains("split") -> Icons.AutoMirrored.Filled.CallSplit
    id.contains("to_images") -> Icons.Default.Collections
    id.contains("json") -> Icons.Default.DataObject
    id.contains("base64") -> Icons.Default.Code
    category == ToolCategory.PDF -> Icons.Default.PictureAsPdf
    category == ToolCategory.VIDEO -> Icons.Default.VideoFile
    category == ToolCategory.IMAGE -> Icons.Default.Image
    else -> Icons.Default.Build
}
