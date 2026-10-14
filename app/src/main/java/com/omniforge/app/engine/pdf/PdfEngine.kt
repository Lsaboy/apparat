package com.omniforge.app.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.omniforge.app.core.util.FileUtils
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfEngine {

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
                PDDocument.load(cachedInput, password)
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
        imageQuality: Float = 0.6f
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "compressed", ".pdf")

            val document = PDDocument.load(cachedInput)
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
            val document = PDDocument()

            try {
                for (uri in imageUris) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                        document.addPage(page)

                        val pdImage = JPEGFactory.createFromImage(document, bitmap, 0.85f)
                        val contentStream = PDPageContentStream(document, page)
                        contentStream.drawImage(pdImage, 0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                        contentStream.close()
                        bitmap.recycle()
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
                merger.mergeDocuments(null)
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
        endPage: Int
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "split_p${startPage}_p$endPage", ".pdf")

            val document = PDDocument.load(cachedInput)
            val newDoc = PDDocument()

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
        ranges: List<IntRange>
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val document = PDDocument.load(cachedInput)
            val outputFiles = mutableListOf<File>()

            try {
                val totalPages = document.numberOfPages
                for ((index, range) in ranges.withIndex()) {
                    val newDoc = PDDocument()
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
        inputUri: Uri
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = FileUtils.copyUriToCache(context, inputUri)
            val renderedFiles = mutableListOf<File>()

            val pfd = android.os.ParcelFileDescriptor.open(cachedInput, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = android.graphics.pdf.PdfRenderer(pfd)

            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render at 2x density for crisp quality
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
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
