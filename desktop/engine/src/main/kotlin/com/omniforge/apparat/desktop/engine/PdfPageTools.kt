package com.omniforge.apparat.desktop.engine

import android.content.Context
import android.net.Uri
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.engine.pdf.PdfPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.util.Matrix
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

/** One page of the output: page [source] (0-based) of the input, turned [extraRotation] degrees clockwise. */
data class PageEdit(val source: Int, val extraRotation: Int = 0)

enum class NumberPosition(val label: String) { BOTTOM_CENTER("Bottom centre"), BOTTOM_RIGHT("Bottom right"), TOP_RIGHT("Top right") }

/** Page numbers: "[first]", or "[first] / total" when [withTotal]. */
data class PageNumbering(
    val position: NumberPosition = NumberPosition.BOTTOM_CENTER,
    val first: Int = 1,
    val withTotal: Boolean = false,
    val fontSize: Float = 10f
)

/** Diagonal text across every page. [opacity] 0..1. */
data class Watermark(val text: String, val opacity: Float = 0.2f, val fontSize: Float = 0f, val diagonal: Boolean = true)

/**
 * An open PDF for the page tools: page count and thumbnails while the user edits. Rendering is
 * serialized (PDFBox isn't thread-safe). Close it when the screen goes away.
 */
class PdfPageSession private constructor(private val doc: PDDocument, val file: File) : Closeable {
    private val renderer = PDFRenderer(doc)
    private val lock = Mutex()
    val pageCount: Int get() = doc.numberOfPages

    /** Page [index] as an image whose longer side is about [maxPx] (its own rotation applied). */
    suspend fun thumbnail(index: Int, maxPx: Int = 240): BufferedImage = lock.withLock {
        withContext(Dispatchers.IO) {
            val page = doc.getPage(index)
            val box = page.cropBox
            val longest = max(box.width, box.height).coerceAtLeast(1f)
            renderer.renderImage(index, (maxPx / longest).coerceIn(0.05f, 4f), ImageType.RGB)
        }
    }

    override fun close() = doc.close()

    companion object {
        /** Opens [file]; throws [PdfPasswordException] when it needs (another) password. */
        suspend fun open(context: Context, file: File, password: String = ""): PdfPageSession = withContext(Dispatchers.IO) {
            PdfPageSession(PdfPageTools.load(context, file, password), file)
        }
    }
}

/** Desktop-only PDF page tools: reorder, rotate, delete, page numbers, watermark, in one pass. */
object PdfPageTools {

    internal fun load(context: Context, file: File, password: String): PDDocument = try {
        Loader.loadPDF(file, password, MemoryUsageSetting.setupTempFileOnly().setTempDir(File(context.cacheDir, "pdfbox").apply { mkdirs() }).streamCache)
    } catch (e: InvalidPasswordException) {
        throw PdfPasswordException(wrongPassword = password.isNotEmpty())
    }

