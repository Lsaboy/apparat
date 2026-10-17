package com.vedant.apparat.feature.image

import androidx.compose.foundation.BorderStroke
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vedant.apparat.core.jobs.ToolJobs
import com.vedant.apparat.core.jobs.retained
import com.vedant.apparat.core.model.ProcessState
import com.vedant.apparat.core.settings.AppSettings
import com.vedant.apparat.core.settings.SaveMode
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.ui.*
import com.vedant.apparat.core.util.FileUtils
import com.vedant.apparat.engine.image.ImageEngine
import com.vedant.apparat.engine.image.ImageOutputFormat
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.unit.sp
import com.vedant.apparat.engine.image.BitmapDecoder
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "image_shrink"

private const val MIN_TARGET_KB = 5
private const val MAX_TARGET_KB = 20000
private val TARGET_KB_PRESETS = listOf(20, 50, 100, 200, 500)

/** Largest side kept when decoding for a size target; the search shrinks further only if it must. */
private const val TARGET_MODE_MAX_DIMENSION = 4096

@Composable
fun ImageShrinkScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(initialUri) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }

    var outputFormat by retained(JOB_KEY, "outputFormat") { mutableStateOf(ImageOutputFormat.JPEG) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(AppSettings(context).defaultSaveMode) }

    var quality by retained(JOB_KEY, "quality") { mutableFloatStateOf(75f) }
    var maxDimension by retained(JOB_KEY, "maxDimension") { mutableFloatStateOf(1920f) }
    var stripExif by retained(JOB_KEY, "stripExif") { mutableStateOf(true) }

    // "Target size" mode: shrink to under N KB (portal upload limits) instead of picking sliders.
    var targetMode by retained(JOB_KEY, "targetMode") { mutableStateOf(false) }
    var targetKbText by retained(JOB_KEY, "targetKbText") { mutableStateOf("100") }
    val targetKb = targetKbText.toIntOrNull()?.takeIf { it in MIN_TARGET_KB..MAX_TARGET_KB }

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

    // Target-size output is always JPEG (the only format whose size the quality slider controls).
    val effectiveFormat = if (targetMode) ImageOutputFormat.JPEG else outputFormat

    val isFormatMismatch = selectedUri != null && !FileUtils.isImageFormatMatching(context, selectedUri!!, effectiveFormat)
    val allowOverwrite = !isFormatMismatch
    val disallowedReason = if (isFormatMismatch) {
        val inputExt = fileName?.substringAfterLast('.', "")?.uppercase() ?: "original"
        "Cannot modify original: format differs ($inputExt → ${effectiveFormat.name})"
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
                placeholderText = "Select Photo to Shrink",
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

            // Mode: manual sliders, or "make it under N KB"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !targetMode,
                    onClick = { targetMode = false },
                    label = { Text("Manual", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentImage,
                        selectedLabelColor = onPrimaryForAccent(AccentImage)
                    )
                )
                FilterChip(
                    selected = targetMode,
                    onClick = { targetMode = true },
                    label = { Text("Target size (KB)", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentImage,
                        selectedLabelColor = onPrimaryForAccent(AccentImage)
                    )
                )
            }

            if (!targetMode) {
            // Quick Presets
            val presets = remember {
                listOf(
                    Triple("Portal / ID (<100KB)", 1000f, Pair(70f, ImageOutputFormat.JPEG)),
                    Triple("WhatsApp / Chat", 1600f, Pair(80f, ImageOutputFormat.WEBP)),
                    Triple("Email / Social", 1920f, Pair(85f, ImageOutputFormat.JPEG)),
                    Triple("Max Quality", 3840f, Pair(95f, ImageOutputFormat.JPEG))
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (name, dim, settings) ->
                        val (q, fmt) = settings
                        val isSelected = maxDimension == dim && quality == q && outputFormat == fmt
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                maxDimension = dim
                                quality = q
                                outputFormat = fmt
                            },
                            label = { Text(name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentImage,
                                selectedLabelColor = onPrimaryForAccent(AccentImage)
                            )
                        )
                    }
                }
            }

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

            }

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
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (targetMode) {
                        Text(
                            text = "Shrink to under",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
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
                            TARGET_KB_PRESETS.forEach { kb ->
                                FilterChip(
                                    selected = targetKb == kb,
                                    onClick = { targetKbText = kb.toString() },
                                    label = { Text("$kb KB", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentImage,
                                        selectedLabelColor = onPrimaryForAccent(AccentImage)
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetKbText,
                            onValueChange = { targetKbText = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Custom size (KB)") },
                            singleLine = true,
                            isError = targetKb == null,
                            supportingText = {
                                Text(
                                    if (targetKb == null) "Enter a size from $MIN_TARGET_KB to $MAX_TARGET_KB KB"
                                    else "Keeps full resolution when it can and only reduces dimensions if quality alone can't reach the size. Saved as JPEG.",
                                    color = if (targetKb == null) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                    val isLosslessPng = outputFormat == ImageOutputFormat.PNG
                    val isLosslessWebp = outputFormat == ImageOutputFormat.WEBP && quality >= 100f
                    val qualityLabel = when {
                        isLosslessPng -> "Compression Quality: Lossless (PNG)"
                        isLosslessWebp -> "Compression Quality: 100% (Lossless WEBP)"
                        else -> "Compression Quality: ${quality.toInt()}%"
                    }
                    Text(
                        text = qualityLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isLosslessPng) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..100f,
                        steps = 17,
                        enabled = !isLosslessPng,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Max Resolution Dimension: ${maxDimension.toInt()}px",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = maxDimension,
                        onValueChange = { maxDimension = it },
                        valueRange = 720f..3840f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    }

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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Wipes GPS coords and device tags",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        Switch(
                            checked = stripExif,
                            onCheckedChange = { stripExif = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onPrimaryForAccent(AccentImage),
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
                        val limitKb = if (targetMode) targetKb else null
                        val result = if (limitKb != null) {
                            ImageEngine.shrinkToTargetKb(
                                context = appContext,
                                inputUri = uri,
                                targetKb = limitKb,
                                maxDimension = TARGET_MODE_MAX_DIMENSION,
                                stripExif = stripExif
                            )
                        } else {
                            ImageEngine.shrinkImage(
                                context = appContext,
                                inputUri = uri,
                                quality = quality.toInt(),
                                maxDimension = maxDimension.toInt(),
                                stripExif = stripExif,
                                targetFormat = outputFormat
                            )
                        }
                        result.fold(
                            onSuccess = { outputFile ->
                                val origBytes = if ((fileSize ?: 0L) > 0L) fileSize!! else {
                                    runCatching { appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: 0L
                                }
                                val newBytes = outputFile.length()

                                if (limitKb != null && newBytes > limitKb * 1000L) {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Could not reach $limitKb KB: the smallest this photo gets is ${FileUtils.formatFileSize(newBytes)}. Try a bigger target."
                                    )
                                } else if (origBytes > 0L && newBytes >= origBytes) {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = if (limitKb != null) {
                                            "Already under $limitKb KB (${FileUtils.formatFileSize(origBytes)}): no shrinking needed."
                                        } else {
                                            "Image is already optimized (${FileUtils.formatFileSize(newBytes)}) — could not reduce size further."
                                        }
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
                                        message = if (limitKb != null) {
                                            "Shrunk to ${FileUtils.formatFileSize(newBytes)} (limit $limitKb KB)."
                                        } else {
                                            "Photo shrunk successfully!"
                                        }
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
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentImage),
                enabled = selectedUri != null && processState !is ProcessState.Processing && (!targetMode || targetKb != null)
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = onPrimaryForAccent(AccentImage))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shrink Photo", color = onPrimaryForAccent(AccentImage), fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentImage)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                // Describe the file that was actually produced, not the current mode selection.
                val resultFormat = ImageOutputFormat.entries.firstOrNull { success.outputFile.name.endsWith(it.extension) } ?: effectiveFormat
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val appContext = context.applicationContext
                        val base = (fileName ?: "photo").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = appContext,
                            file = success.outputFile,
                            mimeType = resultFormat.mimeType,
                            displayName = "${base}_shrunk${resultFormat.extension}"
                        )
                        Toast.makeText(appContext, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, resultFormat.mimeType)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
