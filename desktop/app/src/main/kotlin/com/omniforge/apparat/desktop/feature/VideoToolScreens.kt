package com.omniforge.apparat.desktop.feature

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.AccentVideo
import com.omniforge.apparat.core.theme.onPrimaryForAccent
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.engine.CropAspect
import com.omniforge.apparat.desktop.engine.DesktopVideoTools
import com.omniforge.apparat.desktop.engine.FrameFormat
import com.omniforge.apparat.desktop.engine.JoinMode
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun describe(p: MediaProbe, size: Long?): String {
    val s = p.durationSec.toLong()
    val fps = p.fps?.let { " • ${if (it % 1.0 == 0.0) it.toInt().toString() else "%.2f".format(it)} fps" }.orEmpty()
    return "${p.displayWidth}x${p.displayHeight}$fps • %d:%02d • %s".format(s / 60, s % 60, FileUtils.formatFileSize(size ?: 0))
}

@Composable
private fun OptionsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ApplyButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AccentVideo, contentColor = onPrimaryForAccent(AccentVideo)),
        enabled = enabled
    ) {
        Icon(icon, contentDescription = null, tint = LocalContentColor.current)
        Spacer(Modifier.width(8.dp))
        Text(label, color = LocalContentColor.current, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Outcome(state: ProcessState, fileName: String?, originalSize: Long?, suffix: String) {
    val context = LocalContext.current
    ProcessStateView(state = state, accentColor = AccentVideo)
    (state as? ProcessState.Success)?.let { success ->
        val mime = FileUtils.mimeForFile(success.outputFile)
        ResultCard(
            outputFile = success.outputFile,
            originalSize = originalSize,
            message = success.message,
            saveMimeType = mime,
            saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "video", suffix, success.outputFile) },
            onShare = { FileUtils.shareFile(context, success.outputFile, mime) }
        )
    }
}

/**
 * Shared frame for the one-video tools: file picker with probe details, the tool's [options] (given
 * the probe once it's known), an apply button that runs [run] as a background job, and the result.
 */
