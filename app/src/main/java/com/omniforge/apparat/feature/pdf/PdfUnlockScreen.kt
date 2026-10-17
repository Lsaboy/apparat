package com.omniforge.apparat.feature.pdf

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.sp
import com.omniforge.apparat.core.jobs.ToolJobs
import com.omniforge.apparat.core.jobs.retained
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.core.theme.*
import com.omniforge.apparat.core.ui.*
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.pdf.PdfAccess
import com.omniforge.apparat.engine.pdf.PdfEngine

/** Nav route; also the key for this tool's background job and retained state. */
private const val JOB_KEY = "pdf_unlock"

@Composable
fun PdfUnlockScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var lastHandledInitialUri by retained(JOB_KEY, "lastHandledInitialUri") { mutableStateOf<Uri?>(initialUri) }
    var pendingShareUri by retained(JOB_KEY, "pendingShareUri") { mutableStateOf<Uri?>(null) }

    var selectedUri by retained(JOB_KEY, "selectedUri") { mutableStateOf<Uri?>(initialUri) }
    var fileName by retained(JOB_KEY, "fileName") { mutableStateOf<String?>(initialUri?.let { FileUtils.getFileName(context, it) }) }
    var fileSize by retained(JOB_KEY, "fileSize") { mutableStateOf<Long?>(initialUri?.let { FileUtils.getFileSize(context, it) }) }
    var password by retained(JOB_KEY, "password") { mutableStateOf("") }
    var isPasswordVisible by retained(JOB_KEY, "isPasswordVisible") { mutableStateOf(false) }

    var isCheckingAccess by retained(JOB_KEY, "isCheckingAccess") { mutableStateOf(false) }
    var accessStatus by retained(JOB_KEY, "accessStatus") { mutableStateOf<PdfAccess?>(null) }

    var processState by retained(JOB_KEY, "processState") { mutableStateOf<ProcessState>(ProcessState.Idle) }

    LaunchedEffect(initialUri) {
        if (initialUri != null && initialUri != lastHandledInitialUri) {
            if (ToolJobs.isRunning(JOB_KEY)) {
                pendingShareUri = initialUri
            } else {
                lastHandledInitialUri = initialUri
                pendingShareUri = null
                selectedUri = initialUri
                fileName = FileUtils.getFileName(context, initialUri)
                fileSize = FileUtils.getFileSize(context, initialUri)
                password = ""
                accessStatus = null
                processState = ProcessState.Idle
            }
        }
    }

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
            pendingShareUri = null
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

            // Pending share-in warning card (if user shared a file while another is active)
            if (pendingShareUri != null) {
                val pendingUri = pendingShareUri!!
                val pendingName = remember(pendingUri) { FileUtils.getFileName(context, pendingUri) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Incoming shared file: $pendingName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "A job is currently running on another file. Switch to this new file or dismiss it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    ToolJobs.cancel(JOB_KEY)
                                    selectedUri = pendingUri
                                    fileName = pendingName
                                    fileSize = FileUtils.getFileSize(context, pendingUri)
                                    lastHandledInitialUri = pendingUri
                                    pendingShareUri = null
                                    password = ""
                                    accessStatus = null
                                    processState = ProcessState.Idle
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf))
                            ) {
                                Text("Switch to New File", color = LocalContentColor.current, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    lastHandledInitialUri = pendingUri
                                    pendingShareUri = null
                                }
                            ) {
                                Text("Dismiss", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

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
                    trackColor = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Checking PDF encryption status...",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            when (val access = accessStatus) {
                is PdfAccess.Open -> {
                    if (!access.isEncrypted) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = AccentPdf)
                                Text(
                                    text = "This PDF has no password. It is already unlocked and does not need decryption.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    } else {
                        // Owner/permission restricted without a user password
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, tint = AccentPdf)
                                Text(
                                    text = "This PDF opens without a password, but contains restrictions. Tap Unlock below to export an unrestricted copy.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf)),
                            enabled = processState !is ProcessState.Processing
                        ) {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = LocalContentColor.current)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock & Export PDF", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is PdfAccess.NeedsPassword -> {
                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Enter PDF Password", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPdf,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = AccentPdf
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Text(
                        text = "Enter the current password once to decrypt the PDF and export an unlocked copy.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPdf, contentColor = onPrimaryForAccent(AccentPdf)),
                        enabled = password.isNotEmpty() && !isCheckingAccess && processState !is ProcessState.Processing
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = LocalContentColor.current)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock & Export PDF", color = LocalContentColor.current, fontWeight = FontWeight.Bold)
                    }
                }
                is PdfAccess.Damaged -> {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                    originalSize = null, // size change is irrelevant when removing a password
                    message = success.message,
                    saveMimeType = FileUtils.mimeForFile(success.outputFile),
                    saveName = remember(success.outputFile) { FileUtils.outputName(fileName, "document", "_unlocked", success.outputFile) },
                    onShare = {
                        FileUtils.shareFile(context, success.outputFile, FileUtils.mimeForFile(success.outputFile))
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
