package com.omniforge.app.feature.video

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
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
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.video.VideoEngine
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "video_caption"

@Composable
fun VideoCaptionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videoUri by retained(JOB_KEY, "videoUri") { mutableStateOf<Uri?>(null) }
    var videoName by retained(JOB_KEY, "videoName") { mutableStateOf<String?>(null) }
    var videoSize by retained(JOB_KEY, "videoSize") { mutableStateOf<Long?>(null) }

    var subtitleUri by retained(JOB_KEY, "subtitleUri") { mutableStateOf<Uri?>(null) }
    var subtitleName by retained(JOB_KEY, "subtitleName") { mutableStateOf<String?>(null) }

    var fontSize by retained(JOB_KEY, "fontSize") { mutableFloatStateOf(20f) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            videoUri = it
            videoName = FileUtils.getFileName(context, it)
            videoSize = FileUtils.getFileSize(context, it)
            processState = ProcessState.Idle
        }
    }

    val subPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            subtitleUri = it
            subtitleName = FileUtils.getFileName(context, it)
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(
        title = "Burn Subtitles & Captions",
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

            // Select Video
            FileSelectionCard(
                fileName = videoName,
                fileSize = videoSize,
                placeholderText = "Select Video File",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { videoPicker.launch(arrayOf("video/*")) }
            )

            // Select Subtitle (.srt / .vtt)
            FileSelectionCard(
                fileName = subtitleName,
                fileSize = null,
                placeholderText = "Select Subtitles (.srt or .vtt)",
                icon = Icons.Default.Subtitles,
                accentColor = AccentVideo,
                onClick = { subPicker.launch(arrayOf("*/*")) }
            )

            // Subtitle Options
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Subtitle Text Size: ${fontSize.toInt()}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 12f..36f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentVideo,
                            activeTrackColor = AccentVideo,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                    Text(
                        text = "Subtitles are scaled to video height and drawn near the bottom on a dark box. The video is re-encoded.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }

            Button(
                onClick = {
                    val vUri = videoUri ?: return@Button
                    val sUri = subtitleUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "Burn Subtitles") { appContext ->
                        processState = ProcessState.Processing(0f, "Burning subtitles into video...")
                        val result = VideoEngine.burnSubtitles(
                            context = appContext,
                            videoUri = vUri,
                            subtitleUri = sUri,
                            fontSize = fontSize.toInt(),
                            onProgress = { p, msg ->
                                processState = ProcessState.Processing(p, msg)
                            }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Subtitles burned into video!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to burn subtitles")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo),
                enabled = videoUri != null && subtitleUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Burn Subtitles & Export", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = videoSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val appContext = context.applicationContext
                        val baseName = (videoName ?: "video").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = appContext,
                            file = success.outputFile,
                            mimeType = "video/mp4",
                            displayName = "${baseName}_captioned.mp4"
                        )
                        Toast.makeText(appContext, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, "video/mp4")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
