package android.view

import android.content.Context

/** Desktop stand-in. [isInEditMode] is true so Android-only window tweaks are skipped. */
class View(val context: Context) {
    val isInEditMode: Boolean = true
}

class Window
