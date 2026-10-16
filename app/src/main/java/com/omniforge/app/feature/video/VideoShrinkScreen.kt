package com.omniforge.app.feature.video

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
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.settings.SaveMode
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.video.VideoEngine
import com.omniforge.app.engine.video.VideoShrinkPreset
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

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }

    var selectedPreset by retained(JOB_KEY, "selectedPreset") { mutableStateOf(VideoShrinkPreset.DISCORD_FAST) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(SaveMode.NEW_COPY) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            processState = ProcessState.Idle
        }
    }

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

            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                placeholderText = "Select Video to Compress",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            // Save Mode
            SaveModeSelector(
                saveMode = saveMode,
                onSaveModeChange = { saveMode = it },
                accentColor = AccentVideo
            )

            // Preset Selection
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Target Size / Resolution Preset",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
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
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) AccentVideo else DarkSurfaceElevated,
                                contentColor = if (isSelected) AmoledBlack else TextPrimary
                            ),
                            border = BorderStroke(1.dp, if (isSelected) AccentVideo else DarkBorder)
                        ) {
                            Text(
                                text = preset.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Video Shrinker") {
                        processState = ProcessState.Processing(0.2f, "Preparing video...")
                        val result = VideoEngine.shrinkVideo(
                            context = context,
                            inputUri = uri,
                            preset = selectedPreset,
                            onProgress = { p, msg ->
                                processState = ProcessState.Processing(p, msg)
                            }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                if (saveMode == SaveMode.OVERWRITE_ORIGINAL) {
                                    val overwritten = FileUtils.overwriteOriginal(context, uri, outputFile)
                                    if (overwritten) {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original video overwritten in-place!"
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Compressed! (Saved as copy in Downloads)"
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "Video compressed successfully!"
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shrink Video", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val baseName = (fileName ?: "video").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = VideoEngine.MP4_MIME,
                            displayName = "${baseName}_shrunk.mp4"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, VideoEngine.MP4_MIME)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
