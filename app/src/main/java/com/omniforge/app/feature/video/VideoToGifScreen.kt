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
import androidx.compose.material.icons.filled.Gif
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
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "video_to_gif"

@Composable
fun VideoToGifScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }

    var fps by retained(JOB_KEY, "fps") { mutableFloatStateOf(15f) }
    var width by retained(JOB_KEY, "width") { mutableFloatStateOf(480f) }
    var startSec by retained(JOB_KEY, "startSec") { mutableFloatStateOf(0f) }
    var durationSec by retained(JOB_KEY, "durationSec") { mutableFloatStateOf(5f) }
    var videoDurationSec by retained(JOB_KEY, "videoDurationSec") { mutableFloatStateOf(0f) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    videoDurationSec = durMs / 1000f
                    if (videoDurationSec > 1f && startSec >= videoDurationSec) {
                        startSec = (videoDurationSec - 1f).coerceAtLeast(0f)
                    }
                } catch (_: Exception) {
                    videoDurationSec = 0f
                } finally {
                    retriever.release()
                }
            }
        } else {
            videoDurationSec = 0f
            startSec = 0f
        }
    }

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
        title = "Video to GIF",
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
                placeholderText = "Select Video to Create GIF",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Frame Rate: ${fps.toInt()} FPS",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = fps,
                        onValueChange = { fps = it },
                        valueRange = 8f..30f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentVideo,
                            activeTrackColor = AccentVideo,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Resolution Width: ${width.toInt()}px",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = width,
                        onValueChange = { width = it },
                        valueRange = 320f..720f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentVideo,
                            activeTrackColor = AccentVideo,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    val maxStart = if (videoDurationSec > 1f) (videoDurationSec - 1f).coerceIn(0f, 60f) else 0f
                    if (maxStart > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start Offset: ${startSec.toInt()}s",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Slider(
                            value = startSec.coerceIn(0f, maxStart),
                            onValueChange = { startSec = it },
                            valueRange = 0f..maxStart,
                            steps = (maxStart - 1).toInt().coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentVideo,
                                activeTrackColor = AccentVideo,
                                inactiveTrackColor = DarkBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val maxDur = if (videoDurationSec > 0f) (videoDurationSec - startSec).coerceIn(1f, 15f) else 15f
                    val currentDur = durationSec.coerceIn(1f, maxDur)
                    Text(
                        text = "Max Duration: ${currentDur.toInt()}s",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = currentDur,
                        onValueChange = { durationSec = it },
                        valueRange = 1f..maxDur,
                        steps = if (maxDur > 1f) (maxDur - 1).toInt().coerceAtLeast(0) else 0,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentVideo,
                            activeTrackColor = AccentVideo,
                            inactiveTrackColor = DarkBorder
                        )
                    )
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val effectiveDur = if (videoDurationSec > 0f) durationSec.coerceIn(1f, (videoDurationSec - startSec).coerceAtLeast(1f)) else durationSec
                    ToolJobs.launch(context, JOB_KEY, "Video to GIF") { appContext ->
                        processState = ProcessState.Processing(0f, "Reading video frames...")
                        val result = VideoEngine.videoToGif(
                            context = appContext,
                            videoUri = uri,
                            fps = fps.toInt(),
                            width = width.toInt(),
                            startSec = startSec,
                            durationSec = effectiveDur,
                            onProgress = { p, msg -> processState = ProcessState.Processing(p, msg) }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Animated GIF created!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to generate GIF")
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
                Icon(imageVector = Icons.Default.Gif, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Smooth GIF", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val appContext = context.applicationContext
                        val base = (fileName ?: "animation").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = appContext,
                            file = success.outputFile,
                            mimeType = "image/gif",
                            displayName = "${base}.gif"
                        )
                        Toast.makeText(appContext, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, "image/gif")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
