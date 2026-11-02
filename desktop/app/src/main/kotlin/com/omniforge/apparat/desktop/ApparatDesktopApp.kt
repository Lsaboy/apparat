package com.omniforge.apparat.desktop

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.core.theme.ApparatTheme
import com.omniforge.apparat.feature.home.HomeScreen
import com.omniforge.apparat.feature.image.DocumentScanScreen
import com.omniforge.apparat.feature.image.ExifStripperScreen
import com.omniforge.apparat.feature.image.ImageConvertScreen
import com.omniforge.apparat.feature.image.ImageShrinkScreen
import com.omniforge.apparat.feature.image.PaletteExtractorScreen
import com.omniforge.apparat.feature.pdf.ImagesToPdfScreen
import com.omniforge.apparat.feature.pdf.PdfMergeScreen
import com.omniforge.apparat.feature.pdf.PdfShrinkScreen
import com.omniforge.apparat.feature.pdf.PdfSplitScreen
import com.omniforge.apparat.feature.pdf.PdfToImagesScreen
import com.omniforge.apparat.feature.pdf.PdfUnlockScreen
import com.omniforge.apparat.feature.settings.CustomizationScreen
import com.omniforge.apparat.feature.utilities.Base64StudioScreen
import com.omniforge.apparat.feature.utilities.HashVerifierScreen
import com.omniforge.apparat.feature.utilities.JsonToolScreen
import com.omniforge.apparat.feature.utilities.QrStudioScreen
import com.omniforge.apparat.feature.video.AudioExtractScreen
import com.omniforge.apparat.feature.video.VideoCaptionScreen
import com.omniforge.apparat.feature.video.VideoConvertScreen
import com.omniforge.apparat.feature.video.VideoShrinkScreen
import com.omniforge.apparat.feature.video.VideoToGifScreen
import com.omniforge.apparat.feature.video.VideoTrimMuteScreen
import java.io.File

/** Every tool destination below (same routes as the Android MainActivity). */
val TOOL_ROUTES = setOf(
    "settings",
    "pdf_unlock", "pdf_shrink", "pdf_split", "pdf_merge", "pdf_to_images", "images_to_pdf",
    "video_shrink", "video_trim", "video_caption", "video_to_gif", "video_convert", "audio_extract",
    "image_shrink", "document_scan", "palette_extractor", "image_convert", "exif_strip",
    "qr_studio", "hash_verifier", "json_tool", "base64_studio"
)

/** A file (or files, or text) handed to the app: dropped on the window or opened with Apparat. */
data class SharedPayload(
    val uri: Uri? = null,
    val uris: List<Uri> = emptyList(),
    val mimeType: String? = null,
    val text: String? = null,
    val token: Long = System.nanoTime()
)

/** Navigation and incoming files for the desktop window (the desktop MainActivity). */
class AppController {
    val backStack: SnapshotStateList<String> = mutableStateListOf("home")
    var payload by mutableStateOf<SharedPayload?>(null)
    var forward by mutableStateOf(true)
        private set

    val current: String get() = backStack.last()

    /**
     * Merge PDFs and Photos to PDF import their incoming files in an effect keyed by the payload's
     * token, so their payload must stay put until the user leaves the screen (clearing it right
     * away changes the token and cancels the import halfway).
     */
    private val keepPayloadRoutes = setOf("pdf_merge", "images_to_pdf")
    private var keptPayloadRoute: String? = null

    private fun afterRouteChange() {
        val kept = keptPayloadRoute
        if (kept != null && current != kept) {
            keptPayloadRoute = null
            payload = null
        } else if (kept == null && current in keepPayloadRoutes && payload != null) {
            keptPayloadRoute = current
        }
    }

    fun navigate(route: String) {
        if (route == current) return
        forward = true
        if (route == "home") { backStack.clear(); backStack.add("home") } else backStack.add(route)
        afterRouteChange()
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        forward = false
        backStack.removeAt(backStack.lastIndex)
        afterRouteChange()
        return true
    }

    /** Whether [route] keeps its payload until left (see [keepPayloadRoutes]). */
    fun keepsPayload(route: String) = route in keepPayloadRoutes

