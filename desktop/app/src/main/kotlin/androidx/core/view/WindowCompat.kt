package androidx.core.view

import android.view.View
import android.view.Window

/** No system bars on desktop; these calls do nothing. */
object WindowCompat {
    @JvmStatic
    fun getInsetsController(window: Window, view: View): WindowInsetsControllerCompat = WindowInsetsControllerCompat()
}

class WindowInsetsControllerCompat {
    var isAppearanceLightStatusBars: Boolean = false
    var isAppearanceLightNavigationBars: Boolean = false
}
