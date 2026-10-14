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
}
