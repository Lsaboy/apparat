package com.omniforge.apparat.desktop

import android.content.Context
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.core.settings.ThemeStyle
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders every screen offscreen (no window needed) to PNGs, for checking the desktop UI on CI:
 *   ./gradlew -p desktop :app:screenshots   →   desktop/app/build/screenshots/
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val settings = AppSettings(Context.app)

    fun shoot(name: String, route: String, theme: ThemeStyle, w: Int = 560, h: Int = 900) {
        settings.themeStyle = theme
        val controller = AppController()
        if (route != "home") controller.navigate(route)
        val scene = ImageComposeScene(w, h, Density(1f)) { ApparatDesktopApp(controller, settings) }
        try {
            var t = 0L
            repeat(40) { scene.render(t); t += 50_000_000L } // let animations and effects settle
            val image = scene.render(t)
            val data = image.encodeToData(EncodedImageFormat.PNG) ?: error("encode failed")
            File(out, "$name.png").writeBytes(data.bytes)
            println("wrote $name.png")
        } catch (e: Throwable) {
            System.err.println("FAILED $name: $e")
            e.printStackTrace()
        } finally {
            scene.close()
        }
    }

    settings.hasSeenAppearancePrompt = false
    shoot("00-first-boot", "home", ThemeStyle.LIGHT)
    settings.hasSeenAppearancePrompt = true
    shoot("01-home-light", "home", ThemeStyle.LIGHT)
    shoot("02-home-dark", "home", ThemeStyle.DARK)
    shoot("03-home-wide", "home", ThemeStyle.LIGHT, w = 1280, h = 800)
    var i = 10
    for (route in TOOL_ROUTES.sorted()) {
        shoot("${i++}-$route", route, if (i % 2 == 0) ThemeStyle.LIGHT else ThemeStyle.DARK)
    }
    kotlin.system.exitProcess(0)
}
