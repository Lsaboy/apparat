package com.omniforge.app.feature.image

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.image.ImageEngine
import kotlinx.coroutines.launch

@Composable
fun ImageShrinkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var quality by remember { mutableFloatStateOf(75f) }
    var maxDimension by remember { mutableFloatStateOf(1920f) }
    var stripExif by remember { mutableStateOf(true) }

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
        title = "Photo Shrinker & Optimizer",
        categoryName = "Photo & Image",
        accentColor = AccentImage,
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
                placeholderText = "Select Photo to Shrink",
                icon = Icons.Default.Image,
                accentColor = AccentImage,
                onClick = { picker.launch(arrayOf("image/*")) }
            )

            // Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Quality
                    Text(
                        text = "Compression Quality: ${quality.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..95f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Max Dimension
                    Text(
                        text = "Max Dimension: ${maxDimension.toInt()}px",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Slider(
                        value = maxDimension,
                        onValueChange = { maxDimension = it },
                        valueRange = 720f..3840f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentImage,
                            activeTrackColor = AccentImage,
                            inactiveTrackColor = DarkBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Strip EXIF
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strip EXIF / GPS Metadata",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "Removes location and camera details for privacy.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        Switch(
                            checked = stripExif,
                            onCheckedChange = { stripExif = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AmoledBlack,
                                checkedTrackColor = AccentImage,
                                uncheckedTrackColor = DarkBorder
                            )
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    scope.launch {
                        processState = ProcessState.Processing(0.5f, "Optimizing photo...")
                        val result = ImageEngine.shrinkImage(
                            context = context,
                            inputUri = uri,
                            quality = quality.toInt(),
                            maxDimension = maxDimension.toInt(),
                            stripExif = stripExif
                        )
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Photo shrunk successfully!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to shrink photo")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentImage),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shrink Photo", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentImage)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = "image/jpeg",
                            displayName = "shrunk_${fileName ?: "photo.jpg"}"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/OmniForge" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, "image/jpeg")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
