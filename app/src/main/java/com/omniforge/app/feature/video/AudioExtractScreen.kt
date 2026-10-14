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
import androidx.compose.material.icons.filled.Audiotrack
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
import com.omniforge.app.engine.video.AudioOutputFormat
import com.omniforge.app.engine.video.VideoEngine
import kotlinx.coroutines.launch

@Composable
fun AudioExtractScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var targetFormat by remember { mutableStateOf(AudioOutputFormat.MP3) }
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
        title = "Extract Audio from Video",
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
                placeholderText = "Select Video to Rip Audio",
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
                        text = "Output Audio Format",
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
                        AudioOutputFormat.entries.forEach { format ->
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
                        processState = ProcessState.Processing(0.3f, "Extracting audio track...")
                        val result = VideoEngine.extractAudio(
                            context = context,
                            videoUri = uri,
                            targetFormat = targetFormat
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Audio extracted as ${targetFormat.name}!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to extract audio")
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
                Icon(imageVector = Icons.Default.Audiotrack, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Extract ${targetFormat.name}", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val baseName = (fileName ?: "audio").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = targetFormat.mimeType,
                            displayName = "${baseName}_extracted${targetFormat.extension}"
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
