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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
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
import com.omniforge.app.engine.image.ExifInfo
import com.omniforge.app.engine.image.ImageEngine
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "exif_strip"

@Composable
fun ExifStripperScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var exifInfo by retained(JOB_KEY, "exifInfo") { mutableStateOf<ExifInfo?>(null) }
    var saveMode by retained(JOB_KEY, "saveMode") { mutableStateOf(SaveMode.NEW_COPY) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(initialUri) {
        initialUri?.let {
            exifInfo = ImageEngine.getExifData(context, it)
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
            scope.launch {
                exifInfo = ImageEngine.getExifData(context, it)
            }
        }
    }

    ToolScaffold(
        title = "EXIF Metadata Stripper",
        categoryName = "Photo & Image",
        accentColor = AccentImage,
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
                placeholderText = "Select Photo to Inspect & Strip",
                icon = Icons.Default.Image,
                accentColor = AccentImage,
                onClick = { picker.launch(arrayOf("image/*")) }
            )

            // Save Mode
            SaveModeSelector(
                saveMode = saveMode,
                onSaveModeChange = { saveMode = it },
                accentColor = AccentImage
            )

            // Detected Metadata Card
            if (exifInfo != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Detected Metadata in Image",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val info = exifInfo!!
                        MetaItem(label = "Camera", value = listOfNotNull(info.cameraMake, info.cameraModel).joinToString(" "))
                        MetaItem(label = "Timestamp", value = info.dateTime)
                        MetaItem(label = "ISO", value = info.iso)
                        MetaItem(label = "Aperture / Exposure", value = listOfNotNull(info.fNumber?.let { "f/$it" }, info.exposureTime).joinToString(" • "))
                        if (info.latitude != null && info.longitude != null) {
                            MetaItem(label = "GPS Coordinates", value = "${info.latitude}, ${info.longitude}", isWarning = true)
                        } else {
                            MetaItem(label = "GPS", value = "No location found")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    ToolJobs.launch(context, JOB_KEY, "EXIF Stripper") {
                        processState = ProcessState.Processing(0.5f, "Stripping all metadata...")
                        val result = ImageEngine.stripExif(context, uri)
                        result.fold(
                            onSuccess = { outputFile ->
                                if (saveMode == SaveMode.OVERWRITE_ORIGINAL) {
                                    val overwritten = FileUtils.overwriteOriginal(context, uri, outputFile)
                                    if (overwritten) {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Original photo stripped of EXIF in-place!"
                                        )
                                    } else {
                                        processState = ProcessState.Success(
                                            outputFile = outputFile,
                                            message = "Metadata stripped! (Saved as copy in Downloads)"
                                        )
                                    }
                                } else {
                                    processState = ProcessState.Success(
                                        outputFile = outputFile,
                                        message = "All EXIF & Location tags removed!"
                                    )
                                }
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to strip EXIF")
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
                Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Strip EXIF & Clean Photo", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentImage)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val baseName = (fileName ?: "photo").substringBeforeLast(".")
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = "image/jpeg",
                            displayName = "${baseName}_clean.jpg"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
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

@Composable
private fun MetaItem(label: String, value: String?, isWarning: Boolean = false) {
    if (!value.isNullOrBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (isWarning) ErrorRed else TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
