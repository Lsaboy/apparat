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
import com.omniforge.apparat.core.ui.KeyboardShortcuts
import com.omniforge.apparat.core.ui.ShortcutHandler
import com.omniforge.apparat.core.ui.ToolScaffold
import com.omniforge.apparat.desktop.engine.BatchItemState
import com.omniforge.apparat.desktop.engine.BatchOperation
import com.omniforge.apparat.desktop.engine.BatchOptions
import com.omniforge.apparat.desktop.engine.FolderWatcher
import com.omniforge.apparat.desktop.engine.WatchConfig
import com.omniforge.apparat.desktop.engine.WatchEvent
import com.omniforge.apparat.desktop.platform.DesktopShell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val JOB_KEY = "watch_folder"
private const val LOG_LIMIT = 200

private data class WatchLogEntry(val time: Long, val name: String, val ok: Boolean, val detail: String)

/** Desktop-only: keep a folder watched and run a batch operation on every file dropped into it. */
@Composable
fun WatchFolderScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var operation by retained(JOB_KEY, "operation") { mutableStateOf(BatchOperation.SHRINK_PHOTO) }
    var options by retained(JOB_KEY, "options") { mutableStateOf(BatchOptions()) }
    var inputDir by retained(JOB_KEY, "inputDir") { mutableStateOf<File?>(null) }
    var customOutput by retained(JOB_KEY, "outputDir") { mutableStateOf<File?>(null) }
    var includeExisting by retained(JOB_KEY, "includeExisting") { mutableStateOf(false) }
    val log = retained(JOB_KEY, "log") { mutableStateListOf<WatchLogEntry>() }
    var working by retained(JOB_KEY, "working") { mutableStateOf<Pair<String, Float>?>(null) }
    var status by retained(JOB_KEY, "status") { mutableStateOf<String?>(null) }
    var error by retained(JOB_KEY, "error") { mutableStateOf<String?>(null) }
    val running = ToolJobs.isRunning(JOB_KEY)

    val outputDir: File? = customOutput ?: inputDir?.let { File(it, "Processed") }

    val pickInput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) { inputDir = uri.file; error = null }
    }
    val pickOutput = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) { customOutput = uri.file; error = null }
    }
    ShortcutHandler(KeyboardShortcuts.Action.OPEN, enabled = !running) { pickInput.launch(null) }

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

    fun shortPath(f: File): String {
        val home = System.getProperty("user.home")
        return if (f.path.startsWith(home)) "~" + f.path.removePrefix(home) else f.path
    }

    fun start() {
        val input = inputDir ?: return
        val output = outputDir ?: return
        val cfg = WatchConfig(input = input, output = output, operation = operation, options = options, includeExisting = includeExisting)
        try {
            FolderWatcher.validate(cfg)
        } catch (e: IllegalArgumentException) {
            error = e.message; return
        }
        error = null; working = null; status = "Starting…"
        ToolJobs.launch(context, JOB_KEY, "Watching ${input.name}") { appContext ->
            try {
                FolderWatcher.watch(appContext, cfg) { e ->
                    java.awt.EventQueue.invokeLater {
                        when (e) {
                            is WatchEvent.Started -> status =
                                if (e.ignored > 0) "Watching. ${e.ignored} ${if (e.ignored == 1) "file" else "files"} already there left alone."
                                else "Watching. New files are picked up once they finish copying."
                            is WatchEvent.Working -> working = e.file.name to e.progress
                            is WatchEvent.Finished -> {
                                working = null
                                val s = e.state
                                log.add(0, when (s) {
                                    is BatchItemState.Done -> WatchLogEntry(System.currentTimeMillis(), e.file.name, true, "→ ${s.output.name}")
                                    is BatchItemState.Failed -> WatchLogEntry(System.currentTimeMillis(), e.file.name, false, s.message)
                                    else -> WatchLogEntry(System.currentTimeMillis(), e.file.name, false, "Skipped")
                                })
                                while (log.size > LOG_LIMIT) log.removeAt(log.lastIndex)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                java.awt.EventQueue.invokeLater { working = null; status = "Stopped." }
                throw e
            } catch (e: Exception) {
                java.awt.EventQueue.invokeLater { working = null; status = null; error = e.localizedMessage ?: "Watching stopped" }
            }
        }
    }

    ToolScaffold(title = "Watch Folder", categoryName = "Quick Utilities", accentColor = AccentUtility, onBack = onBack, jobKey = JOB_KEY) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Files you drop into the watched folder are processed automatically while this runs — even after you leave this screen.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Section("1. Folder to watch") {
                Text(inputDir?.let(::shortPath) ?: "None chosen", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = { pickInput.launch(null) }, enabled = !running, shape = RoundedCornerShape(6.dp)) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choose folder…")
                }
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !running) { includeExisting = !includeExisting },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeExisting, onCheckedChange = { includeExisting = it }, enabled = !running,
                        colors = CheckboxDefaults.colors(checkedColor = AccentUtility)
                    )
                    Text("Also process files already in the folder", style = MaterialTheme.typography.bodyMedium)
                }
                Text("Only files directly in the folder are watched, not subfolders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("2. What to do with each file") {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    BatchOperation.entries.forEach { op ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !running) { operation = op }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = op == operation, onClick = { operation = op }, enabled = !running,
                                colors = RadioButtonDefaults.colors(selectedColor = AccentUtility)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(op.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                BatchOptionControls(operation, options) { if (!running) options = it }
            }

            Section("3. Save results into") {
                Text(outputDir?.let(::shortPath) ?: "A “Processed” folder inside the watched folder", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Existing files are never overwritten. Files already handled are remembered, so restarting doesn't redo them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickOutput.launch(null) }, enabled = !running, shape = RoundedCornerShape(6.dp)) { Text("Choose folder…") }
                    if (customOutput != null) TextButton(onClick = { customOutput = null }, enabled = !running) { Text("Use default") }
                }
            }

            if (running) {
                Button(
                    onClick = { ToolJobs.cancel(JOB_KEY) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    Icon(Icons.Default.Stop, null, tint = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop watching", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { start() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentUtility, contentColor = onPrimaryForAccent(AccentUtility)),
                    enabled = inputDir != null
                ) {
                    Icon(Icons.Default.Visibility, null, tint = LocalContentColor.current)
                    Spacer(Modifier.width(8.dp))
                    Text("Start watching", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                }
            }

            error?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium) }

            if (running || status != null || log.isNotEmpty()) {
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        status?.let { Text(it, fontWeight = FontWeight.Bold, color = accentText(AccentUtility)) }
                        working?.let { (name, p) ->
                            Text("Working on $name… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = AccentUtility)
                        }
                        if (log.isNotEmpty()) {
                            val ok = log.count { it.ok }
                            Text("$ok done" + if (log.size > ok) ", ${log.size - ok} failed" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        log.take(50).forEach { entry ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (entry.ok) Icons.Default.CheckCircle else Icons.Default.Error, null,
                                    tint = if (entry.ok) SuccessGreen else ErrorRed, modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = if (entry.ok) MaterialTheme.colorScheme.onSurfaceVariant else ErrorRed)
                                }
                                Text(fmt.format(Date(entry.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        outputDir?.let { dir ->
                            Button(
                                onClick = { dir.mkdirs(); DesktopShell.open(dir) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Open output folder")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
