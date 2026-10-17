package com.omniforge.app.feature.pdf

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PictureAsPdf
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
import com.omniforge.app.engine.pdf.PdfEngine
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_shrink"

@Composable
fun PdfShrinkScreen(
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

    // Verified password for selectedUri ("" = none needed); null until PdfPasswordGate has checked.
    var pdfPassword by retained(JOB_KEY, "pdfPassword") { mutableStateOf<String?>(null) }
    var pageCount by retained(JOB_KEY, "pageCount") { mutableStateOf<Int?>(null) }

    var compressionLevel by retained(JOB_KEY, "compressionLevel") { mutableFloatStateOf(0.6f) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(AppSettings(context).defaultSaveMode) }
    var processState by retained(JOB_KEY, "processState") {
        mutableStateOf<ProcessState>(if (initialUri != null) ProcessState.Processing(0f, "Opening PDF...") else ProcessState.Idle)
    }

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
                pdfPassword = null
                pageCount = null
                processState = ProcessState.Processing(0f, "Opening PDF...")
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pendingShareUri = null
            pdfPassword = null
            pageCount = null
            processState = ProcessState.Processing(0f, "Opening PDF...")
        }
    }

    ToolScaffold(
        title = "Compress PDF",
        categoryName = "PDF Tools",
        accentColor = AccentPdf,
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
                                    pdfPassword = null
                                    pageCount = null
                                    processState = ProcessState.Processing(0f, "Opening PDF...")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf)
                            ) {
                                Text("Switch to New File", color = onPrimaryForAccent(AccentPdf), fontSize = 12.sp)
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

            PdfPasswordGate(
                uri = selectedUri,
                fileName = fileName,
                needsCheck = pdfPassword == null,
                accentColor = AccentPdf,
                onReady = { password, count ->
                    pdfPassword = password
                    pageCount = count
                    processState = ProcessState.Idle
                },
                onFailed = { message ->
                    selectedUri = null
                    fileName = null
                    fileSize = null
                    pageCount = null
                    processState = ProcessState.Error(message)
                }
            )

            val details = when {
                pageCount != null && fileSize != null && fileSize!! > 0 ->
                    "$pageCount ${if (pageCount == 1) "page" else "pages"} • ${FileUtils.formatFileSize(fileSize!!)}"
                pageCount != null -> "$pageCount ${if (pageCount == 1) "page" else "pages"}"
                fileSize != null && fileSize!! > 0 -> FileUtils.formatFileSize(fileSize!!)
                else -> null
            }

            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                fileDetails = details,
                placeholderText = "Select PDF to Compress",
                icon = Icons.Default.PictureAsPdf,
                accentColor = AccentPdf,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )

            // Save Mode
            SaveModeSelector(
                saveMode = saveMode,
                onSaveModeChange = { saveMode = it },
                accentColor = AccentPdf
            )

            // Compression slider
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Image Downsample Quality: ${(compressionLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = compressionLevel,
                        onValueChange = { compressionLevel = it },
                        valueRange = 0.2f..0.9f,
                        steps = 6,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPdf,
                            activeTrackColor = AccentPdf,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Text(
                        text = "Optimizes internal object streams and downscales embedded photos.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Compress PDF") { appContext ->
                        processState = ProcessState.Processing(0.4f, "Compressing PDF streams...")
                        val result = PdfEngine.compressPdf(appContext, uri, compressionLevel, password = pdfPassword ?: "")
                        result.fold(
                            onSuccess = { outputFile ->
                                val origBytes = if ((fileSize ?: 0L) > 0L) fileSize!! else {
                                    runCatching { appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: 0L
                                }
                                val newBytes = outputFile.length()

                                val passwordSuffix = if (!pdfPassword.isNullOrEmpty()) " (Original password kept)" else ""

                                if (origBytes > 0L && newBytes >= origBytes) {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "PDF is already optimized (${FileUtils.formatFileSize(newBytes)}) — could not reduce size further.$passwordSuffix"
                                    )
                                } else if (saveMode == SaveMode.OVERWRITE_ORIGINAL) {
                                    val overwritten = FileUtils.overwriteOriginal(appContext, uri, outputFile)
                                    if (overwritten) {
                                        fileSize = newBytes
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original PDF overwritten in-place!$passwordSuffix"
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Compressed! (Original is read-only — tap Save below to save copy)$passwordSuffix"
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "PDF compressed successfully!$passwordSuffix"
                                    )
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to compress PDF")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf),
                enabled = selectedUri != null && pdfPassword != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = onPrimaryForAccent(AccentPdf))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compress PDF", color = onPrimaryForAccent(AccentPdf), fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val appContext = context.applicationContext
                        val saved = FileUtils.saveToDownloads(
                            context = appContext,
                            file = success.outputFile,
                            mimeType = "application/pdf",
                            displayName = "compressed_${fileName ?: "doc.pdf"}"
                        )
                        Toast.makeText(appContext, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, "application/pdf")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
