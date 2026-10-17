package com.vedant.apparat.feature.home

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
import com.vedant.apparat.core.model.ALL_TOOLS
import com.vedant.apparat.core.model.ToolCategory
import com.vedant.apparat.core.model.ToolItem
import com.vedant.apparat.core.settings.AppSettings
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.ui.ApparatBentoMark
import com.vedant.apparat.core.ui.BraunSegmentedControl
import com.vedant.apparat.core.ui.FirstBootAppearanceDialog
import com.vedant.apparat.core.util.FileUtils

data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val color: Color
)

enum class BraunCategoryTab(val label: String, val category: ToolCategory?) {
    ALL("ALL", null),
    DOCS("PDF", ToolCategory.PDF),
    VIDEO("VIDEO", ToolCategory.VIDEO),
    GRAPHICS("PHOTO", ToolCategory.IMAGE),
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
    onDismissSharedPayload: () -> Unit = {},
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
                title = tool.shortTitle,
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

    // Follow the shared file's category; dismissing the banner resets the filter.
    LaunchedEffect(compatibleCategory) {
        selectedCategory = compatibleCategory
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
                            ApparatBentoMark(
                                size = 20.dp,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
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
                            text = "Model 69 // ${ALL_TOOLS.size} Instruments",
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
                                tint = MaterialTheme.colorScheme.primary,
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Category Rocker Switch (English technical labels)
                if (sharedUri == null) {
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
                    val sharedName = remember(sharedUri) { FileUtils.getFileName(context, sharedUri) }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
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
                                IconButton(
                                    onClick = onDismissSharedPayload,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss shared file",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (filteredTools.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(filteredTools) { tool ->
                                        AssistChip(
                                            onClick = { onNavigateToTool(tool.route) },
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
                            items(quickActions) { action ->
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
                    // Equal-height cards in a row, so a two-line title doesn't make the row ragged.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            ToolGridCard(tool = pair[0], onClick = { onNavigateToTool(pair[0].route) })
                        }
                        if (pair.size > 1) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
            .widthIn(min = 136.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .fillMaxHeight(),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
            .fillMaxHeight()
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
                                color = accentText(tool.category.color),
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
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 2,
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
                        maxLines = 2,
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
    id.contains("merge") -> Icons.Default.MergeType
    id.contains("to_images") -> Icons.Default.Collections
    id.contains("json") -> Icons.Default.DataObject
    id.contains("base64") -> Icons.Default.Code
    category == ToolCategory.PDF -> Icons.Default.PictureAsPdf
    category == ToolCategory.VIDEO -> Icons.Default.VideoFile
    category == ToolCategory.IMAGE -> Icons.Default.Image
    else -> Icons.Default.Build
}
