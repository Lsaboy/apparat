package com.omniforge.app.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.util.FileUtils

enum class ThemeStyle(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Classic Ivory"),
    DARK("Atelier Charcoal");

    companion object {
        fun fromStoredName(name: String?): ThemeStyle {
            return when (name?.uppercase()) {
                "LIGHT", "CLASSIC_IVORY" -> LIGHT
                "DARK", "ATELIER_CHARCOAL", "AMOLED" -> DARK
                "SYSTEM", "MATERIAL_YOU", null, "" -> SYSTEM
                else -> runCatching { valueOf(name!!) }.getOrDefault(SYSTEM)
            }
        }
    }
}

enum class SaveMode(val label: String, val description: String) {
    NEW_COPY("Create New Copy", "Save to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER} with unique filename"),
    OVERWRITE_ORIGINAL("Modify Original", "Replace original file in-place if format matches")
}

data class AccentSwatch(val name: String, val color: Color)

val AppAccentSwatches = listOf(
    AccentSwatch("Braun Orange", BraunOrange),
    AccentSwatch("Technical Slate", Color(0xFF5E6572)),
    AccentSwatch("Cobalt Blue", TechnicalIndicatorBlue),
    AccentSwatch("Instrument Green", TechnicalIndicatorGreen),
    AccentSwatch("Amber Indicator", TechnicalIndicatorYellow),
    AccentSwatch("Signal Red", TechnicalIndicatorRed)
)

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("dpk_settings", Context.MODE_PRIVATE)

    var themeStyle: ThemeStyle
        get() {
            val name = prefs.getString("theme_style", null)
            return ThemeStyle.fromStoredName(name)
        }
        set(value) = prefs.edit().putString("theme_style", value.name).apply()

    var hasSeenAppearancePrompt: Boolean
        get() = prefs.getBoolean("has_seen_appearance_prompt", false)
        set(value) = prefs.edit().putBoolean("has_seen_appearance_prompt", value).apply()

    var accentIndex: Int
        get() = prefs.getInt("accent_index", 0).coerceIn(0, AppAccentSwatches.size - 1)
        set(value) = prefs.edit().putInt("accent_index", value).apply()

    var isDynamicColorEnabled: Boolean
        get() = prefs.getBoolean("dynamic_color", false)
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

    var isGridView: Boolean
        get() = prefs.getBoolean("is_grid_view", true)
        set(value) = prefs.edit().putBoolean("is_grid_view", value).apply()

    var hasAskedNotificationPermission: Boolean
        get() = prefs.getBoolean("asked_notification_permission", false)
        set(value) = prefs.edit().putBoolean("asked_notification_permission", value).apply()

    val currentAccentColor: Color
        get() = AppAccentSwatches[accentIndex].color
}
