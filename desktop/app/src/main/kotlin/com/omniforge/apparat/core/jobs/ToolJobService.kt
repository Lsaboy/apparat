package com.omniforge.apparat.core.jobs

import android.content.Context
import com.omniforge.apparat.core.model.ALL_TOOLS
import com.omniforge.apparat.core.model.ProcessState
import com.omniforge.apparat.desktop.DesktopNotifications

/**
 * Desktop counterpart of the Android foreground service. Jobs run inside the app while it's open;
 * this only posts a desktop notification when a job finishes and its tool isn't on screen.
 */
class ToolJobService {
    companion object {
        fun notifyFinished(context: Context, route: String, title: String, state: ProcessState?) {
            val toolTitle = ALL_TOOLS.firstOrNull { it.route == route }?.title ?: title
            when (state) {
                is ProcessState.Success -> DesktopNotifications.show("$toolTitle finished", state.message, route)
                is ProcessState.Error -> DesktopNotifications.show("$toolTitle failed", state.error, route)
                else -> DesktopNotifications.show("$toolTitle finished", "Open Apparat to see the result.", route)
            }
        }

        fun cancelFinishedNotification(context: Context, route: String) {}
    }
}
