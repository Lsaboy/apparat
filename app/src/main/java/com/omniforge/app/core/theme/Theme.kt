package com.omniforge.app.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.omniforge.app.core.settings.ThemeStyle

fun onPrimaryForAccent(accent: Color): Color {
    return when (accent) {
        BraunOrange, TechnicalIndicatorYellow, BraunSignalOrange, BraunSignalOrangeDark, AccentUtility, AccentUtilityDark -> OnBraunOrange
        AccentPdf, AccentPdfDark, AccentVideo, AccentVideoDark, AccentImage, AccentImageDark,
        SuccessGreen, TechnicalIndicatorRed, TechnicalIndicatorBlue, TechnicalIndicatorGreen -> Color.White
        else -> {
            val lum = 0.2126f * accent.red + 0.7152f * accent.green + 0.0722f * accent.blue
            if (lum > 0.55f) OnBraunOrange else Color.White
        }
    }
}

val BraunLightColorScheme = lightColorScheme(
    primary = BraunOrange,
    onPrimary = OnBraunOrange,
    primaryContainer = IvorySurfaceElevated,
    onPrimaryContainer = IvoryTextPrimary,
    secondary = TechnicalIndicatorBlue,
    onSecondary = Color.White,
    secondaryContainer = IvorySurface,
    onSecondaryContainer = IvoryTextPrimary,
    tertiary = TechnicalIndicatorGreen,
    onTertiary = Color.White,
    tertiaryContainer = IvorySurface,
    onTertiaryContainer = IvoryTextPrimary,
    background = IvoryBackground,
    onBackground = IvoryTextPrimary,
    surface = IvorySurface,
    onSurface = IvoryTextPrimary,
    surfaceVariant = IvorySurfaceElevated,
    onSurfaceVariant = IvoryTextSecondary,
    outline = IvoryBorder,
    outlineVariant = IvoryBorder.copy(alpha = 0.6f),
    error = TechnicalIndicatorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFEAEA),
    onErrorContainer = TechnicalIndicatorRed
)

val BraunDarkColorScheme = darkColorScheme(
    primary = BraunOrange,
    onPrimary = OnBraunOrange,
    primaryContainer = CharcoalSurfaceElevated,
    onPrimaryContainer = CharcoalTextPrimary,
    secondary = TechnicalIndicatorBlue,
    onSecondary = Color.White,
    secondaryContainer = CharcoalSurfaceElevated,
    onSecondaryContainer = CharcoalTextPrimary,
    tertiary = TechnicalIndicatorGreen,
    onTertiary = Color.White,
    tertiaryContainer = CharcoalSurfaceElevated,
    onTertiaryContainer = CharcoalTextPrimary,
    background = CharcoalBackground,
    onBackground = CharcoalTextPrimary,
    surface = CharcoalSurface,
    onSurface = CharcoalTextPrimary,
    surfaceVariant = CharcoalSurfaceElevated,
    onSurfaceVariant = CharcoalTextSecondary,
    outline = CharcoalBorder,
    outlineVariant = CharcoalBorder.copy(alpha = 0.6f),
    error = TechnicalIndicatorRed,
    onError = Color.White,
    errorContainer = Color(0xFF3B1818),
    onErrorContainer = Color(0xFFFF8B8B)
)

@Composable
fun ApparatTheme(
    themeStyle: ThemeStyle = ThemeStyle.SYSTEM,
    accentColor: Color = BraunOrange,
    isDynamicColor: Boolean = false,
    recomposeTrigger: Int = 0,
    content: @Composable () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val _trigger = recomposeTrigger
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeStyle) {
        ThemeStyle.SYSTEM -> isSystemDark
        ThemeStyle.LIGHT -> false
        ThemeStyle.DARK -> true
    }

    val baseScheme = if (isDark) BraunDarkColorScheme else BraunLightColorScheme
    val colorScheme = baseScheme.copy(
        primary = accentColor,
        onPrimary = onPrimaryForAccent(accentColor)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
fun OmniForgeTheme(
    themeStyle: ThemeStyle = ThemeStyle.SYSTEM,
    accentColor: Color = BraunOrange,
    isDynamicColor: Boolean = false,
    recomposeTrigger: Int = 0,
    content: @Composable () -> Unit
) = ApparatTheme(
    themeStyle = themeStyle,
    accentColor = accentColor,
    isDynamicColor = isDynamicColor,
    recomposeTrigger = recomposeTrigger,
    content = content
)
