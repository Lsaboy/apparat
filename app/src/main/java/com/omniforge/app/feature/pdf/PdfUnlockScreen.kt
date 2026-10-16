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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.omniforge.app.core.jobs.ToolJobs
import com.omniforge.app.core.jobs.retained
import com.omniforge.app.core.model.ProcessState
import com.omniforge.app.core.theme.*
import com.omniforge.app.core.ui.*
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.pdf.PdfAccess
import com.omniforge.app.engine.pdf.PdfEngine

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_unlock"

@Composable
fun PdfUnlockScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(null) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(null) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(null) }
    var password by retained(JOB_KEY, "password") { mutableStateOf("") }
    var isPasswordVisible by retained(JOB_KEY, "isPasswordVisible") { mutableStateOf(false) }

    var isCheckingAccess by retained(JOB_KEY, "isCheckingAccess") { mutableStateOf(false) }
    var accessStatus by retained(JOB_KEY, "accessStatus") { mutableStateOf<PdfAccess?>(null) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri == null) {
            accessStatus = null
            isCheckingAccess = false
            return@LaunchedEffect
        }
        if (accessStatus == null) {
            isCheckingAccess = true
            try {
                accessStatus = PdfEngine.checkAccess(context, uri)
            } finally {
                isCheckingAccess = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            fileName = FileUtils.getFileName(context, it)
            fileSize = FileUtils.getFileSize(context, it)
            password = ""
            accessStatus = null
            processState = ProcessState.Idle
        }
    }

    ToolScaffold(
        title = "Remove PDF Password",
        categoryName = "PDF Tools",
        accentColor = AccentPdf,
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

            // File selection
            FileSelectionCard(
                fileName = fileName,
                fileSize = fileSize,
                placeholderText = "Select Password-Protected PDF",
                icon = Icons.Default.Lock,
                accentColor = AccentPdf,
                onClick = { filePicker.launch(arrayOf("application/pdf")) }
            )

            if (isCheckingAccess) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentPdf,
                    trackColor = DarkBorder
                )
                Text(
                    text = "Checking PDF encryption status...",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            when (val access = accessStatus) {
                is PdfAccess.Open -> {
                    if (!access.isEncrypted) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = AccentPdf)
                                Text(
                                    text = "This PDF has no password. It is already unlocked and does not need decryption.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                )
                            }
                        }
                    } else {
                        // Owner/permission restricted without a user password
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = AccentPdf)
                                Text(
                                    text = "This PDF opens without a password, but contains restrictions. Tap Unlock below to export an unrestricted copy.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val uri = selectedUri ?: return@Button
                                ToolJobs.launch(context, JOB_KEY, "Remove PDF Password") { appContext ->
                                    processState = ProcessState.Processing(0.5f, "Removing restrictions...")
                                    val result = PdfEngine.removePassword(appContext, uri, "")
                                    result.fold(
                                        onSuccess = { outputFile ->
                                            processState = ProcessState.Success(
                                                outputFile = outputFile,
                                                message = "Restrictions successfully removed!"
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
                            enabled = processState !is ProcessState.Processing
                        ) {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = AmoledBlack)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock & Export PDF", color = AmoledBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is PdfAccess.NeedsPassword -> {
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
                        text = "Enter the current password once to decrypt the PDF and export an unlocked copy.",
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
                            ToolJobs.launch(context, JOB_KEY, "Remove PDF Password") { appContext ->
                                processState = ProcessState.Processing(0.5f, "Decrypting and removing security...")
                                val result = PdfEngine.removePassword(appContext, uri, password)
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
                        enabled = password.isNotEmpty() && !isCheckingAccess && processState !is ProcessState.Processing
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = AmoledBlack)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock & Export PDF", color = AmoledBlack, fontWeight = FontWeight.Bold)
                    }
                }
                is PdfAccess.Damaged -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                            Text(
                                text = access.message,
                                style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed)
                            )
                        }
                    }
                }
                null -> {
                    // No file chosen or check not run
                }
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
                        val appContext = context.applicationContext
                        val saved = FileUtils.saveToDownloads(
                            context = appContext,
                            file = success.outputFile,
                            mimeType = "application/pdf",
                            displayName = "unlocked_${fileName ?: "document.pdf"}"
                        )
                        Toast.makeText(appContext, if (saved != null) "Saved to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}" else "Failed to save", Toast.LENGTH_SHORT).show()
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
