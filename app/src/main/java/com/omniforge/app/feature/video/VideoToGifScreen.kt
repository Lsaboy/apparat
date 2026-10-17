package com.omniforge.app.feature.video

import androidx.compose.foundation.BorderStroke
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "video_to_gif"

@Composable
fun VideoToGifScreen(
    initialUri: Uri? = null,
    onNavigateToTool: ((String, Uri) -> Unit)? = null,
    onBack: () -> Unit
) {
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

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(null) }
    var lastLoadedUri by retained(JOB_KEY, "lastLoadedUri") { mutableStateOf<Uri?>(null) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            val isNewUri = uri != lastLoadedUri
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
                    val total = durMs / 1000f
                    videoDurationSec = total
                    if (isNewUri) {
                        startSec = 0f
                        if (total > 0f) {
                            durationSec = min(5f, total)
                        }
                        lastLoadedUri = uri
                    }
                } catch (_: Exception) {
                    videoDurationSec = 0f
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        } else {
            videoDurationSec = 0f
            startSec = 0f
            lastLoadedUri = null
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
                                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo)
                            ) {
                                Text("Switch to New File", color = onPrimaryForAccent(AccentVideo), fontSize = 12.sp)
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
                fileDetails = if (videoDurationSec > 0f) "Duration: %.1fs • %s".format(Locale.US, videoDurationSec, FileUtils.formatFileSize(fileSize ?: 0L)) else null,
                placeholderText = "Select Video to Create GIF",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Frame Rate: ${fps.toInt()} FPS",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
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
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Output Width: ${width.toInt()}px",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = width,
                        onValueChange = { width = it },
                        valueRange = 240f..720f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentVideo,
                            activeTrackColor = AccentVideo,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (videoDurationSec > 1f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val maxStart = (videoDurationSec - 1f).coerceAtLeast(0f)
                        Text(
                            text = "Start Offset: %.1fs (of %.1fs)".format(Locale.US, startSec, videoDurationSec),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Slider(
                            value = startSec.coerceIn(0f, maxStart),
                            onValueChange = { startSec = it },
                            valueRange = 0f..maxStart,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentVideo,
                                activeTrackColor = AccentVideo,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val maxDurationAllowed = if (videoDurationSec > 0f) {
                        (videoDurationSec - startSec).coerceIn(1f, 15f)
                    } else 15f
                    Text(
                        text = "GIF Duration: %.1fs".format(Locale.US, durationSec.coerceIn(1f, maxDurationAllowed)),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = durationSec.coerceIn(1f, maxDurationAllowed),
                        onValueChange = { durationSec = it },
                        valueRange = 1f..maxDurationAllowed,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentVideo,
                            activeTrackColor = AccentVideo,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    val effectiveDur = if (videoDurationSec > 0f) durationSec.coerceIn(1f, (videoDurationSec - startSec).coerceAtLeast(1f)) else durationSec
                    val totalFrames = (effectiveDur * fps).toInt()
                    val estBytes = (totalFrames * (width / 480f) * 35_000L).toLong()

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Estimated output: $totalFrames frames (~${FileUtils.formatFileSize(estBytes)})",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Gif, contentDescription = null, tint = onPrimaryForAccent(AccentVideo))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Smooth GIF", color = onPrimaryForAccent(AccentVideo), fontWeight = FontWeight.Bold)
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
                        FileUtils.shareFile(context.applicationContext, success.outputFile, "image/gif")
                    },
                    chainActions = if (onNavigateToTool != null) {
                        listOf(
                            ToolChainAction(
                                label = "Calculate Hash",
                                icon = Icons.Default.Fingerprint,
                                onClick = {
                                    val uri = FileUtils.getContentUri(context, success.outputFile)
                                    onNavigateToTool("hash_verifier", uri)
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
