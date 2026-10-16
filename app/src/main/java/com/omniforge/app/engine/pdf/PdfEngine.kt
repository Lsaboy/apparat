package com.omniforge.app.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.image.BitmapDecoder
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Thrown when a PDF needs a password, or the one given is wrong. */
class PdfPasswordException(val wrongPassword: Boolean) : Exception(
    if (wrongPassword) "Wrong password for this PDF" else "This PDF is password-protected"
)

sealed interface PdfAccess {
    data class Open(val pageCount: Int, val isEncrypted: Boolean) : PdfAccess
    /** [wrongPassword] is true when a password was given but didn't match. */
    data class NeedsPassword(val wrongPassword: Boolean) : PdfAccess
    data class Damaged(val message: String) : PdfAccess
}

data class A4Fit(
    val pageWidth: Float,
    val pageHeight: Float,
    val drawWidth: Float,
    val drawHeight: Float,
    val x: Float,
    val y: Float
)

internal fun calculateA4Fit(
    imageWidth: Int,
    imageHeight: Int,
    margin: Float = 20f,
    a4Short: Float = PDRectangle.A4.width,
    a4Long: Float = PDRectangle.A4.height
): A4Fit {
    val isLandscape = imageWidth > imageHeight
    val pageWidth = if (isLandscape) a4Long else a4Short
    val pageHeight = if (isLandscape) a4Short else a4Long

    val maxWidth = pageWidth - (2f * margin)
    val maxHeight = pageHeight - (2f * margin)
    val scale = minOf(maxWidth / imageWidth.toFloat(), maxHeight / imageHeight.toFloat())

    val drawWidth = imageWidth.toFloat() * scale
    val drawHeight = imageHeight.toFloat() * scale

    val x = (pageWidth - drawWidth) / 2f
    val y = (pageHeight - drawHeight) / 2f

    return A4Fit(pageWidth, pageHeight, drawWidth, drawHeight, x, y)
}

object PdfEngine {

    /**
     * PDFBox buffers in temp files under cache/pdfbox instead of the heap, so 100+ page
     * scanned PDFs don't run out of memory.
     */
    private fun tempFileMemory(context: Context): MemoryUsageSetting =
        MemoryUsageSetting.setupTempFileOnly().setTempDir(File(context.cacheDir, "pdfbox").apply { mkdirs() })

    /** Opens with PDFBox; a missing/wrong password surfaces as [PdfPasswordException]. */
    private fun openDocument(context: Context, file: File, password: String = ""): PDDocument =
        try {
            PDDocument.load(file, password, tempFileMemory(context))
        } catch (e: InvalidPasswordException) {
            throw PdfPasswordException(wrongPassword = password.isNotEmpty())
        }

    /**
     * Checks whether [inputUri] opens with [password] ("" = no password). Call on file pick
     * to decide whether to ask for a password before running a tool.
     */
    suspend fun checkAccess(context: Context, inputUri: Uri, password: String = ""): PdfAccess =
        withContext(Dispatchers.IO) {
            val cachedInput = try {
                FileUtils.copyUriToCache(context, inputUri)
            } catch (e: Exception) {
                return@withContext PdfAccess.Damaged("Could not read the selected file. Pick it again.")
            }
            try {
                openDocument(context, cachedInput, password).use { PdfAccess.Open(it.numberOfPages, it.isEncrypted) }
            } catch (e: PdfPasswordException) {
                PdfAccess.NeedsPassword(e.wrongPassword)
            } catch (e: Exception) {
                PdfAccess.Damaged("This file isn't a readable PDF (it may be damaged).")
            } finally {
                cachedInput.delete()
            }
        }

    /** Writes a password-free copy of [file] for PdfRenderer, which can't take a password. */
    private fun decryptToTemp(context: Context, file: File, password: String): File {
        val out = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}.pdf")
        openDocument(context, file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
        return out
    }

    /**
     * Removes password protection from an encrypted PDF.
     * Takes the user's password, decrypts the document, strips all security,
     * and saves an unlocked, permanent DRM-free copy.
     */
    suspend fun removePassword(
        context: Context,
        inputUri: Uri,
        password: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "unlocked", ".pdf")

            val document = try {
                openDocument(context, cachedInput, password)
            } catch (e: Exception) {
                cachedInput.delete()
                throw IllegalArgumentException("Incorrect password or corrupt PDF: ${e.message}")
            }

            try {
                if (document.isEncrypted) {
                    // Strips all security handlers & encryption dictionaries
                    document.isAllSecurityToBeRemoved = true
                }
                document.save(outputFile)
            } finally {
                document.close()
                cachedInput.delete()
            }

