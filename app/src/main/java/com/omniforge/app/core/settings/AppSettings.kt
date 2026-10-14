package com.omniforge.app.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.omniforge.app.core.theme.*

enum class ThemeStyle(val displayName: String) {
    AMOLED("AMOLED Black"),
    MATERIAL_YOU("Material You (Android 17)"),
    DARK("Material Dark"),
    LIGHT("Material Light")
}

enum class SaveMode(val label: String, val description: String) {
    NEW_COPY("Create New Copy", "Save to Downloads/DigitalPocketKnife with unique filename"),
    OVERWRITE_ORIGINAL("Modify Original", "Replace original file in-place if format matches")
}

data class AccentSwatch(val name: String, val color: Color)

val AppAccentSwatches = listOf(
    AccentSwatch("Cyan", Color(0xFF00E5FF)),
    AccentSwatch("Emerald", Color(0xFF00E676)),
    AccentSwatch("Lavender", Color(0xFFB388FF)),
    AccentSwatch("Amber", Color(0xFFFFD54F)),
    AccentSwatch("Electric Blue", Color(0xFF2979FF)),
    AccentSwatch("Hot Pink", Color(0xFFFF4081)),
    AccentSwatch("Orange", Color(0xFFFF6D00)),
    AccentSwatch("Teal", Color(0xFF00F5D4)),
    AccentSwatch("Ruby", Color(0xFFFF1744))
)

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dpk_settings", Context.MODE_PRIVATE)

    var themeStyle: ThemeStyle
        get() {
            val name = prefs.getString("theme_style", ThemeStyle.AMOLED.name) ?: ThemeStyle.AMOLED.name
            return runCatching { ThemeStyle.valueOf(name) }.getOrDefault(ThemeStyle.AMOLED)
        }
        set(value) = prefs.edit().putString("theme_style", value.name).apply()

    var accentIndex: Int
        get() = prefs.getInt("accent_index", 0).coerceIn(0, AppAccentSwatches.size - 1)
        set(value) = prefs.edit().putInt("accent_index", value).apply()

    var isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean("dynamic_color", true)
        set(value) = prefs.edit().putBoolean("dynamic_color", value).apply()

    var defaultSaveMode: SaveMode
        get() {
            val name = prefs.getString("default_save_mode", SaveMode.NEW_COPY.name) ?: SaveMode.NEW_COPY.name
            return runCatching { SaveMode.valueOf(name) }.getOrDefault(SaveMode.NEW_COPY)
        }
        set(value) = prefs.edit().putString("default_save_mode", value.name).apply()

    var enabledShortcuts: Set<String>
        get() = prefs.getStringSet("enabled_shortcuts", setOf("pdf_unlock", "image_shrink", "video_shrink", "video_trim")) ?: emptySet()
        set(value) = prefs.edit().putStringSet("enabled_shortcuts", value).apply()

    val currentAccentColor: Color
        get() = AppAccentSwatches[accentIndex].color
}
