package com.vedant.apparat.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vedant.apparat.core.settings.ThemeStyle

fun onPrimaryForAccent(accent: Color): Color {
    return when (accent) {
        BraunOrange, TechnicalIndicatorYellow, BraunSignalOrange, BraunSignalOrangeDark, AccentUtility, AccentUtilityDark -> OnBraunOrange
        AccentPdf, AccentPdfDark, AccentVideo, AccentVideoDark, AccentImage, AccentImageDark,
        SuccessGreen, TechnicalIndicatorRed, TechnicalIndicatorBlue, TechnicalIndicatorGreen -> Color.White
        // Anything else (e.g. a future swatch): whichever of dark or white text contrasts more (WCAG).
        else -> if (contrastRatio(OnBraunOrange, accent) >= contrastRatio(Color.White, accent)) OnBraunOrange else Color.White
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
    onErrorContainer = TechnicalIndicatorRed,
    // Material 3 surface roles (switch tracks, dialogs, menus, chips). Without these, Material's
    // default lavender shows through.
    surfaceTint = IvorySurface,
    surfaceBright = IvorySurfaceElevated,
    surfaceDim = Color(0xFFDDD8CB),
    surfaceContainerLowest = IvorySurfaceElevated,
    surfaceContainerLow = Color(0xFFF2EFE7),
    surfaceContainer = Color(0xFFEEEAE1),
    surfaceContainerHigh = IvorySurface,
    surfaceContainerHighest = Color(0xFFE2DDD1),
    inverseSurface = Color(0xFF2A2A2C),
    inverseOnSurface = CharcoalTextPrimary,
    scrim = Color(0xFF000000)
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
    onErrorContainer = Color(0xFFFF8B8B),
    surfaceTint = CharcoalSurface,
    surfaceBright = Color(0xFF34343A),
    surfaceDim = CharcoalBackground,
    surfaceContainerLowest = Color(0xFF0F0F11),
    surfaceContainerLow = Color(0xFF1A1A1E),
    surfaceContainer = CharcoalSurface,
    surfaceContainerHigh = CharcoalSurfaceElevated,
    surfaceContainerHighest = CharcoalBorder,
    inverseSurface = CharcoalTextPrimary,
    inverseOnSurface = CharcoalBackground,
    scrim = Color(0xFF000000)
)

@Composable
fun ApparatTheme(
    themeStyle: ThemeStyle = ThemeStyle.SYSTEM,
    accentColor: Color = BraunOrange,
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

/** WCAG contrast ratio between two colours (1.0 to 21.0). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/**
 * [accent] adjusted for use as text on [background]: blended toward [ink] (the theme's text colour)
 * just enough to reach 4.5:1, the WCAG minimum for normal text. Accents that already pass are unchanged.
 */
fun readableTextColor(accent: Color, background: Color, ink: Color): Color {
    if (contrastRatio(accent, background) >= 4.5f) return accent
    var t = 0.1f
    while (t <= 1f) {
        val c = androidx.compose.ui.graphics.lerp(accent, ink, t)
        if (contrastRatio(c, background) >= 4.5f) return c
        t += 0.1f
    }
    return ink
}

/** [accent] as readable text on the current surface. */
@Composable
fun accentText(accent: Color): Color =
    readableTextColor(accent, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
