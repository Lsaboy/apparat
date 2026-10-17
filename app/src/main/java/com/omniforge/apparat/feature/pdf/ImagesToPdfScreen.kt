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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
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
import com.omniforge.apparat.engine.pdf.PdfEngine
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "images_to_pdf"

@Composable
fun ImagesToPdfScreen(
    initialUris: List<Uri> = emptyList(),
    shareToken: Long = 0L,
    onNavigateToTool: ((route: String, uri: Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUris by retained(JOB_KEY, "selectedUris") { mutableStateOf<List<Uri>>(emptyList()) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }
    var lastShareToken by retained(JOB_KEY, "lastShareToken") { mutableStateOf(0L) }

    // Photos shared from the gallery are added to the list (once per share).
    LaunchedEffect(shareToken) {
        if (initialUris.isNotEmpty() && shareToken != lastShareToken) {
            lastShareToken = shareToken
            selectedUris = (selectedUris + initialUris).distinct()
            processState = ProcessState.Idle
        }
    }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // Add to the list (each photo once) instead of replacing what was already picked.
            selectedUris = (selectedUris + uris).distinct()
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(
        title = "Photos to PDF",
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
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { multiPicker.launch(arrayOf("image/*")) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selectedUris.isNotEmpty()) AccentPdf else MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = AccentPdf,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (selectedUris.isEmpty()) "Select Photos to Combine" else "Add More Photos",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (selectedUris.isEmpty()) "Tap to select one or multiple images" else "${selectedUris.size} selected • tap to add more",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            if (selectedUris.isNotEmpty()) {
                val busy = processState is ProcessState.Processing
                Text(
                    text = "Page order: top to bottom",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                selectedUris.forEachIndexed { index, uri ->
                    key(uri) {
                        val name = remember(uri) { FileUtils.getFileName(context, uri) }
                        PhotoOrderRow(
                            index = index,
                            name = name,
                            canMoveUp = index > 0 && !busy,
                            canMoveDown = index < selectedUris.lastIndex && !busy,
                            canRemove = !busy,
                            onMoveUp = { selectedUris = selectedUris.moved(index, index - 1); processState = ProcessState.Idle },
                            onMoveDown = { selectedUris = selectedUris.moved(index, index + 1); processState = ProcessState.Idle },
                            onRemove = { selectedUris = selectedUris - uri; processState = ProcessState.Idle }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (selectedUris.isEmpty()) return@Button
                    ToolJobs.launch(context, JOB_KEY, "Photos to PDF") { appContext ->
                        processState = ProcessState.Processing(0.5f, "Combining photos into PDF...")
                        val result = PdfEngine.imagesToPdf(appContext, selectedUris)
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "PDF generated with ${selectedUris.size} ${if (selectedUris.size == 1) "page" else "pages"}!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to generate PDF")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf)),
                enabled = selectedUris.isNotEmpty() && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate PDF", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { "Photos_${java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(java.util.Date())}.pdf" },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile))
                    },
                    chainActions = if (onNavigateToTool != null) {
                        listOf(
                            ToolChainAction(
                                label = "Compress PDF",
                                icon = Icons.Default.Compress,
                                onClick = {
                                    val uri = FileUtils.getContentUri(context, success.outputFile)
                                    onNavigateToTool("pdf_shrink", uri)
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
private fun PhotoOrderRow(
    index: Int,
    name: String,
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
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = AccentPdf),
                modifier = Modifier.width(28.dp)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move $name up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move $name down")
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove $name")
            }
        }
    }
}
