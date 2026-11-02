package android.widget

import android.content.Context

/**
 * Desktop stand-in for android.widget.Toast. [show] hands the text to [Toast.sink], which the
 * desktop window renders as a short message at the bottom of the screen.
 */
class Toast private constructor(private val text: CharSequence, private val duration: Int) {
    fun show() {
        sink(text.toString(), duration == LENGTH_LONG)
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        /** Set by the desktop window. Defaults to printing. */
        @Volatile
        var sink: (text: String, long: Boolean) -> Unit = { text, _ -> println("Toast: $text") }

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence, duration: Int): Toast = Toast(text, duration)
    }
}
