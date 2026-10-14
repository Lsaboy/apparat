package com.omniforge.app.feature.pdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.pdf.PdfEngine
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfUnlockScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long?>(null) }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var processState by remember { mutableStateOf<ProcessState>(ProcessState.Idle) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(
        title = "Remove PDF Password",
        categoryName = "PDF Tools",
        accentColor = AccentPdf,
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

            // File selection
            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                placeholderText = "Select Password-Protected PDF",
                icon = Icons.Default.Lock,
                accentColor = AccentPdf,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enter PDF Password", color = TextSecondary) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = TextSecondary
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPdf,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentPdf
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "Enter the current password once. OmniForge will decrypt the PDF and export an unlocked, permanent DRM-free copy.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            // Action Button
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    if (password.isEmpty()) {
                        Toast.makeText(context, "Please enter the password", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        processState = ProcessState.Processing(0.5f, "Decrypting and removing security...")
                        val result = PdfEngine.removePassword(context, uri, password)
                        result.fold(
                            onSuccess = { outputFile ->
                                processState = ProcessState.Success(
                                    outputFile = outputFile,
                                    message = "Password successfully removed!"
                                )
                            },
                            onFailure = { err ->
                                processState = ProcessState.Error(err.localizedMessage ?: "Failed to unlock PDF")
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf),
                enabled = selectedUri != null && password.isNotEmpty() && processState !is ProcessState.Processing
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = AmoledBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock & Export Decrypted PDF",
                    color = AmoledBlack,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status & Results
            ProcessStateView(state = processState, accentColor = AccentPdf)

            if (processState is ProcessState.Success) {
                val success = processState as ProcessState.Success
                ResultCard(
                    outputFile = success.outputFile,
                    originalSize = fileSize,
                    message = success.message,
                    onSaveToDownloads = {
                        val saved = FileUtils.saveToDownloads(
                            context = context,
                            file = success.outputFile,
                            mimeType = "application/pdf",
                            displayName = "unlocked_${fileName ?: "document.pdf"}"
                        )
                        Toast.makeText(context, if (saved != null) "Saved to Downloads/OmniForge" else "Failed to save", Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, "application/pdf")
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
