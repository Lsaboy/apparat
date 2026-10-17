package com.omniforge.app.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.omniforge.app.core.util.FileUtils
import com.omniforge.app.engine.image.BitmapDecoder
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** One PDF to merge: [password] is "" for unprotected files. */
data class PdfMergeInput(val uri: Uri, val password: String = "")

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

/** How one embedded PDF image gets re-encoded: decode with [subsampling], then scale to target. */
data class ImageRecompressPlan(val targetWidth: Int, val targetHeight: Int, val subsampling: Int)

internal const val TARGET_DPI = 150f
private const val MAX_DECODE_PIXELS = 16_000_000L
private const val MIN_RECOMPRESS_PIXELS = 100L * 100L

/**
 * Decides whether and how to re-encode an embedded image, or returns null to keep it as is:
 * - 1-bit images (CCITT/JBIG2 scans) are already tiny; JPEG would be bigger and blurrier,
 * - stencil masks and images with a soft/colour-key mask would lose their transparency,
 * - tiny images (icons, logos) aren't worth it.
 * Otherwise the image is fitted to the page at [TARGET_DPI] (never upscaled), and [subsampling]
 * keeps the decoded bitmap at or above the target but under [MAX_DECODE_PIXELS].
 */
internal fun planImageRecompression(
    width: Int,
    height: Int,
    bitsPerComponent: Int,
    isStencil: Boolean,
    hasMask: Boolean,
    pageWidthPt: Float,
    pageHeightPt: Float,
    targetDpi: Float = TARGET_DPI
): ImageRecompressPlan? {
    if (width <= 0 || height <= 0) return null
    if (isStencil || hasMask || bitsPerComponent == 1) return null
    if (width.toLong() * height < MIN_RECOMPRESS_PIXELS) return null

    val maxW = max(600f, pageWidthPt / 72f * targetDpi)
    val maxH = max(600f, pageHeightPt / 72f * targetDpi)
    val scale = min(1f, min(maxW / width, maxH / height))
    val targetW = max(1, (width * scale).toInt())
    val targetH = max(1, (height * scale).toInt())

    // Largest integer step that still decodes at least the target size...
    var subsampling = max(1, min(width / targetW, height / targetH))
    // ...but never decode more than the memory budget.
    while ((width.toLong() / subsampling) * (height.toLong() / subsampling) > MAX_DECODE_PIXELS) subsampling++
    val decodedW = (width + subsampling - 1) / subsampling
    val decodedH = (height + subsampling - 1) / subsampling
    return ImageRecompressPlan(min(targetW, decodedW), min(targetH, decodedH), subsampling)
}

object PdfEngine {

