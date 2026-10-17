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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedant.apparat.core.jobs.retained
import com.vedant.apparat.core.theme.*
import com.vedant.apparat.core.ui.ToolScaffold
import com.vedant.apparat.engine.common.UtilityEngine

private const val JOB_KEY = "json_tool"

@Composable
fun JsonToolScreen(
    initialText: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val jsonScope = rememberCoroutineScope()

    var jsonInput by retained(JOB_KEY, "jsonInput") {
        mutableStateOf(initialText ?: "{\n  \"app\": \"Apparat\",\n  \"version\": \"2.0.0\",\n  \"pages\": 12,\n  \"features\": [\"video\", \"photo\", \"pdf\", \"utilities\"]\n}")
    }
    var jsonOutput by retained(JOB_KEY, "jsonOutput") { mutableStateOf("") }
    var errorMessage by retained(JOB_KEY, "errorMessage") { mutableStateOf<String?>(null) }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) {
            jsonInput = initialText
            jsonOutput = ""
            errorMessage = null
        }
    }

    ToolScaffold(
        title = "JSON Formatter & Validator",
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

            OutlinedTextField(
                value = jsonInput,
                onValueChange = {
                    jsonInput = it
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("Enter Raw JSON", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentUtility,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                trailingIcon = {
                    if (jsonInput.isNotEmpty()) {
                        IconButton(onClick = {
                            jsonInput = ""
                            errorMessage = null
                        }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clip.isNullOrBlank()) {
                                jsonInput = clip
                                errorMessage = null
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Paste", tint = AccentUtility)
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val input = jsonInput
                        jsonScope.launch {
                            // Off the main thread: a pasted multi-MB JSON shouldn't freeze the screen.
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                UtilityEngine.formatJson(input, pretty = true)
                            }
                            result.fold(
                                onSuccess = {
                                    jsonOutput = it
                                    errorMessage = null
                                },
                                onFailure = {
                                    jsonOutput = "" // don't leave an older result next to the error
                                    errorMessage = it.localizedMessage ?: "Invalid JSON syntax"
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentUtility, contentColor = onPrimaryForAccent(AccentUtility))
                ) {
                    Icon(imageVector = Icons.Default.DataObject, contentDescription = null, tint = LocalContentColor.current)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Prettify", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val input = jsonInput
                        jsonScope.launch {
                            // Off the main thread: a pasted multi-MB JSON shouldn't freeze the screen.
                            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                UtilityEngine.formatJson(input, pretty = false)
                            }
                            result.fold(
                                onSuccess = {
                                    jsonOutput = it
                                    errorMessage = null
                                },
                                onFailure = {
                                    jsonOutput = "" // don't leave an older result next to the error
                                    errorMessage = it.localizedMessage ?: "Invalid JSON syntax"
                                }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text("Minify", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
            }

            if (errorMessage != null) {
                Text(
                    text = "✗ $errorMessage",
                    style = MaterialTheme.typography.bodyMedium.copy(color = ErrorRed, fontWeight = FontWeight.Bold)
                )
            }

            if (jsonOutput.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Formatted JSON",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("JSON", jsonOutput))
                                Toast.makeText(context, "Copied JSON to clipboard", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = AccentUtility)
                            }
                        }
                        Text(
                            text = jsonOutput,
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
