package androidx.compose.ui.platform

import android.content.Context
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

// Desktop stand-ins for Android-only composition locals used by the shared screens.

/** The app-wide desktop [Context]. */
val LocalContext = staticCompositionLocalOf<Context> { Context.app }

/** A placeholder view; [View.isInEditMode] is true so Android window tweaks are skipped. */
val LocalView = staticCompositionLocalOf { View(Context.app) }
