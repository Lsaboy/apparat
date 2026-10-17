package com.omniforge.app.feature.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.FileSelectionCard
import com.omniforge.app.core.ui.ProcessStateView
import com.omniforge.app.core.ui.ToolScaffold
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.common.UtilityEngine

enum class HashInputMode {
    FILE, TEXT
}

private const val JOB_KEY = "hash_verifier"

@Composable
fun HashVerifierScreen(
    initialUri: Uri? = null,
    initialText: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(null) }
    var lastHandledInitialText by retained(JOB_KEY, "lastHandledInitialText") { mutableStateOf<String?>(initialText) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var inputMode by retained(JOB_KEY, "inputMode") {
        mutableStateOf(if (initialText != null) HashInputMode.TEXT else HashInputMode.FILE)
    }
    var textInput by retained(JOB_KEY, "textInput") { mutableStateOf(initialText ?: "") }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(initialUri?.let { FileUtils.getFileSize(context, it) }) }

    var calculatedHashes by retained(JOB_KEY, "calculatedHashes") {
        mutableStateOf<Map<String, String>>(
            if (!initialText.isNullOrBlank()) UtilityEngine.calculateTextChecksums(initialText) else emptyMap()
        )
    }
    var compareHash by retained(JOB_KEY, "compareHash") { mutableStateOf("") }
    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val startCalculation: (Uri) -> Unit = { uri ->
        ToolJobs.launch(context, JOB_KEY, "Hash Verifier") { appContext ->
            processState = ProcessState.Processing(0f, "Calculating checksums...")
            val result = UtilityEngine.calculateChecksums(
                context = appContext,
                uri = uri,
                algorithms = listOf("SHA-256", "SHA-512", "SHA-1", "MD5"),
                onProgress = { p ->
                    val percent = (p * 100).toInt()
                    val msg = if (p > 0f) "Calculating checksums... $percent%" else "Calculating checksums..."
                    processState = ProcessState.Processing(p, msg)
                }
            )
            result.fold(
                onSuccess = { hashes ->
                    calculatedHashes = hashes
                    processState = ProcessState.Idle
                },
                onFailure = { err ->
                    processState = ProcessState.Error(err.localizedMessage ?: "Failed to calculate checksums")
                }
            )
        }
    }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank() && initialText != lastHandledInitialText) {
            lastHandledInitialText = initialText
            inputMode = HashInputMode.TEXT
            textInput = initialText
            calculatedHashes = UtilityEngine.calculateTextChecksums(initialText)
            processState = ProcessState.Idle
        }
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != lastHandledInitialUri) {
            inputMode = HashInputMode.FILE
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                lastHandledInitialUri = initialUri
                pendingShareUri = null
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
                calculatedHashes = emptyMap()
                startCalculation(initialUri)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            pendingShareUri = null
            calculatedHashes = emptyMap()
            startCalculation(it)
        }
    }

    ToolScaffold(
        title = "Checksum & Hash Verifier",
        categoryName = "Quick Utilities",
        accentColor = AccentUtility,
        onBack = onBack,
        jobKey = JOB_KEY
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Mode Selector: Verify File vs Hash Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = inputMode == HashInputMode.FILE,
                    onClick = {
                        if (inputMode != HashInputMode.FILE) {
                            inputMode = HashInputMode.FILE
                            calculatedHashes = emptyMap()
                            compareHash = ""
                            processState = ProcessState.Idle
                            selectedUri?.let { startCalculation(it) }
                        }
                    },
                    label = { Text("Verify File") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = onPrimaryForAccent(AccentUtility)
                    )
                )
                FilterChip(
                    selected = inputMode == HashInputMode.TEXT,
                    onClick = {
                        if (inputMode != HashInputMode.TEXT) {
                            inputMode = HashInputMode.TEXT
                            compareHash = ""
                            processState = ProcessState.Idle
                            calculatedHashes = if (textInput.isNotBlank()) {
                                UtilityEngine.calculateTextChecksums(textInput)
                            } else {
                                emptyMap()
                            }
                        }
                    },
                    label = { Text("Hash Text") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = onPrimaryForAccent(AccentUtility)
                    )
                )
            }

            if (inputMode == HashInputMode.FILE) {
                // Pending share-in warning card
                if (pendingShareUri != null) {
                    val pendingUri = pendingShareUri!!
                    val pendingName = remember(pendingUri) { FileUtils.getFileName(context, pendingUri) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Incoming shared file: $pendingName",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "A job is currently running on another file. Switch to this new file or dismiss it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        ToolJobs.cancel(JOB_KEY)
                                        selectedUri = pendingUri
                                        fileName = pendingName
                                        fileSize = FileUtils.getFileSize(context, pendingUri)
                                        lastHandledInitialUri = pendingUri
                                        pendingShareUri = null
                                        calculatedHashes = emptyMap()
                                        startCalculation(pendingUri)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentUtility)
                                ) {
                                    Text("Switch to New File", color = onPrimaryForAccent(AccentUtility), fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        lastHandledInitialUri = pendingUri
                                        pendingShareUri = null
                                    }
                                ) {
                                    Text("Dismiss", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                FileSelectionCard(
                    fileName = fileName,
                    fileSize = fileSize,
                    placeholderText = "Select Any File to Calculate Hash",
                    icon = Icons.Default.Fingerprint,
                    accentColor = AccentUtility,
                    onClick = { picker.launch(arrayOf("*/*")) }
                )

                ProcessStateView(state = processState, accentColor = AccentUtility)

                if (selectedUri != null && calculatedHashes.isEmpty() && processState !is ProcessState.Processing) {
                    Button(
                        onClick = { selectedUri?.let { startCalculation(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentUtility)
                    ) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = onPrimaryForAccent(AccentUtility))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculate Checksums", color = onPrimaryForAccent(AccentUtility), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Text Mode
                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        calculatedHashes = if (it.isNotBlank()) {
                            UtilityEngine.calculateTextChecksums(it)
                        } else {
                            emptyMap()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Enter or paste text to hash", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    minLines = 3,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentUtility,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        if (textInput.isNotEmpty()) {
                            IconButton(onClick = {
                                textInput = ""
                                calculatedHashes = emptyMap()
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    textInput = clip
                                    calculatedHashes = UtilityEngine.calculateTextChecksums(clip)
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Paste", tint = AccentUtility)
                            }
                        }
                    }
                )

                if (textInput.isNotEmpty()) {
                    val byteCount = remember(textInput) { textInput.toByteArray(Charsets.UTF_8).size }
                    Text(
                        text = "${textInput.length} characters • $byteCount UTF-8 bytes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (calculatedHashes.isNotEmpty()) {
                listOf("SHA-256", "SHA-512", "SHA-1", "MD5").forEach { algo ->
                    calculatedHashes[algo]?.let { hash ->
                        HashCard(
                            title = "$algo Checksum",
                            hash = hash,
                            onCopy = {
                                copyToClipboard(context, algo, hash)
                                Toast.makeText(context, "Copied $algo", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                // Comparison Box
                val cleanCompare = compareHash.trim()
                val matchedAlgo = if (cleanCompare.isNotEmpty()) {
                    calculatedHashes.entries.firstOrNull { (_, hash) ->
                        cleanCompare.equals(hash, ignoreCase = true)
                    }?.key
                } else null

                OutlinedTextField(
                    value = compareHash,
                    onValueChange = { compareHash = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Paste Expected Hash to Verify", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentUtility,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        if (cleanCompare.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (matchedAlgo != null) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (matchedAlgo != null) SuccessGreen else ErrorRed
                                )
                                IconButton(onClick = { compareHash = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clip.isNullOrBlank()) {
                                    compareHash = clip
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Paste from clipboard", tint = AccentUtility)
                            }
                        }
                    }
                )

                if (cleanCompare.isNotEmpty()) {
                    Text(
                        text = if (matchedAlgo != null) "✓ Checksum Match ($matchedAlgo): File integrity confirmed!" else "✗ Checksum Mismatch: File altered or invalid hash!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (matchedAlgo != null) SuccessGreen else ErrorRed,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HashCard(title: String, hash: String, onCopy: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                IconButton(onClick = onCopy) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentUtility)
                }
            }
            Text(
                text = hash,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
