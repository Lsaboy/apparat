package com.omniforge.apparat

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omniforge.apparat.core.jobs.ToolJobs
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

class MainActivity : ComponentActivity() {

    companion object {
        /** Every tool destination in the NavHost below. Keep in sync when adding a screen. */
        val TOOL_ROUTES = setOf(
            "settings",
            "pdf_unlock", "pdf_shrink", "pdf_split", "pdf_merge", "pdf_to_images", "images_to_pdf",
            "video_shrink", "video_trim", "video_caption", "video_to_gif", "video_convert", "audio_extract",
            "image_shrink", "document_scan", "palette_extractor", "image_convert", "exif_strip",
            "qr_studio", "hash_verifier", "json_tool", "base64_studio"
        )
    }

    data class SharedPayload(
        val uri: Uri? = null,
        /** Several files shared at once (photos for Photos to PDF, PDFs for Merge). */
        val uris: List<Uri> = emptyList(),
        val mimeType: String? = null,
        val text: String? = null,
        val token: Long = System.currentTimeMillis()
    )

    private lateinit var appSettings: AppSettings
    private val navEvents = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    private var currentSharedPayload by mutableStateOf<SharedPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appSettings = AppSettings(applicationContext)
        ToolJobs.onActivityCreated(applicationContext, restoring = savedInstanceState != null)

        handleIncomingIntent(intent)

