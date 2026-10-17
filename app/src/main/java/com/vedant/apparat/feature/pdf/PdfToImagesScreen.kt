package com.vedant.apparat.feature.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.sp
import com.vedant.apparat.core.jobs.ToolJobs
import com.vedant.apparat.core.jobs.retained
import com.vedant.apparat.core.model.ProcessState
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.ui.*
import com.vedant.apparat.core.util.FileUtils
import com.vedant.apparat.core.util.SaveItem
import com.vedant.apparat.engine.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_to_images"

@Composable
fun PdfToImagesScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(initialUri) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(initialUri?.let { FileUtils.getFileSize(context, it) }) }

    var imageFormat by retained(JOB_KEY, "imageFormat") { mutableStateOf("JPEG") }
    var dpiSelection by retained(JOB_KEY, "dpiSelection") { mutableIntStateOf(150) }
    var isAllPages by retained(JOB_KEY, "isAllPages") { mutableStateOf(true) }
    var customRangeText by retained(JOB_KEY, "customRangeText") { mutableStateOf("1-3") }

    var renderedPages by retained(JOB_KEY, "renderedPages") { mutableStateOf<List<File>>(emptyList()) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }
    // Verified password for selectedUri ("" = none needed); null until PdfPasswordGate has checked.
    var pdfPassword by retained(JOB_KEY, "pdfPassword") { mutableStateOf<String?>(null) }
    var pageCount by retained(JOB_KEY, "pageCount") { mutableStateOf<Int?>(null) }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != lastHandledInitialUri) {
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                lastHandledInitialUri = initialUri
                pendingShareUri = null
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
                pdfPassword = null
                pageCount = null
                processState = ProcessState.Processing(0f, "Opening PDF...")
                renderedPages = emptyList()
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

            // Pending share-in warning card
            if (pendingShareUri != null) {
                val pendingUri = pendingShareUri!!
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
                                    renderedPages = emptyList()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf))
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

            FormatSelectorRow(
                title = "Output Image Format",
                formats = listOf("JPEG", "PNG"),
                selectedFormat = imageFormat,
                onFormatSelect = { imageFormat = it },
                accentColor = AccentPdf
            )

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Rendering Resolution",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(150 to "150 DPI (Compact)", 300 to "300 DPI (High Res)").forEach { (dpi, label) ->
                            val isSelected = dpiSelection == dpi
                            OutlinedButton(
                                onClick = { dpiSelection = dpi },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentPdf else androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor = if (isSelected) onPrimaryForAccent(AccentPdf) else MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentPdf else MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (dpiSelection == 150) "150 DPI: Fast extraction, reasonable file sizes (recommended)" else "300 DPI: Print-quality high-res extraction (larger file sizes)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            val rangeValidation = remember(isAllPages, customRangeText, pageCount) {
                if (isAllPages) null else validateCustomRanges(customRangeText, pageCount)
            }

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Page Selection",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(true to "All Pages", false to "Custom Range").forEach { (all, label) ->
                            val isSelected = isAllPages == all
                            OutlinedButton(
                                onClick = { isAllPages = all },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentPdf else androidx.compose.ui.graphics.Color.Transparent,
                                    contentColor = if (isSelected) onPrimaryForAccent(AccentPdf) else MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentPdf else MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    if (!isAllPages) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customRangeText,
                            onValueChange = { customRangeText = it },
                            label = { Text("Pages / Ranges (e.g. 1-3, 5)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = rangeValidation is RangeValidation.Invalid
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        when (rangeValidation) {
                            is RangeValidation.Invalid -> {
                                Text(
                                    text = rangeValidation.errorMessage,
                                    color = ErrorRed,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                            is RangeValidation.Valid -> {
                                val distinctPages = rangeValidation.ranges.flatMap { it.toList() }.distinct().sorted()
                                Text(
                                    text = "Will extract ${distinctPages.size} ${if (distinctPages.size == 1) "page" else "pages"} (${distinctPages.joinToString(", ")})",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp
                                )
                            }
                            null -> Unit
                        }
                    }
                }
            }

            val isActionEnabled = selectedUri != null &&
                    pdfPassword != null &&
                    processState !is ProcessState.Processing &&
                    (isAllPages || rangeValidation is RangeValidation.Valid)

            val actionButtonText = if (isAllPages) {
                "Extract All Pages to Images"
            } else {
                val count = (rangeValidation as? RangeValidation.Valid)?.ranges?.flatMap { it.toList() }?.distinct()?.size ?: 0
                "Extract $count ${if (count == 1) "Page" else "Pages"} to Images"
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val pass = pdfPassword ?: ""
                    val format = imageFormat
                    val dpi = dpiSelection
                    val selectedPages = if (isAllPages) null else (rangeValidation as? RangeValidation.Valid)?.ranges?.flatMap { it.toList() }?.distinct()?.sorted()

                    ToolJobs.launch(context, JOB_KEY, "PDF to Images") { appContext ->
                        processState = ProcessState.Processing(0f, "Preparing PDF...")
                        try {
                            val files = renderPdfPagesToImages(
                                context = appContext,
                                inputUri = uri,
                                password = pass,
                                format = format,
                                dpi = dpi,
                                pagesToRender = selectedPages,
                                onProgress = { progress, message ->
                                    processState = ProcessState.Processing(progress, message)
                                }
                            )
                            renderedPages = files
                            if (files.isNotEmpty()) {
                                val formatLabel = if (format.equals("PNG", ignoreCase = true)) "PNG" else "JPEG"
                                processState = ProcessState.Success(
                                    outputFile = files.first(),
                                    message = "Extracted ${files.size} ${if (files.size == 1) "page" else "pages"} as $formatLabel ($dpi DPI)!"
                                )
                            } else {
                                processState = ProcessState.Error("No pages found in PDF")
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e // stopped by the user: ToolJobs resets the screen
                        } catch (e: Exception) {
                            processState = ProcessState.Error(e.localizedMessage ?: "Failed to render PDF")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf)),
                enabled = isActionEnabled
            ) {
                Icon(imageVector = Icons.Default.Collections, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text(actionButtonText, color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (renderedPages.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${renderedPages.size} Pages Rendered Successfully",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentText(SuccessGreen)
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val pageMime = remember(renderedPages) { renderedPages.firstOrNull()?.let { FileUtils.mimeForFile(it) } ?: "image/jpeg" }
                        SaveAllButtons(
                            items = remember(renderedPages) {
                                renderedPages.map { f ->
                                    val page = Regex("""^page_(\d+)_""").find(f.name)?.groupValues?.get(1) ?: "x"
                                    SaveItem(f, pageMime, FileUtils.outputName(fileName, "document", "_page_$page", f))
                                }
                            },
                            noun = if (renderedPages.size == 1) "image" else "images"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { FileUtils.shareFiles(context, renderedPages, pageMime) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(if (renderedPages.size == 1) "Share" else "Share all ${renderedPages.size}", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Native PDF page renderer using [PdfRenderer] to avoid huge memory/disk blowups.
 * Resolves Audit Issue B13: supports JPEG/PNG format, 150/300 DPI, page ranges, and bitmap recycling.
 */
private suspend fun renderPdfPagesToImages(
    context: android.content.Context,
    inputUri: Uri,
    password: String,
    format: String,
    dpi: Int,
    pagesToRender: List<Int>?,
    onProgress: (Float, String) -> Unit
): List<File> = withContext(Dispatchers.IO) {
    val renderedFiles = mutableListOf<File>()
    var cachedInput = if (password.isNotEmpty()) {
        PdfEngine.removePassword(context, inputUri, password).getOrThrow()
    } else {
        PdfEngine.takeInputCopy(context, inputUri)
    }
    var isDecryptedTemp = password.isNotEmpty()

    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        pfd = ParcelFileDescriptor.open(cachedInput, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = try {
            PdfRenderer(pfd)
        } catch (e: SecurityException) {
            pfd.close()
            pfd = null
            if (!isDecryptedTemp) {
                val decrypted = PdfEngine.removePassword(context, inputUri, password).getOrThrow()
                cachedInput.delete()
                cachedInput = decrypted
                isDecryptedTemp = true
                pfd = ParcelFileDescriptor.open(cachedInput, ParcelFileDescriptor.MODE_READ_ONLY)
                PdfRenderer(pfd)
            } else {
                throw e
            }
        }

        val totalPdfPages = renderer.pageCount
        val pages = if (pagesToRender.isNullOrEmpty()) {
            (1..totalPdfPages).toList()
        } else {
            pagesToRender.filter { it in 1..totalPdfPages }
        }

        if (pages.isEmpty()) {
            throw IllegalArgumentException("No valid pages to extract")
        }

        val isPng = format.equals("PNG", ignoreCase = true)
        val ext = if (isPng) ".png" else ".jpg"
        val baseScale = dpi.toDouble() / 72.0

        for ((idx, pageNum) in pages.withIndex()) {
            ensureActive()
            val progress = idx.toFloat() / pages.size.toFloat()
            val msg = "Rendering page $pageNum (${idx + 1} of ${pages.size})…"
            onProgress(progress, msg)

            val page = renderer.openPage(pageNum - 1)
            var bitmap: Bitmap? = null
            val pageFile = FileUtils.createTempOutputFile(context, "page_$pageNum", ext)
            try {
                val maxScale = sqrt(16_000_000.0 / (page.width.toDouble() * page.height))
                val scale = min(baseScale, maxScale)
                val targetWidth = max(1, (page.width * scale).toInt())
                val targetHeight = max(1, (page.height * scale).toInt())

                bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                FileOutputStream(pageFile).use { out ->
                    if (isPng) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                }
                renderedFiles.add(pageFile)
            } catch (e: Throwable) {
                pageFile.delete()
                throw e
            } finally {
                bitmap?.recycle()
                page.close()
            }
        }

        onProgress(1f, "Extracted ${renderedFiles.size} pages")
        renderedFiles
    } catch (t: Throwable) {
        renderedFiles.forEach { it.delete() }
        throw t
    } finally {
        renderer?.close()
        pfd?.close()
        cachedInput.delete()
    }
}
