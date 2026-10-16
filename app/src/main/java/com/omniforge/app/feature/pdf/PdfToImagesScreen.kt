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
import androidx.compose.material.icons.filled.Collections
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
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.pdf.PdfEngine
import kotlinx.coroutines.launch
import java.io.File

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_to_images"

@Composable
fun PdfToImagesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }

    var renderedPages by retained(JOB_KEY, "renderedPages") { mutableStateOf<List<File>>(emptyList()) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }
    // Verified password for selectedUri ("" = none needed); null until PdfPasswordGate has checked.
    var pdfPassword by retained(JOB_KEY, "pdfPassword") { mutableStateOf<String?>(null) }
    var pageCount by retained(JOB_KEY, "pageCount") { mutableStateOf<Int?>(null) }
    var saveProgress by retained(JOB_KEY, "saveProgress") { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pdfPassword = null
            pageCount = null
            processState = ProcessState.Processing(0f, "Opening PDF...")
            renderedPages = emptyList()
        }
    }

    ToolScaffold(
        title = "PDF to Images",
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
                placeholderText = "Select PDF Document",
                icon = Icons.Default.PictureAsPdf,
                accentColor = AccentPdf,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "PDF to Images") { appContext ->
                        processState = ProcessState.Processing(0.3f, "Rendering pages into PNG images...")
                        val result = PdfEngine.pdfToImages(appContext, uri, password = pdfPassword ?: "")
                        result.fold(
                            onSuccess = { files ->
                                renderedPages = files
                                if (files.isNotEmpty()) {
                                    processState = ProcessState.Success(
                                        outputFile = files.first(),
                                        message = "Extracted ${files.size} pages as high-res images!"
                                    )
                                } else {
                                    processState = ProcessState.Error("No pages found in PDF")
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to render PDF")
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
                Icon(imageVector = Icons.Default.Collections, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extract All Pages to Images", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (renderedPages.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${renderedPages.size} Pages Rendered Successfully",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (saveProgress != null) return@Button
                                    val files = renderedPages.toList()
                                    val base = (fileName ?: "doc").substringBeforeLast(".")
                                    val appContext = context.applicationContext
                                    val total = files.size
                                    scope.launch {
                                        try {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                                var count = 0
                                                for ((idx, file) in files.withIndex()) {
                                                    saveProgress = "Saving ${idx + 1} of $total…"
                                                    val uri = FileUtils.saveToDownloads(
                                                        appContext,
                                                        file,
                                                        "image/png",
                                                        "${base}_page_${idx + 1}.png"
                                                    )
                                                    if (uri != null) count++
                                                }
                                                Toast.makeText(appContext, "Saved $count of $total images to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}", Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            saveProgress = null
                                        }
                                    }
                                },
                                enabled = saveProgress == null,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(saveProgress ?: "Save All Images", color = AmoledBlack, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (renderedPages.isNotEmpty()) {
                                        FileUtils.shareFile(context, renderedPages.first(), "image/png")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Share First Page", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
