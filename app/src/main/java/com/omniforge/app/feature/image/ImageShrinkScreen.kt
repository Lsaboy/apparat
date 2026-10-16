package com.omniforge.app.feature.image

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "image_shrink"

@Composable
fun ImageShrinkScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }

    var outputFormat by retained(JOB_KEY, "outputFormat") { mutableStateOf(ImageOutputFormat.JPEG) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(AppSettings(context).defaultSaveMode) }

    var quality by retained(JOB_KEY, "quality") { mutableFloatStateOf(75f) }
    var maxDimension by retained(JOB_KEY, "maxDimension") { mutableFloatStateOf(1920f) }
    var stripExif by retained(JOB_KEY, "stripExif") { mutableStateOf(true) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            processState = ProcessState.Idle
        }
    }

    val isFormatMismatch = selectedUri != null && !FileUtils.isImageFormatMatching(context, selectedUri!!, outputFormat)
    val allowOverwrite = !isFormatMismatch
    val disallowedReason = if (isFormatMismatch) {
        val inputExt = fileName?.substringAfterLast('.', "")?.uppercase() ?: "original"
        "Cannot modify original: format differs ($inputExt → ${outputFormat.name})"
    } else null

    ToolScaffold(
        title = "Shrink Image",
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

            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                placeholderText = "Select Photo to Shrink",
                icon = Icons.Default.Image,
                accentColor = AccentImage,
                onClick = { picker.launch(arrayOf("image/*")) }
            )

            // Format Selection Row
            FormatSelectorRow(
                title = "Output Format",
                formats = listOf("jpg", "webp", "png"),
                selectedFormat = outputFormat.extension.removePrefix("."),
                onFormatSelect = { ext ->
                    outputFormat = when (ext.lowercase()) {
                        "png" -> ImageOutputFormat.PNG
                        "webp" -> ImageOutputFormat.WEBP
                        else -> ImageOutputFormat.JPEG
                    }
                },
                accentColor = AccentImage
            )

            // Save Mode Preference
            SaveModeSelector(
                saveMode = saveMode,
                onSaveModeChange = { saveMode = it },
                accentColor = AccentImage,
                allowOverwrite = allowOverwrite,
                disallowedReason = disallowedReason
            )

            // Compression Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val isLosslessPng = outputFormat == ImageOutputFormat.PNG
                    Text(
                        text = if (isLosslessPng) "Compression Quality: Lossless (PNG)" else "Compression Quality: ${quality.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isLosslessPng) TextSecondary else TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..95f,
                        steps = 16,
                        enabled = !isLosslessPng,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Max Resolution Dimension: ${maxDimension.toInt()}px",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = maxDimension,
                        onValueChange = { maxDimension = it },
                        valueRange = 720f..3840f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strip EXIF / Location",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Wipes GPS coords and device tags",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = stripExif,
                            onCheckedChange = { stripExif = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AmoledBlack,
                                checkedTrackColor = AccentImage
                            )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Shrink Image") { appContext ->
                        processState = ProcessState.Processing(0.5f, "Optimizing photo...")
                        val result = ImageEngine.shrinkImage(
                            context = appContext,
                            inputUri = uri,
                            quality = quality.toInt(),
                            maxDimension = maxDimension.toInt(),
                            stripExif = stripExif,
                            targetFormat = outputFormat
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                val origBytes = if ((fileSize ?: 0L) > 0L) fileSize!! else {
                                    runCatching { appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: 0L
                                }
                                val newBytes = outputFile.length()

                                if (origBytes > 0L && newBytes >= origBytes) {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Image is already optimized (${FileUtils.formatFileSize(newBytes)}) — could not reduce size further."
                                    )
                                } else if (saveMode == SaveMode.OVERWRITE_ORIGINAL && allowOverwrite) {
                                    val overwritten = FileUtils.overwriteOriginal(appContext, uri, outputFile)
                                    if (overwritten) {
                                        fileSize = newBytes
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original photo overwritten in-place!"
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Processed! (Original is read-only — tap Save below to save copy)"
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Photo shrunk successfully!"
                                    )
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to shrink photo")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentImage),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shrink Photo", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentImage)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val base = (fileName ?: "photo").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = outputFormat.mimeType,
                            displayName = "${base}_shrunk${outputFormat.extension}"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, outputFormat.mimeType)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
