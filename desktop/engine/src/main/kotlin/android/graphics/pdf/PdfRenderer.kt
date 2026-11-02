package android.graphics.pdf

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.RenderDestination
import java.io.Closeable

/**
 * Desktop stand-in for android.graphics.pdf.PdfRenderer, rendering with PDFBox. Like Android, a
 * PDF that needs a password to open throws [SecurityException].
 */
class PdfRenderer(input: ParcelFileDescriptor) : Closeable {
    private val document: PDDocument = try {
        Loader.loadPDF(input.file)
    } catch (e: InvalidPasswordException) {
        throw SecurityException("password required", e)
    }
    internal val renderer = org.apache.pdfbox.rendering.PDFRenderer(document)
    internal val doc: PDDocument get() = document

    val pageCount: Int get() = document.numberOfPages

    fun openPage(index: Int): Page = Page(this, index)

    override fun close() = document.close()

    class Page internal constructor(private val owner: PdfRenderer, val index: Int) : Closeable {
        private val box = owner.doc.getPage(index).let { p ->
            val b = p.cropBox ?: p.mediaBox
            if (p.rotation % 180 != 0) b.height to b.width else b.width to b.height
        }

        /** Page size in points (1/72 inch), as on Android. */
        val width: Int get() = box.first.toInt().coerceAtLeast(1)
        val height: Int get() = box.second.toInt().coerceAtLeast(1)

        /** Renders the page scaled to fill [destination]; [destClip]/[transform] are not supported. */
        fun render(destination: Bitmap, destClip: Any?, transform: Any?, renderMode: Int) {
            val scale = destination.width.toFloat() / box.first
            val image = owner.renderer.renderImage(
                index, scale, ImageType.ARGB,
                if (renderMode == Page.RENDER_MODE_FOR_PRINT) RenderDestination.PRINT else RenderDestination.VIEW
            )
            val w = minOf(image.width, destination.width)
            val h = minOf(image.height, destination.height)
            val row = IntArray(w)
            val canvas = android.graphics.Canvas(destination)
            val tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            for (y in 0 until h) {
                image.getRGB(0, y, w, 1, row, 0, w)
                tmp.setPixels(row, 0, w, 0, y, w, 1)
            }
            canvas.drawBitmap(tmp, 0f, 0f, null)
        }

        override fun close() {}

        companion object {
            const val RENDER_MODE_FOR_DISPLAY = 1
            const val RENDER_MODE_FOR_PRINT = 2
        }
    }
}
