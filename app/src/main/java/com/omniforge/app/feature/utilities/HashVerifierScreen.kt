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
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.FileSelectionCard
import com.omniforge.app.core.ui.ToolScaffold
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.common.UtilityEngine
import kotlinx.coroutines.launch

@Composable
fun HashVerifierScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }

    var md5Hash by remember { mutableStateOf<String?>(null) }
    var sha256Hash by remember { mutableStateOf<String?>(null) }
    var compareHash by remember { mutableStateOf("") }
    var isCalculating by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            isCalculating = true
            scope.launch {
                md5Hash = UtilityEngine.calculateChecksum(context, it, "MD5").getOrNull()
                sha256Hash = UtilityEngine.calculateChecksum(context, it, "SHA-256").getOrNull()
                isCalculating = false
            }
        }
    }

    ToolScaffold(
        title = "Checksum & Hash Verifier",
        categoryName = "Quick Utilities",
        accentColor = AccentUtility,
        onBack = onBack
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

            if (isCalculating) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentUtility,
                    trackColor = DarkBorder
                )
            }

            if (sha256Hash != null) {
                HashCard(
                    title = "SHA-256 Checksum",
                    hash = sha256Hash!!,
                    onCopy = {
                        copyToClipboard(context, "SHA-256", sha256Hash!!)
                        Toast.makeText(context, "Copied SHA-256", Toast.LENGTH_SHORT).show()
                    }
                )

                HashCard(
                    title = "MD5 Checksum",
                    hash = md5Hash ?: "Calculating...",
                    onCopy = {
                        md5Hash?.let {
                            copyToClipboard(context, "MD5", it)
                            Toast.makeText(context, "Copied MD5", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                // Comparison Box
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
                        if (compareHash.isNotBlank()) {
                            val cleanCompare = compareHash.trim()
                            val isMatch = cleanCompare.equals(sha256Hash, ignoreCase = true) ||
                                    cleanCompare.equals(md5Hash, ignoreCase = true)
                            Icon(
                                imageVector = if (isMatch) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (isMatch) SuccessGreen else ErrorRed
                            )
                        }
                    }
                )

                if (compareHash.isNotBlank()) {
                    val cleanCompare = compareHash.trim()
                    val isMatch = cleanCompare.equals(sha256Hash, ignoreCase = true) ||
                            cleanCompare.equals(md5Hash, ignoreCase = true)
                    Text(
                        text = if (isMatch) "✓ Checksum Match: File integrity confirmed!" else "✗ Checksum Mismatch: File altered or invalid hash!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isMatch) SuccessGreen else ErrorRed,
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
