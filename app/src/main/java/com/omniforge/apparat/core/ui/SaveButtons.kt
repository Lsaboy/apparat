package com.omniforge.apparat.core.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.util.OutputSaver
import com.omniforge.apparat.core.util.SaveItem
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Save controls for one output file: "Save to <default folder>" (one tap) and "Save as…" (the
 * system save picker: any folder, editable name). Saving survives leaving the screen midway.
 */
@Composable
fun SaveButtons(file: File, mimeType: String, fileName: String) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    // Re-read when the screen is shown again, in case the default was changed in Settings.
    val destinationLabel = remember(file) { OutputSaver.destination(appContext).label }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { target: Uri? ->
        if (target == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val outcome = withContext(NonCancellable) { OutputSaver.saveToDocument(appContext, file, target) }
            saving = false
            Toast.makeText(appContext, outcome.message, Toast.LENGTH_SHORT).show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
        onClick = {
            if (saving) return@Button
            saving = true
            scope.launch {
                val outcome = withContext(NonCancellable) {
                    OutputSaver.saveToDefault(appContext, listOf(SaveItem(file, mimeType, fileName)))
                }
                saving = false
                Toast.makeText(appContext, outcome.message, Toast.LENGTH_LONG).show()
            }
        },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Saving…", fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save to $destinationLabel", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    OutlinedButton(
        onClick = { runCatching { saveAs.launch(fileName) } },
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Save as… (choose folder and name)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
    }
}

/**
 * Save controls for several outputs: "Save all to <default folder>" and "Save all to a folder…"
 * (pick one folder once, never one dialog per file). Shows progress while saving.
 */
@Composable
fun SaveAllButtons(items: List<SaveItem>, noun: String = "files") {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<String?>(null) }
    val destinationLabel = remember(items) { OutputSaver.destination(appContext).label }
    val saving = progress != null

    fun run(save: suspend (onProgress: (Int, Int) -> Unit) -> com.omniforge.apparat.core.util.SaveOutcome) {
        progress = "Saving…"
        scope.launch {
            val outcome = withContext(NonCancellable) { save { done, total -> progress = "Saving $done of $total…" } }
            progress = null
            Toast.makeText(appContext, outcome.message, Toast.LENGTH_LONG).show()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) run { onProgress -> OutputSaver.saveToPickedFolder(appContext, tree, items, onProgress) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
        onClick = { if (!saving) run { onProgress -> OutputSaver.saveToDefault(appContext, items, onProgress) } },
        enabled = !saving && items.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(6.dp)
    ) {
        if (saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(progress ?: "Saving…", fontWeight = FontWeight.Bold)
        } else {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save all ${items.size} $noun to $destinationLabel", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    OutlinedButton(
        onClick = { runCatching { pickFolder.launch(null) } },
        enabled = !saving && items.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Save all to a folder…", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
    }
}
