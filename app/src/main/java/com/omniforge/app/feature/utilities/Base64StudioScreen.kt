package com.omniforge.app.feature.utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
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
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.ToolScaffold
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.engine.common.UtilityEngine

private const val JOB_KEY = "base64_studio"

@Composable
fun Base64StudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var inputText by retained(JOB_KEY, "inputText") { mutableStateOf("") }
    var outputText by retained(JOB_KEY, "outputText") { mutableStateOf("") }
    var isEncoding by retained(JOB_KEY, "isEncoding") { mutableStateOf(true) }
    var urlSafe by retained(JOB_KEY, "urlSafe") { mutableStateOf(false) }

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
                    },
                    label = { Text("Encode to Base64") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = AmoledBlack
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = !isEncoding,
                    onClick = {
                        isEncoding = false
                        outputText = ""
                    },
                    label = { Text("Decode from Base64") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentUtility,
                        selectedLabelColor = AmoledBlack
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
                        selectedLabelColor = AmoledBlack
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (urlSafe) "URL-safe format (- and _ instead of + and /)" else "Standard Base64 format (+ and /)",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    fontSize = 11.sp
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                label = { Text(if (isEncoding) "Text to Encode" else "Base64 to Decode", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentUtility,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = {
                    if (isEncoding) {
                        outputText = UtilityEngine.encodeBase64(inputText, urlSafe = urlSafe)
                    } else {
                        val result = UtilityEngine.decodeBase64(inputText, urlSafe = urlSafe)
                        outputText = result.getOrElse { it.localizedMessage ?: "Invalid Base64 string" }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentUtility),
                enabled = inputText.isNotBlank()
            ) {
                Icon(imageVector = Icons.Default.Transform, contentDescription = null, tint = AmoledBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEncoding) "Encode" else "Decode", color = AmoledBlack, fontWeight = FontWeight.Bold)
            }

            if (outputText.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Result",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
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
                                color = TextPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
