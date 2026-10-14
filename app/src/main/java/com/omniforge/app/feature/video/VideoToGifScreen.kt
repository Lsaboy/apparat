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
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.video.VideoEngine
import kotlinx.coroutines.launch

@Composable
fun VideoToGifScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var fps by remember { mutableFloatStateOf(15f) }
    var width by remember { mutableFloatStateOf(480f) }
    var durationSec by remember { mutableFloatStateOf(5f) }
    var processState by remember { mutableStateOf<ProcessState>(ProcessState.Idle) }

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
        title = "Video to Smooth GIF Maker",
        categoryName = "Video & Audio",
        accentColor = AccentVideo,
        onBack = onBack
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Max Duration: ${durationSec.toInt()}s",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = durationSec,
                        onValueChange = { durationSec = it },
                        valueRange = 2f..15f,
                        steps = 12,
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
                    scope.launch {
                        processState = ProcessState.Processing(0.3f, "Generating GIF with 2-pass palettegen...")
                        val result = VideoEngine.videoToGif(
                            context = context,
                            videoUri = uri,
                            fps = fps.toInt(),
                            width = width.toInt(),
                            startSec = 0f,
                            durationSec = durationSec,
                            onProgress = { p, msg -> processState = ProcessState.Processing(p, msg) }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Smooth animated GIF generated!"
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
                        val base = (fileName ?: "animation").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = "image/gif",
                            displayName = "${base}_palette.gif"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/OmniForge" else "Failed to save", Toast.LENGTH_SHORT).show()
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
