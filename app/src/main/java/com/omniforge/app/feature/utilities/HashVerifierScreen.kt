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

private const val JOB_KEY = "hash_verifier"

@Composable
fun HashVerifierScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }

    var calculatedHashes by retained(JOB_KEY, "calculatedHashes") { mutableStateOf<Map<String, String>>(emptyMap()) }
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

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentUtility)
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = AmoledBlack)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculate Checksums", color = AmoledBlack, fontWeight = FontWeight.Bold)
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
                    label = { Text("Paste Expected Hash to Verify", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentUtility,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        if (cleanCompare.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (matchedAlgo != null) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (matchedAlgo != null) SuccessGreen else ErrorRed
                                )
                                IconButton(onClick = { compareHash = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
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
                        color = TextPrimary
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
                    color = TextSecondary,
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
