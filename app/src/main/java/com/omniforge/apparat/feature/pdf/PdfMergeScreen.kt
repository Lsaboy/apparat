package com.omniforge.apparat.feature.pdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.pdf.PdfAccess
import com.omniforge.apparat.engine.pdf.PdfEngine
import com.omniforge.apparat.engine.pdf.PdfMergeInput
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_merge"

/** One PDF in the merge list. [password] is "" for unprotected files. */
data class MergeEntry(
    val uri: Uri,
    val name: String,
    val size: Long,
    val pageCount: Int,
    val password: String = ""
)

/** Moves the item at [from] to [to]; returns the list unchanged when either index is out of range. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
fun PdfMergeScreen(
    initialUri: Uri? = null,
    initialUris: List<Uri> = emptyList(),
    shareToken: Long = 0L,
    onNavigateToTool: ((route: String, uri: Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var entries by retained(JOB_KEY, "entries") { mutableStateOf<List<MergeEntry>>(emptyList()) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }
    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(null) }

    // PDFs that need a password, asked one at a time.
    var passwordQueue by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var wrongPassword by remember { mutableStateOf(false) }
    var checkingPassword by remember { mutableStateOf(false) }

    val busy = processState is ProcessState.Processing

    fun toEntry(uri: Uri, pageCount: Int, password: String) = MergeEntry(
        uri = uri,
        name = FileUtils.getFileName(context, uri),
        size = FileUtils.getFileSize(context, uri),
        pageCount = pageCount,
        password = password
    )

    suspend fun addUris(uris: List<Uri>) {
        val known = entries.map { it.uri }.toSet() + passwordQueue
        val needPassword = mutableListOf<Uri>()
        var unreadable = 0
        for (uri in uris.distinct()) {
            if (uri in known) continue
            when (val access = PdfEngine.checkAccess(context, uri)) {
                is PdfAccess.Open -> entries = entries + toEntry(uri, access.pageCount, "")
                is PdfAccess.NeedsPassword -> needPassword += uri
                is PdfAccess.Damaged -> unreadable++
            }
        }
        passwordQueue = passwordQueue + needPassword
        if (unreadable > 0) {
            Toast.makeText(
                context,
                if (unreadable == 1) "1 file isn't a readable PDF and was skipped" else "$unreadable files aren't readable PDFs and were skipped",
                Toast.LENGTH_LONG
            ).show()
        }
        processState = ProcessState.Idle
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != lastHandledInitialUri) {
            lastHandledInitialUri = initialUri
            addUris(listOf(initialUri))
        }
    }
    var lastShareToken by retained(JOB_KEY, "lastShareToken") { mutableStateOf(0L) }
    LaunchedEffect(shareToken) {
        if (initialUris.isNotEmpty() && shareToken != lastShareToken) {
            lastShareToken = shareToken
            addUris(initialUris)
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) scope.launch { addUris(uris) }
    }

    passwordQueue.firstOrNull()?.let { current ->
        key(current) {
            PdfPasswordDialog(
                fileName = FileUtils.getFileName(context, current),
                wrongPassword = wrongPassword,
                checking = checkingPassword,
                accentColor = AccentPdf,
                onSubmit = { password ->
                    scope.launch {
                        checkingPassword = true
                        val access = PdfEngine.checkAccess(context, current, password)
                        checkingPassword = false
                        when (access) {
                            is PdfAccess.Open -> {
                                entries = entries + toEntry(current, access.pageCount, password)
                                passwordQueue = passwordQueue.drop(1)
                                wrongPassword = false
                            }
                            is PdfAccess.NeedsPassword -> wrongPassword = true
                            is PdfAccess.Damaged -> {
                                Toast.makeText(context, access.message, Toast.LENGTH_LONG).show()
                                passwordQueue = passwordQueue.drop(1)
                                wrongPassword = false
                            }
                        }
                    }
                },
                onDismiss = {
                    Toast.makeText(context, "Skipped ${FileUtils.getFileName(context, current)}", Toast.LENGTH_SHORT).show()
                    passwordQueue = passwordQueue.drop(1)
                    wrongPassword = false
                }
            )
        }
    }

    ToolScaffold(
        title = "Merge PDFs",
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy) { picker.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (entries.isNotEmpty()) AccentPdf else MaterialTheme.colorScheme.outline)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = AccentPdf,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (entries.isEmpty()) "Select PDFs to Merge" else "Add More PDFs",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (entries.isEmpty()) "Pick two or more PDFs" else "Tap to add to the list below",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            if (entries.isNotEmpty()) {
                val totalPages = entries.sumOf { it.pageCount }
                val totalSize = entries.sumOf { it.size.coerceAtLeast(0L) }
                Text(
                    text = "Merge order: top to bottom  •  $totalPages pages" +
                        if (totalSize > 0) "  •  ${FileUtils.formatFileSize(totalSize)}" else "",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                entries.forEachIndexed { index, entry ->
                    key(entry.uri) {
                        MergeEntryRow(
                            index = index,
                            entry = entry,
                            canMoveUp = index > 0 && !busy,
                            canMoveDown = index < entries.lastIndex && !busy,
                            canRemove = !busy,
                            onMoveUp = { entries = entries.moved(index, index - 1); processState = ProcessState.Idle },
                            onMoveDown = { entries = entries.moved(index, index + 1); processState = ProcessState.Idle },
                            onRemove = { entries = entries.filterNot { it.uri == entry.uri }; processState = ProcessState.Idle }
                        )
                    }
                }
            }

            if (entries.size == 1) {
                Text(
                    text = "Add at least one more PDF to merge.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            Button(
                onClick = {
                    val inputs = entries.map { PdfMergeInput(it.uri, it.password) }
                    val pages = entries.sumOf { it.pageCount }
                    if (inputs.size < 2) return@Button
                    ToolJobs.launch(context, JOB_KEY, "Merge PDFs") { appContext ->
                        processState = ProcessState.Processing(0.5f, "Merging ${inputs.size} PDFs...")
                        PdfEngine.mergePdfs(appContext, inputs).fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Merged ${inputs.size} PDFs into one ($pages pages)!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to merge PDFs")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf)),
                enabled = entries.size >= 2 && !busy
            ) {
                Icon(imageVector = Icons.Default.MergeType, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (entries.size >= 2) "Merge ${entries.size} PDFs" else "Merge PDFs",
                    color = LocalContentColor.current,
                    fontWeight = FontWeight.Bold
                )
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { "Merged_${java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(java.util.Date())}.pdf" },
                    onShare = { FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile)) },
                    chainActions = if (onNavigateToTool != null) {
                        listOf(
                            ToolChainAction(
                                label = "Compress PDF",
                                icon = Icons.Default.Compress,
                                onClick = {
                                    onNavigateToTool("pdf_shrink", FileUtils.getContentUri(context, success.outputFile))
                                }
                            )
                        )
                    } else emptyList()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MergeEntryRow(
    index: Int,
    entry: MergeEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = AccentPdf
                ),
                modifier = Modifier.width(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.password.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlocked with a password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "${entry.pageCount} ${if (entry.pageCount == 1) "page" else "pages"}" +
                            if (entry.size > 0) " • ${FileUtils.formatFileSize(entry.size)}" else "",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move ${entry.name} up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move ${entry.name} down")
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${entry.name}")
            }
        }
    }
}
