package com.omniforge.app

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
import com.omniforge.app.core.settings.AppSettings
import com.omniforge.app.core.theme.OmniForgeTheme
import com.omniforge.app.feature.home.HomeScreen
import com.omniforge.app.feature.image.DocumentScanScreen
import com.omniforge.app.feature.image.ExifStripperScreen
import com.omniforge.app.feature.image.ImageConvertScreen
import com.omniforge.app.feature.image.ImageShrinkScreen
import com.omniforge.app.feature.image.PaletteExtractorScreen
import com.omniforge.app.feature.pdf.ImagesToPdfScreen
import com.omniforge.app.feature.pdf.PdfShrinkScreen
import com.omniforge.app.feature.pdf.PdfSplitScreen
import com.omniforge.app.feature.pdf.PdfToImagesScreen
import com.omniforge.app.feature.pdf.PdfUnlockScreen
import com.omniforge.app.feature.settings.CustomizationScreen
import com.omniforge.app.feature.utilities.Base64StudioScreen
import com.omniforge.app.feature.utilities.HashVerifierScreen
import com.omniforge.app.feature.utilities.JsonToolScreen
import com.omniforge.app.feature.utilities.QrStudioScreen
import com.omniforge.app.feature.video.AudioExtractScreen
import com.omniforge.app.feature.video.VideoCaptionScreen
import com.omniforge.app.feature.video.VideoConvertScreen
import com.omniforge.app.feature.video.VideoShrinkScreen
import com.omniforge.app.feature.video.VideoToGifScreen
import com.omniforge.app.feature.video.VideoTrimMuteScreen

class MainActivity : ComponentActivity() {

    companion object {
        /** Every tool destination in the NavHost below. Keep in sync when adding a screen. */
        val TOOL_ROUTES = setOf(
            "settings",
            "pdf_unlock", "pdf_shrink", "pdf_split", "pdf_to_images", "images_to_pdf",
            "video_shrink", "video_trim", "video_caption", "video_to_gif", "video_convert", "audio_extract",
            "image_shrink", "document_scan", "palette_extractor", "image_convert", "exif_strip",
            "qr_studio", "hash_verifier", "json_tool", "base64_studio"
        )
    }

    data class SharedPayload(
        val uri: Uri? = null,
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

        handleIncomingIntent(intent)

        setContent {
            var themeRecomposeTrigger by remember { mutableIntStateOf(0) }

            OmniForgeTheme(
                themeStyle = appSettings.themeStyle,
                accentColor = appSettings.currentAccentColor,
                isDynamicColor = appSettings.isDynamicColorEnabled,
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
                        PdfUnlockScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_shrink") {
                        PdfShrinkScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_split") {
                        PdfSplitScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_to_images") {
                        PdfToImagesScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("images_to_pdf") {
                        ImagesToPdfScreen(onBack = { navController.popBackStack() })
                    }

                    // Video Tools
                    composable("video_shrink") {
                        VideoShrinkScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("video_trim") {
                        VideoTrimMuteScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("video_caption") {
                        VideoCaptionScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_to_gif") {
                        VideoToGifScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("video_convert") {
                        VideoConvertScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("audio_extract") {
                        AudioExtractScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true || incomingMimeType?.startsWith("audio/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Image Tools
                    composable("image_shrink") {
                        ImageShrinkScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("document_scan") {
                        DocumentScanScreen(onBack = { navController.popBackStack() })
                    }
                    composable("palette_extractor") {
                        PaletteExtractorScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("image_convert") {
                        ImageConvertScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("exif_strip") {
                        ExifStripperScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("image/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    // Utility Tools
                    composable("qr_studio") {
                        QrStudioScreen(
                            initialText = incomingText,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("hash_verifier") {
                        HashVerifierScreen(
                            initialUri = incomingUri,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("json_tool") {
                        JsonToolScreen(
                            initialText = incomingText?.takeIf { it.trim().startsWith("{") || it.trim().startsWith("[") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("base64_studio") {
                        Base64StudioScreen(onBack = { navController.popBackStack() })
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

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        when (action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                val mime = intent.type ?: uri?.let { contentResolver.getType(it) }
                if (uri != null) {
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

                if (streamUri != null) {
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
        }
        extractRoute(intent)?.let { navEvents.trySend(it) }
    }

    private fun extractRoute(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "dpk") return null
        val route = uri.host ?: uri.path?.removePrefix("/")
        return route?.takeIf { it in TOOL_ROUTES }
    }
}
