package com.omniforge.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.model.ToolCategory
import com.omniforge.app.core.model.ToolItem
import com.omniforge.app.core.theme.*

val ALL_TOOLS = listOf(
    // PDF Tools
    ToolItem(
        id = "pdf_unlock",
        title = "Remove PDF Password",
        description = "Enter password once to decrypt and save a permanent DRM-free, unlocked PDF.",
        category = ToolCategory.PDF,
        route = "pdf_unlock",
        badge = "Popular"
    ),
    ToolItem(
        id = "pdf_shrink",
        title = "Shrink & Compress PDF",
        description = "Downsample embedded images and clean redundant streams to reduce PDF file size.",
        category = ToolCategory.PDF,
        route = "pdf_shrink"
    ),
    ToolItem(
        id = "images_to_pdf",
        title = "Photos to PDF",
        description = "Combine multiple pictures into a clean multi-page PDF document.",
        category = ToolCategory.PDF,
        route = "images_to_pdf"
    ),

    // Video Tools
    ToolItem(
        id = "video_shrink",
        title = "Video Shrinker",
        description = "Compress videos for Discord (10MB/25MB), WhatsApp, Email, or custom resolutions.",
        category = ToolCategory.VIDEO,
        route = "video_shrink",
        badge = "FFmpeg"
    ),
    ToolItem(
        id = "video_caption",
        title = "Burn Subtitles & Captions",
        description = "Hardcode .srt or .vtt subtitles directly into video with custom font sizing.",
        category = ToolCategory.VIDEO,
        route = "video_caption",
        badge = "Subtitles"
    ),
    ToolItem(
        id = "video_convert",
        title = "Video Format Exchange",
        description = "Transcode between MP4, MKV, WEBM, and high-quality animated GIFs.",
        category = ToolCategory.VIDEO,
        route = "video_convert"
    ),
    ToolItem(
        id = "audio_extract",
        title = "Extract Audio from Video",
        description = "Rip audio tracks directly to MP3, M4A, WAV, or lossless FLAC.",
        category = ToolCategory.VIDEO,
        route = "audio_extract"
    ),

    // Image Tools
    ToolItem(
        id = "image_shrink",
        title = "Photo Shrinker & Optimizer",
        description = "Compress photo file size with quality slider, resolution downscaling, and EXIF strip.",
        category = ToolCategory.IMAGE,
        route = "image_shrink"
    ),
    ToolItem(
        id = "image_convert",
        title = "Photo Format Exchange",
        description = "Convert between JPEG, PNG, and WEBP formats seamlessly.",
        category = ToolCategory.IMAGE,
        route = "image_convert"
    ),
    ToolItem(
        id = "exif_strip",
        title = "EXIF Privacy Stripper",
        description = "Inspect hidden camera/GPS info and strip all location & metadata before sharing.",
        category = ToolCategory.IMAGE,
        route = "exif_strip",
        badge = "Privacy"
    ),

    // Utility Tools
    ToolItem(
        id = "qr_studio",
        title = "QR Code Studio",
        description = "Generate high-res QR codes for Wi-Fi, URLs, and text 100% offline.",
        category = ToolCategory.UTILITIES,
        route = "qr_studio"
    ),
    ToolItem(
        id = "hash_verifier",
        title = "Checksum & Hash Verifier",
        description = "Compute and verify MD5, SHA-1, SHA-256 hashes of any file against clipboard.",
        category = ToolCategory.UTILITIES,
        route = "hash_verifier"
    ),
    ToolItem(
        id = "base64_studio",
        title = "Base64 Studio",
        description = "Quick offline Base64 encoder and decoder for strings and text.",
        category = ToolCategory.UTILITIES,
        route = "base64_studio"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTool: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }

    val filteredTools = remember(searchQuery, selectedCategory) {
        ALL_TOOLS.filter { tool ->
            val matchesCategory = selectedCategory == null || tool.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                    tool.title.contains(searchQuery, ignoreCase = true) ||
                    tool.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        containerColor = AmoledBlack,
        topBar = {
            Column(
                modifier = Modifier
                    .background(AmoledBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "OmniForge",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary,
                                letterSpacing = (-0.5).sp
                            )
                        )
                        Text(
                            text = "Offline Media & Document Toolkit",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "100% OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryTeal,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    placeholder = { Text("Search utilities (e.g. compress, password, mp4)...", color = TextTertiary, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All (${ALL_TOOLS.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                selectedContainerColor = PrimaryTeal,
                                labelColor = TextSecondary,
                                selectedLabelColor = AmoledBlack
                            ),
                            border = BorderStroke(1.dp, if (selectedCategory == null) PrimaryTeal else DarkBorder)
                        )
                    }
                    items(ToolCategory.entries) { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = if (selectedCategory == category) null else category
                            },
                            label = { Text(category.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DarkSurfaceElevated,
                                selectedContainerColor = category.color,
                                labelColor = TextSecondary,
                                selectedLabelColor = AmoledBlack
                            ),
                            border = BorderStroke(1.dp, if (selectedCategory == category) category.color else DarkBorder)
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Hero Privacy Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Zero Uploads. Zero Subscriptions.",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Your photos, videos, and PDFs never leave this phone.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(filteredTools) { tool ->
                ToolCard(tool = tool, onClick = { onNavigateToTool(tool.route) })
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = getCategoryIcon(tool.id, tool.category)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tool.category.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tool.category.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    if (tool.badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tool.category.color.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tool.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = tool.category.color,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

fun getCategoryIcon(id: String, category: ToolCategory): ImageVector = when {
    id.contains("password") || id.contains("unlock") -> Icons.Default.LockOpen
    id.contains("shrink") || id.contains("compress") -> Icons.Default.Compress
    id.contains("caption") || id.contains("subtitle") -> Icons.Default.Subtitles
    id.contains("convert") -> Icons.Default.Transform
    id.contains("audio") -> Icons.Default.Audiotrack
    id.contains("exif") -> Icons.Default.Shield
    id.contains("qr") -> Icons.Default.QrCode
    id.contains("hash") -> Icons.Default.Fingerprint
    id.contains("base64") -> Icons.Default.Code
    category == ToolCategory.PDF -> Icons.Default.PictureAsPdf
    category == ToolCategory.VIDEO -> Icons.Default.VideoFile
    category == ToolCategory.IMAGE -> Icons.Default.Image
    else -> Icons.Default.Build
}