    /** Same routing rules as Android's share/open intents. */
    fun handleFiles(files: List<File>) {
        val existing = files.filter { it.isFile }
        if (existing.isEmpty()) return
        val uris = existing.map { Uri.fromFile(it) }
        val resolver = Context.app.contentResolver
        if (uris.size == 1) {
            payload = SharedPayload(uri = uris[0], mimeType = resolver.getType(uris[0]))
            navigate("home")
            return
        }
        val types = uris.map { resolver.getType(it).orEmpty() }
        when {
            types.all { it.startsWith("image/") } -> { payload = SharedPayload(uris = uris, mimeType = "image/*"); navigate("images_to_pdf") }
            types.all { it == "application/pdf" } -> { payload = SharedPayload(uris = uris, mimeType = "application/pdf"); navigate("pdf_merge") }
            else -> Toast.makeText(Context.app, "Drop several photos (to make a PDF) or several PDFs (to merge them).", Toast.LENGTH_LONG).show()
        }
    }

    fun handleText(text: String) {
        if (text.isBlank()) return
        payload = SharedPayload(text = text, mimeType = "text/plain")
        val trimmed = text.trim()
        navigate(if (trimmed.startsWith("{") || trimmed.startsWith("[")) "json_tool" else "qr_studio")
    }
}

@Composable
fun ApparatDesktopApp(controller: AppController, appSettings: AppSettings) {
    var themeRecomposeTrigger by remember { mutableIntStateOf(0) }

    ApparatTheme(
        themeStyle = appSettings.themeStyle,
        accentColor = appSettings.currentAccentColor,
        recomposeTrigger = themeRecomposeTrigger
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // The screens are designed for a phone-width column; keep them readable on wide windows.
            Box(Modifier.widthIn(max = 880.dp).fillMaxHeight().align(Alignment.TopCenter)) {
                AnimatedContent(
                    targetState = controller.current,
                    transitionSpec = {
                        if (controller.forward) {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 6 }) togetherWith fadeOut(tween(180))
                        } else {
                            fadeIn(tween(220)) togetherWith (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 6 })
                        }
                    },
                    label = "nav"
                ) { route ->
                    Destination(route, controller, appSettings) { themeRecomposeTrigger++ }
                }
            }
            with(ToastHost) { Render() }
        }
    }
}