@Composable
private fun SingleVideoTool(
    jobKey: String,
    title: String,
    initialUri: Uri?,
    onBack: () -> Unit,
    buttonLabel: String,
    buttonIcon: ImageVector,
    suffix: String,
    successMessage: String,
    ready: (MediaProbe) -> Boolean = { true },
    options: @Composable ColumnScope.(MediaProbe?) -> Unit,
    run: (MediaProbe) -> (suspend (Context, Uri, (Float, String) -> Unit) -> Result<File>)
) {
    val context = LocalContext.current
    var selectedUri by retained(jobKey, "selectedUri") { mutableStateOf(initialUri) }
    var fileName by retained(jobKey, "fileName") { mutableStateOf(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(jobKey, "fileSize") { mutableStateOf(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var probe by retained(jobKey, "probe") { mutableStateOf<MediaProbe?>(null) }
    var processState by retained(jobKey, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        if (probe == null) probe = withContext(Dispatchers.IO) { MediaProbe.of(uri.file) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            fileName = FileUtils.getFileName(context, uri)
            fileSize = FileUtils.getFileSize(context, uri)
            probe = null
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(title = title, categoryName = "Video & Audio", accentColor = AccentVideo, onBack = onBack, jobKey = jobKey) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            val p = probe
            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                fileDetails = p?.let { describe(it, fileSize) },
                placeholderText = "Select Video",
                icon = Icons.Default.VideoFile,
                accentColor = AccentVideo,
                onClick = { picker.launch(arrayOf("video/*")) }
            )
            if (selectedUri != null && p != null && !p.hasVideo) {
                Text("This file has no video track.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OptionsCard { options(p) }
            ApplyButton(
                label = buttonLabel,
                icon = buttonIcon,
                enabled = selectedUri != null && p != null && p.hasVideo && ready(p) && processState !is ProcessState.Processing
            ) {
                val uri = selectedUri ?: return@ApplyButton
                val info = probe ?: return@ApplyButton
                val task = run(info)
                ToolJobs.launch(context, jobKey, title) { appContext ->
                    processState = ProcessState.Processing(0f, "Starting...")
                    task(appContext, uri) { pr, msg -> processState = ProcessState.Processing(pr, msg) }.fold(
                        onSuccess = { processState = ProcessState.Success(it, successMessage) },
                        onFailure = { processState = ProcessState.Error(it.localizedMessage ?: "Couldn't process the video") }
                    )
                }
            }
            Outcome(processState, fileName, fileSize, suffix)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------- crop

/** Desktop-only: crop a video to a preset shape (centred), re-encoded to H.264. */
@Composable
fun VideoCropScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    var aspect by retained("video_crop", "aspect") { mutableStateOf(CropAspect.SQUARE) }
    SingleVideoTool(
        jobKey = "video_crop",
        title = "Crop Video",
        initialUri = initialUri,
        onBack = onBack,
        buttonLabel = "Crop",
        buttonIcon = Icons.Default.Crop,
        suffix = "_cropped",
        successMessage = "Video cropped!",
        ready = { p -> DesktopVideoTools.centeredCrop(p.displayWidth, p.displayHeight, aspect).let { it.width < p.displayWidth - 1 || it.height < p.displayHeight - 1 } },
        options = { p ->
            Heading("Shape")
            BraunSegmentedControl(
                options = CropAspect.entries.toList(),
                selectedOption = aspect,
                onOptionSelected = { aspect = it },
                labelForOption = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            if (p != null && p.hasVideo) {
                val r = DesktopVideoTools.centeredCrop(p.displayWidth, p.displayHeight, aspect)
                if (r.width >= p.displayWidth - 1 && r.height >= p.displayHeight - 1) Hint("The video already has this shape.")
                else Hint("Keeps the centre ${r.width}x${r.height} of ${p.displayWidth}x${p.displayHeight}. 9:16 suits Reels, Shorts and Stories; 1:1 and 4:5 suit feeds.")
            } else {
                Hint("The middle of the picture is kept; the edges are cut off.")
            }
        },
        run = { p ->
            val rect = DesktopVideoTools.centeredCrop(p.displayWidth, p.displayHeight, aspect)
            val r: suspend (Context, Uri, (Float, String) -> Unit) -> Result<File> = { ctx, uri, prog -> DesktopVideoTools.cropVideo(ctx, uri, rect, prog) }
            r
        }
    )
}

// ------------------------------------------------------------------------------ resolution / fps

/** Desktop-only: change a video's resolution and/or frame rate. */
@Composable
fun VideoResizeScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    var height by retained("video_resize", "height") { mutableStateOf<Int?>(1080) }
    var fps by retained("video_resize", "fps") { mutableStateOf<Double?>(null) }
    SingleVideoTool(
        jobKey = "video_resize",
        title = "Resolution & Frame Rate",
        initialUri = initialUri,
        onBack = onBack,
        buttonLabel = "Convert",
        buttonIcon = Icons.Default.AspectRatio,
        suffix = "_resized",
        successMessage = "Video converted!",
        ready = { p ->
            val sameH = height == null || height == p.displayHeight
            val f = fps
            val sameF = f == null || (p.fps?.let { kotlin.math.abs(it - f) < 0.01 } == true)
            !(sameH && sameF)
        },
        options = { p ->
            Heading("Height")
            BraunSegmentedControl(
                options = listOf<Int?>(null) + DesktopVideoTools.HEIGHTS,
                selectedOption = height,
                onOptionSelected = { height = it },
                labelForOption = { it?.let { h -> "${h}p" } ?: "Same" },
                modifier = Modifier.fillMaxWidth()
            )
            Heading("Frame rate")
            BraunSegmentedControl(
                options = listOf<Double?>(null) + DesktopVideoTools.FRAME_RATES,
                selectedOption = fps,
                onOptionSelected = { fps = it },
                labelForOption = { it?.let { f -> "${f.toInt()}" } ?: "Same" },
                modifier = Modifier.fillMaxWidth()
            )
            val h = height
            if (p != null && h != null && h > p.displayHeight) Hint("Going above the original ${p.displayHeight}p makes the file bigger without adding detail.")
            else Hint("Width follows the picture's shape. Re-encoded to H.264; sound is copied when it can be.")
        },
        run = { _ ->
            val h = height; val f = fps
            val r: suspend (Context, Uri, (Float, String) -> Unit) -> Result<File> = { ctx, uri, prog -> DesktopVideoTools.changeResolution(ctx, uri, h, f, prog) }
            r
        }
    )
}

// ---------------------------------------------------------------------------- frames / contact sheet

private enum class FramesMode(val label: String) { SHEET("Contact sheet"), FRAMES("Separate frames") }

/** Desktop-only: a contact sheet (one image of thumbnails) or evenly spaced frames in a ZIP. */
@Composable
fun VideoFramesScreen(initialUri: Uri? = null, onBack: () -> Unit) {
    var mode by retained("video_frames", "mode") { mutableStateOf(FramesMode.SHEET) }
    var columns by retained("video_frames", "columns") { mutableIntStateOf(4) }
    var rows by retained("video_frames", "rows") { mutableIntStateOf(4) }
    var count by retained("video_frames", "count") { mutableIntStateOf(10) }
    var format by retained("video_frames", "format") { mutableStateOf(FrameFormat.JPG) }
    SingleVideoTool(
        jobKey = "video_frames",
        title = "Frames & Contact Sheet",
        initialUri = initialUri,
        onBack = onBack,
        buttonLabel = if (mode == FramesMode.SHEET) "Make contact sheet" else "Save $count frames",
        buttonIcon = if (mode == FramesMode.SHEET) Icons.Default.GridOn else Icons.Default.PhotoLibrary,
        suffix = if (mode == FramesMode.SHEET) "_sheet" else "_frames",
        successMessage = if (mode == FramesMode.SHEET) "Contact sheet ready!" else "Frames ready (ZIP)!",
        options = { _ ->
            BraunSegmentedControl(
                options = FramesMode.entries.toList(),
                selectedOption = mode,
                onOptionSelected = { mode = it },
                labelForOption = { it.label },
                modifier = Modifier.fillMaxWidth()
            )
            if (mode == FramesMode.SHEET) {
                Heading("Columns")
                BraunSegmentedControl(listOf(3, 4, 5, 6), columns, { columns = it }, Modifier.fillMaxWidth(), { "$it" })
                Heading("Rows")
                BraunSegmentedControl(listOf(3, 4, 5, 6, 8), rows, { rows = it }, Modifier.fillMaxWidth(), { "$it" })
                Hint("${columns * rows} evenly spaced frames, each labelled with its time, on one 1920 px wide JPG.")
            } else {
                Heading("How many")
                BraunSegmentedControl(listOf(5, 10, 20, 50, 100), count, { count = it }, Modifier.fillMaxWidth(), { "$it" })
                Heading("Format")
                BraunSegmentedControl(FrameFormat.entries.toList(), format, { format = it }, Modifier.fillMaxWidth(), { it.label })
                Hint("Full-size frames spread evenly across the video, named with their time, packed into one ZIP.")
            }
        },
        run = { _ ->
            val m = mode; val c = columns; val r = rows; val n = count; val f = format
            val task: suspend (Context, Uri, (Float, String) -> Unit) -> Result<File> = { ctx, uri, prog ->
                if (m == FramesMode.SHEET) DesktopVideoTools.contactSheet(ctx, uri, c, r, onProgress = prog)
                else DesktopVideoTools.extractFrames(ctx, uri, n, f, prog)
            }
            task
        }
    )
}

// -------------------------------------------------------------------------------------------- join

private const val JOIN_KEY = "video_join"

/** Desktop-only: join clips in order into one MP4 (lossless when they match). */
@Composable
fun VideoJoinScreen(initialUris: List<Uri> = emptyList(), onBack: () -> Unit) {
    val context = LocalContext.current
    val clips = retained(JOIN_KEY, "clips") { mutableStateListOf<File>().apply { addAll(initialUris.map { it.file }) } }
    val probes = retained(JOIN_KEY, "probes") { mutableStateMapOf<String, MediaProbe?>() }
    var forceReencode by retained(JOIN_KEY, "forceReencode") { mutableStateOf(false) }
    var processState by retained(JOIN_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    // Videos dropped on the window while the screen was kept from an earlier visit.
    LaunchedEffect(initialUris) { initialUris.map { it.file }.filter { it !in clips }.let { clips += it } }
    LaunchedEffect(clips.toList()) {
        for (f in clips.toList()) if (f.path !in probes) probes[f.path] = withContext(Dispatchers.IO) { MediaProbe.of(f) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        clips += uris.map { it.file }.filter { it !in clips }
        processState = ProcessState.Idle
    }

    val known = clips.mapNotNull { probes[it.path] }
    val sizes = known.map { it.displayWidth to it.displayHeight }.distinct()
    val totalSec = known.sumOf { it.durationSec }.toLong()
    val totalBytes = clips.sumOf { it.length() }

    ToolScaffold(title = "Join Videos", categoryName = "Video & Audio", accentColor = AccentVideo, onBack = onBack, jobKey = JOIN_KEY) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            OptionsCard {
                Heading("Clips, in order (${clips.size})")
                if (clips.isEmpty()) Hint("Add two or more videos. They're joined top to bottom.")
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    clips.forEachIndexed { i, f ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                val p = probes[f.path]
                                Hint(
                                    when {
                                        f.path !in probes -> "Reading..."
                                        p == null || !p.hasVideo -> "Not a video. Remove it."
                                        else -> describe(p, f.length())
                                    }
                                )
                            }
                            IconButton(onClick = { clips.add(i - 1, clips.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                            IconButton(onClick = { clips.add(i + 1, clips.removeAt(i)) }, enabled = i < clips.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                            IconButton(onClick = { clips.removeAt(i); processState = ProcessState.Idle }) { Icon(Icons.Default.Close, "Remove ${f.name}", Modifier.size(18.dp)) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, shape = RoundedCornerShape(6.dp), enabled = processState !is ProcessState.Processing) {
                        Icon(Icons.Default.VideoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add videos…")
                    }
                    if (clips.isNotEmpty()) TextButton(onClick = { clips.clear(); processState = ProcessState.Idle }) { Text("Clear") }
                }
                if (clips.size >= 2 && known.size == clips.size) {
                    Hint("Total %d:%02d • %s".format(totalSec / 60, totalSec % 60, FileUtils.formatFileSize(totalBytes)))
                    Hint(
                        if (sizes.size > 1) "Clips differ in size: all are fitted to the first clip's ${sizes.first().first}x${sizes.first().second} (black bars where needed) and re-encoded."
                        else "Clips from the same camera/app are joined instantly with no quality loss; otherwise they're re-encoded."
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = forceReencode, onCheckedChange = { forceReencode = it }, colors = CheckboxDefaults.colors(checkedColor = AccentVideo))
                    Text("Always re-encode (fixes glitches at the joins)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            val usable = clips.size >= 2 && clips.all { probes[it.path]?.hasVideo == true }
            ApplyButton("Join ${clips.size} videos", Icons.Default.Movie, usable && processState !is ProcessState.Processing) {
                val list = clips.map { Uri.fromFile(it) }
                val force = forceReencode
                ToolJobs.launch(context, JOIN_KEY, "Join Videos") { appContext ->
                    processState = ProcessState.Processing(0f, "Joining...")
                    DesktopVideoTools.joinVideos(appContext, list, force) { p, msg -> processState = ProcessState.Processing(p, msg) }.fold(
                        onSuccess = { (file, how) ->
                            processState = ProcessState.Success(file, if (how == JoinMode.LOSSLESS) "Joined with no quality loss!" else "Joined (re-encoded to H.264)!")
                        },
                        onFailure = { processState = ProcessState.Error(it.localizedMessage ?: "Couldn't join the videos") }
                    )
                }
            }
            Outcome(processState, clips.firstOrNull()?.name, totalBytes.takeIf { it > 0 }, "_joined")
            Spacer(Modifier.height(24.dp))
        }
    }
}
