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
            generatedFiles = emptyList()
        }
    }

    val currentValidation = when (splitMode) {
        SplitMode.CUSTOM_RANGES -> validateCustomRanges(customRangesText, pageCount)
        SplitMode.EVERY_N_PAGES -> validateEveryN(intervalPagesText, pageCount)
        SplitMode.SINGLE_RANGE -> validateSingleRange(startPageText, endPageText, pageCount)
    }

    ToolScaffold(
        title = "Split PDF",
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
                                isError = currentValidation is RangeValidation.Invalid,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentPdf,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    errorBorderColor = ErrorRed
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            when (currentValidation) {
                                is RangeValidation.Invalid -> {
                                    Text(
                                        text = currentValidation.errorMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontSize = 11.sp)
                                    )
                                }
                                is RangeValidation.Valid -> {
                                    Text(
                                        text = if (pageCount != null) currentValidation.previewText else "Creates a distinct PDF file for each specified range.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (pageCount != null) AccentPdf else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                        SplitMode.EVERY_N_PAGES -> {
                            OutlinedTextField(
                                value = intervalPagesText,
                                onValueChange = { intervalPagesText = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Pages per PDF chunk", color = TextSecondary) },
                                placeholder = { Text("e.g. 1 for single-page PDFs", color = TextTertiary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = currentValidation is RangeValidation.Invalid,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentPdf,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    errorBorderColor = ErrorRed
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            when (currentValidation) {
                                is RangeValidation.Invalid -> {
                                    Text(
                                        text = currentValidation.errorMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontSize = 11.sp)
                                    )
                                }
                                is RangeValidation.Valid -> {
                                    Text(
                                        text = if (pageCount != null) currentValidation.previewText else "Splits document into chunks of N pages (use 1 for single-page PDFs).",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (pageCount != null) AccentPdf else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
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
                                    isError = currentValidation is RangeValidation.Invalid,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentPdf,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        errorBorderColor = ErrorRed
                                    )
                                )

                                OutlinedTextField(
                                    value = endPageText,
                                    onValueChange = { endPageText = it.filter { c -> c.isDigit() } },
                                    modifier = Modifier.weight(1f),
                                    label = { Text("End Page", color = TextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = currentValidation is RangeValidation.Invalid,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AccentPdf,
                                        unfocusedBorderColor = DarkBorder,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        errorBorderColor = ErrorRed
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            when (currentValidation) {
                                is RangeValidation.Invalid -> {
                                    Text(
                                        text = currentValidation.errorMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontSize = 11.sp)
                                    )
                                }
                                is RangeValidation.Valid -> {
                                    Text(
                                        text = currentValidation.previewText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (pageCount != null) AccentPdf else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val validRanges = (currentValidation as? RangeValidation.Valid)?.ranges ?: emptyList()
                    if (validRanges.isEmpty()) {
                        processState = ProcessState.Error("Please enter valid page ranges")
                        return@Button
                    }

                    ToolJobs.launch(context, JOB_KEY, "Split PDF") { appContext ->
                        processState = ProcessState.Processing(0.3f, "Splitting PDF document...")
                        val result = PdfEngine.multiSplitPdf(appContext, uri, validRanges, password = pdfPassword ?: "")
                        result.fold(
                            onSuccess = { files ->
                                generatedFiles = files
                                if (files.isNotEmpty()) {
                                    val msg = if (!pdfPassword.isNullOrEmpty()) {
                                        "Split into ${files.size} PDF files! (Output files have no password)"
                                    } else {
                                        "Split into ${files.size} PDF files successfully!"
                                    }
                                    processState = ProcessState.Success(
                                        outputFile = files.first(),
                                        message = msg
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
                enabled = selectedUri != null && pdfPassword != null && pageCount != null && currentValidation is RangeValidation.Valid && processState !is ProcessState.Processing
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
                                                        "application/pdf",
                                                        "${base}_part_${idx + 1}.pdf"
                                                    )
                                                    if (uri != null) count++
                                                }
                                                Toast.makeText(appContext, "Saved $count of $total PDF files to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}", Toast.LENGTH_SHORT).show()
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

sealed interface RangeValidation {
    data class Valid(val ranges: List<IntRange>, val previewText: String) : RangeValidation
    data class Invalid(val errorMessage: String) : RangeValidation
}

internal fun validateCustomRanges(text: String, totalPages: Int?): RangeValidation {
    val tokens = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
        return RangeValidation.Invalid("Enter at least one page range (e.g. 1-3, 4-6)")
    }
    val ranges = mutableListOf<IntRange>()
    for (token in tokens) {
        if (token.contains("-")) {
            val parts = token.split("-").map { it.trim() }
            if (parts.size != 2) {
                return RangeValidation.Invalid("Invalid range '$token'. Use format like '1-3' or '5'")
            }
            val s = parts[0].toIntOrNull()
                ?: return RangeValidation.Invalid("Invalid number '${parts[0]}' in range '$token'")
            val e = parts[1].toIntOrNull()
                ?: return RangeValidation.Invalid("Invalid number '${parts[1]}' in range '$token'")
            if (s < 1) {
                return RangeValidation.Invalid("Page numbers must be 1 or greater")
            }
            if (s > e) {
                return RangeValidation.Invalid("Range '$token': start page ($s) cannot be greater than end page ($e)")
            }
            if (totalPages != null && (s > totalPages || e > totalPages)) {
                return RangeValidation.Invalid("Range '$token' exceeds document length ($totalPages ${if (totalPages == 1) "page" else "pages"})")
            }
            ranges.add(s..e)
        } else {
            val p = token.toIntOrNull()
                ?: return RangeValidation.Invalid("Invalid page number '$token'")
            if (p < 1) {
                return RangeValidation.Invalid("Page number must be 1 or greater")
            }
            if (totalPages != null && p > totalPages) {
                return RangeValidation.Invalid("Page $p exceeds document length ($totalPages ${if (totalPages == 1) "page" else "pages"})")
            }
            ranges.add(p..p)
        }
    }
    val count = ranges.size
    val preview = "Will create $count PDF ${if (count == 1) "file" else "files"}"
    return RangeValidation.Valid(ranges, preview)
}

internal fun validateEveryN(intervalText: String, totalPages: Int?): RangeValidation {
    val n = intervalText.toIntOrNull()
    if (n == null || n < 1) {
        return RangeValidation.Invalid("Enter a valid interval (minimum 1 page)")
    }
    val count = if (totalPages != null && totalPages > 0) {
        (totalPages + n - 1) / n
    } else {
        null
    }
    val preview = if (count != null) {
        "Will create $count PDF ${if (count == 1) "file" else "files"}"
    } else {
        "Splits document into chunks of $n ${if (n == 1) "page" else "pages"}"
    }
    val ranges = if (totalPages != null && totalPages > 0) {
        PdfEngine.computeEveryNChunks(totalPages, n)
    } else {
        emptyList()
    }
    return RangeValidation.Valid(ranges, preview)
}

internal fun validateSingleRange(startText: String, endText: String, totalPages: Int?): RangeValidation {
    val s = startText.toIntOrNull()
        ?: return RangeValidation.Invalid("Enter a valid start page")
    val e = endText.toIntOrNull()
        ?: return RangeValidation.Invalid("Enter a valid end page")
    if (s < 1) {
        return RangeValidation.Invalid("Start page must be 1 or greater")
    }
    if (e < 1) {
        return RangeValidation.Invalid("End page must be 1 or greater")
    }
    if (s > e) {
        return RangeValidation.Invalid("Start page ($s) cannot be greater than end page ($e)")
    }
    if (totalPages != null && (s > totalPages || e > totalPages)) {
        return RangeValidation.Invalid("Range exceeds document length ($totalPages ${if (totalPages == 1) "page" else "pages"})")
    }
    val pageCount = e - s + 1
    val preview = "Extracts pages $s to $e ($pageCount ${if (pageCount == 1) "page" else "pages"})"
    return RangeValidation.Valid(listOf(s..e), preview)
}
