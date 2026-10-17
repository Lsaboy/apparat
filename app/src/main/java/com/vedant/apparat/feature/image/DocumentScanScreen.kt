package com.vedant.apparat.feature.image

import androidx.compose.foundation.BorderStroke
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vedant.apparat.core.jobs.ToolJobs
import com.vedant.apparat.core.jobs.retained
import com.vedant.apparat.core.model.ProcessState
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.ui.*
import com.vedant.apparat.core.util.FileUtils
import com.vedant.apparat.engine.image.ImageEngine
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "document_scan"

@Composable
fun DocumentScanScreen(
    initialUri: Uri? = null,
    onNavigateToTool: ((String, Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }

    var contrast by retained(JOB_KEY, "contrast") { mutableFloatStateOf(1.8f) }
    var isMonochrome by retained(JOB_KEY, "isMonochrome") { mutableStateOf(true) }
    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(null) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(initialUri) {
        if (initialUri == null) return@LaunchedEffect
        if (initialUri != lastHandledInitialUri) {
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                lastHandledInitialUri = initialUri
                pendingShareUri = null
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
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

    ToolScaffold(
        title = "Document & Receipt Enhancer",
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
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Incoming shared photo: $pendingName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enhancement is currently running on another photo. Switch to this new file or dismiss it.",
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
                                colors = ButtonDefaults.buttonColors(containerColor = AccentImage, contentColor = onPrimaryForAccent(AccentImage))
                            ) {
                                Text("Switch to New File", color = LocalContentColor.current, fontSize = 12.sp)
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
                placeholderText = "Select Photo of Document or Receipt",
                icon = Icons.Default.Image,
                accentColor = AccentImage,
                onClick = { picker.launch(arrayOf("image/*")) }
            )

            LargePhotoNotice(selectedUri, maxPixels = 16_000_000L)

            selectedUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .size(coil.size.Size(512, 512))
                            .build(),
                        contentDescription = "Document Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Contrast Boost: ${String.format(java.util.Locale.US, "%.1f", contrast)}x",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        valueRange = 1.0f..3.0f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            activeTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent,
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Monochrome / B&W Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Switch(
                            checked = isMonochrome,
                            onCheckedChange = { isMonochrome = it },
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    ToolJobs.launch(context, JOB_KEY, "Document Enhancer") { appContext ->
                        processState = ProcessState.Processing(0.5f, "Enhancing scan contrast...")
                        val result = ImageEngine.enhanceDocumentScan(
                            context = appContext,
                            inputUri = uri,
                            contrastMultiplier = contrast,
                            convertToMonochrome = isMonochrome
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Document enhanced into clean scan!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to enhance document")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentImage, contentColor = onPrimaryForAccent(AccentImage)),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enhance Document Scan", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentImage)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "scan", "_scanned", success.outputFile) },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile))
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
