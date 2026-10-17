package com.vedant.apparat.core.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.util.FileUtils
import com.vedant.apparat.core.util.OutputSaver

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
        get() {
            val name = prefs.getString("theme_style", null)
            return ThemeStyle.fromStoredName(name)
        }
        set(value) = prefs.edit().putString("theme_style", value.name).apply()

    var hasSeenAppearancePrompt: Boolean
        get() = prefs.getBoolean("has_seen_appearance_prompt", false)
        set(value) = prefs.edit().putBoolean("has_seen_appearance_prompt", value).apply()

    /** Subfolder of Downloads that outputs are saved to (see OutputSaver). */
    var saveFolderName: String
        get() = OutputSaver.sanitizeFolderName(prefs.getString("save_folder_name", OutputSaver.DEFAULT_FOLDER) ?: OutputSaver.DEFAULT_FOLDER)
        set(value) = prefs.edit().putString("save_folder_name", OutputSaver.sanitizeFolderName(value)).apply()

    /** A folder picked with the system folder picker (persisted tree URI), used instead of Downloads. */
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
        get() = prefs.getBoolean("asked_notification_permission", false)
        set(value) = prefs.edit().putBoolean("asked_notification_permission", value).apply()

    val currentAccentColor: Color
        get() = AppAccentSwatches[accentIndex].color

    fun updateLauncherShortcuts(context: Context) {
        runCatching {
            val targetIds = enabledShortcuts
            val selectedTools = com.vedant.apparat.core.model.ALL_TOOLS.filter { it.id in targetIds }.take(4)
            val effectiveTools = if (selectedTools.isNotEmpty()) {
                selectedTools
            } else {
                com.vedant.apparat.core.model.ALL_TOOLS.filter { it.id in setOf("pdf_unlock", "image_shrink", "video_shrink", "video_trim") }.take(4)
            }

            val shortcuts = effectiveTools.map { tool ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("apparat://${tool.id}")).apply {
                    setClass(context, com.vedant.apparat.MainActivity::class.java)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                androidx.core.content.pm.ShortcutInfoCompat.Builder(context, "shortcut_${tool.id}")
                    .setShortLabel(tool.shortTitle)
                    .setLongLabel(tool.title)
                    .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(context, com.vedant.apparat.R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build()
            }
            androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
