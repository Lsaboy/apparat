package com.omniforge.apparat.feature.video

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.video.VideoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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

    val startVal = startSecText.toFloatOrNull()
    val durVal = durationSecText.toFloatOrNull()
    val isTrimValid = if (isMuteOnly) {
        true
    } else {
        startVal != null && durVal != null && startVal >= 0f && durVal > 0f &&
            (videoTotalSec <= 0f || (startVal < videoTotalSec && startVal + durVal <= videoTotalSec + 0.05f))
    }
    val trimErrorMessage: String? = if (!isMuteOnly && selectedUri != null) {
        when {
            startVal == null -> "Please enter a valid start time"
            startVal < 0f -> "Start time cannot be negative"
            videoTotalSec > 0f && startVal >= videoTotalSec -> "Start time exceeds video duration (%.1fs)".format(Locale.US, videoTotalSec)
            durVal == null -> "Please enter a valid duration"
            durVal <= 0f -> "Duration must be greater than 0s"
            videoTotalSec > 0f && (startVal + durVal > videoTotalSec + 0.05f) -> "Trim end (%.1fs) exceeds video duration (%.1fs)".format(Locale.US, startVal + durVal, videoTotalSec)
            else -> null
        }
    } else null

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
                    shape = RoundedCornerShape(8.dp)
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
                                    processState = ProcessState.Idle
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo, contentColor = onPrimaryForAccent(AccentVideo))
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
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Strip audio track without trimming or re-encoding.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        Switch(
                            checked = isMuteOnly,
                            onCheckedChange = { isMuteOnly = it },
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedThumbColor = onPrimaryForAccent(AccentVideo),
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            if (videoTotalSec > 0f) {
                                Text(
                                    text = "Total: %.1fs".format(Locale.US, videoTotalSec),
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                label = { Text("Start Time (s)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                isError = trimErrorMessage != null && startVal == null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentVideo,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    errorBorderColor = ErrorRed
                                )
                            )

                            OutlinedTextField(
                                value = durationSecText,
                                onValueChange = { durationSecText = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Duration (s)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                isError = trimErrorMessage != null && (durVal == null || durVal <= 0f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentVideo,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    errorBorderColor = ErrorRed
                                )
                            )
                        }

                        if (trimErrorMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = trimErrorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed, fontSize = 11.sp)
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
                            val start = startVal ?: 0f
                            val duration = if (videoTotalSec > 0f) min(durVal ?: 10f, max(0.1f, videoTotalSec - start)) else (durVal ?: 10f)
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
                enabled = selectedUri != null && !ToolJobs.isRunning(JOB_KEY) && isTrimValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo, contentColor = onPrimaryForAccent(AccentVideo)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (isMuteOnly) Icons.AutoMirrored.Filled.VolumeOff else Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = LocalContentColor.current
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMuteOnly) "Mute Video" else "Trim Video Losslessly",
                    color = LocalContentColor.current,
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
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "video", if (success.outputFile.name.startsWith("muted")) "_muted" else "_trimmed", success.outputFile) },
                    onShare = {
                        FileUtils.shareFile(context.applicationContext, success.outputFile, FileUtils.mimeForFile(success.outputFile))
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
