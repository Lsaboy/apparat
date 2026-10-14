package com.omniforge.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class OmniForgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox for Android
        PDFBoxResourceLoader.init(applicationContext)
    }
}
