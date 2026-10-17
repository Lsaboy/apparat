package com.omniforge.apparat.feature.settings

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.omniforge.apparat.core.model.ALL_TOOLS
import com.omniforge.apparat.core.settings.*
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.ApparatBentoMark
import com.omniforge.apparat.core.ui.BraunSegmentedControl
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.core.util.OutputSaver
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
    var currentSaveMode by remember { mutableStateOf(appSettings.defaultSaveMode) }

    // Default save destination (see OutputSaver)
    var destinationLabel by remember { mutableStateOf(OutputSaver.destination(context).label) }
    var usingPickedFolder by remember { mutableStateOf(appSettings.saveTreeUri != null) }
    var pickedFolderUnavailable by remember { mutableStateOf(OutputSaver.isPickedFolderUnavailable(context)) }
    fun refreshDestination() {
        destinationLabel = OutputSaver.destination(context).label
        usingPickedFolder = appSettings.saveTreeUri != null
        pickedFolderUnavailable = OutputSaver.isPickedFolderUnavailable(context)
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    val pickDefaultFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            val ok = OutputSaver.setDefaultTree(context, tree)
            if (!ok) Toast.makeText(context, "Apparat can't save to that folder. Pick another one.", Toast.LENGTH_LONG).show()
            refreshDestination()
        }
    }
    if (showRenameDialog) {
        val preview = OutputSaver.sanitizeFolderName(renameDraft)
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Folder in Downloads") },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it.take(80) },
                        singleLine = true,
                        label = { Text("Folder name") }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Files will be saved to Downloads/$preview", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    OutputSaver.useDownloads(context, renameDraft)
                    refreshDestination()
                    showRenameDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }
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
                                .background(MaterialTheme.colorScheme.primary)
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
                        text = "APPEARANCE",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accent Colors
                    Text(
                        text = "ACCENT COLOUR",
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
                                    .semantics {
                                        contentDescription = swatch.name
                                        selected = isSelected
                                        role = Role.RadioButton
                                    }
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
                                            .background(onPrimaryForAccent(swatch.color))
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${AppAccentSwatches[currentAccentIndex].name}: used for Home, Settings, the Save buttons and dialogs. Each tool keeps its category colour (red PDF, blue video, green photo, amber utilities) so you can tell them apart.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Launcher Icon (Bento Caliper Matrix)
                    Text(
                        text = "LAUNCHER ICON // BENTO CALIPER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    var currentIconVariant by remember { mutableStateOf(appSettings.appIconVariant) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppSettings.AppIconVariant.values().forEach { variant ->
                            val isSelected = currentIconVariant == variant
                            val isDarkVariant = (variant == AppSettings.AppIconVariant.CHARCOAL)
                            val chassisBg = if (isDarkVariant) Color(0xFF161618) else Color(0xFFF5F3EC)
                            val chassisBorder = if (isDarkVariant) Color(0xFF2E2E34) else Color(0xFFD6D1C4)

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        currentIconVariant = variant
                                        appSettings.applyIconVariant(context, variant)
                                        Toast.makeText(context, "Launcher icon set to ${variant.displayName}", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(chassisBg)
                                            .border(1.dp, chassisBorder, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ApparatBentoMark(
                                            size = 34.dp,
                                            isDark = isDarkVariant,
                                            accentColor = BraunOrange
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = variant.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The Bento Caliper Matrix encodes Apparat's 4 instrument pillars (Docs, Video, Graphics, Utility). Supports Android 13+ dynamic themed icons.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            // 2. OUTPUT DESTINATION & FILE BEHAVIOR
            item {
                SettingsGroupCard(title = "02 // OUTPUT & STORAGE") {
                    Text(
                        text = "SAVE FILES TO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = destinationLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    if (pickedFolderUnavailable) {
                        Text(
                            text = "The folder you chose isn't available any more (moved, deleted or permission removed). Files will be saved to Downloads/${appSettings.saveFolderName} until you choose another.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                        )
                    }
                    Text(
                        text = if (usingPickedFolder) {
                            "A folder you chose. If it's removed or the permission is revoked, files go to Downloads/${OutputSaver.DEFAULT_FOLDER} instead."
                        } else {
                            "Every tool saves here with one tap. Use \"Save as…\" on a result to pick another place just once."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Dark text with an accent outline: accent-coloured text is too faint on Ivory.
                    val storageButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    val storageButtonBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!usingPickedFolder) {
                            OutlinedButton(onClick = { renameDraft = appSettings.saveFolderName; showRenameDialog = true }, shape = RoundedCornerShape(6.dp), colors = storageButtonColors, border = storageButtonBorder) {
                                Text("Rename folder", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    OutputSaver.useDownloads(context, appSettings.saveFolderName)
                                    refreshDestination()
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = storageButtonColors,
                                border = storageButtonBorder
                            ) { Text("Use Downloads", fontSize = 12.sp) }
                        }
                        OutlinedButton(onClick = { runCatching { pickDefaultFolder.launch(null) } }, shape = RoundedCornerShape(6.dp), colors = storageButtonColors, border = storageButtonBorder) {
                            Text("Choose folder…", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "WHEN A TOOL CAN CHANGE THE ORIGINAL",
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
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
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
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
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
                            color = MaterialTheme.colorScheme.primary
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

            // 4. QUICK ACTIONS & LAUNCHER SHORTCUTS
            item {
                SettingsGroupCard(title = "04 // QUICK ACTIONS & LAUNCHER SHORTCUTS") {
                    Text(
                        text = "Choose which tools appear as pinned Quick Actions on the Home screen and in your launcher long-press menu (up to 4):",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    val chosenCount = ALL_TOOLS.count { it.id in enabledShortcuts }
                    Text(
                        text = "$chosenCount of 4 chosen",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ALL_TOOLS.forEach { tool ->
                        val isChecked = enabledShortcuts.contains(tool.id)
                        // Android shows at most 4 launcher shortcuts; don't let a 5th be ticked.
                        val canToggle = isChecked || chosenCount < 4
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = canToggle) {
                                    val updated = if (isChecked) {
                                        enabledShortcuts - tool.id
                                    } else {
                                        enabledShortcuts + tool.id
                                    }
                                    enabledShortcuts = updated
                                    appSettings.enabledShortcuts = updated
                                    appSettings.updateLauncherShortcuts(context)
                                    onSettingsChanged()
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                enabled = canToggle,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) {
                                        enabledShortcuts + tool.id
                                    } else {
                                        enabledShortcuts - tool.id
                                    }
                                    enabledShortcuts = updated
                                    appSettings.enabledShortcuts = updated
                                    appSettings.updateLauncherShortcuts(context)
                                    onSettingsChanged()
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