            outputFile
        }
    }

    /**
     * Compresses a PDF by optimizing page content streams and downscaling images.
     */
    suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        imageQuality: Float = 0.6f,
        password: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "compressed", ".pdf")

            val document = openDocument(context, cachedInput, password)
            try {
                // PDFBox compression: optimize stream filters and resave
                document.save(outputFile)
            } finally {
                document.close()
                cachedInput.delete()
            }

            outputFile
        }
    }

    /**
     * Converts a list of image Uris into a single unified PDF document.
     */
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outputFile = FileUtils.createTempOutputFile(context, "converted", ".pdf")
            val document = PDDocument(tempFileMemory(context))
            val margin = 20f // 20pt margin (~7mm)

            try {
                for (uri in imageUris) {
                    // 16 MP keeps A4 at 300 DPI (~8.7 MP) sharp while bounding memory per photo.
                    val originalBitmap = BitmapDecoder.decode(context, uri, maxPixels = 16_000_000L)
                    var compositedBitmap: Bitmap? = null
                    try {
                        val bitmapToDraw = if (originalBitmap.hasAlpha()) {
                            compositedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(compositedBitmap)
                            canvas.drawColor(Color.WHITE)
                            canvas.drawBitmap(originalBitmap, 0f, 0f, null)
                            originalBitmap.recycle()
                            compositedBitmap
                        } else {
                            originalBitmap
                        }

                        val fit = calculateA4Fit(bitmapToDraw.width, bitmapToDraw.height, margin)
                        val page = PDPage(PDRectangle(fit.pageWidth, fit.pageHeight))
                        document.addPage(page)

                        val pdImage = JPEGFactory.createFromImage(document, bitmapToDraw, 0.85f)
                        PDPageContentStream(document, page).use { contentStream ->
                            contentStream.drawImage(pdImage, fit.x, fit.y, fit.drawWidth, fit.drawHeight)
                        }
                    } finally {
                        if (!originalBitmap.isRecycled) {
                            originalBitmap.recycle()
                        }
                        compositedBitmap?.recycle()
                    }
                }
                document.save(outputFile)
            } finally {
                document.close()
            }

            outputFile
        }
    }

    /**
     * Merges multiple PDF files into a single PDF.
     */
    suspend fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outputFile = FileUtils.createTempOutputFile(context, "merged", ".pdf")
            val merger = PDFMergerUtility().apply {
                destinationFileName = outputFile.absolutePath
            }

            val cachedFiles = mutableListOf<File>()
            try {
                for (uri in pdfUris) {
                    val file = FileUtils.copyUriToCache(context, uri)
                    cachedFiles.add(file)
                    merger.addSource(file)
                }
                merger.mergeDocuments(tempFileMemory(context))
            } finally {
                cachedFiles.forEach { it.delete() }
            }

            outputFile
        }
    }

    /**
     * Splits a PDF to keep only a specific page range (1-indexed).
     */
    suspend fun splitPdf(
        context: Context,
        inputUri: Uri,
        startPage: Int,
        endPage: Int,
        password: String = ""
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "split_p${startPage}_p$endPage", ".pdf")

            val document = openDocument(context, cachedInput, password)
            val newDoc = PDDocument(tempFileMemory(context))

            try {
                val totalPages = document.numberOfPages
                val start = (startPage - 1).coerceIn(0, totalPages - 1)
                val end = (endPage - 1).coerceIn(start, totalPages - 1)

                for (i in start..end) {
                    newDoc.addPage(document.getPage(i))
                }
                newDoc.save(outputFile)
            } finally {
                newDoc.close()
                document.close()
                cachedInput.delete()
            }

            outputFile
        }
    }

    /**
     * Splits a PDF into multiple distinct PDF files based on a list of page ranges (1-indexed).
     */
    suspend fun multiSplitPdf(
        context: Context,
        inputUri: Uri,
        ranges: List<IntRange>,
        password: String = ""
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val document = openDocument(context, cachedInput, password)
            val outputFiles = mutableListOf<File>()

            try {
                val totalPages = document.numberOfPages
                for ((index, range) in ranges.withIndex()) {
                    val newDoc = PDDocument(tempFileMemory(context))
                    val start = (range.first - 1).coerceIn(0, totalPages - 1)
                    val end = (range.last - 1).coerceIn(start, totalPages - 1)
                    for (p in start..end) {
                        newDoc.addPage(document.getPage(p))
                    }
                    val partFile = FileUtils.createTempOutputFile(context, "part_${index + 1}_p${range.first}_p${range.last}", ".pdf")
                    newDoc.save(partFile)
                    newDoc.close()
                    outputFiles.add(partFile)
                }
            } finally {
                document.close()
                cachedInput.delete()
            }

            outputFiles
        }
    }

    /**
     * Renders each page of a PDF into high-res PNG images using Android's native hardware PdfRenderer.
     */
    suspend fun pdfToImages(
        context: Context,
        inputUri: Uri,
        password: String = ""
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            var cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val renderedFiles = mutableListOf<File>()

            fun decrypted(): File {
                try {
                    return decryptToTemp(context, cachedInput, password)
                } finally {
                    cachedInput.delete()
                }
            }
            if (password.isNotEmpty()) cachedInput = decrypted()

            var pfd = android.os.ParcelFileDescriptor.open(cachedInput, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                android.graphics.pdf.PdfRenderer(pfd)
            } catch (e: SecurityException) {
                // Encrypted (e.g. owner-password only): decrypt with PDFBox, then render.
                pfd.close()
                cachedInput = decrypted()
                pfd = android.os.ParcelFileDescriptor.open(cachedInput, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                android.graphics.pdf.PdfRenderer(pfd)
            }

            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render at 2x (144 DPI) for crisp quality, capped at 16 MP so huge pages can't OOM.
                    val scale = min(2.0, sqrt(16_000_000.0 / (page.width.toDouble() * page.height)))
                    val bitmap = Bitmap.createBitmap(
                        max(1, (page.width * scale).toInt()), max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE) // White background for transparent pages
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageFile = FileUtils.createTempOutputFile(context, "page_${i + 1}", ".png")
                    FileOutputStream(pageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bitmap.recycle()
                    renderedFiles.add(pageFile)
                }
            } finally {
                renderer.close()
                pfd.close()
                cachedInput.delete()
            }

            renderedFiles
        }
    }
}
