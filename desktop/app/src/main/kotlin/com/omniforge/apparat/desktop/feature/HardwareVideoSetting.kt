package com.omniforge.apparat.desktop.feature

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.desktop.platform.HwAccel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Desktop-only setting under the shared Settings screen: GPU video encoding on/off and what was found. */
@Composable
fun HardwareVideoSetting(appSettings: AppSettings, modifier: Modifier = Modifier) {
    var on by remember { mutableStateOf(appSettings.hardwareVideo) }
    var summary by remember { mutableStateOf(HwAccel.summary()) }
    LaunchedEffect(on) {
        HwAccel.enabled = on
        // Detection can take a second the first time; never on the UI thread.
        summary = withContext(Dispatchers.IO) { if (on) HwAccel.backend(); HwAccel.summary() }
    }
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hardware video encoding", style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = on, onCheckedChange = { on = it; appSettings.hardwareVideo = it })
        }
    }
}
