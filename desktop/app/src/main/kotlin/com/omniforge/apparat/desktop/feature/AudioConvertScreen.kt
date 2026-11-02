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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.GraphicEq
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
import com.omniforge.apparat.desktop.engine.AudioConvertFormat
import com.omniforge.apparat.desktop.engine.DesktopMediaTools
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val JOB_KEY = "audio_convert"

/** Desktop-only: convert any audio (or a video's sound) to MP3/M4A/Opus/FLAC/WAV. */
@Composable
fun AudioConvertScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var details by retained(JOB_KEY, "details") { mutableStateOf<String?>(null) }
    var format by retained(JOB_KEY, "format") { mutableStateOf(AudioConvertFormat.MP3) }
    var bitrate by retained(JOB_KEY, "bitrate") { mutableIntStateOf(192) }
    var normalize by retained(JOB_KEY, "normalize") { mutableStateOf(false) }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        details = withContext(Dispatchers.IO) {
            MediaProbe.of(uri.file)?.let { p ->
                if (!p.hasAudio) "No sound in this file"
                else {
                    val s = p.durationSec.toLong()
                    listOfNotNull(
                        "%d:%02d".format(s / 60, s % 60),
                        p.audioCodec?.uppercase(),
                        p.audioSampleRate?.let { "${it / 1000.0} kHz" },
                        p.audioChannels?.let { if (it == 1) "mono" else if (it == 2) "stereo" else "$it ch" },
                        FileUtils.formatFileSize(fileSize ?: 0)
                    ).joinToString(" • ")
                }
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

    ToolScaffold(title = "Audio Converter", categoryName = "Video & Audio", accentColor = AccentVideo, onBack = onBack, jobKey = JOB_KEY) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                fileDetails = details,
                placeholderText = "Select Audio or Video",
                icon = Icons.Default.Audiotrack,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("audio/*", "video/*")) }
            )

            FormatSelectorRow(
                formats = AudioConvertFormat.entries.map { it.label },
                selectedFormat = format.label,
                onFormatSelect = { label -> format = AudioConvertFormat.entries.first { it.label == label } },
                accentColor = AccentVideo
            )

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (format.lossless) {
                        Text("Lossless: the sound is kept exactly (bigger files).", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Quality: $bitrate kbps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        BraunSegmentedControl(
                            options = listOf(96, 128, 192, 256, 320),
                            selectedOption = bitrate,
                            onOptionSelected = { bitrate = it },
                            labelForOption = { "$it" },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "128 kbps is fine for speech, 192 for music, 320 for the best MP3. Opus sounds as good at lower rates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Level the volume", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Evens out quiet and loud recordings (podcast/streaming loudness).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = normalize,
                            onCheckedChange = { normalize = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentVideo, checkedThumbColor = onPrimaryForAccent(AccentVideo))
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val chosen = format
                    ToolJobs.launch(context, JOB_KEY, "Audio Converter") { appContext ->
                        processState = ProcessState.Processing(0f, "Converting to ${chosen.label}...")
                        DesktopMediaTools.convertAudio(appContext, uri, chosen, bitrate, normalize) { p, msg ->
                            processState = ProcessState.Processing(p, msg)
                        }.fold(
                            onSuccess = { processState = ProcessState.Success(it, "Converted to ${chosen.label}!") },
                            onFailure = { processState = ProcessState.Error(it.localizedMessage ?: "Couldn't convert the audio") }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentVideo, contentColor = onPrimaryForAccent(AccentVideo)),
                enabled = selectedUri != null && processState !is ProcessState.Processing
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = LocalContentColor.current)
                Spacer(Modifier.width(8.dp))
                Text("Convert Audio", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            ProcessStateView(state = processState, accentColor = AccentVideo)

            (processState as? ProcessState.Success)?.let { success ->
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "audio", "", success.outputFile) },
                    onShare = { FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile)) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
