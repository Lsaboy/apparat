package android.app

import android.content.Context
import android.view.Window

/** Desktop stand-in so shared theme code compiles; there are no Android activities on desktop. */
open class Activity : Context() {
    val window: Window = Window()
}
