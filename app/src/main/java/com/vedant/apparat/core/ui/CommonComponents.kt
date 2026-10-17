package com.vedant.apparat.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vedant.apparat.core.jobs.ToolJobs
import com.vedant.apparat.core.model.ProcessState
import com.vedant.apparat.core.settings.AppSettings
import com.vedant.apparat.core.settings.ThemeStyle
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.util.FileUtils
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import java.io.File

// ==========================================
// Braun / Dieter Rams Industrial Components
// ==========================================

/**
 * Bento Caliper Matrix Brand Mark - The official Apparat geometric brand mark.
 * Proportioned with 4 interlocked golden-ratio bento compartments:
 * - Documents (Wide Top-Left)
 * - Video (Tall Top-Right)
 * - Graphics (Square Bottom-Left)
 * - Utility Engine Block (Solid Braun Orange Bottom-Right)
 */
@Composable
fun ApparatBentoMark(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 22.dp,
    isDark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val tileFill = if (isDark) Color(0xFF202024) else Color(0xFFE8E4DA)
    val tileBorder = if (isDark) Color(0xFF333338) else Color(0xFFD0CBC0)
    val glyphColor = if (isDark) Color(0xFFF0F0F0) else Color(0xFF191919)
    val subGlyphColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF6A6A6A)
    val utilityFill = accentColor
    val utilitySlot = OnBraunOrange

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val scale = w / 56f
        val r = 5f * scale

        // 1. Documents (Top-Left: 34w x 26h, corner radius 5*scale)
        drawRoundRect(
            color = tileFill,
            topLeft = Offset(0f, 0f),
            size = Size(34f * scale, 26f * scale),
            cornerRadius = CornerRadius(r, r)
        )
        drawRoundRect(
            color = tileBorder,
            topLeft = Offset(0f, 0f),
            size = Size(34f * scale, 26f * scale),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = 1f * scale)
        )
        drawLine(
            color = glyphColor,
            start = Offset(6f * scale, 8f * scale),
            end = Offset(22f * scale, 8f * scale),
            strokeWidth = 2f * scale,
            cap = StrokeCap.Round
        )
        drawLine(
            color = subGlyphColor,
            start = Offset(6f * scale, 14f * scale),
            end = Offset(18f * scale, 14f * scale),
            strokeWidth = 1.4f * scale,
            cap = StrokeCap.Round
        )

        // 2. Video (Top-Right: 18w x 34h at x=38, y=0)
        drawRoundRect(
            color = tileFill,
            topLeft = Offset(38f * scale, 0f),
            size = Size(18f * scale, 34f * scale),
            cornerRadius = CornerRadius(r, r)
        )
        drawRoundRect(
            color = tileBorder,
            topLeft = Offset(38f * scale, 0f),
            size = Size(18f * scale, 34f * scale),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = 1f * scale)
        )
        val playPath = Path().apply {
            moveTo(44f * scale, 13.5f * scale)
            lineTo(51.5f * scale, 17f * scale)
            lineTo(44f * scale, 20.5f * scale)
            close()
        }
        drawPath(playPath, color = glyphColor)

        // 3. Graphics (Bottom-Left: 22w x 26h at x=0, y=30)
        drawRoundRect(
            color = tileFill,
            topLeft = Offset(0f, 30f * scale),
            size = Size(22f * scale, 26f * scale),
            cornerRadius = CornerRadius(r, r)
        )
        drawRoundRect(
            color = tileBorder,
            topLeft = Offset(0f, 30f * scale),
            size = Size(22f * scale, 26f * scale),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = 1f * scale)
        )
        drawCircle(
            color = glyphColor,
            radius = 4.5f * scale,
            center = Offset(11f * scale, 43f * scale),
            style = Stroke(width = 1.5f * scale)
        )
        drawCircle(
            color = glyphColor,
            radius = 2f * scale,
            center = Offset(11f * scale, 43f * scale)
        )

        // 4. Utility Engine Block (Bottom-Right: 30w x 18h at x=26, y=38)
        drawRoundRect(
            color = utilityFill,
            topLeft = Offset(26f * scale, 38f * scale),
            size = Size(30f * scale, 18f * scale),
            cornerRadius = CornerRadius(r, r)
        )
        drawLine(
            color = utilitySlot,
            start = Offset(33f * scale, 47f * scale),
            end = Offset(49f * scale, 47f * scale),
            strokeWidth = 2.2f * scale,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun <T> BraunSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelForOption: (T) -> String = { it.toString() }
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onOptionSelected(option) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = labelForOption(option),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.5.sp,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 1
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 2.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BraunActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = BraunOrange,
    contentColor: Color = OnBraunOrange,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.dp, containerColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun FirstBootAppearanceDialog(
    appSettings: AppSettings,
    onDismiss: () -> Unit
) {
    var selectedTheme by rememberSaveable { mutableStateOf(appSettings.themeStyle) }

    AlertDialog(
        onDismissRequest = {
            appSettings.hasSeenAppearancePrompt = true
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APPARAT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SELECT APPEARANCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeStyle.entries.forEach { style ->
                    val isSelected = style == selectedTheme
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { selectedTheme = style },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedTheme = style },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = style.displayName.uppercase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = when (style) {
                                        ThemeStyle.LIGHT -> "Matte warm ivory • High legibility"
                                        ThemeStyle.DARK -> "Precision charcoal • Low glare"
                                        ThemeStyle.SYSTEM -> "Match current system appearance"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            BraunActionButton(
                onClick = {
                    appSettings.themeStyle = selectedTheme
                    appSettings.hasSeenAppearancePrompt = true
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CONFIRM SELECTION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    )
}

// ==========================================
// Core Tool UI Components
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    categoryName: String,
    accentColor: Color,
    onBack: () -> Unit,
    jobKey: String? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val context = LocalContext.current
    val isRunning = jobKey != null && ToolJobs.isRunning(jobKey)
    var confirmLeave by remember { mutableStateOf(false) }
    val requestBack = { if (isRunning) confirmLeave = true else onBack() }
    BackHandler(enabled = isRunning) { confirmLeave = true }

    if (jobKey != null) {
        ToolJobLifecycle(jobKey)
        NotificationPermissionOnFirstJob(isRunning)
    }
    if (confirmLeave && isRunning) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Still working") },
            text = {
                val notificationsOn = remember {
                    androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
                Text(
                    if (notificationsOn) {
                        "Keep it running in the background? You'll get a notification when it's done, and the result will be here when you come back."
                    } else {
                        "Keep it running in the background? Notifications are off for Apparat, so you won't be told when it's done. Come back to this tool to see the result."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Keep running") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    ToolJobs.cancel(jobKey!!)
                    onBack()
                }) { Text("Stop", color = ErrorRed) }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = categoryName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = readableTextColor(accentColor, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground),
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}

@Composable
fun NotificationPermissionOnFirstJob(isJobRunning: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context) }
    if (appSettings.hasAskedNotificationPermission) return

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        appSettings.hasAskedNotificationPermission = true
    }

    LaunchedEffect(isJobRunning) {
        if (isJobRunning && !appSettings.hasAskedNotificationPermission) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                appSettings.hasAskedNotificationPermission = true
            }
        }
    }
}

@Composable
fun ToolJobLifecycle(jobKey: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(jobKey, lifecycleOwner) {
        ToolJobs.onScreenOpened(jobKey)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> ToolJobs.onScreenShown(context, jobKey)
                Lifecycle.Event.ON_STOP -> ToolJobs.onScreenHidden(jobKey)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ToolJobs.onScreenLeft(jobKey)
        }
    }
}

