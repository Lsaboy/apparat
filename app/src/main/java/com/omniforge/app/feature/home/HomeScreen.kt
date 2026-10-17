package com.omniforge.app.feature.home

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.model.ToolCategory
import com.omniforge.app.core.model.ALL_TOOLS
import com.omniforge.app.core.model.ToolItem
import com.omniforge.app.core.settings.AppSettings
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.util.FileUtils

data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appSettings: AppSettings,
    sharedUri: Uri? = null,
    sharedMimeType: String? = null,
    onNavigateToTool: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onDismissSharedPayload: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ToolCategory?>(null) }
    var isGridView by remember { mutableStateOf(appSettings.isGridView) }

    val enabledShortcuts = appSettings.enabledShortcuts
    val quickActions = remember(enabledShortcuts) {
        val selectedTools = ALL_TOOLS.filter { it.id in enabledShortcuts }
        val effectiveTools = if (selectedTools.isNotEmpty()) {
            selectedTools.take(4)
        } else {
            ALL_TOOLS.filter { it.id in setOf("pdf_unlock", "video_trim", "image_shrink", "video_shrink") }
        }
        effectiveTools.map { tool ->
            QuickActionItem(
                title = tool.title,
                subtitle = tool.badge ?: tool.category.displayName.substringBefore(" "),
                icon = getCategoryIcon(tool.id, tool.category),
                color = tool.category.color,
                route = tool.route
            )
        }
    }

    // When an incoming file is shared, filter down to tools matching that MIME type
    val compatibleCategory: ToolCategory? = remember(sharedMimeType) {
        when {
            sharedMimeType == null -> null
            sharedMimeType.startsWith("video/") -> ToolCategory.VIDEO
            sharedMimeType.startsWith("audio/") -> ToolCategory.VIDEO
            sharedMimeType.startsWith("image/") -> ToolCategory.IMAGE
            sharedMimeType == "application/pdf" -> ToolCategory.PDF
            else -> null
        }
    }

    val filteredTools = remember(searchQuery, selectedCategory, compatibleCategory, sharedUri) {
        ALL_TOOLS.filter { tool ->
            val matchesCategory = when {
                sharedUri != null && compatibleCategory == null -> {
                    // Arbitrary file shared (e.g. ZIP, ISO, APK, binary) -> filter to Hash Verifier
                    tool.id == "hash_verifier"
                }
                compatibleCategory != null -> {
                    if (sharedMimeType?.startsWith("audio/") == true) {
                        tool.id == "audio_extract" || tool.id == "video_trim" || tool.id == "hash_verifier"
                    } else {
                        tool.category == compatibleCategory || tool.id == "hash_verifier"
                    }
                }
                selectedCategory != null -> tool.category == selectedCategory
                else -> true
            }

            val tokens = searchQuery.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val matchesQuery = tokens.isEmpty() || tokens.all { token ->
                tool.title.contains(token, ignoreCase = true) ||
                tool.description.contains(token, ignoreCase = true) ||
                tool.keywords.any { it.contains(token, ignoreCase = true) }
            }

            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Handyman,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Digital Pocket Knife",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                    color = TextPrimary,
                                    letterSpacing = (-0.4).sp
                                )
                            )
                            Text(
                                text = "${ALL_TOOLS.size} Tools · 100% Local",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated),
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                contentDescription = "Toggle Grid or List View",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Customization & Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Modern Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkSurfaceElevated)
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
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search utilities (e.g. compress, split, trim)...",
                                    color = TextTertiary,
                                    fontSize = 13.5.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
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
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Category Filter Pills
                if (compatibleCategory == null) {
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            CategoryPill(
                                title = "All (${ALL_TOOLS.size})",
                                icon = Icons.Default.Dashboard,
                                isSelected = selectedCategory == null,
                                accentColor = MaterialTheme.colorScheme.primary,
                                onClick = { selectedCategory = null }
                            )
                        }
                        items(ToolCategory.entries) { category ->
                            val count = ALL_TOOLS.count { it.category == category }
                            CategoryPill(
                                title = "${category.displayName.substringBefore(" ")} ($count)",
                                icon = getCategoryHeaderIcon(category),
                                isSelected = selectedCategory == category,
                                accentColor = category.color,
                                onClick = {
                                    selectedCategory = if (selectedCategory == category) null else category
                                }
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Contextual Share Banner (if file was shared into app)
            if (sharedUri != null) {
                item {
                    val sharedName = remember(sharedUri) { FileUtils.getFileName(context, sharedUri) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Shared File: $sharedName",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Tap a tool below or select from the list:",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismissSharedPayload,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss shared file",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
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
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions Horizontal Row (shown on default dashboard)
            if (searchQuery.isEmpty() && selectedCategory == null && sharedUri == null) {
                item {
                    Column {
                        Text(
                            text = "QUICK SHORTCUTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextTertiary,
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
                            items(quickActions) { action ->
                                QuickActionCard(action = action, onClick = { onNavigateToTool(action.route) })
                            }
                        }
                    }
                }
            }

            // Section Header or Active Filter Title
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
                            searchQuery.isNotEmpty() -> "Search Results (${filteredTools.size})"
                            selectedCategory != null -> "${selectedCategory?.displayName} (${filteredTools.size})"
                            else -> "All Utilities (${filteredTools.size})"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.5.sp
                        )
                    )

                    Text(
                        text = if (isGridView) "Grid view" else "List view",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Tools Layout (Grid vs List)
            if (isGridView) {
                // 2-Column Responsive Grid via chunked pairs
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
                // 1-Column Refined List View
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
fun CategoryPill(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.18f) else DarkSurfaceElevated,
        border = BorderStroke(1.dp, if (isSelected) accentColor else DarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) accentColor else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isSelected) accentColor else TextSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.5.sp
                )
            )
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkBorder)
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
                    .clip(RoundedCornerShape(10.dp))
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
                        color = TextPrimary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
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
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkBorder)
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tool.category.color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tool.category.color,
                        modifier = Modifier.size(21.dp)
                    )
                }

                if (tool.badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tool.category.color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tool.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
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
                    color = TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceCard,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tool.category.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tool.category.color,
                    modifier = Modifier.size(23.dp)
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
                            fontSize = 14.5.sp,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tool.badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(tool.category.color.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tool.badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = tool.category.color,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
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
