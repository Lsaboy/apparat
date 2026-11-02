package com.omniforge.apparat.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.omniforge.apparat.core.settings.AppSettings
import java.awt.Image
import javax.imageio.ImageIO

/** Window, taskbar and tray icons (rendered from the Android launcher vectors). */
object AppIcons {
    private fun load(name: String) =
        AppIcons::class.java.getResourceAsStream("/icons/$name")!!.use { ImageIO.read(it) }

    fun painter(variant: AppSettings.AppIconVariant): Painter =
        BitmapPainter(
            load(if (variant == AppSettings.AppIconVariant.CHARCOAL) "apparat-charcoal.png" else "apparat.png").toComposeImageBitmap()
        )

    fun trayImage(): Image = load("apparat-32.png")
}
