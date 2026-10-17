package com.vedant.apparat.feature.utilities

import androidx.compose.foundation.BorderStroke
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedant.apparat.core.ui.SaveButtons
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.ui.ToolScaffold
import com.vedant.apparat.core.jobs.retained
import com.vedant.apparat.core.util.FileUtils
import com.vedant.apparat.engine.common.UtilityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val JOB_KEY = "base64_studio"

@Composable
fun Base64StudioScreen(
    initialText: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var lastHandledInitialText by retained(JOB_KEY, "lastHandledInitialText") { mutableStateOf<String?>(null) }
    var inputText by retained(JOB_KEY, "inputText") { mutableStateOf(initialText ?: "") }
    var outputText by retained(JOB_KEY, "outputText") { mutableStateOf("") }
    var isEncoding by retained(JOB_KEY, "isEncoding") { mutableStateOf(true) }
    var urlSafe by retained(JOB_KEY, "urlSafe") { mutableStateOf(false) }

    var detectedBinaryType by retained(JOB_KEY, "detectedBinaryType") { mutableStateOf<Pair<String, String>?>(null) }
    var decodedBinaryBytes by retained(JOB_KEY, "decodedBinaryBytes") { mutableStateOf<ByteArray?>(null) }
    var isSavingBinary by retained(JOB_KEY, "isSavingBinary") { mutableStateOf(false) }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank() && initialText != lastHandledInitialText) {
            lastHandledInitialText = initialText
            inputText = initialText
            withContext(Dispatchers.Default) {
                if (isEncoding) {
                    val encoded = UtilityEngine.encodeBase64(initialText, urlSafe)
                    withContext(Dispatchers.Main) {
                        outputText = encoded
                        detectedBinaryType = null
                        decodedBinaryBytes = null
                    }
                } else {
                    val clean = UtilityEngine.stripDataUrlPrefix(initialText)
                    val byteResult = UtilityEngine.decodeBase64ToBytes(clean, urlSafe)
                    byteResult.fold(
                        onSuccess = { bytes ->
                            val detected = UtilityEngine.detectBinaryType(bytes)
                            if (detected != null) {
                                val textResult = UtilityEngine.decodeBase64(clean, urlSafe)
                                withContext(Dispatchers.Main) {
                                    detectedBinaryType = detected
                                    decodedBinaryBytes = bytes
                                    outputText = textResult.getOrDefault("")
                                }
                            } else {
                                val textResult = UtilityEngine.decodeBase64(clean, urlSafe)
                                withContext(Dispatchers.Main) {
                                    outputText = textResult.getOrElse { "Decoding error: ${it.localizedMessage}" }
                                    detectedBinaryType = null
                                    decodedBinaryBytes = null
                                }
                            }
                        },
                        onFailure = { err ->
                            withContext(Dispatchers.Main) {
                                outputText = "Decoding error: ${err.localizedMessage}"
                                detectedBinaryType = null
                                decodedBinaryBytes = null
                            }
                        }
                    )
                }
            }
        }
    }

    ToolScaffold(
        title = "Base64 Studio",
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

            // Mode Selector
            Row(modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = isEncoding,
                    onClick = {
                        isEncoding = true
                        outputText = ""
                        detectedBinaryType = null
                        decodedBinaryBytes = null
                    },
                    label = { Text("Encode to Base64") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = onPrimaryForAccent(AccentUtility)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !isEncoding,
                    onClick = {
                        isEncoding = false
                        outputText = ""
                        detectedBinaryType = null
                        decodedBinaryBytes = null
                    },
                    label = { Text("Decode from Base64") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = onPrimaryForAccent(AccentUtility)
                    )
                )
            }

            // URL Safe Option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = urlSafe,
                    onClick = { urlSafe = !urlSafe },
                    label = { Text("URL-Safe (- and _)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = onPrimaryForAccent(AccentUtility)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (urlSafe) "URL-safe format (- and _ instead of + and /)" else "Standard Base64 format (+ and /)",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    fontSize = 11.sp
                )
            }

            // Action row: Clear & Paste
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEncoding) "Input Text" else "Input Base64",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    inputText = text
                                    outputText = ""
                                    detectedBinaryType = null
                                    decodedBinaryBytes = null
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = AccentUtility, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", color = accentText(AccentUtility), fontSize = 12.sp)
                    }

                    if (inputText.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                inputText = ""
                                outputText = ""
                                detectedBinaryType = null
                                decodedBinaryBytes = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    detectedBinaryType = null
                    decodedBinaryBytes = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text(if (isEncoding) "Enter text to encode..." else "Paste Base64 string to decode...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentUtility,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp)
            )

            Button(
                onClick = {
                    val textToProcess = inputText
                    scope.launch(Dispatchers.Default) {
                        if (isEncoding) {
                            val encoded = UtilityEngine.encodeBase64(textToProcess, urlSafe = urlSafe)
                            withContext(Dispatchers.Main) {
                                outputText = encoded
                                detectedBinaryType = null
                                decodedBinaryBytes = null
                            }
                        } else {
                            val clean = UtilityEngine.stripDataUrlPrefix(textToProcess)
                            val bytesResult = UtilityEngine.decodeBase64ToBytes(clean, urlSafe = urlSafe)
                            if (bytesResult.isFailure) {
                                withContext(Dispatchers.Main) {
                                    outputText = "Invalid Base64 input"
                                    detectedBinaryType = null
                                    decodedBinaryBytes = null
                                }
                            } else {
                                val bytes = bytesResult.getOrThrow()
                                val binaryType = UtilityEngine.detectBinaryType(bytes)
                                if (binaryType != null) {
                                    val textResult = UtilityEngine.decodeBase64(clean, urlSafe = urlSafe)
                                    withContext(Dispatchers.Main) {
                                        detectedBinaryType = binaryType
                                        decodedBinaryBytes = bytes
                                        outputText = textResult.getOrDefault("")
                                    }
                                } else {
                                    val textResult = UtilityEngine.decodeBase64(clean, urlSafe = urlSafe)
                                    withContext(Dispatchers.Main) {
                                        detectedBinaryType = null
                                        decodedBinaryBytes = null
                                        outputText = textResult.getOrElse { it.localizedMessage ?: "Invalid Base64 string" }
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentUtility, contentColor = onPrimaryForAccent(AccentUtility)),
                enabled = inputText.isNotBlank()
            ) {
                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = LocalContentColor.current)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEncoding) "Encode" else "Decode", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
            }

            // Detected Binary File Card
            if (detectedBinaryType != null && decodedBinaryBytes != null) {
                val mime = detectedBinaryType!!.first
                val ext = detectedBinaryType!!.second
                val bytes = decodedBinaryBytes!!

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (ext == ".pdf") Icons.Default.PictureAsPdf else Icons.Default.Image,
                                contentDescription = null,
                                tint = AccentUtility,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Binary File: ${mime.substringAfter('/') .uppercase()}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "${FileUtils.formatFileSize(bytes.size.toLong())} • Format $ext",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        // Write the decoded bytes to an output file so the shared Save buttons can use it.
                        var binaryFile by remember(bytes) { mutableStateOf<File?>(null) }
                        LaunchedEffect(bytes, ext) {
                            binaryFile = withContext(Dispatchers.IO) {
                                runCatching {
                                    FileUtils.createTempOutputFile(context.applicationContext, "decoded", ext).apply { writeBytes(bytes) }
                                }.getOrNull()
                            }
                        }
                        binaryFile?.let { f ->
                            SaveButtons(
                                file = f,
                                mimeType = mime,
                                fileName = remember(f) { "Decoded_${java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(java.util.Date())}$ext" }
                            )
                        }
                    }
                }
            }

            // Output Text Card (for text results or errors)
            if (outputText.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Result",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Base64 Result", outputText))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentUtility)
                            }
                        }
                        Text(
                            text = outputText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
