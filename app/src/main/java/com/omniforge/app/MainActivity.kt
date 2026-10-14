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

    private lateinit var appSettings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appSettings = AppSettings(applicationContext)

        // Parse incoming intent (Share Sheet or Shortcut Deep Link)
        val incomingUri: Uri? = if (intent?.action == Intent.ACTION_SEND) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else null

        val incomingMimeType = intent?.type

        val shortcutRoute: String? = if (intent?.data?.scheme == "dpk") {
            intent.data?.host ?: intent.data?.path?.removePrefix("/")
        } else null

        setContent {
            var themeRecomposeTrigger by remember { mutableIntStateOf(0) }

            OmniForgeTheme(
                themeStyle = appSettings.themeStyle,
                accentColor = appSettings.currentAccentColor,
                isDynamicColor = appSettings.isDynamicColorEnabled
            ) {
                val navController = rememberNavController()

                // If launched via direct shortcut
                LaunchedEffect(shortcutRoute) {
                    if (!shortcutRoute.isNullOrBlank()) {
                        navController.navigate(shortcutRoute)
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
                            onNavigateToSettings = { navController.navigate("settings") }
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
                        PdfUnlockScreen(onBack = { navController.popBackStack() })
                    }
                    composable("pdf_shrink") {
                        PdfShrinkScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType == "application/pdf" },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("pdf_split") {
                        PdfSplitScreen(onBack = { navController.popBackStack() })
                    }
                    composable("pdf_to_images") {
                        PdfToImagesScreen(onBack = { navController.popBackStack() })
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
                        VideoTrimMuteScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_caption") {
                        VideoCaptionScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_to_gif") {
                        VideoToGifScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_convert") {
                        VideoConvertScreen(
                            initialUri = incomingUri?.takeIf { incomingMimeType?.startsWith("video/") == true },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("audio_extract") {
                        AudioExtractScreen(onBack = { navController.popBackStack() })
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
                        PaletteExtractorScreen(onBack = { navController.popBackStack() })
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
                        QrStudioScreen(onBack = { navController.popBackStack() })
                    }
                    composable("hash_verifier") {
                        HashVerifierScreen(onBack = { navController.popBackStack() })
                    }
                    composable("json_tool") {
                        JsonToolScreen(onBack = { navController.popBackStack() })
                    }
                    composable("base64_studio") {
                        Base64StudioScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
