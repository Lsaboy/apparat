package com.omniforge.apparat.feature.video

import androidx.compose.foundation.BorderStroke
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.*
import androidx.compose.ui.unit.sp
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEngine
import com.omniforge.apparat.engine.video.VideoMetadata

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "video_convert"

@Composable
fun VideoConvertScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(initialUri) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var videoMetadata by retained(JOB_KEY, "videoMetadata") { mutableStateOf<VideoMetadata?>(null) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }
    val codecs = remember { VideoCodecChoice.availableOnDevice() }
    var selectedCodec by retained(JOB_KEY, "selectedCodec") { mutableStateOf(VideoCodecChoice.H264) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        videoMetadata = if (uri != null) VideoEngine.getVideoMetadata(context, uri) else null
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != lastHandledInitialUri) {
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
                lastHandledInitialUri = initialUri
                pendingShareUri = null
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

    val currentFileSize = fileSize
    val fileDetails = remember(currentFileSize, videoMetadata) {
        buildList {
            videoMetadata?.let {
                add(it.resolutionFormatted)
                if (it.durationMs > 0) add(it.durationFormatted)
            }
            if (currentFileSize != null && currentFileSize > 0) {
                add(FileUtils.formatFileSize(currentFileSize))
            }
        }.joinToString(" • ").takeIf { it.isNotEmpty() }
    }

    ToolScaffold(
        title = "Convert to MP4",
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
                fileDetails = fileDetails,
                placeholderText = "Select Video to Convert",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            Text(
                text = "Accepts MKV, WEBM, MOV, 3GP and other videos your phone can play. Output is an MP4 (${selectedCodec.shortName} + AAC)" +
                    if (selectedCodec == VideoCodecChoice.H264) " that plays everywhere." else ".",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )

            // Codec Selection: only shown when the phone can encode more than H.264 in hardware.
            if (codecs.size > 1) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Output Codec",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        codecs.forEach { codec ->
                            val isSelected = selectedCodec == codec
                            OutlinedButton(
                                onClick = { selectedCodec = codec },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentVideo else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) onPrimaryForAccent(AccentVideo) else MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentVideo else MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = codec.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val codec = selectedCodec.takeIf { it in codecs } ?: VideoCodecChoice.H264
                    ToolJobs.launch(context, JOB_KEY, "Convert to MP4") { appContext ->
                        var usedCodec = codec
                        processState = ProcessState.Processing(0f, "Converting to MP4...")
                        val result = VideoEngine.convertToMp4(
                            context = appContext,
                            inputUri = uri,
                            codec = codec,
                            onCodecUsed = { usedCodec = it },
                            onProgress = { p, msg -> processState = ProcessState.Processing(p, msg) }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                val fallbackNote = if (usedCodec != codec) {
                                    " Saved as H.264 because this phone's ${codec.shortName} encoder failed."
                                } else ""
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Converted to MP4!$fallbackNote"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to transcode")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo, contentColor = onPrimaryForAccent(AccentVideo)),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert to MP4", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "video", "_converted", success.outputFile) },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile))
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
