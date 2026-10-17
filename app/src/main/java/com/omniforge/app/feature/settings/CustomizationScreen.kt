package com.omniforge.app.feature.settings

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.settings.*
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.BraunSegmentedControl
import com.omniforge.app.core.util.FileUtils
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BraunOrange)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "APPARAT // CONFIGURATION",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                    }
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

            // 1. APPEARANCE & THEME ENGINE CARD
            item {
                SettingsGroupCard(title = "01 // THEME & APPEARANCE") {
                    Text(
                        text = "HARDWARE APPEARANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Braun 3-way Mechanical Rocker Switch
                    BraunSegmentedControl(
                        options = listOf(ThemeStyle.SYSTEM, ThemeStyle.LIGHT, ThemeStyle.DARK),
                        selectedOption = currentTheme,
                        onOptionSelected = { selected ->
                            currentTheme = selected
                            appSettings.themeStyle = selected
                            onSettingsChanged()
                        },
                        labelForOption = { mode ->
                            when (mode) {
                                ThemeStyle.SYSTEM -> "SYSTEM"
                                ThemeStyle.LIGHT -> "LIGHT"
                                ThemeStyle.DARK -> "DARK"
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when (currentTheme) {
                            ThemeStyle.SYSTEM -> "Following system appearance automatically."
                            ThemeStyle.LIGHT -> "Classic Ivory mode locked. Optimal daylight legibility."
                            ThemeStyle.DARK -> "Atelier Charcoal mode locked. Low-glare studio finish."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dynamic Color Calibration",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "Harmonize accents with device system palette",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                        text = "INDICATOR ACCENT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(swatch.color)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        currentAccentIndex = index
                                        appSettings.accentIndex = index
                                        onSettingsChanged()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. OUTPUT DESTINATION & FILE BEHAVIOR
            item {
                SettingsGroupCard(title = "02 // OUTPUT & STORAGE") {
                    Text(
                        text = "DESTINATION DIRECTORY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}/",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = BraunOrange
                        )
                    )
                    Text(
                        text = "All processed files and exports are saved strictly to this folder for easy discovery.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "COLLISION BEHAVIOR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SaveMode.entries.forEach { mode ->
                        val isSelected = currentSaveMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    currentSaveMode = mode
                                    appSettings.defaultSaveMode = mode
                                    onSettingsChanged()
                                },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) BraunOrange.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
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
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. DIETER RAMS DESIGN PHILOSOPHY
            item {
                SettingsGroupCard(title = "03 // PHILOSOPHY") {
                    Text(
                        text = "“WENIGER, ABER BESSER”",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = BraunOrange
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Less, but better. Apparat is designed according to the functionalist principles of Dieter Rams: honest, unobtrusive, aesthetic, and down to the absolute essential minimum.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            // 4. SYSTEM SHARE SHEET & SHORTCUTS
            item {
                SettingsGroupCard(title = "04 // SHARE SHEET SHORTCUTS") {
                    Text(
                        text = "Choose which instruments appear as system shortcuts when sharing files from other apps:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                .padding(vertical = 6.dp),
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
private fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
