package com.omniforge.app.feature.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.settings.*
import com.omniforge.app.core.theme.*
import com.omniforge.app.feature.home.ALL_TOOLS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    appSettings: AppSettings,
    onSettingsChanged: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(appSettings.themeStyle) }
    var currentAccentIndex by remember { mutableIntStateOf(appSettings.accentIndex) }
    var isDynamicColor by remember { mutableStateOf(appSettings.isDynamicColorEnabled) }
    var currentSaveMode by remember { mutableStateOf(appSettings.defaultSaveMode) }
    var enabledShortcuts by remember { mutableStateOf(appSettings.enabledShortcuts) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Customization & Settings",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. APPEARANCE CARD
            item {
                SettingsGroupCard(title = "Theme & Appearance") {
                    Text(
                        text = "Theme Engine",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Segmented Theme Pills
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ThemePill(
                                title = "AMOLED Black",
                                isSelected = currentTheme == ThemeStyle.AMOLED,
                                modifier = Modifier.weight(1f)
                            ) {
                                currentTheme = ThemeStyle.AMOLED
                                appSettings.themeStyle = ThemeStyle.AMOLED
                                onSettingsChanged()
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            ThemePill(
                                title = "Material You",
                                isSelected = currentTheme == ThemeStyle.MATERIAL_YOU,
                                modifier = Modifier.weight(1f)
                            ) {
                                currentTheme = ThemeStyle.MATERIAL_YOU
                                appSettings.themeStyle = ThemeStyle.MATERIAL_YOU
                                onSettingsChanged()
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            ThemePill(
                                title = "Dark",
                                isSelected = currentTheme == ThemeStyle.DARK,
                                modifier = Modifier.weight(1f)
                            ) {
                                currentTheme = ThemeStyle.DARK
                                appSettings.themeStyle = ThemeStyle.DARK
                                onSettingsChanged()
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            ThemePill(
                                title = "Light",
                                isSelected = currentTheme == ThemeStyle.LIGHT,
                                modifier = Modifier.weight(1f)
                            ) {
                                currentTheme = ThemeStyle.LIGHT
                                appSettings.themeStyle = ThemeStyle.LIGHT
                                onSettingsChanged()
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentTheme == ThemeStyle.MATERIAL_YOU) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Android 17 Dynamic Color",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "Extract color palette from system wallpaper",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Switch(
                                checked = isDynamicColor,
                                onCheckedChange = {
                                    isDynamicColor = it
                                    appSettings.isDynamicColorEnabled = it
                                    onSettingsChanged()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accent Colors
                    Text(
                        text = "Accent Color Swatch",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(AppAccentSwatches) { index, swatch ->
                            val isSelected = currentAccentIndex == index
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(swatch.color)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(3.dp, Color.White, RoundedCornerShape(10.dp))
                                        } else {
                                            Modifier.border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                        }
                                    )
                                    .clickable {
                                        currentAccentIndex = index
                                        appSettings.accentIndex = index
                                        onSettingsChanged()
                                    }
                            )
                        }
                    }
                }
            }

            // 2. FILE SAVING BEHAVIOR
            item {
                SettingsGroupCard(title = "Default Save Behavior") {
                    Text(
                        text = "When processing an existing file:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SaveMode.entries.forEach { mode ->
                        val isSelected = currentSaveMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    currentSaveMode = mode
                                    appSettings.defaultSaveMode = mode
                                    onSettingsChanged()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else DarkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        currentSaveMode = mode
                                        appSettings.defaultSaveMode = mode
                                        onSettingsChanged()
                                    }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = mode.description,
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. SYSTEM SHARE SHEET & SHORTCUTS
            item {
                SettingsGroupCard(title = "Share Sheet Shortcuts") {
                    Text(
                        text = "Choose which tools appear as system shortcuts when sharing files from other apps:",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ALL_TOOLS.forEach { tool ->
                        val isChecked = enabledShortcuts.contains(tool.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = if (isChecked) {
                                        enabledShortcuts - tool.id
                                    } else {
                                        enabledShortcuts + tool.id
                                    }
                                    enabledShortcuts = updated
                                    appSettings.enabledShortcuts = updated
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) {
                                        enabledShortcuts + tool.id
                                    } else {
                                        enabledShortcuts - tool.id
                                    }
                                    enabledShortcuts = updated
                                    appSettings.enabledShortcuts = updated
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = tool.category.displayName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = tool.category.color,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ThemePill(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) AmoledBlack else TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
