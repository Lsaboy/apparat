package com.omniforge.apparat.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.util.OutputSaver

// Desktop version of the Android AppSettings: same settings and stored names. Launcher-only
// features (home-screen shortcuts) are no-ops; the icon variant changes the window icon.

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
                else -> runCatching { valueOf(name) }.getOrDefault(SYSTEM)
            }
        }
    }
}

enum class SaveMode(val label: String, val description: String) {
    NEW_COPY("Create New Copy", "Keep the original and save the result as a new file"),
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
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("apparat_settings", Context.MODE_PRIVATE)

    var themeStyle: ThemeStyle
        get() = ThemeStyle.fromStoredName(prefs.getString("theme_style", null))
        set(value) = prefs.edit().putString("theme_style", value.name).apply()

    var hasSeenAppearancePrompt: Boolean
        get() = prefs.getBoolean("has_seen_appearance_prompt", false)
        set(value) = prefs.edit().putBoolean("has_seen_appearance_prompt", value).apply()

    /** Subfolder of Downloads that outputs are saved to (see OutputSaver). */
    var saveFolderName: String
        get() = OutputSaver.sanitizeFolderName(prefs.getString("save_folder_name", OutputSaver.DEFAULT_FOLDER) ?: OutputSaver.DEFAULT_FOLDER)
        set(value) = prefs.edit().putString("save_folder_name", OutputSaver.sanitizeFolderName(value)).apply()

    /** A folder picked in Settings (stored as a file URI), used instead of Downloads. */
    var saveTreeUri: String?
        get() = prefs.getString("save_tree_uri", null)
        set(value) = prefs.edit().apply { if (value == null) remove("save_tree_uri") else putString("save_tree_uri", value) }.apply()

    var accentIndex: Int
        get() = prefs.getInt("accent_index", 0).coerceIn(0, AppAccentSwatches.size - 1)
        set(value) = prefs.edit().putInt("accent_index", value).apply()

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
        get() = prefs.getBoolean("asked_notification_permission", true)
        set(value) = prefs.edit().putBoolean("asked_notification_permission", value).apply()

    val currentAccentColor: Color
        get() = AppAccentSwatches[accentIndex].color

    enum class AppIconVariant(val displayName: String, val aliasName: String) {
        IVORY("Classic Ivory", "LauncherIvory"),
        CHARCOAL("Atelier Charcoal", "LauncherCharcoal")
    }

    var appIconVariant: AppIconVariant
        get() {
            val name = prefs.getString("app_icon_variant", AppIconVariant.IVORY.name) ?: AppIconVariant.IVORY.name
            return runCatching { AppIconVariant.valueOf(name) }.getOrDefault(AppIconVariant.IVORY)
        }
        set(value) = prefs.edit().putString("app_icon_variant", value.name).apply()

    /** On desktop the variant is used for the window and taskbar icon. */
    fun applyIconVariant(context: Context, variant: AppIconVariant) {
        appIconVariant = variant
        onIconVariantChanged(variant)
    }

    /** Home-screen launcher shortcuts don't exist on desktop; the Home quick actions still use [enabledShortcuts]. */
    fun updateLauncherShortcuts(context: Context) {}

    companion object {
        /** Set by the desktop window to swap its icon. */
        @Volatile
        var onIconVariantChanged: (AppIconVariant) -> Unit = {}
    }
}
