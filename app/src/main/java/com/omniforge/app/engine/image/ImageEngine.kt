package com.omniforge.app.engine.image

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.omniforge.app.core.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ImageOutputFormat(val extension: String, val mimeType: String) {
    JPEG(".jpg", "image/jpeg"),
    PNG(".png", "image/png"),
    WEBP(".webp", "image/webp")
}

data class ExifInfo(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val dateTime: String? = null,
    val iso: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    fun hasAnyMetadata(): Boolean {
        return !cameraMake.isNullOrBlank() ||
               !cameraModel.isNullOrBlank() ||
               !dateTime.isNullOrBlank() ||
               !iso.isNullOrBlank() ||
               !fNumber.isNullOrBlank() ||
               !exposureTime.isNullOrBlank() ||
               (latitude != null && longitude != null)
    }
}

object ImageEngine {

    /**
     * Shrinks and optimizes image with custom quality, dimension scaling, and optional EXIF stripping.
     */
    suspend fun shrinkImage(
        context: Context,
        inputUri: Uri,
        quality: Int = 80,
        maxDimension: Int = 1920,
        stripExif: Boolean = true,
        targetFormat: ImageOutputFormat = ImageOutputFormat.JPEG
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val originalBitmap = BitmapDecoder.decode(context, inputUri, maxDimension = maxDimension)
            var compositedBitmap: Bitmap? = null
            val outputFile = FileUtils.createTempOutputFile(context, "shrunk", targetFormat.extension)

            try {
                val bitmapToCompress = if (targetFormat == ImageOutputFormat.JPEG && originalBitmap.hasAlpha()) {
                    compositedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(compositedBitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(originalBitmap, 0f, 0f, null)
                    originalBitmap.recycle()
                    compositedBitmap
                } else {
                    originalBitmap
                }

                FileOutputStream(outputFile).use { out ->
                    val format = when (targetFormat) {
                        ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                        ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                        ImageOutputFormat.WEBP -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                if (quality >= 100) {
                                    Bitmap.CompressFormat.WEBP_LOSSLESS
                                } else {
                                    Bitmap.CompressFormat.WEBP_LOSSY
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP
                            }
                        }
                    }
                    bitmapToCompress.compress(format, quality.coerceIn(1, 100), out)
                }
            } finally {
                if (!originalBitmap.isRecycled) {
                    originalBitmap.recycle()
                }
                compositedBitmap?.recycle()
            }

            if (!stripExif && targetFormat == ImageOutputFormat.JPEG) {
                // Copy EXIF attributes (JPEG only; guarded against write errors)
                runCatching {
                    context.contentResolver.openInputStream(inputUri)?.use { originalStream ->
                        val srcExif = ExifInterface(originalStream)
                        val dstExif = ExifInterface(outputFile.absolutePath)
                        copyExifTags(srcExif, dstExif)
                        dstExif.saveAttributes()
                    }
                }
            }

            outputFile
        }
    }

    /**
     * Converts image to target format (JPEG, PNG, WEBP).
     */
    suspend fun convertFormat(
        context: Context,
        inputUri: Uri,
        targetFormat: ImageOutputFormat,
        quality: Int = 90
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val originalBitmap = BitmapDecoder.decode(context, inputUri)
            var compositedBitmap: Bitmap? = null
            val outputFile = FileUtils.createTempOutputFile(context, "converted", targetFormat.extension)

            try {
                val bitmapToCompress = if (targetFormat == ImageOutputFormat.JPEG && originalBitmap.hasAlpha()) {
                    compositedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(compositedBitmap)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(originalBitmap, 0f, 0f, null)
                    originalBitmap.recycle()
                    compositedBitmap
                } else {
                    originalBitmap
                }

                FileOutputStream(outputFile).use { out ->
                    val format = when (targetFormat) {
                        ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                        ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                        ImageOutputFormat.WEBP -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                if (quality >= 100) {
                                    Bitmap.CompressFormat.WEBP_LOSSLESS
                                } else {
                                    Bitmap.CompressFormat.WEBP_LOSSY
                                }
                            } else {
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP
                            }
                        }
                    }
                    bitmapToCompress.compress(format, quality.coerceIn(1, 100), out)
                }
            } finally {
                if (!originalBitmap.isRecycled) {
                    originalBitmap.recycle()
                }
                compositedBitmap?.recycle()
            }

            outputFile
        }
    }

    private val TAGS_TO_PRESERVE = setOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_IMAGE_LENGTH
    )

    private val ALL_EXIF_TAGS: List<String> by lazy {
        ExifInterface::class.java.fields
            .filter { it.name.startsWith("TAG_") && it.type == String::class.java }
            .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
            .filter { it !in TAGS_TO_PRESERVE }
    }

    /**
     * Strips all EXIF metadata (camera, location, timestamps) for privacy.
     * Uses lossless tag clearing for JPEG (preserving orientation), and preserves
     * native format for PNG and WEBP.
     */
    suspend fun stripExif(
        context: Context,
        inputUri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(inputUri)?.lowercase()
            val ext = FileUtils.getFileName(context, inputUri).substringAfterLast('.', "").lowercase()
            val isJpeg = mime in listOf("image/jpeg", "image/jpg") || ext in listOf("jpg", "jpeg", "jpe")
            val isPng = mime == "image/png" || ext == "png"
            val isWebp = mime == "image/webp" || ext == "webp"

            if (isJpeg) {
                val outputFile = FileUtils.createTempOutputFile(context, "clean_exif", ".jpg")
                try {
                    // Step 1: Copy raw bytes directly (zero generation loss, fast)
                    context.contentResolver.openInputStream(inputUri)?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw java.io.IOException("Cannot read input: $inputUri")

                    // Step 2: Clear metadata tags while strictly preserving TAG_ORIENTATION
                    val exif = ExifInterface(outputFile.absolutePath)
                    for (tag in ALL_EXIF_TAGS) {
                        exif.setAttribute(tag, null)
                    }
                    exif.saveAttributes()

                    // Verification check: ensure GPS coordinates were completely removed
                    val verifyExif = ExifInterface(outputFile.absolutePath)
                    if (verifyExif.latLong != null) {
                        throw java.io.IOException("GPS location tags could not be stripped losslessly")
                    }

                    outputFile
                } catch (e: Exception) {
                    // Step 3: Fallback if JPEG has non-consecutive thumbnail strips or malformed APP1
                    outputFile.delete()
                    val fallbackFile = FileUtils.createTempOutputFile(context, "clean_exif_fallback", ".jpg")
                    val bitmap = BitmapDecoder.decode(context, inputUri)
                    FileOutputStream(fallbackFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    bitmap.recycle()
                    fallbackFile
                }
            } else if (isPng) {
                val outputFile = FileUtils.createTempOutputFile(context, "clean_exif", ".png")
                val bitmap = BitmapDecoder.decode(context, inputUri)
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
                outputFile
            } else if (isWebp) {
                val outputFile = FileUtils.createTempOutputFile(context, "clean_exif", ".webp")
                val bitmap = BitmapDecoder.decode(context, inputUri)
                FileOutputStream(outputFile).use { out ->
                    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    bitmap.compress(format, 100, out)
                }
                bitmap.recycle()
                outputFile
            } else {
                // Fallback for HEIC or other formats
                val outputFile = FileUtils.createTempOutputFile(context, "clean_exif", ".jpg")
                val bitmap = BitmapDecoder.decode(context, inputUri)
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                bitmap.recycle()
                outputFile
            }
        }
    }

    /**
     * Reads EXIF metadata from image.
     */
    suspend fun getExifData(context: Context, inputUri: Uri): ExifInfo = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(inputUri)?.use { input ->
            val exif = ExifInterface(input)
            val latLong = exif.latLong
            ExifInfo(
                cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
                cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
                exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                latitude = latLong?.get(0),
                longitude = latLong?.get(1)
            )
        } ?: ExifInfo()
    }

    private fun copyExifTags(src: ExifInterface, dst: ExifInterface) {
        val tags = arrayOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            // TAG_ORIENTATION is deliberately not copied: BitmapDecoder already rotated the pixels.
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH
        )
        for (tag in tags) {
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
    }

    /**
     * Enhances document photos (receipts, pages) into clean high-contrast black & white scans.
     */
    suspend fun enhanceDocumentScan(
        context: Context,
        inputUri: Uri,
        contrastMultiplier: Float = 1.8f,
        convertToMonochrome: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // Input + output bitmap are alive together, so use a smaller budget.
            val original = BitmapDecoder.decode(context, inputUri, maxPixels = 16_000_000L)

            val outputBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)
            val paint = Paint()

            val cm = ColorMatrix()
            if (convertToMonochrome) {
                cm.setSaturation(0f)
            }
            val scale = contrastMultiplier
            val translate = (-0.5f * scale + 0.5f) * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)
            paint.colorFilter = ColorMatrixColorFilter(cm)

            canvas.drawBitmap(original, 0f, 0f, paint)
            original.recycle()

            val outputFile = FileUtils.createTempOutputFile(context, "scanned_doc", ".jpg")
            FileOutputStream(outputFile).use { out ->
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            outputBitmap.recycle()
            outputFile
        }
    }

    /**
     * Extracts top dominant colors as hex strings from an image.
     */
    suspend fun extractColorPalette(
        context: Context,
        inputUri: Uri,
        count: Int = 6
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val original = BitmapDecoder.decode(context, inputUri, maxDimension = 256)

            // Downsample for fast color sampling
            val scaled = Bitmap.createScaledBitmap(original, 64, 64, true)
            if (scaled != original) original.recycle()

            val colorCounts = mutableMapOf<Int, Int>()
            for (x in 0 until scaled.width step 2) {
                for (y in 0 until scaled.height step 2) {
                    val pixel = scaled.getPixel(x, y)
                    // Quantize to reduce subtle variations
                    val r = (pixel shr 16 and 0xFF) / 16 * 16
                    val g = (pixel shr 8 and 0xFF) / 16 * 16
                    val b = (pixel and 0xFF) / 16 * 16
                    val quantized = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
                }
            }
            scaled.recycle()

            colorCounts.entries
                .sortedByDescending { it.value }
                .take(count)
                .map { String.format("#%06X", (0xFFFFFF and it.key)) }
        }
    }
}
