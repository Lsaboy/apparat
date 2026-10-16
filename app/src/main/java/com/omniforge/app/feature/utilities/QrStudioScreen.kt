package com.omniforge.app.feature.utilities

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.ToolScaffold
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.common.UtilityEngine
import kotlinx.coroutines.launch

@Composable
fun QrStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("https://github.com") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(inputText) {
        if (inputText.isNotBlank()) {
            try {
                qrBitmap = UtilityEngine.generateQrCode(inputText, 512)
            } catch (_: Exception) {
                qrBitmap = null
            }
        } else {
            qrBitmap = null
        }
    }

    ToolScaffold(
        title = "QR Code Studio",
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter Text, URL, or Wi-Fi config", color = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentUtility,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentUtility
                ),
                shape = RoundedCornerShape(14.dp)
            )

            // QR Preview
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "Generated QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val bmp = qrBitmap ?: return@Button
                            scope.launch {
                                val file = UtilityEngine.saveQrToFile(context, bmp)
                                val saved = FileUtils.saveToDownloads(context, file, "image/png", "qrcode_${System.currentTimeMillis()}.png")
                                Toast.makeText(context, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentUtility),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = AmoledBlack)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save PNG", color = AmoledBlack, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val bmp = qrBitmap ?: return@OutlinedButton
                            scope.launch {
                                val file = UtilityEngine.saveQrToFile(context, bmp)
                                FileUtils.shareFile(context, file, "image/png")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
