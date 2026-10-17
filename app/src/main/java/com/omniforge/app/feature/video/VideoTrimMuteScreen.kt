package com.omniforge.app.feature.video

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.omniforge.app.engine.video.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "video_trim"

@Composable
fun VideoTrimMuteScreen(
    initialUri: Uri? = null,
    onNavigateToTool: ((route: String, uri: Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }

    var videoTotalSec by retained(JOB_KEY, "videoTotalSec") { mutableFloatStateOf(0f) }
    var startSecText by retained(JOB_KEY, "startSecText") { mutableStateOf("0") }
    var durationSecText by retained(JOB_KEY, "durationSecText") { mutableStateOf("10") }
    var isMuteOnly by retained(JOB_KEY, "isMuteOnly") { mutableStateOf(false) }

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(null) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                try {
                    retriever.setDataSource(context, uri)
                } catch (_: Exception) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                    }
                }
                val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val totalSec = durMs / 1000f
                if (totalSec > 0f) {
                    videoTotalSec = totalSec
                    val currentStart = startSecText.toFloatOrNull() ?: 0f
                    if (currentStart >= totalSec) {
                        startSecText = "0"
                    }
                    val currentDur = durationSecText.toFloatOrNull() ?: 10f
                    if (currentDur > totalSec) {
                        durationSecText = "%.1f".format(Locale.US, totalSec)
                    }
                }
            } catch (_: Exception) {
                videoTotalSec = 0f
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri == null) return@LaunchedEffect
        if (initialUri != lastHandledInitialUri) {
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                lastHandledInitialUri = initialUri
                pendingShareUri = null
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
                processState = ProcessState.Idle
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pendingShareUri = null
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(
        title = "Trim & Mute",
        categoryName = "Video & Audio",
        accentColor = AccentVideo,
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

            pendingShareUri?.let { pendingUri ->
                val pendingName = remember(pendingUri) { FileUtils.getFileName(context, pendingUri) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Incoming shared video: $pendingName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "A job is currently running on another video. Switch to this new file or dismiss it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
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
                                    processState = ProcessState.Idle
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo)
                            ) {
                                Text("Switch to New File", color = AmoledBlack, fontSize = 12.sp)
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

            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                fileDetails = if (videoTotalSec > 0f) "Duration: %.1fs • %s".format(Locale.US, videoTotalSec, FileUtils.formatFileSize(fileSize ?: 0L)) else null,
                placeholderText = "Select Video to Trim or Mute",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            // Mode Toggle
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mute Audio Only",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Strip audio track without trimming or re-encoding.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = isMuteOnly,
                            onCheckedChange = { isMuteOnly = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AmoledBlack,
                                checkedTrackColor = AccentVideo
                            )
                        )
                    }

                    if (!isMuteOnly) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trim Range (Seconds)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            if (videoTotalSec > 0f) {
                                Text(
                                    text = "Total: %.1fs".format(Locale.US, videoTotalSec),
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = startSecText,
                                onValueChange = { startSecText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Start Time (s)", color = TextSecondary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentVideo,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = durationSecText,
                                onValueChange = { durationSecText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Duration (s)", color = TextSecondary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentVideo,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Trim & Mute") { appContext ->
                        processState = ProcessState.Processing(0f, "Processing...")
                        val result = if (isMuteOnly) {
                            VideoEngine.stripAudio(appContext, uri) { p, msg -> processState = ProcessState.Processing(p, msg) }
                        } else {
                            val start = startSecText.toFloatOrNull() ?: 0f
                            val duration = durationSecText.toFloatOrNull() ?: 10f
                            VideoEngine.losslessTrim(appContext, uri, start, duration) { p, msg -> processState = ProcessState.Processing(p, msg) }
                        }

                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = if (isMuteOnly) "Audio track removed successfully!" else "Video trimmed successfully!"
                                )
                            },
                            onFailure = { error ->
                                processState = ProcessState.Error(error.localizedMessage ?: "Operation failed")
                            }
                        )
                    }
                },
                enabled = selectedUri != null && !ToolJobs.isRunning(JOB_KEY),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isMuteOnly) Icons.Default.VolumeOff else Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = AmoledBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMuteOnly) "Mute Video" else "Trim Video Losslessly",
                    color = AmoledBlack,
                    fontWeight = FontWeight.Bold
                )
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val uri = FileUtils.saveToDownloads(
                            context = context.applicationContext,
                            file = success.outputFile,
                            mimeType = "video/mp4",
                            displayName = "Trimmed_${System.currentTimeMillis()}.mp4"
                        )
                        if (uri != null) {
                            Toast.makeText(context.applicationContext, "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context.applicationContext, "Failed to save file", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = {
                        FileUtils.shareFile(context.applicationContext, success.outputFile, "video/mp4")
                    },
                    chainActions = if (onNavigateToTool != null) {
                        listOf(
                            ToolChainAction(
                                label = "Make GIF",
                                icon = Icons.Default.Gif,
                                onClick = {
                                    val uri = FileUtils.getContentUri(context, success.outputFile)
                                    onNavigateToTool("video_to_gif", uri)
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
