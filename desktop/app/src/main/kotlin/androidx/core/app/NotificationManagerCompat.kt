package androidx.core.app

import android.content.Context
import com.omniforge.apparat.desktop.DesktopNotifications

class NotificationManagerCompat private constructor() {
    fun areNotificationsEnabled(): Boolean = DesktopNotifications.isSupported

    companion object {
        @JvmStatic
        fun from(context: Context): NotificationManagerCompat = NotificationManagerCompat()
    }
}