    /**
     * Writes a new PDF with the pages of [uri] listed in [pages] (in that order; pages left out are
     * deleted), each turned by its extra rotation, then stamps [numbering] and [watermark]. A
     * password-protected input is saved without the password.
     */
    suspend fun apply(
        context: Context,
        uri: Uri,
        pages: List<PageEdit>,
        numbering: PageNumbering? = null,
        watermark: Watermark? = null,
        password: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (pages.isEmpty()) throw IllegalArgumentException("Keep at least one page.")
            if (pages.map { it.source }.toSet().size != pages.size) throw IllegalArgumentException("A page can only appear once.")
            val output = FileUtils.createTempOutputFile(context, "pages", ".pdf")
            try {
                load(context, uri.file, password).use { doc ->
                    val all = (0 until doc.numberOfPages).map { doc.getPage(it) }
                    pages.forEach { require(it.source in all.indices) { "Page ${it.source + 1} doesn't exist." } }
                    // Rebuild the page tree in the new order; the page objects keep their content and resources.
                    for (i in all.indices.reversed()) doc.removePage(i)
                    for (edit in pages) {
                        val page = all[edit.source]
                        page.rotation = (((page.rotation + edit.extraRotation) % 360) + 360) % 360
                        doc.addPage(page)
                    }
                    if (numbering != null) stampNumbers(doc, numbering)
                    if (watermark != null && watermark.text.isNotBlank()) stampWatermark(doc, watermark)
                    if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
                    doc.save(output)
                }
                Result.success(output)
            } catch (t: Throwable) {
                output.delete()
                throw t
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ------------------------------------------------------------ drawing in the page as shown

    /** Width and height of [page] as displayed (after its /Rotate). */
    internal fun visualSize(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox
        return if (page.rotation % 180 == 0) box.width to box.height else box.height to box.width
    }

    /**
     * Maps coordinates in the page as displayed (origin bottom-left, y up) to PDF user space, so
     * stamps sit upright and in the right corner whatever the page's /Rotate.
     */
    internal fun visualToUser(page: PDPage): Matrix {
        val b = page.cropBox
        val llx = b.lowerLeftX; val lly = b.lowerLeftY; val w = b.width; val h = b.height
        return when (((page.rotation % 360) + 360) % 360) {
            90 -> Matrix(0f, 1f, -1f, 0f, llx + w, lly)
            180 -> Matrix(-1f, 0f, 0f, -1f, llx + w, lly + h)
            270 -> Matrix(0f, -1f, 1f, 0f, llx, lly + h)
            else -> Matrix(1f, 0f, 0f, 1f, llx, lly)
        }
    }

    private val unicodeFontCandidates = listOf(
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/TTF/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/google-noto/NotoSans-Regular.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        (System.getenv("WINDIR") ?: "C:\\Windows") + "\\Fonts\\arial.ttf",
        (System.getenv("WINDIR") ?: "C:\\Windows") + "\\Fonts\\segoeui.ttf"
    )

    /** Helvetica when it can draw [text]; otherwise an embedded system font that can. */
    internal fun fontFor(doc: PDDocument, text: String, bold: Boolean): PDFont {
        val helvetica = PDType1Font(if (bold) Standard14Fonts.FontName.HELVETICA_BOLD else Standard14Fonts.FontName.HELVETICA)
        if (runCatching { helvetica.encode(text) }.isSuccess) return helvetica
        for (path in unicodeFontCandidates) {
            val f = File(path)
            if (!f.isFile) continue
            val font = runCatching { PDType0Font.load(doc, f) }.getOrNull() ?: continue
            if (runCatching { font.encode(text) }.isSuccess) return font
        }
        throw IllegalArgumentException("No installed font can draw \"$text\". Use Latin letters and digits.")
    }

    private fun width(font: PDFont, text: String, size: Float) = font.getStringWidth(text) / 1000f * size

    private fun stampNumbers(doc: PDDocument, n: PageNumbering) {
        val total = doc.numberOfPages
        val lastLabel = if (n.withTotal) "${n.first + total - 1} / ${n.first + total - 1}" else "${n.first + total - 1}"
        val font = fontFor(doc, lastLabel + "0123456789 /", bold = false)
        doc.pages.forEachIndexed { i, page ->
            val label = if (n.withTotal) "${n.first + i} / ${n.first + total - 1}" else "${n.first + i}"
            val (vw, vh) = visualSize(page)
            val size = n.fontSize.coerceIn(4f, 72f)
            val margin = max(18f, size * 2f)
            val tw = width(font, label, size)
            val (x, y) = when (n.position) {
                NumberPosition.BOTTOM_CENTER -> (vw - tw) / 2 to margin
                NumberPosition.BOTTOM_RIGHT -> vw - margin - tw to margin
                NumberPosition.TOP_RIGHT -> vw - margin - tw to vh - margin - size
            }
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.transform(visualToUser(page))
                cs.beginText()
                cs.setFont(font, size)
                cs.setNonStrokingColor(0.15f, 0.15f, 0.15f)
                cs.newLineAtOffset(x, y)
                cs.showText(label)
                cs.endText()
            }
        }
    }

    private fun stampWatermark(doc: PDDocument, wm: Watermark) {
        val text = wm.text.trim()
        val font = fontFor(doc, text, bold = true)
        val gs = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = wm.opacity.coerceIn(0.03f, 1f)
            strokingAlphaConstant = wm.opacity.coerceIn(0.03f, 1f)
        }
        for (page in doc.pages) {
            val (vw, vh) = visualSize(page)
            val span = if (wm.diagonal) sqrt(vw * vw + vh * vh) else vw
            val unit = width(font, text, 1f).coerceAtLeast(0.01f)
            val size = if (wm.fontSize > 0f) wm.fontSize else (span * 0.7f / unit).coerceIn(12f, 200f)
            val tw = unit * size
            val angle = if (wm.diagonal) Math.atan2(vh.toDouble(), vw.toDouble()) else 0.0
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.transform(visualToUser(page))
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f)
                cs.beginText()
                cs.setFont(font, size)
                // Centre of the page, rotated along the diagonal; the text's own centre sits on it.
                val m = Matrix.getRotateInstance(angle, vw / 2, vh / 2)
                m.translate(-tw / 2, -size * 0.35f)
                cs.setTextMatrix(m)
                cs.showText(text)
                cs.endText()
            }
        }
    }
}
