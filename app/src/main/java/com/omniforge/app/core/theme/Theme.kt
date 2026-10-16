package com.omniforge.app.core.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.omniforge.app.core.settings.ThemeStyle

private val AmoledDarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = AmoledBlack,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = PrimaryTeal,
    secondary = SecondaryPurple,
    onSecondary = TextPrimary,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = AmoledBlack,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = ErrorRed,
    onError = AmoledBlack
)

private val MaterialDarkColorScheme = darkColorScheme(
    primary = PrimaryTeal,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFF70F7D8),
    secondary = SecondaryPurple,
    onSecondary = Color.White,
    background = Color(0xFF191C1B),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938F)
)

private val MaterialLightColorScheme = lightColorScheme(
    primary = Color(0xFF006B58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF70F7D8),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635B),
    onSecondary = Color.White,
    background = Color(0xFFFBFDFA),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    outline = Color(0xFF6F7975)
)

@Composable
fun OmniForgeTheme(
    themeStyle: ThemeStyle = ThemeStyle.AMOLED,
    accentColor: Color = PrimaryTeal,
    isDynamicColor: Boolean = true,
    recomposeTrigger: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()

    val colorScheme = when (themeStyle) {
        ThemeStyle.AMOLED -> {
            AmoledDarkColorScheme.copy(
                primary = accentColor,
                onPrimaryContainer = accentColor
            )
        }
        ThemeStyle.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDynamicColor) {
                if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isSystemDark) {
                    MaterialDarkColorScheme.copy(primary = accentColor)
                } else {
                    MaterialLightColorScheme.copy(primary = accentColor)
                }
            }
        }
        ThemeStyle.DARK -> {
            MaterialDarkColorScheme.copy(primary = accentColor)
        }
        ThemeStyle.LIGHT -> {
            MaterialLightColorScheme.copy(primary = accentColor)
        }
    }

    val isDark = themeStyle != ThemeStyle.LIGHT && (themeStyle != ThemeStyle.MATERIAL_YOU || isSystemDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarBg = if (themeStyle == ThemeStyle.AMOLED) AmoledBlack.toArgb() else colorScheme.surface.toArgb()
            window.statusBarColor = statusBarBg
            window.navigationBarColor = statusBarBg
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !isDark
            insets.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
