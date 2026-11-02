package com.omniforge.apparat.desktop

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.delay

/** Shows Toast.makeText(...).show() messages at the bottom of the window, Android-style. */
object ToastHost {
    private data class Message(val text: String, val long: Boolean, val id: Long = System.nanoTime())

    private var current by mutableStateOf<Message?>(null)

    fun install() {
        Toast.sink = { text, long -> java.awt.EventQueue.invokeLater { current = Message(text, long) } }
    }

    @Composable
    fun BoxScope.Render() {
        val message = current
        LaunchedEffect(message?.id) {
            if (message != null) {
                delay(if (message.long) 3500 else 2200)
                if (current?.id == message.id) current = null
            }
        }
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp, start = 24.dp, end = 24.dp)
        ) {
            var shown by remember { mutableStateOf<Message?>(null) }
            if (message != null) shown = message
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(shown?.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
