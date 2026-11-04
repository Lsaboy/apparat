package com.omniforge.apparat.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * What the keyboard shortcuts (desktop: Ctrl+O, Ctrl+S, Ctrl+Shift+S, Ctrl+F) do on the screen
 * that's showing. Screens and shared controls register a handler while they're on screen; on
 * Android nothing triggers them.
 */
object KeyboardShortcuts {
    enum class Action { OPEN, SAVE, SAVE_AS, FIND }

    private class Entry(val action: Action, val handler: () -> Unit)

    private val entries = mutableListOf<Entry>()

    /** Set when Find was pressed away from Home; Home focuses its search box once shown. */
    @Volatile
    var pendingFind = false

    fun register(action: Action, handler: () -> Unit): Any = Entry(action, handler).also { synchronized(entries) { entries += it } }

    fun unregister(token: Any) { synchronized(entries) { entries.remove(token) } }

    /**
     * Runs the handler for [action]; false when the screen has none. Open goes to the first
     * picker on screen (the main input); Save to the newest result.
     */
    fun trigger(action: Action): Boolean {
        val entry = synchronized(entries) {
            val matching = entries.filter { it.action == action }
            if (action == Action.OPEN || action == Action.FIND) matching.firstOrNull() else matching.lastOrNull()
        } ?: return false
        entry.handler()
        return true
    }
}

/** Handles [action] while this is on screen and [enabled]. */
@Composable
fun ShortcutHandler(action: KeyboardShortcuts.Action, enabled: Boolean = true, handler: () -> Unit) {
    val current by rememberUpdatedState(handler)
    DisposableEffect(action, enabled) {
        val token = if (enabled) KeyboardShortcuts.register(action) { current() } else null
        onDispose { token?.let(KeyboardShortcuts::unregister) }
    }
}
