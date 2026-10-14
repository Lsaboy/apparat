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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.ToolScaffold
import com.omniforge.app.engine.common.UtilityEngine

@Composable
fun JsonToolScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var jsonInput by remember { mutableStateOf("{\"app\":\"OmniForge\",\"version\":\"1.0.0\",\"offline\":true,\"features\":[\"video\",\"photo\",\"pdf\"]}") }
    var jsonOutput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ToolScaffold(
        title = "JSON Formatter & Validator",
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

            OutlinedTextField(
                value = jsonInput,
                onValueChange = {
                    jsonInput = it
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("Enter Raw JSON", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentUtility,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val result = UtilityEngine.formatJson(jsonInput, pretty = true)
                        result.fold(
                            onSuccess = {
                                jsonOutput = it
                                errorMessage = null
                            },
                            onFailure = {
                                errorMessage = it.localizedMessage ?: "Invalid JSON syntax"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentUtility)
                ) {
                    Icon(imageVector = Icons.Default.DataObject, contentDescription = null, tint = AmoledBlack)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Prettify", color = AmoledBlack, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val result = UtilityEngine.formatJson(jsonInput, pretty = false)
                        result.fold(
                            onSuccess = {
                                jsonOutput = it
                                errorMessage = null
                            },
                            onFailure = {
                                errorMessage = it.localizedMessage ?: "Invalid JSON syntax"
                            }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
                ) {
                    Text("Minify", color = TextPrimary, fontWeight = FontWeight.SemiBold)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
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
                                    color = TextPrimary
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
