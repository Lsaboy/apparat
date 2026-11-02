package com.omniforge.apparat.engine.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.platform.AwtImages
import com.omniforge.apparat.engine.image.BitmapDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSBase
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessStreamCache
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.max
import kotlin.math.min

// Desktop port of the Android PdfEngine (same functions and behaviour), on Apache PDFBox 3.

/** One PDF to merge: [password] is "" for unprotected files. */
data class PdfMergeInput(val uri: Uri, val password: String = "")

/** Thrown when a PDF needs a password, or the one given is wrong. */
class PdfPasswordException(val wrongPassword: Boolean) : Exception(
    if (wrongPassword) "Wrong password for this PDF" else "This PDF is password-protected"
)

sealed interface PdfAccess {
    data class Open(val pageCount: Int, val isEncrypted: Boolean) : PdfAccess
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

data class ImageRecompressPlan(val targetWidth: Int, val targetHeight: Int, val subsampling: Int)

internal const val TARGET_DPI = 150f
private const val MAX_DECODE_PIXELS = 16_000_000L
private const val MIN_RECOMPRESS_PIXELS = 100L * 100L

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
    var subsampling = max(1, min(width / targetW, height / targetH))
    while ((width.toLong() / subsampling) * (height.toLong() / subsampling) > MAX_DECODE_PIXELS) subsampling++
    val decodedW = (width + subsampling - 1) / subsampling
    val decodedH = (height + subsampling - 1) / subsampling
    return ImageRecompressPlan(min(targetW, decodedW), min(targetH, decodedH), subsampling)
}

object PdfEngine {

    private const val TAG = "PdfEngine"

    /** PDFBox buffers in temp files under cache/pdfbox, so 100+ page scans don't fill the heap. */
    private fun tempFileMemory(context: Context): RandomAccessStreamCache.StreamCacheCreateFunction =
        MemoryUsageSetting.setupTempFileOnly().setTempDir(File(context.cacheDir, "pdfbox").apply { mkdirs() }).streamCache

    private val keptCopies = LinkedHashMap<String, Pair<File, Long>>()
    private const val MAX_KEPT_COPIES = 3

    @Synchronized
    private fun keepInputCopy(context: Context, uri: Uri, copy: File) {
        keptCopies.remove(uri.toString())?.first?.takeIf { it != copy }?.delete()
        keptCopies[uri.toString()] = copy to FileUtils.getFileSize(context, uri)
        while (keptCopies.size > MAX_KEPT_COPIES) {
            val oldest = keptCopies.keys.first()
            keptCopies.remove(oldest)?.first?.delete()
        }
    }

    /** A private copy of [uri] that the caller owns (and deletes), reusing the one [checkAccess] made. */
    @Synchronized
    fun takeInputCopy(context: Context, uri: Uri): File {
        val kept = keptCopies.remove(uri.toString())
        if (kept != null) {
            val (file, sizeThen) = kept
            val sizeNow = FileUtils.getFileSize(context, uri)
            if (file.exists() && (sizeNow <= 0L || sizeNow == sizeThen)) return file
            file.delete()
        }
        return FileUtils.copyUriToCache(context, uri)
    }

    private fun openDocument(context: Context, file: File, password: String = ""): PDDocument =
        try {
            Loader.loadPDF(file, password, tempFileMemory(context))
        } catch (e: InvalidPasswordException) {
            throw PdfPasswordException(wrongPassword = password.isNotEmpty())
        }

    suspend fun checkAccess(context: Context, inputUri: Uri, password: String = ""): PdfAccess =
        withContext(Dispatchers.IO) {
            val cachedInput = try {
                takeInputCopy(context, inputUri)
            } catch (e: Exception) {
                Log.w(TAG, "checkAccess: could not copy $inputUri", e)
                return@withContext PdfAccess.Damaged("Could not read the selected file. Pick it again.")
            }
            val access = try {
                openDocument(context, cachedInput, password).use { PdfAccess.Open(it.numberOfPages, it.isEncrypted) }
            } catch (e: PdfPasswordException) {
                PdfAccess.NeedsPassword(e.wrongPassword)
            } catch (e: Exception) {
                Log.w(TAG, "checkAccess: could not open $inputUri as PDF", e)
                PdfAccess.Damaged("This file isn't a readable PDF (it may be damaged).")
            }
            if (access is PdfAccess.Damaged) cachedInput.delete() else keepInputCopy(context, inputUri, cachedInput)
            access
        }

