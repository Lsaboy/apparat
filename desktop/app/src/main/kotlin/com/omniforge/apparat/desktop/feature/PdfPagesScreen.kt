package com.omniforge.apparat.desktop.feature

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.AccentPdf
import com.omniforge.apparat.core.theme.onPrimaryForAccent
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.engine.NumberPosition
import com.omniforge.apparat.desktop.engine.PageEdit
import com.omniforge.apparat.desktop.engine.PageNumbering
import com.omniforge.apparat.desktop.engine.PdfPageSession
import com.omniforge.apparat.desktop.engine.PdfPageTools
import com.omniforge.apparat.desktop.engine.Watermark

private const val JOB_KEY = "pdf_pages"
private val OPACITIES = listOf(0.1f, 0.2f, 0.35f, 0.5f)

/** Desktop-only: reorder, rotate and delete PDF pages on thumbnails, add page numbers and a watermark. */
@Composable
fun PdfPagesScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var needsCheck by retained(JOB_KEY, "needsCheck") { mutableStateOf(initialUri != null) }
    var password by retained(JOB_KEY, "password") { mutableStateOf<String?>(null) }
    var pageCount by retained(JOB_KEY, "pageCount") { mutableIntStateOf(0) }
    var pages by retained(JOB_KEY, "pages") { mutableStateOf<List<PageEdit>>(emptyList()) }
    var numbersOn by retained(JOB_KEY, "numbersOn") { mutableStateOf(false) }
    var position by retained(JOB_KEY, "position") { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var withTotal by retained(JOB_KEY, "withTotal") { mutableStateOf(false) }
    var firstNumber by retained(JOB_KEY, "firstNumber") { mutableStateOf("1") }
    var watermarkText by retained(JOB_KEY, "watermarkText") { mutableStateOf("") }
    var opacity by retained(JOB_KEY, "opacity") { mutableFloatStateOf(0.2f) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            fileName = FileUtils.getFileName(context, uri)
            fileSize = FileUtils.getFileSize(context, uri)
            password = null
            pageCount = 0
            pages = emptyList()
            needsCheck = true
            processState = ProcessState.Idle
        }
    }

    PdfPasswordGate(
        uri = selectedUri,
        fileName = fileName,
        needsCheck = needsCheck,
        accentColor = AccentPdf,
        onReady = { pw, count ->
            needsCheck = false
            password = pw
            if (pageCount != count || pages.isEmpty()) {
                pageCount = count
                pages = (0 until count).map { PageEdit(it) }
            }
        },
        onFailed = { msg ->
            needsCheck = false
            selectedUri = null; fileName = null; fileSize = null
            pages = emptyList(); pageCount = 0
            processState = ProcessState.Error(msg)
        }
    )

    // The open document, for thumbnails; closed when the file changes or the screen goes away.
    val session by produceState<PdfPageSession?>(null, selectedUri, password) {
        val uri = selectedUri
        val pw = password
        value = if (uri != null && pw != null) runCatching { PdfPageSession.open(context, uri.file, pw) }.getOrNull() else null
        awaitDispose { value?.close() }
    }
    val thumbs = remember(session) { mutableStateMapOf<Int, ImageBitmap>() }

    val firstNum = firstNumber.toIntOrNull()
    val changed = pages != (0 until pageCount).map { PageEdit(it) } || numbersOn || watermarkText.isNotBlank()

    ToolScaffold(title = "Page Tools", categoryName = "PDF Tools", accentColor = AccentPdf, onBack = onBack, jobKey = JOB_KEY) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FileSelectionCard(
                    fileName = fileName,
                    fileSize = fileSize,
                    fileDetails = if (pageCount > 0) "$pageCount pages • ${FileUtils.formatFileSize(fileSize ?: 0)}" else null,
                    placeholderText = "Select PDF",
                    icon = Icons.Default.PictureAsPdf,
                    accentColor = AccentPdf,
                    onClick = { picker.launch(arrayOf("application/pdf")) }
                )
            }

            if (pageCount > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OptionsCard(
                        numbersOn = numbersOn, onNumbersOn = { numbersOn = it },
                        position = position, onPosition = { position = it },
                        withTotal = withTotal, onWithTotal = { withTotal = it },
                        firstNumber = firstNumber, onFirstNumber = { firstNumber = it.filter(Char::isDigit).take(5) },
                        watermarkText = watermarkText, onWatermarkText = { watermarkText = it.take(60) },
                        opacity = opacity, onOpacity = { opacity = it }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${pages.size} of $pageCount pages kept. Use the arrows to move a page, ↻ to turn it, the bin to drop it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { pages = (0 until pageCount).map { PageEdit(it) } },
                            enabled = pages != (0 until pageCount).map { PageEdit(it) }
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Reset pages")
                        }
                    }
                }

                itemsIndexed(pages, key = { _, p -> p.source }) { index, edit ->
                    val s = session
                    LaunchedEffect(s, edit.source) {
                        if (s != null && thumbs[edit.source] == null) {
                            runCatching { s.thumbnail(edit.source, 240).toComposeImageBitmap() }.getOrNull()?.let { thumbs[edit.source] = it }
                        }
                    }
                    PageCard(
                        number = index + 1,
                        edit = edit,
                        thumb = thumbs[edit.source],
                        canLeft = index > 0,
                        canRight = index < pages.lastIndex,
                        canDelete = pages.size > 1,
                        onLeft = { pages = pages.toMutableList().apply { add(index - 1, removeAt(index)) } },
                        onRight = { pages = pages.toMutableList().apply { add(index + 1, removeAt(index)) } },
                        onRotate = { pages = pages.toMutableList().apply { set(index, edit.copy(extraRotation = (edit.extraRotation + 90) % 360)) } },
                        onDelete = { pages = pages.toMutableList().apply { removeAt(index) } }
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            val edits = pages
                            val numbering = if (numbersOn) PageNumbering(position, firstNum ?: 1, withTotal) else null
                            val wm = watermarkText.trim().takeIf { it.isNotEmpty() }?.let { Watermark(it, opacity) }
                            val pw = password ?: ""
                            ToolJobs.launch(context, JOB_KEY, "Page Tools") { appContext ->
                                processState = ProcessState.Processing(0.3f, "Writing PDF...")
                                PdfPageTools.apply(appContext, uri, edits, numbering, wm, pw).fold(
                                    onSuccess = { processState = ProcessState.Success(it, "PDF ready: ${edits.size} pages") },
                                    onFailure = { processState = ProcessState.Error(it.localizedMessage ?: "Couldn't write the PDF") }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf)),
                        enabled = pages.isNotEmpty() && changed && (!numbersOn || firstNum != null) && processState !is ProcessState.Processing
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                        Text("Save new PDF", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                ProcessStateView(state = processState, accentColor = AccentPdf)
            }

            (processState as? ProcessState.Success)?.let { success ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ResultCard(
                        outputFile = success.outputFile,
                        originalSize = fileSize,
                        message = success.message,
                        saveMimeType = "application/pdf",
                        saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "document", "_pages", success.outputFile) },
                        onShare = { FileUtils.shareFile(context, success.outputFile, "application/pdf") }
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(
    numbersOn: Boolean, onNumbersOn: (Boolean) -> Unit,
    position: NumberPosition, onPosition: (NumberPosition) -> Unit,
    withTotal: Boolean, onWithTotal: (Boolean) -> Unit,
    firstNumber: String, onFirstNumber: (String) -> Unit,
    watermarkText: String, onWatermarkText: (String) -> Unit,
    opacity: Float, onOpacity: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = numbersOn, onCheckedChange = onNumbersOn, colors = CheckboxDefaults.colors(checkedColor = AccentPdf))
                Text("Add page numbers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (numbersOn) {
                BraunSegmentedControl(
                    options = NumberPosition.entries.toList(),
                    selectedOption = position,
                    onOptionSelected = onPosition,
                    labelForOption = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = firstNumber,
                        onValueChange = onFirstNumber,
                        label = { Text("Start at") },
                        singleLine = true,
                        isError = firstNumber.toIntOrNull() == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                    Checkbox(checked = withTotal, onCheckedChange = onWithTotal, colors = CheckboxDefaults.colors(checkedColor = AccentPdf))
                    Text("Show total (\"3 / 12\")", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            Text("Watermark", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = watermarkText,
                onValueChange = onWatermarkText,
                placeholder = { Text("e.g. DRAFT or CONFIDENTIAL (leave empty for none)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (watermarkText.isNotBlank()) {
                Text("Opacity", style = MaterialTheme.typography.bodyMedium)
                BraunSegmentedControl(
                    options = OPACITIES,
                    selectedOption = opacity,
                    onOptionSelected = onOpacity,
                    labelForOption = { "${(it * 100).toInt()}%" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PageCard(
    number: Int,
    edit: PageEdit,
    thumb: ImageBitmap?,
    canLeft: Boolean,
    canRight: Boolean,
    canDelete: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = "Page ${edit.source + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp).rotate(edit.extraRotation.toFloat())
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp), color = AccentPdf, strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (number == edit.source + 1) "Page $number" else "$number  (was ${edit.source + 1})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onLeft, enabled = canLeft, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ChevronLeft, contentDescription = "Move earlier") }
                IconButton(onClick = onRotate, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.RotateRight, contentDescription = "Turn 90°") }
                IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = "Remove page") }
                IconButton(onClick = onRight, enabled = canRight, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ChevronRight, contentDescription = "Move later") }
            }
        }
    }
}
