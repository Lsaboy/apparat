package com.omniforge.app.feature.pdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.pdf.PdfEngine
import kotlinx.coroutines.launch
import java.io.File

enum class SplitMode(val label: String) {
    CUSTOM_RANGES("Custom Ranges"),
    EVERY_N_PAGES("Every N Pages"),
    SINGLE_RANGE("Single Range")
}

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_split"

@Composable
fun PdfSplitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }

    var splitMode by retained(JOB_KEY, "splitMode") { mutableStateOf(SplitMode.CUSTOM_RANGES) }
    var customRangesText by retained(JOB_KEY, "customRangesText") { mutableStateOf("1-3, 4-6") }
    var intervalPagesText by retained(JOB_KEY, "intervalPagesText") { mutableStateOf("1") }
    var startPageText by retained(JOB_KEY, "startPageText") { mutableStateOf("1") }
    var endPageText by retained(JOB_KEY, "endPageText") { mutableStateOf("3") }

    var generatedFiles by retained(JOB_KEY, "generatedFiles") { mutableStateOf<List<File>>(emptyList()) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }
    // Verified password for selectedUri ("" = none needed); null until PdfPasswordGate has checked.
    var pdfPassword by retained(JOB_KEY, "pdfPassword") { mutableStateOf<String?>(null) }
    var saveProgress by retained(JOB_KEY, "saveProgress") { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pdfPassword = null
            processState = ProcessState.Processing(0f, "Opening PDF...")
            generatedFiles = emptyList()
        }
    }

    ToolScaffold(
        title = "Split PDF / Multi-File Split",
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
                placeholderText = "Select PDF Document to Split",
                icon = Icons.Default.PictureAsPdf,
                accentColor = AccentPdf,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )

            // Split Mode Selector
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Split Strategy",
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
                        SplitMode.entries.forEach { mode ->
                            val isSelected = splitMode == mode
                            OutlinedButton(
                                onClick = { splitMode = mode },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentPdf else DarkSurfaceElevated,
                                    contentColor = if (isSelected) AmoledBlack else TextPrimary
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentPdf else DarkBorder)
                            ) {
                                Text(
                                    text = mode.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when (splitMode) {
                        SplitMode.CUSTOM_RANGES -> {
                            OutlinedTextField(
                                value = customRangesText,
                                onValueChange = { customRangesText = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Comma-separated page ranges", color = TextSecondary) },
                                placeholder = { Text("e.g. 1-2, 3-5, 6-10", color = TextTertiary) },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentPdf,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Creates a distinct PDF file for each specified range.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                        SplitMode.EVERY_N_PAGES -> {
                            OutlinedTextField(
                                value = intervalPagesText,
                                onValueChange = { intervalPagesText = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Pages per PDF chunk", color = TextSecondary) },
                                placeholder = { Text("e.g. 1 for single-page PDFs", color = TextTertiary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentPdf,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Splits document into chunks of N pages (use 1 for single-page PDFs).",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                        SplitMode.SINGLE_RANGE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = startPageText,
                                    onValueChange = { startPageText = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("Start Page", color = TextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentPdf,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                OutlinedTextField(
                                    value = endPageText,
                                    onValueChange = { endPageText = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("End Page", color = TextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentPdf,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Split PDF") {
                        processState = ProcessState.Processing(0.3f, "Splitting PDF document...")
                        val ranges: List<IntRange> = when (splitMode) {
                            SplitMode.CUSTOM_RANGES -> {
                                parseCustomRanges(customRangesText)
                            }
                            SplitMode.EVERY_N_PAGES -> {
                                val n = intervalPagesText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                // 100 pages upper estimate chunked
                                (1..100 step n).map { it..(it + n - 1) }
                            }
                            SplitMode.SINGLE_RANGE -> {
                                val s = startPageText.toIntOrNull() ?: 1
                                val e = endPageText.toIntOrNull() ?: s
                                listOf(s..e)
                            }
                        }

                        if (ranges.isEmpty()) {
                            processState = ProcessState.Error("Please enter valid page ranges")
                            return@launch
                        }

                        val result = PdfEngine.multiSplitPdf(context, uri, ranges, password = pdfPassword ?: "")
                        result.fold(
                            onSuccess = { files ->
                                generatedFiles = files
                                if (files.isNotEmpty()) {
                                    processState = ProcessState.Success(
                                        outputFile = files.first(),
                                        message = "Split into ${files.size} PDF files successfully!"
                                    )
                                } else {
                                    processState = ProcessState.Error("No files generated")
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to split PDF")
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
                Icon(imageVector = Icons.Default.CallSplit, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Split Document", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (generatedFiles.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${generatedFiles.size} Split Files Generated",
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
                                    val files = generatedFiles.toList()
                                    val base = (fileName ?: "doc").substringBeforeLast(".")
                                    scope.launch {
                                        var count = 0
                                        try {
                                            for ((idx, file) in files.withIndex()) {
                                                saveProgress = "Saving ${idx + 1} of ${files.size}…"
                                                val uri = FileUtils.saveToDownloads(
                                                    context,
                                                    file,
                                                    "application/pdf",
                                                    "${base}_part_${idx + 1}.pdf"
                                                )
                                                if (uri != null) count++
                                            }
                                        } finally {
                                            saveProgress = null
                                        }
                                        Toast.makeText(context, "Saved $count PDF files to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = saveProgress == null,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(saveProgress ?: "Save All Files", color = AmoledBlack, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    if (generatedFiles.isNotEmpty()) {
                                        FileUtils.shareFile(context, generatedFiles.first(), "application/pdf")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Share First File", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun parseCustomRanges(text: String): List<IntRange> {
    val results = mutableListOf<IntRange>()
    val tokens = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    for (token in tokens) {
        if (token.contains("-")) {
            val parts = token.split("-").map { it.trim() }
            val s = parts[0].toIntOrNull() ?: continue
            val e = parts.getOrNull(1)?.toIntOrNull() ?: s
            if (s <= e) results.add(s..e)
        } else {
            val p = token.toIntOrNull() ?: continue
            results.add(p..p)
        }
    }
    return results
}
