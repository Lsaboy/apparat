package com.omniforge.apparat.desktop

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlin.time.Duration.Companion.minutes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omniforge.apparat.core.settings.AppSettings
import com.omniforge.apparat.desktop.platform.DesktopPaths
import com.omniforge.apparat.desktop.platform.Ffmpeg
import com.omniforge.apparat.desktop.platform.MediaProbe
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Drives the real shared screens on desktop: a file comes in (as when dropped on the window), the
 * tool's button is clicked, the job runs through ToolJobs and the desktop engine, and the result
 * is saved with the result card's Save button.
 */
@OptIn(ExperimentalTestApi::class)
class EndToEndUiTest {

    companion object {
        lateinit var work: File

        @BeforeClass
        @JvmStatic
        fun setUp() {
            work = File(System.getProperty("java.io.tmpdir"), "apparat-e2e").apply { deleteRecursively(); mkdirs() }
            AppSettings(Context.app).hasSeenAppearancePrompt = true
        }
    }

    private object TestLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    /** Runs a UI test; on failure saves what the window showed to build/screenshots/fail-<name>.png. */
    private fun e2e(name: String, block: ComposeUiTest.() -> Unit) = runComposeUiTest(testTimeout = 5.minutes) {
        try {
            block()
        } catch (t: Throwable) {
            runCatching {
                val bmp = onRoot().captureToImage().asSkiaBitmap()
                val data = org.jetbrains.skia.Image.makeFromBitmap(bmp).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
                File("build/screenshots").apply { mkdirs() }.resolve("fail-$name.png").writeBytes(data.bytes)
            }
            throw t
        }
    }

    private fun ComposeUiTest.open(route: String, file: File? = null, mime: String? = null): AppController {
        val controller = AppController()
        if (file != null) controller.payload = SharedPayload(uri = Uri.fromFile(file), mimeType = mime)
        controller.navigate(route)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides TestLifecycle) {
                ApparatDesktopApp(controller, AppSettings(Context.app))
            }
        }
        return controller
    }

    private fun ComposeUiTest.button(label: String): SemanticsNodeInteraction =
        onNode(hasText(label) and hasClickAction())

    private fun ComposeUiTest.waitForText(text: String, timeoutMs: Long = 120_000) {
        waitUntil(timeoutMillis = timeoutMs) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.clickWhenEnabled(label: String, timeoutMs: Long = 60_000) {
        waitUntil(timeoutMillis = timeoutMs) { onAllNodes(hasText(label) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        button(label).performScrollTo().performClick()
    }

    private fun savedFiles(): List<File> = File(DesktopPaths.downloadsDir, "Apparat").listFiles()?.toList().orEmpty()

    private fun ComposeUiTest.saveAndCheck(expectedName: String) {
        val before = savedFiles().toSet()
        clickWhenEnabled("Save to Downloads/Apparat")
        waitUntil(timeoutMillis = 30_000) { (savedFiles().toSet() - before).isNotEmpty() }
        val saved = (savedFiles().toSet() - before).single()
        assertEquals(expectedName, saved.name)
        assertTrue(saved.length() > 0)
    }

    @Test
    fun compressPdfAndSave() = e2e("compressPdfAndSave") {
        val pdf = File(work, "scan.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val img = java.awt.image.BufferedImage(2000, 1500, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val r = java.util.Random(3)
            for (y in 0 until 1500) for (x in 0 until 2000) img.setRGB(x, y, ((x / 7) shl 16) or ((y / 7) shl 8) or r.nextInt(50))
            PDPageContentStream(doc, page).use { it.drawImage(LosslessFactory.createFromImage(doc, img), 20f, 20f, 555f, 400f) }
            doc.save(pdf)
        }
        open("pdf_shrink", pdf, "application/pdf")
        waitForText("scan.pdf")
        clickWhenEnabled("Compress PDF")
        waitForText("PDF compressed successfully")
        saveAndCheck("scan_compressed.pdf")
        val out = File(File(DesktopPaths.downloadsDir, "Apparat"), "scan_compressed.pdf")
        assertTrue("smaller: ${out.length()} vs ${pdf.length()}", out.length() < pdf.length())
    }

    @Test
    fun shrinkVideoAndSave() = e2e("shrinkVideoAndSave") {
        val video = File(work, "holiday.mp4")
        runBlocking {
            Ffmpeg.run(
                listOf(
                    "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=25:duration=3",
                    "-f", "lavfi", "-i", "sine=duration=3", "-c:v", "libx264", "-b:v", "6M",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", video.absolutePath
                )
            )
        }
        open("video_shrink", video, "video/mp4")
        waitForText("holiday.mp4")
        clickWhenEnabled("WhatsApp / Messaging (480p)")
        clickWhenEnabled("Shrink Video")
        waitForText("Video compressed successfully", 180_000)
        saveAndCheck("holiday_shrunk.mp4")
        val probe = MediaProbe.of(File(File(DesktopPaths.downloadsDir, "Apparat"), "holiday_shrunk.mp4"))!!
        assertEquals(480, minOf(probe.width, probe.height))
    }

    @Test
    fun shrinkPhotoUnderLimit() = e2e("shrinkPhotoUnderLimit") {
        val photo = File(work, "passport.png")
        runBlocking { Ffmpeg.run(listOf("-f", "lavfi", "-i", "testsrc2=size=1600x1200", "-frames:v", "1", photo.absolutePath)) }
        open("image_shrink", photo, "image/png")
        waitForText("passport.png")
        clickWhenEnabled("Shrink Photo")
        waitUntil(timeoutMillis = 60_000) {
            onAllNodes(hasText("shrunk", substring = true)).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(hasText("Shrunk to", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun formatJson() = e2e("formatJson") {
        open("json_tool")
        onNode(hasSetTextAction()).performTextInput("""{"tool":"apparat","n":[1,2]}""")
        clickWhenEnabled("Prettify")
        waitForText("\"tool\": \"apparat\"")
    }

    @Test
    fun droppedPdfsGoToMerge() = e2e("droppedPdfsGoToMerge") {
        val a = File(work, "a.pdf"); val b = File(work, "b.pdf")
        for (f in listOf(a, b)) PDDocument().use { it.addPage(PDPage()); it.save(f) }
        val controller = AppController()
        controller.handleFiles(listOf(a, b))
        assertEquals("pdf_merge", controller.current)
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides TestLifecycle) { ApparatDesktopApp(controller, AppSettings(Context.app)) }
        }
        waitForText("a.pdf")
        waitForText("b.pdf")
    }
}
