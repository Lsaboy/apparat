package com.omniforge.app.core.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.omniforge.app.engine.pdf.PdfAccess
import com.omniforge.app.engine.pdf.PdfEngine
import kotlinx.coroutines.launch

/**
 * Checks a picked PDF before a tool runs. If it is password-protected, asks for the password
 * (and re-asks on a wrong one).
 *
 * [needsCheck] should be true while the screen has no verified password for [uri]. When the
 * PDF opens, [onReady] gets the password to pass to the engine ("" when there is none).
 * [onFailed] is called when the PDF is damaged or the user cancels the dialog; the screen
 * should clear its selection then.
 */
@Composable
fun PdfPasswordGate(
    uri: Uri?,
    fileName: String?,
    needsCheck: Boolean,
    accentColor: Color,
    onReady: (password: String, pageCount: Int) -> Unit,
    onFailed: (message: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // null = no dialog; false = first ask; true = previous password was wrong.
    var prompt by remember(uri) { mutableStateOf<Boolean?>(null) }
    var checking by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri, needsCheck) {
        if (uri == null || !needsCheck) return@LaunchedEffect
        when (val access = PdfEngine.checkAccess(context, uri)) {
            is PdfAccess.Open -> onReady("", access.pageCount)
            is PdfAccess.NeedsPassword -> prompt = false
            is PdfAccess.Damaged -> onFailed(access.message)
        }
    }

    val wrong = prompt ?: return
    PdfPasswordDialog(
        fileName = fileName,
        wrongPassword = wrong,
        checking = checking,
        accentColor = accentColor,
        onSubmit = { password ->
            val target = uri ?: return@PdfPasswordDialog
            scope.launch {
                checking = true
                val access = PdfEngine.checkAccess(context, target, password)
                checking = false
                when (access) {
                    is PdfAccess.Open -> { prompt = null; onReady(password, access.pageCount) }
                    is PdfAccess.NeedsPassword -> prompt = true
                    is PdfAccess.Damaged -> { prompt = null; onFailed(access.message) }
                }
            }
        },
        onDismiss = {
            prompt = null
            onFailed("This PDF is password-protected. Pick it again and enter the password to continue.")
        }
    )
}

@Composable
fun PdfPasswordDialog(
    fileName: String?,
    wrongPassword: Boolean,
    checking: Boolean,
    accentColor: Color,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val submit = { if (password.isNotEmpty() && !checking) onSubmit(password) }

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = accentColor) },
        title = { Text("Password needed") },
        text = {
            Column {
                Text(
                    text = (fileName?.let { "\"$it\" is password-protected." } ?: "This PDF is password-protected.") +
                        " Enter its password to continue.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text("Wrong password. Try again.") }
                    } else null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (visible) "Hide password" else "Show password"
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = password.isNotEmpty() && !checking) {
                if (checking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Open", color = accentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checking) { Text("Cancel") }
        }
    )
}
