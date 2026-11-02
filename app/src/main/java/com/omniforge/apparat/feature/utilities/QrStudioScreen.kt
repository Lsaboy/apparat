package com.omniforge.apparat.feature.utilities

import androidx.compose.foundation.BorderStroke
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniforge.apparat.core.ui.SaveButtons
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.ToolScaffold
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.common.UtilityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val JOB_KEY = "qr_studio"

enum class QrTemplate(val label: String) {
    TEXT_URL("Text / URL"),
    WIFI("Wi-Fi"),
    UPI("UPI Pay"),
    VCARD("Contact")
}

@Composable
fun QrStudioScreen(
    initialText: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTemplate by retained(JOB_KEY, "selectedTemplate") { mutableStateOf(QrTemplate.TEXT_URL) }

    // Text / URL
    var rawText by retained(JOB_KEY, "rawText") { mutableStateOf(initialText ?: "https://github.com") }

    // Wi-Fi
    var wifiSsid by retained(JOB_KEY, "wifiSsid") { mutableStateOf("") }
    var wifiPassword by retained(JOB_KEY, "wifiPassword") { mutableStateOf("") }
    var wifiSecurity by retained(JOB_KEY, "wifiSecurity") { mutableStateOf("WPA") } // WPA, WEP, nopass
    var wifiHidden by retained(JOB_KEY, "wifiHidden") { mutableStateOf(false) }

    // UPI Payment
    var upiVpa by retained(JOB_KEY, "upiVpa") { mutableStateOf("") }
    var upiName by retained(JOB_KEY, "upiName") { mutableStateOf("") }
    var upiAmount by retained(JOB_KEY, "upiAmount") { mutableStateOf("") }
    var upiNote by retained(JOB_KEY, "upiNote") { mutableStateOf("") }

    // Contact (vCard)
    var vcardName by retained(JOB_KEY, "vcardName") { mutableStateOf("") }
    var vcardPhone by retained(JOB_KEY, "vcardPhone") { mutableStateOf("") }
    var vcardEmail by retained(JOB_KEY, "vcardEmail") { mutableStateOf("") }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // The current QR as a PNG in the output cache, for Save / Save as / Share.
    var qrFile by remember { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(qrBitmap) {
        val bmp = qrBitmap
        val previous = qrFile
        qrFile = bmp?.let { UtilityEngine.saveQrToFile(context.applicationContext, it) }
        previous?.delete()
    }
    var qrErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) {
            selectedTemplate = QrTemplate.TEXT_URL
            rawText = initialText
        }
    }

    val activePayload = remember(
        selectedTemplate, rawText,
        wifiSsid, wifiPassword, wifiSecurity, wifiHidden,
        upiVpa, upiName, upiAmount, upiNote,
        vcardName, vcardPhone, vcardEmail
    ) {
        when (selectedTemplate) {
            QrTemplate.TEXT_URL -> rawText
            QrTemplate.WIFI -> UtilityEngine.formatWifiQr(wifiSsid, wifiPassword, wifiSecurity, wifiHidden)
            QrTemplate.UPI -> UtilityEngine.formatUpiQr(upiVpa, upiName, upiAmount, upiNote)
            QrTemplate.VCARD -> UtilityEngine.formatVCardQr(vcardName, vcardPhone, vcardEmail)
        }
    }

    LaunchedEffect(activePayload) {
        if (activePayload.isNotBlank()) {
            delay(150)
            try {
                val bmp = withContext(Dispatchers.Default) {
                    UtilityEngine.generateQrCode(activePayload, 512)
                }
                qrBitmap = bmp
                qrErrorMessage = null
            } catch (e: Exception) {
                qrBitmap = null
                val msg = e.message ?: ""
                qrErrorMessage = if (e is com.google.zxing.WriterException || msg.contains("Data too big", ignoreCase = true) || msg.contains("too large", ignoreCase = true)) {
                    "Text is too long for a QR code"
                } else {
                    "Failed to generate QR code: ${e.localizedMessage ?: "Unknown error"}"
                }
            }
        } else {
            qrBitmap = null
            qrErrorMessage = null
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // Template Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QrTemplate.values().forEach { template ->
                    FilterChip(
                        selected = selectedTemplate == template,
                        onClick = { selectedTemplate = template },
                        label = { Text(template.label, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentUtility,
                            selectedLabelColor = onPrimaryForAccent(AccentUtility)
                        )
                    )
                }
            }

            // Template Specific Fields
            when (selectedTemplate) {
                QrTemplate.TEXT_URL -> {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter Text, URL, or data", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentUtility,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = AccentUtility
                        ),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 3
                    )
                }

                QrTemplate.WIFI -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Network Name (SSID)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentUtility,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = AccentUtility
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        if (wifiSecurity != "nopass") {
                            OutlinedTextField(
                                value = wifiPassword,
                                onValueChange = { wifiPassword = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Wi-Fi Password", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentUtility,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = AccentUtility
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // Security type chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("WPA" to "WPA/WPA2", "WEP" to "WEP", "nopass" to "Open (None)").forEach { (key, label) ->
                                FilterChip(
                                    selected = wifiSecurity == key,
                                    onClick = { wifiSecurity = key },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AccentUtility,
                                        selectedLabelColor = onPrimaryForAccent(AccentUtility)
                                    )
                                )
                            }
                        }
                    }
                }

                QrTemplate.UPI -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isVpaInvalid = upiVpa.isNotBlank() && !UtilityEngine.isValidUpiId(upiVpa)
                        OutlinedTextField(
                            value = upiVpa,
                            onValueChange = { upiVpa = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("UPI ID / VPA (e.g. merchant@okhdfcbank)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            isError = isVpaInvalid,
                            supportingText = if (isVpaInvalid) {
                                { Text("A UPI ID looks like name@bank (no spaces). No QR until it's valid.") }
                            } else null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentUtility,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = AccentUtility
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = upiName,
                            onValueChange = { upiName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Payee Name (Required by UPI apps)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentUtility,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = AccentUtility
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isAmountInvalid = upiAmount.isNotBlank() && !UtilityEngine.isValidUpiAmount(upiAmount)
                            OutlinedTextField(
                                value = upiAmount,
                                onValueChange = { upiAmount = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Amount (₹, opt.)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                isError = isAmountInvalid,
                                supportingText = if (isAmountInvalid) {
                                    { Text("Digits only, like 500 or 150.50 (no commas). No QR until it's valid.", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                                } else null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentUtility,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = AccentUtility,
                                    errorBorderColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = upiNote,
                                onValueChange = { upiNote = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("Note (opt.)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentUtility,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = AccentUtility
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                QrTemplate.VCARD -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = vcardName,
                            onValueChange = { vcardName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Full Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentUtility,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = AccentUtility
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = vcardPhone,
                            onValueChange = { vcardPhone = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Phone Number", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentUtility,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = AccentUtility
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        OutlinedTextField(
                            value = vcardEmail,
                            onValueChange = { vcardEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email Address", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentUtility,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = AccentUtility
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

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

                qrFile?.let { f ->
                    SaveButtons(
                        file = f,
                        mimeType = "image/png",
                        fileName = remember(f) { "qrcode_${java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.US).format(java.util.Date())}.png" }
                    )
                    OutlinedButton(
                        onClick = { FileUtils.shareFile(context, f, "image/png") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (qrErrorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = qrErrorMessage!!,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            } else if (activePayload.isNotBlank()) {
                Text(
                    text = "Generating QR code...",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            } else {
                Text(
                    text = "Fill in the fields above to generate a QR code",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
