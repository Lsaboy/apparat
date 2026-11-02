package com.omniforge.apparat.desktop.feature

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.theme.AccentUtility
import com.omniforge.apparat.core.theme.ErrorRed
import com.omniforge.apparat.core.theme.SuccessGreen
import com.omniforge.apparat.core.theme.accentText
import com.omniforge.apparat.core.theme.onPrimaryForAccent
import com.omniforge.apparat.core.ui.BraunSegmentedControl
import com.omniforge.apparat.core.ui.ToolScaffold
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.engine.AudioConvertFormat
import com.omniforge.apparat.desktop.engine.BatchItemState
import com.omniforge.apparat.desktop.engine.BatchOperation
import com.omniforge.apparat.desktop.engine.BatchOptions
import com.omniforge.apparat.desktop.engine.BatchRunner
import com.omniforge.apparat.desktop.engine.BatchSummary
import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.desktop.platform.DesktopShell
import com.omniforge.apparat.engine.image.ImageOutputFormat
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import java.io.File

private const val JOB_KEY = "batch_process"

/** Files dropped on the window while the batch screen is open go here instead of Home. */
object BatchDrop {
    @Volatile
    var target: ((List<File>) -> Unit)? = null
}

/** Desktop-only: apply one operation to many files and write every result into a folder. */
@Composable
fun BatchScreen(initialFiles: List<File> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current

    var operation by retained(JOB_KEY, "operation") { mutableStateOf(BatchOperation.COMPRESS_PDF) }
    var options by retained(JOB_KEY, "options") { mutableStateOf(BatchOptions()) }
    val files = retained(JOB_KEY, "files") { mutableStateListOf<File>().apply { addAll(initialFiles) } }
    val states = retained(JOB_KEY, "states") { mutableStateMapOf<Int, BatchItemState>() }
    var outputDir by retained(JOB_KEY, "outputDir") { mutableStateOf(File(DesktopPaths.downloadsDir, "Apparat/Batch")) }
    var summary by retained(JOB_KEY, "summary") { mutableStateOf<BatchSummary?>(null) }
    var error by retained(JOB_KEY, "error") { mutableStateOf<String?>(null) }
    val running = ToolJobs.isRunning(JOB_KEY)

    fun addFiles(new: List<File>) {
        if (running) return
        val expanded = new.flatMap { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList().sortedBy { it.path } else listOf(f) }
        val known = files.toSet()
        files += expanded.filter { it.isFile && it !in known }
        states.clear(); summary = null; error = null
    }

    DisposableEffect(Unit) {
        BatchDrop.target = { dropped -> java.awt.EventQueue.invokeLater { addFiles(dropped) } }
        onDispose { BatchDrop.target = null }
    }

    val accepted = files.count { operation.input.accepts(it) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        addFiles(uris.map { it.file })
    }
    val pickInputFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) addFiles(listOf(uri.file))
    }
    val pickOutputFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) outputDir = uri.file
    }

    @Composable
    fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }

    ToolScaffold(title = "Batch Processor", categoryName = "Quick Utilities", accentColor = AccentUtility, onBack = onBack, jobKey = JOB_KEY) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Section("1. What to do") {
                BatchOperation.entries.forEach { op ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !running) { operation = op; states.clear(); summary = null },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = op == operation,
                            onClick = { operation = op; states.clear(); summary = null },
                            enabled = !running,
                            colors = RadioButtonDefaults.colors(selectedColor = AccentUtility)
                        )
                        Text(op.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                when (operation) {
                    BatchOperation.PHOTO_UNDER_KB -> BraunSegmentedControl(
                        options = listOf(50, 100, 200, 500, 1000), selectedOption = options.photoLimitKb,
                        onOptionSelected = { options = options.copy(photoLimitKb = it) }, labelForOption = { "$it KB" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    BatchOperation.CONVERT_IMAGE -> BraunSegmentedControl(
                        options = ImageOutputFormat.available(), selectedOption = options.imageFormat,
                        onOptionSelected = { options = options.copy(imageFormat = it) }, labelForOption = { it.name },
                        modifier = Modifier.fillMaxWidth()
                    )
                    BatchOperation.SHRINK_VIDEO -> BraunSegmentedControl(
                        options = VideoShrinkPreset.entries.toList(), selectedOption = options.videoPreset,
                        onOptionSelected = { options = options.copy(videoPreset = it) }, labelForOption = { "${it.shortSide}p" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    BatchOperation.CONVERT_AUDIO -> BraunSegmentedControl(
                        options = AudioConvertFormat.entries.toList(), selectedOption = options.audioFormat,
                        onOptionSelected = { options = options.copy(audioFormat = it) }, labelForOption = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                    BatchOperation.SHRINK_PHOTO -> Text("Longest side 1920 px, quality 80%, location and camera data removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BatchOperation.COMPRESS_PDF -> Text("Photos and scans inside each PDF re-saved at about 150 DPI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {}
                }
            }

            Section("2. Files (${files.size}${if (files.isNotEmpty() && accepted != files.size) ", $accepted usable" else ""})") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFiles.launch(operation.input.mimes.toTypedArray()) }, enabled = !running, shape = RoundedCornerShape(6.dp)) {
                        Icon(Icons.Default.NoteAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add files…")
                    }
                    OutlinedButton(onClick = { pickInputFolder.launch(null) }, enabled = !running, shape = RoundedCornerShape(6.dp)) {
                        Icon(Icons.Default.CreateNewFolder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add a folder…")
                    }
                    if (files.isNotEmpty()) TextButton(onClick = { files.clear(); states.clear(); summary = null }, enabled = !running) { Text("Clear") }
                }
                if (files.isEmpty()) {
                    Text("Or drop files and folders onto this window.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                files.forEachIndexed { i, f ->
                    val usable = operation.input.accepts(f)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                color = if (usable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            val status = when (val s = states[i]) {
                                null, BatchItemState.Waiting -> if (usable) FileUtils.formatFileSize(f.length()) else "Not a ${operation.input.name.lowercase().replace('_', ' ')} file — will be skipped"
                                is BatchItemState.Running -> "Working… ${(s.progress * 100).toInt()}%"
                                is BatchItemState.Done -> {
                                    val pct = if (s.originalSize > 0) ((s.originalSize - s.output.length()) * 100 / s.originalSize) else 0
                                    "→ ${s.output.name} • ${FileUtils.formatFileSize(s.output.length())}" + if (pct > 0) " (−$pct%)" else ""
                                }
                                is BatchItemState.Failed -> "Failed: ${s.message}"
                                BatchItemState.Skipped -> "Skipped"
                            }
                            Text(
                                status, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = when (states[i]) {
                                    is BatchItemState.Done -> SuccessGreen
                                    is BatchItemState.Failed -> ErrorRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (!running) IconButton(onClick = { files.removeAt(i); states.clear(); summary = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove ${f.name}", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Section("3. Save into") {
                Text(outputDir.path, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Existing files are never overwritten: a number is added instead.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { pickOutputFolder.launch(null) }, enabled = !running, shape = RoundedCornerShape(6.dp)) { Text("Choose folder…") }
            }

            Button(
                onClick = {
                    val batch = files.toList(); val op = operation; val opts = options; val dir = outputDir
                    states.clear(); summary = null; error = null
                    ToolJobs.launch(context, JOB_KEY, "Batch: ${op.label}") { appContext ->
                        try {
                            summary = BatchRunner.run(appContext, op, opts, batch, dir) { i, s ->
                                java.awt.EventQueue.invokeLater { states[i] = s }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "The batch stopped"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentUtility, contentColor = onPrimaryForAccent(AccentUtility)),
                enabled = accepted > 0 && !running
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    val doneCount = states.values.count { it is BatchItemState.Done || it is BatchItemState.Failed || it is BatchItemState.Skipped }
                    Text("Processing ${minOf(doneCount + 1, files.size)} of ${files.size}…", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.PlaylistPlay, null, tint = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text("Process $accepted ${if (accepted == 1) "file" else "files"}", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                }
            }

            error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium) }

            summary?.let { s ->
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Batch finished: ${s.done} done" + (if (s.failed > 0) ", ${s.failed} failed" else "") + (if (s.skipped > 0) ", ${s.skipped} skipped" else ""),
                            fontWeight = FontWeight.Bold, color = accentText(AccentUtility)
                        )
                        if (s.savedBytes > 0) Text("${FileUtils.formatFileSize(s.savedBytes)} smaller in total", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { DesktopShell.open(s.outputDir) },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open output folder")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
