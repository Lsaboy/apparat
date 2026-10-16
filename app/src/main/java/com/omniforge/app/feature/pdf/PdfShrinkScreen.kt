package com.omniforge.app.feature.pdf

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
import com.omniforge.app.core.settings.SaveMode
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.pdf.PdfEngine
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

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }

    var compressionLevel by retained(JOB_KEY, "compressionLevel") { mutableFloatStateOf(0.6f) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(SaveMode.NEW_COPY) }
    var processState by retained(JOB_KEY, "processState") {
        mutableStateOf<ProcessState>(if (initialUri != null) ProcessState.Processing(0f, "Opening PDF...") else ProcessState.Idle)
    }
    // Verified password for selectedUri ("" = none needed); null until PdfPasswordGate has checked.
    var pdfPassword by retained(JOB_KEY, "pdfPassword") { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pdfPassword = null
            processState = ProcessState.Processing(0f, "Opening PDF...")
        }
    }

    ToolScaffold(
        title = "Shrink & Compress PDF",
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

            PdfPasswordGate(
                uri = selectedUri,
                fileName = fileName,
                needsCheck = pdfPassword == null,
                accentColor = AccentPdf,
                onReady = { password ->
                    pdfPassword = password
                    processState = ProcessState.Idle
                },
                onFailed = { message ->
                    selectedUri = null
                    fileName = null
                    fileSize = null
                    processState = ProcessState.Error(message)
                }
            )

            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Image Downsample Quality: ${(compressionLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
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
                            inactiveTrackColor = DarkBorder
                        )
                    )
                    Text(
                        text = "Optimizes internal object streams and downscales embedded photos.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Compress PDF") {
                        processState = ProcessState.Processing(0.4f, "Compressing PDF streams...")
                        val result = PdfEngine.compressPdf(context, uri, compressionLevel, password = pdfPassword ?: "")
                        result.fold(
                            onSuccess = { outputFile ->
                                if (saveMode == SaveMode.OVERWRITE_ORIGINAL) {
                                    val overwritten = FileUtils.overwriteOriginal(context, uri, outputFile)
                                    if (overwritten) {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original PDF overwritten in-place!"
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Compressed! (Original protected, saved as copy)"
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "PDF compressed successfully!"
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf),
                enabled = selectedUri != null && pdfPassword != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compress PDF", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = "application/pdf",
                            displayName = "compressed_${fileName ?: "doc.pdf"}"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
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