    private const val TAG = "PdfEngine"

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
                Log.w(TAG, "checkAccess: could not copy $inputUri", e)
                return@withContext PdfAccess.Damaged("Could not read the selected file. Pick it again.")
            }
            try {
                openDocument(context, cachedInput, password).use { PdfAccess.Open(it.numberOfPages, it.isEncrypted) }
            } catch (e: PdfPasswordException) {
                PdfAccess.NeedsPassword(e.wrongPassword)
            } catch (e: Exception) {
                Log.w(TAG, "checkAccess: could not open $inputUri as PDF", e)
                PdfAccess.Damaged("This file isn't a readable PDF (it may be damaged).")
            } finally {
                cachedInput.delete()
            }
        }

    /** Writes a password-free copy of [file] for PdfRenderer, which can't take a password. */
    private fun decryptToTemp(context: Context, file: File, password: String): File {
        val out = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}_${System.nanoTime()}.pdf")
        openDocument(context, file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
        return out
    }

    /**
     * Removes password protection from an encrypted PDF.
     * Takes the user's password, decrypts the document, strips all security,
     * and saves an unlocked, unrestricted copy.
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
            } catch (e: PdfPasswordException) {
                cachedInput.delete()
                outputFile.delete()
                throw IllegalArgumentException(
                    if (e.wrongPassword) "Incorrect password for this PDF"
                    else "Password required to unlock this PDF"
                )
            } catch (e: Exception) {
                cachedInput.delete()
                outputFile.delete()
                throw IllegalArgumentException("Corrupt or invalid PDF file: ${e.localizedMessage ?: "Unknown error"}")
            }

            try {
                if (document.isEncrypted) {
                    // Strips all security handlers & encryption dictionaries
                    document.isAllSecurityToBeRemoved = true
                }
                document.save(outputFile)
                outputFile
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            } finally {
                document.close()
                cachedInput.delete()
            }
        }
    }

    /**
     * Compresses a PDF by downscaling embedded image XObjects and re-encoding to JPEG.
     * Resolves Audit Issue B5: imageQuality is actively applied to embedded PDImageXObjects.
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

            try {
                val document = openDocument(context, cachedInput, password)
                try {
                    val stats = recompressImages(document, imageQuality.coerceIn(0.1f, 0.95f))
                    Log.i(TAG, "compressPdf: $stats")
                    document.save(outputFile)
                    outputFile
                } finally {
                    document.close()
                }
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            } finally {
                cachedInput.delete()
            }
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
                outputFile
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            } finally {
                document.close()
            }
        }
    }

    /**
     * Merges PDF files, in the given order, into a single PDF. Password-protected inputs are
     * opened with their own password and come out unprotected in the merged file.
     */
    suspend fun mergePdfs(
        context: Context,
        inputs: List<PdfMergeInput>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputs.size >= 2) { "Pick at least two PDFs to merge" }
            val outputFile = FileUtils.createTempOutputFile(context, "merged", ".pdf")
            val merger = PDFMergerUtility().apply {
                destinationFileName = outputFile.absolutePath
            }

            val tempFiles = mutableListOf<File>()
            try {
                for (input in inputs) {
                    val copied = FileUtils.copyUriToCache(context, input.uri)
                    tempFiles.add(copied)
                    val source = if (input.password.isNotEmpty()) {
                        decryptToTemp(context, copied, input.password).also { tempFiles.add(it) }
                    } else copied
                    merger.addSource(source)
                }
                try {
                    merger.mergeDocuments(tempFileMemory(context))
                } catch (e: InvalidPasswordException) {
                    throw IllegalArgumentException(
                        "One of the PDFs is password-protected. Remove it from the list and add it again to enter its password.", e
                    )
                }
                outputFile
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            } finally {
                tempFiles.forEach { it.delete() }
            }
        }
    }

    /**
     * Computes consecutive page ranges chunked by [n] pages up to [totalPages] (1-indexed).
     * e.g. totalPages = 10, n = 3 -> [1..3, 4..6, 7..9, 10..10]
     */
    fun computeEveryNChunks(totalPages: Int, n: Int): List<IntRange> {
        if (totalPages <= 0 || n <= 0) return emptyList()
        val chunks = ArrayList<IntRange>((totalPages + n - 1) / n)
        var start = 1
        while (start <= totalPages) {
            val end = minOf(start + n - 1, totalPages)
            chunks.add(start..end)
            start += n
        }
        return chunks
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

            try {
                openDocument(context, cachedInput, password).use { document ->
                    val totalPages = document.numberOfPages
                    val start = (startPage - 1).coerceIn(0, totalPages - 1)
                    val end = (endPage - 1).coerceIn(start, totalPages - 1)

                    PDDocument(tempFileMemory(context)).use { newDoc ->
                        for (i in start..end) {
                            newDoc.addPage(document.getPage(i))
                        }
                        newDoc.save(outputFile)
                    }
                }
                outputFile
            } catch (t: Throwable) {
                outputFile.delete()
                throw t
            } finally {
                cachedInput.delete()
            }
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
            val outputFiles = mutableListOf<File>()

            try {
                openDocument(context, cachedInput, password).use { document ->
                    val totalPages = document.numberOfPages
                    val validRanges = ranges.filter { range ->
                        range.first in 1..totalPages && range.last >= range.first
                    }
                    if (validRanges.isEmpty()) {
                        throw IllegalArgumentException("No valid page ranges within document (1..$totalPages)")
                    }

                    for ((index, range) in validRanges.withIndex()) {
                        val start = range.first - 1
                        val end = minOf(range.last, totalPages) - 1
                        val partFile = FileUtils.createTempOutputFile(
                            context,
                            "part_${index + 1}_p${range.first}_p${minOf(range.last, totalPages)}",
                            ".pdf"
                        )
                        try {
                            PDDocument(tempFileMemory(context)).use { newDoc ->
                                for (p in start..end) {
                                    newDoc.addPage(document.getPage(p))
                                }
                                newDoc.save(partFile)
                            }
                            outputFiles.add(partFile)
                        } catch (e: Throwable) {
                            partFile.delete()
                            throw e
                        }
                    }
                }
                outputFiles
            } catch (t: Throwable) {
                outputFiles.forEach { it.delete() }
                throw t
            } finally {
                cachedInput.delete()
            }
        }
    }

    // ------------------------------------------------------------ image re-compression

    data class RecompressStats(var replaced: Int = 0, var keptLarger: Int = 0, var skipped: Int = 0, var failed: Int = 0)

    /**
     * Re-encodes the embedded images of [document] as JPEG at about [TARGET_DPI], walking page
     * resources and nested form XObjects. Each image stream is handled once (by object identity),
     * and the original is kept whenever the new stream isn't smaller.
     */
    private fun recompressImages(document: PDDocument, quality: Float): RecompressStats {
        val stats = RecompressStats()
        // original image stream -> replacement (null = keep the original)
        val done = java.util.IdentityHashMap<COSBase, PDImageXObject?>()
        val visitedResources = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<COSBase, Boolean>())

        fun walk(resources: PDResources, pageWidthPt: Float, pageHeightPt: Float) {
            if (!visitedResources.add(resources.cosObject)) return
            for (name in resources.xObjectNames.toList()) {
                val xObject = try { resources.getXObject(name) } catch (e: Exception) { null } ?: continue
                when (xObject) {
                    is PDFormXObject -> xObject.resources?.let { walk(it, pageWidthPt, pageHeightPt) }
                    is PDImageXObject -> {
                        val key = xObject.cosObject
                        val replacement = if (done.containsKey(key)) {
                            done[key]
                        } else {
                            recompressImage(document, xObject, quality, pageWidthPt, pageHeightPt, stats)
                                .also { done[key] = it }
                        }
                        if (replacement != null) resources.put(name, replacement)
                    }
                }
            }
        }

        for (page in document.pages) {
            val box = page.cropBox ?: page.mediaBox
            val resources = page.resources ?: continue
            walk(resources, box?.width ?: PDRectangle.A4.width, box?.height ?: PDRectangle.A4.height)
        }
        return stats
    }

    private fun recompressImage(
        document: PDDocument,
        image: PDImageXObject,
        quality: Float,
        pageWidthPt: Float,
        pageHeightPt: Float,
        stats: RecompressStats
    ): PDImageXObject? {
        val dict = image.cosObject
        val plan = planImageRecompression(
            width = image.width,
            height = image.height,
            bitsPerComponent = image.bitsPerComponent,
            isStencil = image.isStencil,
            hasMask = dict.containsKey(COSName.SMASK) || dict.containsKey(COSName.MASK),
            pageWidthPt = pageWidthPt,
            pageHeightPt = pageHeightPt
        )
        if (plan == null) {
            stats.skipped++
            return null
        }
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        var flattened: Bitmap? = null
        return try {
            decoded = image.getImage(null, plan.subsampling) ?: run { stats.failed++; return null }
            scaled = if (decoded.width != plan.targetWidth || decoded.height != plan.targetHeight) {
                Bitmap.createScaledBitmap(decoded, plan.targetWidth, plan.targetHeight, true)
            } else decoded
            // Masked images are skipped above, so any alpha here is incidental: flatten onto white.
            val opaque = if (scaled.hasAlpha()) {
                Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also {
                    flattened = it
                    Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(scaled, 0f, 0f, null) }
                }
            } else scaled
            val candidate = JPEGFactory.createFromImage(document, opaque, quality)
            if (candidate.cosObject.length >= dict.length) {
                stats.keptLarger++
                null // re-encoding didn't help (e.g. already a small JPEG); keep the original
            } else {
                stats.replaced++
                candidate
            }
        } catch (e: Exception) {
            Log.w(TAG, "compressPdf: kept an image that couldn't be re-encoded", e)
            stats.failed++
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "compressPdf: image too large to re-encode, kept original", e)
            stats.failed++
            null
        } finally {
            flattened?.recycle()
            if (scaled != null && scaled !== decoded) scaled.recycle()
            decoded?.recycle()
        }
    }
}
