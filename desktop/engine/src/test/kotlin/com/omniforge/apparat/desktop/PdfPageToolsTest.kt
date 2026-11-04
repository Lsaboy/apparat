package com.omniforge.apparat.desktop

import android.content.Context
import com.omniforge.apparat.desktop.engine.NumberPosition
import com.omniforge.apparat.desktop.engine.PageEdit
import com.omniforge.apparat.desktop.engine.PageNumbering
import com.omniforge.apparat.desktop.engine.PdfPageSession
import com.omniforge.apparat.desktop.engine.PdfPageTools
import com.omniforge.apparat.desktop.engine.Watermark
import com.omniforge.apparat.engine.pdf.PdfPasswordException
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfPageToolsTest {
    private val ctx = Context.app

    private fun makePdf(name: String, pages: Int, password: String? = null, rotation: Int = 0, label: Boolean = true): File {
        val f = File(TestMedia.dir, name)
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4).apply { this.rotation = rotation }
                doc.addPage(page)
                if (label) PDPageContentStream(doc, page).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 24f)
                    cs.newLineAtOffset(100f, 400f); cs.showText("Sheet ${i + 1}"); cs.endText()
                }
            }
            if (password != null) doc.protect(StandardProtectionPolicy("owner", password, AccessPermission()).apply { encryptionKeyLength = 128 })
            doc.save(f)
        }
        return f
    }

    private fun pageText(doc: PDDocument, page: Int) =
        PDFTextStripper().apply { startPage = page + 1; endPage = page + 1 }.getText(doc).filterNot { it.isWhitespace() }

    @Test fun reordersRotatesAndDeletes() = runBlocking {
        val src = makePdf("pages4.pdf", 4)
        val out = PdfPageTools.apply(ctx, TestMedia.uri(src), listOf(PageEdit(3), PageEdit(0, 90), PageEdit(2, -90))).getOrThrow()
        Loader.loadPDF(out).use { doc ->
            assertEquals(3, doc.numberOfPages)
            assertTrue(pageText(doc, 0).contains("Sheet4"))
            assertTrue(pageText(doc, 1).contains("Sheet1"))
            assertTrue(pageText(doc, 2).contains("Sheet3"))
            assertEquals(0, doc.getPage(0).rotation); assertEquals(90, doc.getPage(1).rotation); assertEquals(270, doc.getPage(2).rotation)
        }
        assertTrue(PdfPageTools.apply(ctx, TestMedia.uri(src), emptyList()).isFailure)
        assertTrue(PdfPageTools.apply(ctx, TestMedia.uri(src), listOf(PageEdit(1), PageEdit(1))).isFailure)
    }

    @Test fun stampsPageNumbersAndWatermark() = runBlocking {
        val src = makePdf("pages3.pdf", 3)
        val out = PdfPageTools.apply(
            ctx, TestMedia.uri(src), (0..2).map { PageEdit(it) },
            numbering = PageNumbering(withTotal = true), watermark = Watermark("DRAFT")
        ).getOrThrow()
        Loader.loadPDF(out).use { doc ->
            for (i in 0..2) {
                val t = pageText(doc, i)
                assertTrue(t, t.contains("${i + 1}/3")); assertTrue(t, t.contains("DRAFT"))
            }
        }
    }

    /** Fraction of dark pixels in the bottom and top fifth of the page as shown. */
    private fun darkBottomTop(file: File): Pair<Double, Double> = Loader.loadPDF(file).use { doc ->
        val img = PDFRenderer(doc).renderImage(0, 0.5f)
        fun dark(y0: Int, y1: Int): Double {
            var n = 0; var d = 0
            for (y in y0 until y1) for (x in 0 until img.width) { n++; if ((img.getRGB(x, y) and 0xFF) < 128) d++ }
            return d.toDouble() / n
        }
        val strip = img.height / 5
        dark(img.height - strip, img.height) to dark(0, strip)
    }

    @Test fun numbersSitAtTheBottomOfRotatedPages() = runBlocking {
        for (rot in listOf(0, 90, 180, 270)) {
            val src = makePdf("blank_rot$rot.pdf", 1, rotation = rot, label = false)
            val out = PdfPageTools.apply(ctx, TestMedia.uri(src), listOf(PageEdit(0)), numbering = PageNumbering(fontSize = 40f)).getOrThrow()
            val (bottom, top) = darkBottomTop(out)
            assertTrue("rotation $rot: bottom=$bottom top=$top", bottom > 0.0 && top == 0.0)
        }
    }

    @Test fun topRightPosition() = runBlocking {
        val src = makePdf("blank_tr.pdf", 1, rotation = 90, label = false)
        val out = PdfPageTools.apply(ctx, TestMedia.uri(src), listOf(PageEdit(0)), numbering = PageNumbering(NumberPosition.TOP_RIGHT, fontSize = 40f)).getOrThrow()
        val (bottom, top) = darkBottomTop(out)
        assertTrue("bottom=$bottom top=$top", top > 0.0 && bottom == 0.0)
    }

    @Test fun sessionRendersThumbnailsAndHandlesPasswords() = runBlocking {
        val src = makePdf("locked_pages.pdf", 2, password = "pw")
        try {
            PdfPageSession.open(ctx, src).close(); throw AssertionError("needs a password")
        } catch (e: PdfPasswordException) {
            assertFalse(e.wrongPassword)
        }
        PdfPageSession.open(ctx, src, "pw").use { s ->
            assertEquals(2, s.pageCount)
            val img = s.thumbnail(0, 200)
            assertTrue("height ${img.height}", kotlin.math.abs(img.height - 200) <= 2)
        }
        val out = PdfPageTools.apply(ctx, TestMedia.uri(src), listOf(PageEdit(1)), password = "pw").getOrThrow()
        Loader.loadPDF(out).use { assertFalse(it.isEncrypted); assertEquals(1, it.numberOfPages) }
    }
}
