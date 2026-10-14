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
import androidx.compose.material.icons.filled.Transform
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
import com.omniforge.app.engine.video.VideoOutputFormat
import kotlinx.coroutines.launch

@Composable
fun VideoConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var targetFormat by remember { mutableStateOf(VideoOutputFormat.MP4) }
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
        title = "Video Format Exchange",
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
                placeholderText = "Select Video to Convert",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            // Target Format Cards
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Target Container / Format",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoOutputFormat.entries.forEach { format ->
                            val isSelected = targetFormat == format
                            OutlinedButton(
                                onClick = { targetFormat = format },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) AccentVideo else DarkSurfaceElevated,
                                    contentColor = if (isSelected) AmoledBlack else TextPrimary
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AccentVideo else DarkBorder)
                            ) {
                                Text(
                                    text = format.name,
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
                    scope.launch {
                        processState = ProcessState.Processing(0.2f, "Transcoding to ${targetFormat.name}...")
                        val result = VideoEngine.convertFormat(
                            context = context,
                            inputUri = uri,
                            targetFormat = targetFormat,
                            onProgress = { p, msg -> processState = ProcessState.Processing(p, msg) }
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Converted to ${targetFormat.name}!"
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert to ${targetFormat.name}", color = AmoledBlack, fontWeight = FontWeight.Bold)
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
                            mimeType = targetFormat.mimeType,
                            displayName = "${baseName}_converted${targetFormat.extension}"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/OmniForge" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, targetFormat.mimeType)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