        setContent {
            var themeRecomposeTrigger by remember { mutableIntStateOf(0) }

            ApparatTheme(
                themeStyle = appSettings.themeStyle,
                accentColor = appSettings.currentAccentColor,
                recomposeTrigger = themeRecomposeTrigger
            ) {
                val navController = rememberNavController()

                val payload = currentSharedPayload
                val incomingUri = payload?.uri
                val incomingMimeType = payload?.mimeType
                val incomingText = payload?.text

                // Handle deep links and notification taps via buffered events
                LaunchedEffect(navController) {
                    for (route in navEvents) {
                        if (navController.currentDestination?.route != route) {
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        }
                    }
                }

                val navigateToToolWithFile: (String, Uri) -> Unit = { route, uri ->
                    val mime = contentResolver.getType(uri) ?: when (route) {
                        "pdf_shrink" -> "application/pdf"
                        "image_shrink" -> "image/jpeg"
                        "video_to_gif" -> "video/mp4"
                        "hash_verifier" -> "*/*"
                        else -> null
                    }
                    currentSharedPayload = SharedPayload(uri = uri, mimeType = mime)
                    navController.navigate(route)
                }

                @Composable
                fun ConsumeSharedPayloadEffect(targetUri: Uri?) {
                    LaunchedEffect(targetUri) {
                        if (targetUri != null) {
                            currentSharedPayload = null
                        }
                    }
                }

                @Composable
                fun ConsumeSharedTextEffect(targetText: String?) {
                    LaunchedEffect(targetText) {
                        if (!targetText.isNullOrBlank()) {
                            currentSharedPayload = null
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = { fadeIn(animationSpec = tween(220)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220)) },
                    exitTransition = { fadeOut(animationSpec = tween(180)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                    popExitTransition = { fadeOut(animationSpec = tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) }
                ) {
                    composable("home") {
                        HomeScreen(
                            appSettings = appSettings,
                            sharedUri = incomingUri,
                            sharedMimeType = incomingMimeType,
                            onNavigateToTool = { route -> navController.navigate(route) },
                            onNavigateToSettings = { navController.navigate("settings") },
                            onDismissSharedPayload = { currentSharedPayload = null },
                            onThemeChanged = { themeRecomposeTrigger++ }
                        )
                    }

                    // Settings & Customization (Lumen-Android inspired)
                    composable("settings") {
                        CustomizationScreen(
                            appSettings = appSettings,
                            onSettingsChanged = { themeRecomposeTrigger++ },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // PDF Tools
                    composable("pdf_unlock") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" }
                        ConsumeSharedPayloadEffect(targetUri)
                        PdfUnlockScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_shrink") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" }
                        ConsumeSharedPayloadEffect(targetUri)
                        PdfShrinkScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_merge") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" }
                        val sharedPdfs = payload?.uris?.takeIf { incomingMimeType == "application/pdf" }.orEmpty()
                        ConsumeSharedPayloadEffect(targetUri ?: sharedPdfs.firstOrNull())
                        PdfMergeScreen(
                            initialUri = targetUri,
                            initialUris = sharedPdfs,
                            shareToken = payload?.token ?: 0L,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_split") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" }
                        ConsumeSharedPayloadEffect(targetUri)
                        PdfSplitScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_to_images") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" }
                        ConsumeSharedPayloadEffect(targetUri)
                        PdfToImagesScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("images_to_pdf") {
                        val sharedPhotos = when {
                            payload?.uris?.isNotEmpty() == true && incomingMimeType == "image/*" -> payload.uris
                            // A single photo shared via the tool picker
                            incomingUri != null && incomingMimeType?.startsWith("image/") == true -> listOf(incomingUri)
                            else -> emptyList()
                        }
                        ConsumeSharedPayloadEffect(sharedPhotos.firstOrNull())
                        ImagesToPdfScreen(
                            initialUris = sharedPhotos,
                            shareToken = payload?.token ?: 0L,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Video Tools
                    composable("video_shrink") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        VideoShrinkScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("video_trim") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        VideoTrimMuteScreen(
                            initialUri = targetUri,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("video_caption") {
                        VideoCaptionScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_to_gif") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        VideoToGifScreen(
                            initialUri = targetUri,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("video_convert") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        VideoConvertScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("audio_extract") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true || incomingMimeType?.startsWith("audio/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        AudioExtractScreen(
                            initialUri = targetUri,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Image Tools
                    composable("image_shrink") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        ImageShrinkScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("document_scan") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        DocumentScanScreen(
                            initialUri = targetUri,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("palette_extractor") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        PaletteExtractorScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("image_convert") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        ImageConvertScreen(
                            initialUri = targetUri,
                            onNavigateToTool = navigateToToolWithFile,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("exif_strip") {
                        val targetUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true }
                        ConsumeSharedPayloadEffect(targetUri)
                        ExifStripperScreen(
                            initialUri = targetUri,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Utility Tools
                    composable("qr_studio") {
                        ConsumeSharedTextEffect(incomingText)
                        QrStudioScreen(
                            initialText = incomingText,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("hash_verifier") {
                        if (incomingUri != null) {
                            ConsumeSharedPayloadEffect(incomingUri)
                        } else if (!incomingText.isNullOrBlank()) {
                            ConsumeSharedTextEffect(incomingText)
                        }
                        HashVerifierScreen(
                            initialUri = incomingUri,
                            initialText = incomingText,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("json_tool") {
                        ConsumeSharedTextEffect(incomingText)
                        JsonToolScreen(
                            initialText = incomingText,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("base64_studio") {
                        ConsumeSharedTextEffect(incomingText)
                        Base64StudioScreen(
                            initialText = incomingText,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun isUnsafeUri(uri: Uri): Boolean {
        if (uri.scheme == "file") return true
        if (uri.authority == "${packageName}.fileprovider") return true
        return false
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        when (action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                val mime = intent.type ?: uri?.let { contentResolver.getType(it) }
                if (uri != null && uri.scheme != "apparat" && !isUnsafeUri(uri)) {
                    currentSharedPayload = SharedPayload(uri = uri, mimeType = mime)
                    navEvents.trySend("home")
                }
            }
            Intent.ACTION_SEND -> {
                val mime = intent.type
                val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.getItemAt(0)?.uri

                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

                if (streamUri != null && !isUnsafeUri(streamUri)) {
                    currentSharedPayload = SharedPayload(uri = streamUri, mimeType = mime)
                    navEvents.trySend("home")
                } else if (!sharedText.isNullOrBlank()) {
                    currentSharedPayload = SharedPayload(text = sharedText, mimeType = mime ?: "text/plain")
                    val trimmed = sharedText.trim()
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        navEvents.trySend("json_tool")
                    } else {
                        navEvents.trySend("qr_studio")
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streams: List<Uri> = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }
                    ?: emptyList()).filterNot { isUnsafeUri(it) }.distinct()
                if (streams.isNotEmpty()) {
                    val types = streams.map { contentResolver.getType(it) ?: intent.type ?: "" }
                    when {
                        types.all { it.startsWith("image/") } -> {
                            currentSharedPayload = SharedPayload(uris = streams, mimeType = "image/*")
                            navEvents.trySend("images_to_pdf")
                        }
                        types.all { it == "application/pdf" } -> {
                            currentSharedPayload = SharedPayload(uris = streams, mimeType = "application/pdf")
                            navEvents.trySend("pdf_merge")
                        }
                        else -> android.widget.Toast.makeText(
                            this, "Share several photos (to make a PDF) or several PDFs (to merge them).", android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val processText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!processText.isNullOrBlank()) {
                    currentSharedPayload = SharedPayload(text = processText, mimeType = "text/plain")
                    val className = intent.component?.className ?: ""
                    val targetRoute = when {
                        className.endsWith("ProcessTextQrActivity") -> "qr_studio"
                        className.endsWith("ProcessTextJsonActivity") -> "json_tool"
                        className.endsWith("ProcessTextBase64Activity") -> "base64_studio"
                        className.endsWith("ProcessTextHashActivity") -> "hash_verifier"
                        else -> "qr_studio"
                    }
                    navEvents.trySend(targetRoute)
                }
            }
        }
        extractRoute(intent)?.let { navEvents.trySend(it) }
    }

    private fun extractRoute(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "apparat") return null
        val route = uri.host ?: uri.path?.removePrefix("/")
        return route?.takeIf { it in TOOL_ROUTES }
    }
}