    private fun decryptToTemp(context: Context, file: File, password: String): File {
        val out = File(context.cacheDir, "decrypted_${System.currentTimeMillis()}_${System.nanoTime()}.pdf")
        openDocument(context, file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
        return out
    }

    suspend fun removePassword(context: Context, inputUri: Uri, password: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedInput = takeInputCopy(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "unlocked", ".pdf")
            val document = try {
                openDocument(context, cachedInput, password)
            } catch (e: PdfPasswordException) {
                cachedInput.delete(); outputFile.delete()
                throw IllegalArgumentException(
                    if (e.wrongPassword) "Incorrect password for this PDF" else "Password required to unlock this PDF"
                )
            } catch (e: Exception) {
                cachedInput.delete(); outputFile.delete()
                throw IllegalArgumentException("Corrupt or invalid PDF file: ${e.localizedMessage ?: "Unknown error"}")
            }
            try {
                if (document.isEncrypted) document.isAllSecurityToBeRemoved = true
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

    suspend fun compressPdf(context: Context, inputUri: Uri, imageQuality: Float = 0.6f, password: String = ""): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cachedInput = takeInputCopy(context, inputUri)
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

    suspend fun imagesToPdf(context: Context, imageUris: List<Uri>): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outputFile = FileUtils.createTempOutputFile(context, "converted", ".pdf")
            val document = PDDocument(tempFileMemory(context))
            val margin = 20f
            try {
                for (uri in imageUris) {
                    // A4 at 300 DPI is ~8.7 MP: more only adds file size.
                    val bitmap = BitmapDecoder.decode(context, uri, maxPixels = 9_000_000L)
                    val image = AwtImages.toRgbOnWhite(bitmap)
                    bitmap.recycle()
                    val fit = calculateA4Fit(image.width, image.height, margin)
                    val page = PDPage(PDRectangle(fit.pageWidth, fit.pageHeight))
                    document.addPage(page)
                    val pdImage = JPEGFactory.createFromImage(document, image, 0.85f)
                    PDPageContentStream(document, page).use { it.drawImage(pdImage, fit.x, fit.y, fit.drawWidth, fit.drawHeight) }
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

    suspend fun mergePdfs(context: Context, inputs: List<PdfMergeInput>): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputs.size >= 2) { "Pick at least two PDFs to merge" }
            val outputFile = FileUtils.createTempOutputFile(context, "merged", ".pdf")
            val merger = PDFMergerUtility().apply { destinationFileName = outputFile.absolutePath }
            val tempFiles = mutableListOf<File>()
            try {
                for (input in inputs) {
                    val copied = takeInputCopy(context, input.uri)
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

    suspend fun multiSplitPdf(context: Context, inputUri: Uri, ranges: List<IntRange>, password: String = ""): Result<List<File>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cachedInput = takeInputCopy(context, inputUri)
                val outputFiles = mutableListOf<File>()
                try {
                    openDocument(context, cachedInput, password).use { document ->
                        val totalPages = document.numberOfPages
                        val validRanges = ranges.filter { it.first in 1..totalPages && it.last >= it.first }
                        if (validRanges.isEmpty()) throw IllegalArgumentException("No valid page ranges within document (1..$totalPages)")
                        for ((index, range) in validRanges.withIndex()) {
                            val start = range.first - 1
                            val end = minOf(range.last, totalPages) - 1
                            val partFile = FileUtils.createTempOutputFile(
                                context, "part_${index + 1}_p${range.first}_p${minOf(range.last, totalPages)}", ".pdf"
                            )
                            try {
                                PDDocument(tempFileMemory(context)).use { newDoc ->
                                    for (p in start..end) newDoc.importPage(document.getPage(p))
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

    data class RecompressStats(var replaced: Int = 0, var keptLarger: Int = 0, var skipped: Int = 0, var failed: Int = 0)

    private fun recompressImages(document: PDDocument, quality: Float): RecompressStats {
        val stats = RecompressStats()
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
                        val replacement = if (done.containsKey(key)) done[key] else
                            recompressImage(document, xObject, quality, pageWidthPt, pageHeightPt, stats).also { done[key] = it }
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
        return try {
            val decoded: BufferedImage = image.getImage(null, plan.subsampling) ?: run { stats.failed++; return null }
            val opaque = BufferedImage(plan.targetWidth, plan.targetHeight, BufferedImage.TYPE_INT_RGB)
            opaque.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                color = java.awt.Color.WHITE
                fillRect(0, 0, plan.targetWidth, plan.targetHeight)
                drawImage(decoded, 0, 0, plan.targetWidth, plan.targetHeight, null)
                dispose()
            }
            val candidate = JPEGFactory.createFromImage(document, opaque, quality)
            if (candidate.cosObject.length >= dict.length) {
                stats.keptLarger++
                null
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
        }
    }
}
