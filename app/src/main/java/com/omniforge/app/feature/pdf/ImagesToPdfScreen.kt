package com.omniforge.app.feature.pdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.pdf.PdfEngine
import kotlinx.coroutines.launch

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "images_to_pdf"

@Composable
fun ImagesToPdfScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUris by retained(JOB_KEY, "selectedUris") { mutableStateOf<List<Uri>>(emptyList()) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(
        title = "Photos to PDF",
        categoryName = "PDF Tools",
        accentColor = AccentPdf,
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { multiPicker.launch(arrayOf("image/*")) },
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = BorderStroke(1.dp, if (selectedUris.isNotEmpty()) AccentPdf else DarkBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = AccentPdf,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (selectedUris.isEmpty()) "Select Photos to Combine" else "${selectedUris.size} Photos Selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (selectedUris.isEmpty()) "Tap to select one or multiple images" else "Tap to replace selection",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (selectedUris.isEmpty()) return@Button
                    ToolJobs.launch(context, JOB_KEY, "Photos to PDF") {
                        processState = ProcessState.Processing(0.5f, "Combining photos into PDF...")
                        val result = PdfEngine.imagesToPdf(context, selectedUris)
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "PDF generated with ${selectedUris.size} pages!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to generate PDF")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf),
                enabled = selectedUris.isNotEmpty() && processState !is ProcessState.Processing
            ) {
                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate PDF", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    message = success.message,
                    onSaveToDownloads = {
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = "application/pdf",
                            displayName = "combined_photos.pdf"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, "application/pdf")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
