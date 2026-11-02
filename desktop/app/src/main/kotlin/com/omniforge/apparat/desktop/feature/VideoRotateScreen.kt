package com.omniforge.apparat.desktop.feature

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.AccentVideo
import com.omniforge.apparat.core.theme.onPrimaryForAccent
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.engine.DesktopMediaTools
import com.omniforge.apparat.desktop.engine.VideoTurn
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val JOB_KEY = "video_rotate"

private fun speedLabel(s: Float) = if (s == 1f) "1×" else if (s < 1f) "${s}×" else "${if (s % 1f == 0f) s.toInt().toString() else s.toString()}×"

/** Desktop-only: rotate (lossless when that's all), flip, and change speed. */
@Composable
fun VideoRotateScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var details by retained(JOB_KEY, "details") { mutableStateOf<String?>(null) }
    var turn by retained(JOB_KEY, "turn") { mutableStateOf(VideoTurn.NONE) }
    var flipH by retained(JOB_KEY, "flipH") { mutableStateOf(false) }
    var flipV by retained(JOB_KEY, "flipV") { mutableStateOf(false) }
    var speed by retained(JOB_KEY, "speed") { mutableFloatStateOf(1f) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        details = withContext(Dispatchers.IO) {
            MediaProbe.of(uri.file)?.let { p ->
                val s = p.durationSec.toLong()
                "${p.displayWidth}x${p.displayHeight} • %d:%02d • %s".format(s / 60, s % 60, FileUtils.formatFileSize(fileSize ?: 0))
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            fileName = FileUtils.getFileName(context, uri)
            fileSize = FileUtils.getFileSize(context, uri)
            details = null
            processState = ProcessState.Idle
        }
    }

    val lossless = !flipH && !flipV && speed == 1f
    val nothingChosen = turn == VideoTurn.NONE && lossless

    ToolScaffold(title = "Rotate, Flip & Speed", categoryName = "Video & Audio", accentColor = AccentVideo, onBack = onBack, jobKey = JOB_KEY) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                fileDetails = details,
                placeholderText = "Select Video",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Rotate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BraunSegmentedControl(
                        options = VideoTurn.entries.toList(),
                        selectedOption = turn,
                        onOptionSelected = { turn = it },
                        labelForOption = { it.label },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = flipH, onCheckedChange = { flipH = it }, colors = CheckboxDefaults.colors(checkedColor = AccentVideo))
                        Text("Mirror left ↔ right", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(16.dp))
                        Checkbox(checked = flipV, onCheckedChange = { flipV = it }, colors = CheckboxDefaults.colors(checkedColor = AccentVideo))
                        Text("Flip upside down", style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text("Speed: ${speedLabel(speed)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    BraunSegmentedControl(
                        options = DesktopMediaTools.SPEEDS,
                        selectedOption = speed,
                        onOptionSelected = { speed = it },
                        labelForOption = { speedLabel(it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (lossless) "Rotation only: done instantly with no quality loss (the video's rotation tag is changed)."
                        else "Flipping or changing speed re-encodes the video (H.264). Sound keeps its pitch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val t = turn; val h = flipH; val v = flipV; val s = speed
                    ToolJobs.launch(context, JOB_KEY, "Rotate, Flip & Speed") { appContext ->
                        processState = ProcessState.Processing(0f, "Processing video...")
                        DesktopMediaTools.transformVideo(appContext, uri, t, h, v, s) { p, msg ->
                            processState = ProcessState.Processing(p, msg)
                        }.fold(
                            onSuccess = { processState = ProcessState.Success(it, "Video ready!") },
                            onFailure = { processState = ProcessState.Error(it.localizedMessage ?: "Couldn't process the video") }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo, contentColor = onPrimaryForAccent(AccentVideo)),
                enabled = selectedUri != null && !nothingChosen && processState !is ProcessState.Processing
            ) {
                Icon(Icons.Default.Rotate90DegreesCw, contentDescription = null, tint = LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text("Apply", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            (processState as? ProcessState.Success)?.let { success ->
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "video", "_edited", success.outputFile) },
                    onShare = { FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile)) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
