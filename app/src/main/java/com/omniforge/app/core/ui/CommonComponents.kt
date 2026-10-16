package com.omniforge.app.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.settings.AppSettings
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.util.FileUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(
    title: String,
    categoryName: String,
    accentColor: Color,
    onBack: () -> Unit,
    /** Route of a tool that runs jobs through ToolJobs; enables keep-running-on-back handling. */
    jobKey: String? = null,
    content: @Composable (PaddingValues) -> Unit
) {
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
                Text("Keep it running in the background? You'll get a notification when it's done, and the result will be here when you come back.")
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
                                color = accentColor,
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
        },
        content = content
    )
}

/** Tells ToolJobs when this tool's screen is visible, hidden or closed. */
@Composable
private fun ToolJobLifecycle(jobKey: String) {
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

/** Asks once per app install/settings, when the first job starts, so progress/done notifications can show. */
@Composable
private fun NotificationPermissionOnFirstJob(isRunning: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val settings = remember(context) { AppSettings(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(isRunning) {
        if (isRunning && !settings.hasAskedNotificationPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            settings.hasAskedNotificationPermission = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun FileSelectionCard(
    fileName: String?,
    fileSize: Long?,
    placeholderText: String = "Tap to choose a file",
    icon: ImageVector = Icons.Default.Description,
    accentColor: Color = PrimaryTeal,
    fileDetails: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, if (fileName != null) accentColor.copy(alpha = 0.5f) else DarkBorder)
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
                    .clip(RoundedCornerShape(12.dp))
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
                            color = TextPrimary
                        ),
                        maxLines = 1
                    )
                    val detailsText = fileDetails ?: if (fileSize != null && fileSize > 0) FileUtils.formatFileSize(fileSize) else null
                    if (detailsText != null) {
                        Text(
                            text = detailsText,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                } else {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Icon(
                imageVector = if (fileName != null) Icons.Default.CheckCircle else Icons.Default.FileUpload,
                contentDescription = null,
                tint = if (fileName != null) accentColor else TextSecondary
            )
        }
    }
}

@Composable
fun ResultCard(
    outputFile: File,
    originalSize: Long? = null,
    message: String = "Processed successfully!",
    onSaveToDownloads: suspend () -> Unit,
    onShare: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = DarkSurfaceElevated,
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
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (originalSize != null && originalSize > 0) {
                        val diff = originalSize - newSize
                        val percent = ((diff.toDouble() / originalSize.toDouble()) * 100).toInt()
                        if (percent > 0) {
                            Text(
                                text = "Reduced by $percent% (${FileUtils.formatFileSize(diff)} saved)",
                                style = MaterialTheme.typography.bodySmall.copy(color = SuccessGreen)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        scope.launch {
                            try {
                                onSaveToDownloads()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AmoledBlack
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = AmoledBlack
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSaving) "Saving…" else "Save", color = AmoledBlack, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TextSecondary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ProcessStateView(
    state: ProcessState,
    accentColor: Color = PrimaryTeal
) {
    when (state) {
        is ProcessState.Processing -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accentColor,
                            trackColor = DarkBorder
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accentColor,
                            trackColor = DarkBorder
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            }
        }
        is ProcessState.Error -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.7f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
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
    saveMode: com.omniforge.app.core.settings.SaveMode,
    onSaveModeChange: (com.omniforge.app.core.settings.SaveMode) -> Unit,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    allowOverwrite: Boolean = true,
    disallowedReason: String? = null
) {
    LaunchedEffect(allowOverwrite) {
        if (!allowOverwrite && saveMode == com.omniforge.app.core.settings.SaveMode.OVERWRITE_ORIGINAL) {
            onSaveModeChange(com.omniforge.app.core.settings.SaveMode.NEW_COPY)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Output Save Mode",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.omniforge.app.core.settings.SaveMode.entries.forEach { mode ->
                    val isSelected = saveMode == mode
                    val isEnabled = mode != com.omniforge.app.core.settings.SaveMode.OVERWRITE_ORIGINAL || allowOverwrite
                    OutlinedButton(
                        onClick = { onSaveModeChange(mode) },
                        enabled = isEnabled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) accentColor else Color.Transparent,
                            contentColor = if (isSelected) AmoledBlack else TextPrimary,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = TextSecondary.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) accentColor else DarkBorder
                        )
                    ) {
                        Text(
                            text = if (mode == com.omniforge.app.core.settings.SaveMode.NEW_COPY) "New Copy" else "Modify OG",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (!allowOverwrite && disallowedReason != null) disallowedReason else saveMode.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (!allowOverwrite && disallowedReason != null) ErrorRed else TextSecondary,
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
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) accentColor else Color.Transparent,
                            contentColor = if (isSelected) AmoledBlack else TextPrimary
                        ),
                        border = BorderStroke(1.dp, if (isSelected) accentColor else DarkBorder)
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