@Composable
fun FileSelectionCard(
    fileName: String?,
    fileSize: Long?,
    placeholderText: String = "Tap to choose a file",
    icon: ImageVector = Icons.Default.Description,
    accentColor: Color = BraunOrange,
    fileDetails: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (fileName != null) accentColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (fileName != null) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1
                    )
                    val detailsText = fileDetails ?: if (fileSize != null && fileSize > 0) FileUtils.formatFileSize(fileSize) else null
                    if (detailsText != null) {
                        Text(
                            text = detailsText,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                } else {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Icon(
                imageVector = if (fileName != null) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                contentDescription = null,
                tint = if (fileName != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class ToolChainAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)

@Composable
fun ResultCard(
    outputFile: File,
    originalSize: Long? = null,
    message: String = "Processed successfully!",
    /** MIME type and suggested file name (with extension) used by the Save buttons. */
    saveMimeType: String,
    saveName: String,
    onShare: () -> Unit,
    onOpenFile: (() -> Unit)? = null,
    chainActions: List<ToolChainAction> = emptyList()
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = accentText(SuccessGreen),
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val ext = outputFile.extension.lowercase()
            val isImage = ext in setOf("jpg", "jpeg", "png", "webp", "gif")
            if (isImage) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(outputFile)
                            .crossfade(true)
                            .size(coil.size.Size(512, 512))
                            .build(),
                        contentDescription = "Output image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            val newSize = outputFile.length()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Output Size: ${FileUtils.formatFileSize(newSize)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (originalSize != null && originalSize > 0) {
                        val diff = originalSize - newSize
                        val percent = ((diff.toDouble() / originalSize.toDouble()) * 100).toInt()
                        if (percent > 0) {
                            Text(
                                text = "Reduced by $percent% (${FileUtils.formatFileSize(diff)} saved)",
                                style = MaterialTheme.typography.bodySmall.copy(color = accentText(SuccessGreen))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SaveButtons(file = outputFile, mimeType = saveMimeType, fileName = saveName)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (onOpenFile != null) {
                            onOpenFile()
                        } else {
                            FileUtils.openFile(context.applicationContext, outputFile)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }

            if (chainActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Continue with…",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chainActions.forEach { action ->
                        AssistChip(
                            onClick = action.onClick,
                            label = { Text(action.label, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = action.icon?.let { icon ->
                                {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProcessStateView(
    state: ProcessState,
    accentColor: Color = BraunOrange
) {
    when (state) {
        is ProcessState.Processing -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.progress != null) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = accentText(accentColor),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = accentColor,
                            strokeWidth = 3.dp
                        )
                    }

                    if (state.message != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
        is ProcessState.Error -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed)
                    )
                }
            }
        }
        else -> Unit
    }
}

@Composable
fun SaveModeSelector(
    saveMode: com.vedant.apparat.core.settings.SaveMode,
    onSaveModeChange: (com.vedant.apparat.core.settings.SaveMode) -> Unit,
    accentColor: Color = BraunOrange,
    allowOverwrite: Boolean = true,
    disallowedReason: String? = null
) {
    LaunchedEffect(allowOverwrite) {
        if (!allowOverwrite && saveMode == com.vedant.apparat.core.settings.SaveMode.OVERWRITE_ORIGINAL) {
            onSaveModeChange(com.vedant.apparat.core.settings.SaveMode.NEW_COPY)
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Output Save Mode",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.vedant.apparat.core.settings.SaveMode.entries.forEach { mode ->
                    val isSelected = saveMode == mode
                    val isEnabled = mode != com.vedant.apparat.core.settings.SaveMode.OVERWRITE_ORIGINAL || allowOverwrite
                    OutlinedButton(
                        onClick = { onSaveModeChange(mode) },
                        enabled = isEnabled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) accentColor else Color.Transparent,
                            contentColor = if (isSelected) onPrimaryForAccent(accentColor) else MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) accentColor else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            text = if (mode == com.vedant.apparat.core.settings.SaveMode.NEW_COPY) "New Copy" else "Replace Original",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (!allowOverwrite && disallowedReason != null) disallowedReason else saveMode.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (!allowOverwrite && disallowedReason != null) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
fun FormatSelectorRow(
    title: String = "Output Format",
    formats: List<String>,
    selectedFormat: String,
    onFormatSelect: (String) -> Unit,
    accentColor: Color = BraunOrange
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                formats.forEach { format ->
                    val isSelected = selectedFormat.equals(format, ignoreCase = true)
                    OutlinedButton(
                        onClick = { onFormatSelect(format) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) accentColor else Color.Transparent,
                            contentColor = if (isSelected) onPrimaryForAccent(accentColor) else MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, if (isSelected) accentColor else MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = format.uppercase(),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tells the user, before they run a tool, that a very large photo will be saved at a lower
 * resolution to fit this phone's memory. Shows nothing for photos within [maxPixels].
 */
@Composable
fun LargePhotoNotice(uri: android.net.Uri?, maxPixels: Long = com.vedant.apparat.engine.image.BitmapDecoder.DEFAULT_MAX_PIXELS) {
    val context = LocalContext.current
    var pixels by remember(uri) { mutableStateOf<Long?>(null) }
    LaunchedEffect(uri) {
        pixels = uri?.let {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.vedant.apparat.engine.image.BitmapDecoder.pixelCount(context, it)
            }
        }
    }
    val budget = remember(maxPixels) {
        com.vedant.apparat.engine.image.BitmapDecoder.effectivePixelBudget(context, maxPixels)
    }
    val total = pixels ?: return
    if (total <= budget) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "This photo is ${megapixels(total)} MP. It will be saved at about ${megapixels(budget)} MP to fit this phone's memory.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

private fun megapixels(pixels: Long): String = (pixels / 1_000_000.0).let { mp ->
    if (mp >= 10) mp.toInt().toString() else String.format(java.util.Locale.US, "%.1f", mp)
}
