package com.omniforge.apparat

import android.app.Application
import com.omniforge.apparat.core.util.FileUtils
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ApparatApp : Application() {
    override fun onCreate() {
        val appStartTime = System.currentTimeMillis()
        super.onCreate()
        // Initialize PDFBox for Android
        PDFBoxResourceLoader.init(applicationContext)

        // Clean stale temp/scratch/output files in background
        CoroutineScope(Dispatchers.IO).launch {
            FileUtils.cleanStaleCache(applicationContext, appStartTime = appStartTime)
        }

        // Initialize dynamic launcher shortcuts based on user preferences
        com.omniforge.apparat.core.settings.AppSettings(applicationContext).updateLauncherShortcuts(applicationContext)
    }
}
