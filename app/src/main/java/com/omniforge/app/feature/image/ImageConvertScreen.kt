package com.omniforge.app.feature.image

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.settings.AppSettings
import com.omniforge.app.core.settings.SaveMode
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.image.ImageEngine
import com.omniforge.app.engine.image.ImageOutputFormat
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import com.omniforge.app.engine.image.BitmapDecoder
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "image_convert"

@Composable
fun ImageConvertScreen(
    initialUri: Uri? = null,
    onNavigateToTool: ((route: String, uri: Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(initialUri) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var targetFormat by retained(JOB_KEY, "targetFormat") { mutableStateOf(ImageOutputFormat.PNG) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(AppSettings(context).defaultSaveMode) }
    var quality by retained(JOB_KEY, "quality") { mutableFloatStateOf(90f) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != lastHandledInitialUri) {
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
                lastHandledInitialUri = initialUri
                pendingShareUri = null
                processState = ProcessState.Idle
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pendingShareUri = null
            processState = ProcessState.Idle
        }
    }

    val isAnimated = remember(selectedUri) {
        selectedUri?.let { runCatching { BitmapDecoder.isAnimated(context, it) }.getOrDefault(false) } ?: false
    }

    val isFormatMismatch = selectedUri != null && !FileUtils.isImageFormatMatching(context, selectedUri!!, targetFormat)
    val allowOverwrite = !isFormatMismatch
    val disallowedReason = if (isFormatMismatch) {
        val inputExt = fileName?.substringAfterLast('.', "")?.uppercase() ?: "original"
        "Cannot modify original: format differs ($inputExt → ${targetFormat.name})"
    } else null

    ToolScaffold(
        title = "Convert Image",
        categoryName = "Photo & Image",
        accentColor = AccentImage,
        onBack = onBack,
        jobKey = JOB_KEY
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            pendingShareUri?.let { pendingUri ->
                val pendingName = remember(pendingUri) { FileUtils.getFileName(context, pendingUri) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Incoming shared file: $pendingName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "A job is currently running on another file. Switch to this new file or dismiss it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    ToolJobs.cancel(JOB_KEY)
                                    selectedUri = pendingUri
                                    fileName = pendingName
                                    fileSize = FileUtils.getFileSize(context, pendingUri)
                                    lastHandledInitialUri = pendingUri
                                    pendingShareUri = null
                                    processState = ProcessState.Idle
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentImage)
                            ) {
                                Text("Switch to New File", color = onPrimaryForAccent(AccentImage), fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    lastHandledInitialUri = pendingUri
                                    pendingShareUri = null
                                }
                            ) {
                                Text("Dismiss", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                placeholderText = "Select Photo to Convert",
                icon = Icons.Default.Image,
                accentColor = AccentImage,
                onClick = { picker.launch(arrayOf("image/*")) }
            )

            if (isAnimated) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AccentImage,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: Animated images are processed as a still frame.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Format Selection
            FormatSelectorRow(
                title = "Target Format",
                formats = listOf("png", "jpg", "webp"),
                selectedFormat = targetFormat.extension.removePrefix("."),
                onFormatSelect = { ext ->
                    targetFormat = when (ext.lowercase()) {
                        "png" -> ImageOutputFormat.PNG
                        "webp" -> ImageOutputFormat.WEBP
                        else -> ImageOutputFormat.JPEG
                    }
                },
                accentColor = AccentImage
            )

            // Save Mode
            SaveModeSelector(
                saveMode = saveMode,
                onSaveModeChange = { saveMode = it },
                accentColor = AccentImage,
                allowOverwrite = allowOverwrite,
                disallowedReason = disallowedReason
            )

            if (targetFormat != ImageOutputFormat.PNG) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val isLosslessWebp = targetFormat == ImageOutputFormat.WEBP && quality >= 100f
                        val qualityLabel = if (isLosslessWebp) "Quality: 100% (Lossless WEBP)" else "Quality: ${quality.toInt()}%"
                        Text(
                            text = qualityLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Slider(
                            value = quality,
                            onValueChange = { quality = it },
                            valueRange = 10f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentImage,
                                activeTrackColor = AccentImage,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Convert Image") { appContext ->
                        processState = ProcessState.Processing(0.5f, "Converting photo format...")
                        val result = ImageEngine.convertFormat(
                            context = appContext,
                            inputUri = uri,
                            targetFormat = targetFormat,
                            quality = quality.toInt()
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                if (saveMode == SaveMode.OVERWRITE_ORIGINAL && allowOverwrite) {
                                    val overwritten = FileUtils.overwriteOriginal(appContext, uri, outputFile)
                                    if (overwritten) {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original overwritten with new format!"
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Converted! (Original is read-only — tap Save below to save copy)"
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Converted to ${targetFormat.name}!"
                                    )
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to convert")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentImage),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = onPrimaryForAccent(AccentImage))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert to ${targetFormat.name}", color = onPrimaryForAccent(AccentImage), fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentImage)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val appContext = context.applicationContext
                        val baseName = (fileName ?: "photo").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = appContext,
                            file = success.outputFile,
                            mimeType = targetFormat.mimeType,
                            displayName = "${baseName}_converted${targetFormat.extension}"
                        )
                        Toast.makeText(appContext, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, targetFormat.mimeType)
                    },
                    chainActions = if (onNavigateToTool != null) {
                        listOf(
                            ToolChainAction(
                                label = "Shrink Photo",
                                icon = Icons.Default.Compress,
                                onClick = {
                                    val uri = FileUtils.getContentUri(context, success.outputFile)
                                    onNavigateToTool("image_shrink", uri)
                                }
                            )
                        )
                    } else emptyList()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
