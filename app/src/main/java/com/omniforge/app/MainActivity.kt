package com.omniforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omniforge.app.core.theme.OmniForgeTheme
import com.omniforge.app.feature.home.HomeScreen
import com.omniforge.app.feature.image.ExifStripperScreen
import com.omniforge.app.feature.image.ImageConvertScreen
import com.omniforge.app.feature.image.ImageShrinkScreen
import com.omniforge.app.feature.pdf.ImagesToPdfScreen
import com.omniforge.app.feature.pdf.PdfShrinkScreen
import com.omniforge.app.feature.pdf.PdfUnlockScreen
import com.omniforge.app.feature.utilities.Base64StudioScreen
import com.omniforge.app.feature.utilities.HashVerifierScreen
import com.omniforge.app.feature.utilities.QrStudioScreen
import com.omniforge.app.feature.video.AudioExtractScreen
import com.omniforge.app.feature.video.VideoCaptionScreen
import com.omniforge.app.feature.video.VideoConvertScreen
import com.omniforge.app.feature.video.VideoShrinkScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OmniForgeTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "home",
                    enterTransition = { fadeIn(animationSpec = tween(250)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250)) },
                    exitTransition = { fadeOut(animationSpec = tween(200)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(250)) },
                    popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250)) }
                ) {
                    composable("home") {
                        HomeScreen(onNavigateToTool = { route -> navController.navigate(route) })
                    }

                    // PDF Tools
                    composable("pdf_unlock") {
                        PdfUnlockScreen(onBack = { navController.popBackStack() })
                    }
                    composable("pdf_shrink") {
                        PdfShrinkScreen(onBack = { navController.popBackStack() })
                    }
                    composable("images_to_pdf") {
                        ImagesToPdfScreen(onBack = { navController.popBackStack() })
                    }

                    // Video Tools
                    composable("video_shrink") {
                        VideoShrinkScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_caption") {
                        VideoCaptionScreen(onBack = { navController.popBackStack() })
                    }
                    composable("video_convert") {
                        VideoConvertScreen(onBack = { navController.popBackStack() })
                    }
                    composable("audio_extract") {
                        AudioExtractScreen(onBack = { navController.popBackStack() })
                    }

                    // Image Tools
                    composable("image_shrink") {
                        ImageShrinkScreen(onBack = { navController.popBackStack() })
                    }
                    composable("image_convert") {
                        ImageConvertScreen(onBack = { navController.popBackStack() })
                    }
                    composable("exif_strip") {
                        ExifStripperScreen(onBack = { navController.popBackStack() })
                    }

                    // Utility Tools
                    composable("qr_studio") {
                        QrStudioScreen(onBack = { navController.popBackStack() })
                    }
                    composable("hash_verifier") {
                        HashVerifierScreen(onBack = { navController.popBackStack() })
                    }
                    composable("base64_studio") {
                        Base64StudioScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
