package com.omniforge.apparat.feature.video

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
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
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.core.settings.SaveMode
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.video.VideoCodecChoice
import com.omniforge.apparat.engine.video.VideoEngine
import com.omniforge.apparat.engine.video.VideoMetadata
import com.omniforge.apparat.engine.video.VideoShrinkPreset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "video_shrink"

@Composable
fun VideoShrinkScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(initialUri) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var videoMetadata by retained(JOB_KEY, "videoMetadata") { mutableStateOf<VideoMetadata?>(null) }

    var selectedPreset by retained(JOB_KEY, "selectedPreset") { mutableStateOf(VideoShrinkPreset.DISCORD_FAST) }
    val codecs = remember { VideoCodecChoice.availableOnDevice() }
    var selectedCodec by retained(JOB_KEY, "selectedCodec") { mutableStateOf(VideoCodecChoice.H264) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(AppSettings(context).defaultSaveMode) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

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

    val isFormatMismatch = selectedUri != null && !FileUtils.isVideoMp4(context, selectedUri!!)
    val allowOverwrite = !isFormatMismatch
    val disallowedReason = if (isFormatMismatch) {
        val inputExt = fileName?.substringAfterLast('.', "")?.uppercase() ?: "original"
        "Cannot modify original: output is MP4 but original is $inputExt"
    } else null

    ToolScaffold(
        title = "Video Shrinker",
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
                placeholderText = "Select Video to Compress",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            // Save Mode
            SaveModeSelector(
                saveMode = saveMode,
                onSaveModeChange = { saveMode = it },
                accentColor = AccentVideo,
                allowOverwrite = allowOverwrite,
                disallowedReason = disallowedReason
            )

            // Preset Selection
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Target Size / Resolution Preset",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    VideoShrinkPreset.entries.forEach { preset ->
                        val isSelected = selectedPreset == preset
                        OutlinedButton(
                            onClick = { selectedPreset = preset },
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
                                text = preset.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

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
                    ToolJobs.launch(context, JOB_KEY, "Video Shrinker") { appContext ->
                        processState = ProcessState.Processing(0.2f, "Preparing video...")
                        var usedCodec = codec
                        val result = VideoEngine.shrinkVideo(
                            context = appContext,
                            inputUri = uri,
                            preset = selectedPreset,
                            codec = codec,
                            onCodecUsed = { usedCodec = it },
                            onProgress = { p, msg ->
                                processState = ProcessState.Processing(p, msg)
                            }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                val fallbackNote = if (usedCodec != codec) {
                                    " Saved as H.264 because this phone's ${codec.shortName} encoder failed."
                                } else ""
                                val origBytes = if ((fileSize ?: 0L) > 0L) fileSize!! else {
                                    runCatching { appContext.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: 0L
                                }
                                val newBytes = outputFile.length()

                                if (origBytes > 0L && newBytes >= origBytes) {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Video is already optimized (${FileUtils.formatFileSize(newBytes)}) — could not reduce size further." + fallbackNote
                                    )
                                } else if (saveMode == SaveMode.OVERWRITE_ORIGINAL && allowOverwrite) {
                                    val overwritten = FileUtils.overwriteOriginal(appContext, uri, outputFile)
                                    if (overwritten) {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original video overwritten in-place!" + fallbackNote
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Processed! (Original is read-only — tap Save below to save copy)" + fallbackNote
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Video compressed successfully!" + fallbackNote
                                    )
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to compress video")
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
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shrink Video", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "video", "_shrunk", success.outputFile) },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile))
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