@Composable
private fun Destination(route: String, controller: AppController, appSettings: AppSettings, onThemeChanged: () -> Unit) {
    val payload = controller.payload
    val incomingUri = payload?.uri
    val incomingMimeType = payload?.mimeType
    val incomingText = payload?.text
    val back: () -> Unit = { controller.pop() }

    val navigateToToolWithFile: (String, Uri) -> Unit = { target, uri ->
        val mime = Context.app.contentResolver.getType(uri) ?: when (target) {
            "pdf_shrink" -> "application/pdf"
            "image_shrink" -> "image/jpeg"
            "video_to_gif" -> "video/mp4"
            "hash_verifier" -> "*/*"
            else -> null
        }
        controller.payload = SharedPayload(uri = uri, mimeType = mime)
        controller.navigate(target)
    }

    @Composable
    fun ConsumeSharedPayloadEffect(targetUri: Uri?) {
        LaunchedEffect(targetUri) { if (targetUri != null && !controller.keepsPayload(route)) controller.payload = null }
    }

    @Composable
    fun ConsumeSharedTextEffect(targetText: String?) {
        LaunchedEffect(targetText) { if (!targetText.isNullOrBlank()) controller.payload = null }
    }

    fun pdfUri() = incomingUri?.takeIf { incomingMimeType == "application/pdf" }
    fun videoUri() = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true }
    fun imageUri() = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true }

    when (route) {
        "home" -> HomeScreen(
            appSettings = appSettings,
            sharedUri = incomingUri,
            sharedMimeType = incomingMimeType,
            onNavigateToTool = { controller.navigate(it) },
            onNavigateToSettings = { controller.navigate("settings") },
            onDismissSharedPayload = { controller.payload = null },
            onThemeChanged = onThemeChanged
        )
        "settings" -> CustomizationScreen(appSettings = appSettings, onSettingsChanged = onThemeChanged, onBack = back)

        "pdf_unlock" -> { val u = pdfUri(); ConsumeSharedPayloadEffect(u); PdfUnlockScreen(initialUri = u, onBack = back) }
        "pdf_shrink" -> { val u = pdfUri(); ConsumeSharedPayloadEffect(u); PdfShrinkScreen(initialUri = u, onBack = back) }
        "pdf_merge" -> {
            val u = pdfUri()
            val shared = payload?.uris?.takeIf { incomingMimeType == "application/pdf" }.orEmpty()
            ConsumeSharedPayloadEffect(u ?: shared.firstOrNull())
            PdfMergeScreen(initialUri = u, initialUris = shared, shareToken = payload?.token ?: 0L, onNavigateToTool = navigateToToolWithFile, onBack = back)
        }
        "pdf_split" -> { val u = pdfUri(); ConsumeSharedPayloadEffect(u); PdfSplitScreen(initialUri = u, onBack = back) }
        "pdf_to_images" -> { val u = pdfUri(); ConsumeSharedPayloadEffect(u); PdfToImagesScreen(initialUri = u, onBack = back) }
        "images_to_pdf" -> {
            val photos = when {
                payload?.uris?.isNotEmpty() == true && incomingMimeType == "image/*" -> payload.uris
                incomingUri != null && incomingMimeType?.startsWith("image/") == true -> listOf(incomingUri)
                else -> emptyList()
            }
            ConsumeSharedPayloadEffect(photos.firstOrNull())
            ImagesToPdfScreen(initialUris = photos, shareToken = payload?.token ?: 0L, onNavigateToTool = navigateToToolWithFile, onBack = back)
        }

        "video_shrink" -> { val u = videoUri(); ConsumeSharedPayloadEffect(u); VideoShrinkScreen(initialUri = u, onBack = back) }
        "video_trim" -> { val u = videoUri(); ConsumeSharedPayloadEffect(u); VideoTrimMuteScreen(initialUri = u, onNavigateToTool = navigateToToolWithFile, onBack = back) }
        "video_caption" -> VideoCaptionScreen(onBack = back)
        "video_to_gif" -> { val u = videoUri(); ConsumeSharedPayloadEffect(u); VideoToGifScreen(initialUri = u, onNavigateToTool = navigateToToolWithFile, onBack = back) }
        "video_convert" -> { val u = videoUri(); ConsumeSharedPayloadEffect(u); VideoConvertScreen(initialUri = u, onBack = back) }
        "audio_extract" -> {
            val u = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true || incomingMimeType?.startsWith("audio/") == true }
            ConsumeSharedPayloadEffect(u)
            AudioExtractScreen(initialUri = u, onNavigateToTool = navigateToToolWithFile, onBack = back)
        }

        "image_shrink" -> { val u = imageUri(); ConsumeSharedPayloadEffect(u); ImageShrinkScreen(initialUri = u, onBack = back) }
        "document_scan" -> { val u = imageUri(); ConsumeSharedPayloadEffect(u); DocumentScanScreen(initialUri = u, onNavigateToTool = navigateToToolWithFile, onBack = back) }
        "palette_extractor" -> { val u = imageUri(); ConsumeSharedPayloadEffect(u); PaletteExtractorScreen(initialUri = u, onBack = back) }
        "image_convert" -> { val u = imageUri(); ConsumeSharedPayloadEffect(u); ImageConvertScreen(initialUri = u, onNavigateToTool = navigateToToolWithFile, onBack = back) }
        "exif_strip" -> { val u = imageUri(); ConsumeSharedPayloadEffect(u); ExifStripperScreen(initialUri = u, onBack = back) }

        "qr_studio" -> { ConsumeSharedTextEffect(incomingText); QrStudioScreen(initialText = incomingText, onBack = back) }
        "hash_verifier" -> {
            if (incomingUri != null) ConsumeSharedPayloadEffect(incomingUri)
            else if (!incomingText.isNullOrBlank()) ConsumeSharedTextEffect(incomingText)
            HashVerifierScreen(initialUri = incomingUri, initialText = incomingText, onBack = back)
        }
        "json_tool" -> { ConsumeSharedTextEffect(incomingText); JsonToolScreen(initialText = incomingText, onBack = back) }
        "base64_studio" -> { ConsumeSharedTextEffect(incomingText); Base64StudioScreen(initialText = incomingText, onBack = back) }
        else -> HomeScreen(
            appSettings = appSettings,
            onNavigateToTool = { controller.navigate(it) },
            onNavigateToSettings = { controller.navigate("settings") }
        )
    }
}
