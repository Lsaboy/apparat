package com.omniforge.apparat.desktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.drew.imaging.ImageMetadataReader
import com.omniforge.apparat.desktop.platform.JpegExif
import com.omniforge.apparat.desktop.platform.SkiaImages
import com.omniforge.apparat.engine.common.UtilityEngine
import com.omniforge.apparat.engine.image.BitmapDecoder
import com.omniforge.apparat.engine.image.ImageEngine
import com.omniforge.apparat.engine.image.ImageOutputFormat
import com.omniforge.apparat.engine.pdf.PdfAccess
import com.omniforge.apparat.engine.pdf.PdfEngine
import com.omniforge.apparat.engine.pdf.PdfMergeInput
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfImageEngineDesktopTest {
    private val ctx = Context.app
    private fun uri(f: File) = TestMedia.uri(f)

    private fun makePdf(name: String, pages: Int, password: String? = null, withPhoto: Boolean = false): File {
        val f = File(TestMedia.dir, name)
        PDDocument().use { doc ->
            repeat(pages) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                if (withPhoto) {
                    // A large noisy RGB image (stored losslessly) that compression can shrink.
                    val img = java.awt.image.BufferedImage(2400, 1800, java.awt.image.BufferedImage.TYPE_INT_RGB)
                    val r = java.util.Random(1)
                    for (y in 0 until 1800) for (x in 0 until 2400) img.setRGB(x, y, ((x / 8) shl 16) or ((y / 8) shl 8) or r.nextInt(40))
                    val x = LosslessFactory.createFromImage(doc, img)
                    PDPageContentStream(doc, page).use { it.drawImage(x, 20f, 20f, 555f, 400f) }
                }
            }
            if (password != null) {
                val policy = StandardProtectionPolicy("owner-$password", password, AccessPermission())
                policy.encryptionKeyLength = 128
                doc.protect(policy)
            }
            doc.save(f)
        }
        return f
    }

    private fun pages(f: File, password: String = "") = Loader.loadPDF(f, password).use { it.numberOfPages }

    @Test fun pdfAccessPasswordAndUnlock() = runBlocking {
        val locked = makePdf("locked.pdf", 2, password = "secret")
        assertEquals(PdfAccess.NeedsPassword(false), PdfEngine.checkAccess(ctx, uri(locked)))
        assertEquals(PdfAccess.NeedsPassword(true), PdfEngine.checkAccess(ctx, uri(locked), "nope"))
        val open = PdfEngine.checkAccess(ctx, uri(locked), "secret") as PdfAccess.Open
        assertEquals(2, open.pageCount)
        val unlocked = PdfEngine.removePassword(ctx, uri(locked), "secret").getOrThrow()
        Loader.loadPDF(unlocked).use { assertFalse(it.isEncrypted); assertEquals(2, it.numberOfPages) }
        assertTrue(PdfEngine.removePassword(ctx, uri(locked), "bad").isFailure)
        val junk = File(TestMedia.dir, "junk.pdf").apply { writeText("not a pdf") }
        assertTrue(PdfEngine.checkAccess(ctx, uri(junk)) is PdfAccess.Damaged)
    }

    @Test fun splitAndMerge() = runBlocking {
        val doc = makePdf("ten.pdf", 10)
        val parts = PdfEngine.multiSplitPdf(ctx, uri(doc), listOf(1..3, 4..10)).getOrThrow()
        assertEquals(listOf(3, 7), parts.map { pages(it) })
        val locked = makePdf("locked2.pdf", 2, password = "pw")
        val merged = PdfEngine.mergePdfs(ctx, listOf(PdfMergeInput(uri(parts[0])), PdfMergeInput(uri(locked), "pw"), PdfMergeInput(uri(parts[1])))).getOrThrow()
        assertEquals(12, pages(merged))
        Loader.loadPDF(merged).use { assertFalse(it.isEncrypted) }
    }

    @Test fun compressShrinksPhotoPdf() = runBlocking {
        val big = makePdf("photo.pdf", 1, withPhoto = true)
        val out = PdfEngine.compressPdf(ctx, uri(big), 0.5f).getOrThrow()
        assertTrue("compressed ${out.length()} < ${big.length()}", out.length() < big.length() / 2)
        assertEquals(1, pages(out))
    }

    @Test fun imagesToPdfAndRender() = runBlocking {
        val out = PdfEngine.imagesToPdf(ctx, listOf(uri(TestMedia.jpeg), uri(TestMedia.png))).getOrThrow()
        Loader.loadPDF(out).use { d ->
            assertEquals(2, d.numberOfPages)
            assertTrue("landscape photo → landscape page", d.getPage(0).mediaBox.width > d.getPage(0).mediaBox.height)
        }
        PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            assertEquals(2, r.pageCount)
            val page = r.openPage(0)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            assertTrue(bmp.pixels.any { it != 0 })
        }
        val locked = makePdf("locked3.pdf", 1, password = "x")
        assertTrue(runCatching { PdfRenderer(ParcelFileDescriptor.open(locked, 0)) }.exceptionOrNull() is SecurityException)
    }

    @Test fun decodeShrinkConvert() = runBlocking {
        val bmp = BitmapDecoder.decode(ctx, uri(TestMedia.jpeg), maxDimension = 800)
        assertEquals(800, bmp.width); assertEquals(600, bmp.height)
        assertEquals(1600L * 1200, BitmapDecoder.pixelCount(ctx, uri(TestMedia.jpeg)))
        val shrunk = ImageEngine.shrinkImage(ctx, uri(TestMedia.jpeg), quality = 70, maxDimension = 1000).getOrThrow()
        assertEquals(1000, SkiaImages.size(shrunk.readBytes())!!.first)
        for (fmt in listOf(ImageOutputFormat.PNG, ImageOutputFormat.WEBP, ImageOutputFormat.JPEG)) {
            val out = ImageEngine.convertFormat(ctx, uri(TestMedia.png), fmt).getOrThrow()
            assertEquals(fmt.extension, ".${out.extension}")
            assertEquals(800 to 600, SkiaImages.size(out.readBytes()))
        }
        assertTrue(ImageEngine.convertFormat(ctx, uri(TestMedia.png), ImageOutputFormat.HEIC).isFailure)
        assertFalse(ImageOutputFormat.HEIC in ImageOutputFormat.available())
    }

    @Test fun targetKbStaysUnderLimit() = runBlocking {
        for (kb in listOf(20, 50, 100)) {
            val out = ImageEngine.shrinkToTargetKb(ctx, uri(TestMedia.jpeg), kb, stripExif = false).getOrThrow()
            assertTrue("$kb KB target → ${out.length()} bytes", out.length() <= kb * 1000L)
        }
    }

    private fun jpegWithExif(): File {
        val f = File(TestMedia.dir, "exif.jpg")
        val fields = JpegExif.ExifFields(orientation = 6, make = "TestCam", model = "X1", dateTime = "2024:05:01 10:00:00", flash = 1, focalLength = 50L to 10L)
        val withExif = JpegExif.rewrite(TestMedia.jpeg.readBytes(), fields)
        f.writeBytes(withExif)
        return f
    }

    @Test fun exifReadStripAndOrientation(): Unit = runBlocking {
        val f = jpegWithExif()
        val info = ImageEngine.getExifData(ctx, uri(f))
        assertEquals("TestCam", info.cameraMake); assertEquals("X1", info.cameraModel)
        assertTrue(info.hasAnyMetadata())
        // Orientation 6 = rotate 90° clockwise: decoded size is swapped.
        val bmp = BitmapDecoder.decode(ctx, uri(f))
        assertEquals(1200, bmp.width); assertEquals(1600, bmp.height)

        val clean = ImageEngine.stripExif(ctx, uri(f)).getOrThrow()
        val cleanInfo = ImageEngine.getExifData(ctx, uri(clean))
        assertNull(cleanInfo.cameraMake); assertFalse(cleanInfo.hasAnyMetadata())
        assertEquals("orientation kept", 6, BitmapDecoder.orientationOf(clean))
        // Lossless: image data after the header is byte-identical.
        val src = TestMedia.jpeg.readBytes(); val out = clean.readBytes()
        val tail = src.copyOfRange(src.size - 5000, src.size)
        assertTrue(out.copyOfRange(out.size - 5000, out.size).contentEquals(tail))

        // Shrink with "keep camera info" copies make/model but not orientation.
        val kept = ImageEngine.shrinkImage(ctx, uri(f), stripExif = false).getOrThrow()
        assertEquals("TestCam", ImageEngine.getExifData(ctx, uri(kept)).cameraMake)
        assertEquals(1, BitmapDecoder.orientationOf(kept))
        ImageMetadataReader.readMetadata(kept) // parses
        Unit
    }

    @Test fun scanAndPalette() = runBlocking {
        val scan = ImageEngine.enhanceDocumentScan(ctx, uri(TestMedia.png)).getOrThrow()
        val b = SkiaImages.decode(scan.readBytes())!!
        val p = b.getPixel(400, 300)
        assertEquals("monochrome", (p shr 16) and 0xFF, p and 0xFF)
        val palette = ImageEngine.extractColorPalette(ctx, uri(TestMedia.png)).getOrThrow()
        assertEquals(6, palette.size)
        assertTrue(palette.all { it.matches(Regex("#[0-9A-F]{6}")) })
    }

    @Test fun qrAndBase64() = runBlocking {
        val qr = UtilityEngine.generateQrCode("https://example.com", 256)
        assertEquals(256, qr.width)
        val file = UtilityEngine.saveQrToFile(ctx, qr)
        assertEquals(256 to 256, SkiaImages.size(file.readBytes()))
        val enc = UtilityEngine.encodeBase64("héllo wörld?>", urlSafe = false)
        assertEquals(java.util.Base64.getEncoder().encodeToString("héllo wörld?>".toByteArray()), enc)
        assertEquals("héllo wörld?>", UtilityEngine.decodeBase64(enc).getOrThrow())
        assertEquals("héllo wörld?>", UtilityEngine.decodeBase64(UtilityEngine.encodeBase64("héllo wörld?>", urlSafe = true), urlSafe = true).getOrThrow())
        assertTrue(UtilityEngine.decodeBase64("!!!").isFailure)
        val sums = UtilityEngine.calculateChecksums(ctx, uri(TestMedia.png)).getOrThrow()
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(TestMedia.png.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, sums["SHA-256"])
    }
}
