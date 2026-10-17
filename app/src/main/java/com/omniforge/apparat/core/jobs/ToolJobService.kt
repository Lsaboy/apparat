package com.omniforge.apparat.core.jobs

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.omniforge.apparat.MainActivity
import com.omniforge.apparat.R
import com.omniforge.apparat.core.model.ProcessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the app alive while [ToolJobs] has work running, so the OS
 * doesn't kill a long video encode when the user switches apps. Shows progress; stops itself
 * when nothing is running.
 */
@SuppressLint("MissingPermission") // notify() is a no-op without POST_NOTIFICATIONS; wrapped in runCatching
class ToolJobService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        // Promote to foreground right away so long jobs survive backgrounding. The system can refuse
        // a type (e.g. its daily time limit is used up, or newer Android rules), so try each one and
        // fall back to running without the service: jobs still finish while the app is open.
        if (!startForegroundWithFallback() || ToolJobs.running.isEmpty()) stopSelfNow()
        return START_NOT_STICKY
    }

    private fun startForegroundWithFallback(): Boolean {
        val types = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                add(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            }
            add(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        }
        for (type in types) {
            try {
                ServiceCompat.startForeground(this, PROGRESS_ID, progressNotification(), type)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service type $type refused", e)
            }
        }
        return false
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Refresh at most twice a second; the system rate-limits notification updates anyway.
            snapshotFlow { ToolJobs.running.toMap() to ToolJobs.running.keys.map { ToolJobs.processState(it) } }
                .collect {
                    if (ToolJobs.running.isEmpty()) {
                        stopSelfNow()
                    } else {
                        runCatching { NotificationManagerCompat.from(this@ToolJobService).notify(PROGRESS_ID, progressNotification()) }
                        delay(500)
                    }
                }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun stopSelfNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelfNow()
    }

    private fun progressNotification() = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .apply {
            val entries = ToolJobs.running.entries.toList()
            val first = entries.firstOrNull()
            val state = first?.let { ToolJobs.processState(it.key) } as? ProcessState.Processing
            setContentTitle(
                when {
                    entries.size > 1 -> "Working on ${entries.size} tasks"
                    first != null -> "${first.value}: working…"
                    else -> "Working…"
                }
            )
            setContentText(if (entries.size > 1) entries.joinToString { it.value } else state?.message ?: "Processing…")
            if (state != null && state.progress > 0f && entries.size == 1) {
                setProgress(100, (state.progress * 100).toInt(), false)
            } else {
                setProgress(0, 0, true)
            }
            first?.let { setContentIntent(openToolIntent(this@ToolJobService, it.key)) }
        }
        .build()

    companion object {
        private const val CHANNEL_PROGRESS = "jobs_progress"
        private const val CHANNEL_DONE = "jobs_done"
        private const val PROGRESS_ID = 1001
        private const val TAG = "ToolJobService"

        private fun ensureChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "Running tasks", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Finished tasks", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        /** Opens the app on the tool's screen (via the apparat:// shortcut route). */
        private fun openToolIntent(context: Context, route: String): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("apparat://$route"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context, route.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun doneId(route: String) = 2000 + (route.hashCode() and 0x3FF)

        /** Posted when a job ends while its screen isn't visible. */
        fun notifyFinished(context: Context, route: String, title: String, state: ProcessState?) {
            ensureChannels(context)
            val text = when (state) {
                is ProcessState.Error -> "Failed: ${state.error}"
                is ProcessState.Success -> state.message + " Tap to save or share."
                else -> "Finished. Tap to see the result."
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(if (state is ProcessState.Error) "$title failed" else "$title finished")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(openToolIntent(context, route))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(doneId(route), notification) }
        }

        fun cancelFinishedNotification(context: Context, route: String) {
            NotificationManagerCompat.from(context).cancel(doneId(route))
        }
    }
}
