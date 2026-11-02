package com.omniforge.apparat.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Directory
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.exif.GpsDirectory
import com.omniforge.apparat.core.util.CodecSupport
import com.omniforge.apparat.core.util.FileUtils
import com.omniforge.apparat.desktop.platform.JpegExif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

// Desktop port of the Android ImageEngine: same functions, pixels through Skia, EXIF handled
// losslessly at the JPEG segment level.

enum class ImageOutputFormat(val extension: String, val mimeType: String) {
    JPEG(".jpg", "image/jpeg"),
    PNG(".png", "image/png"),
    WEBP(".webp", "image/webp"),
    /** Not offered on desktop (no HEIC encoder bundled). */
    HEIC(".heic", "image/heic");

    companion object {
        fun availableFor(canEncodeHeic: Boolean): List<ImageOutputFormat> = entries.filter { it != HEIC || canEncodeHeic }

        fun available(): List<ImageOutputFormat> = availableFor(CodecSupport.canEncodeHeic)

        fun fromExtension(ext: String): ImageOutputFormat =
            entries.firstOrNull { it.extension.removePrefix(".").equals(ext.removePrefix("."), ignoreCase = true) } ?: JPEG
    }
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
    fun hasAnyMetadata(): Boolean =
        !cameraMake.isNullOrBlank() || !cameraModel.isNullOrBlank() || !dateTime.isNullOrBlank() ||
            !iso.isNullOrBlank() || !fNumber.isNullOrBlank() || !exposureTime.isNullOrBlank() ||
            (latitude != null && longitude != null)
}

object ImageEngine {

    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return out
    }

    suspend fun shrinkImage(
        context: Context,
        inputUri: Uri,
        quality: Int = 80,
        maxDimension: Int = 1920,
        stripExif: Boolean = true,
        targetFormat: ImageOutputFormat = ImageOutputFormat.JPEG
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val original = BitmapDecoder.decode(context, inputUri, maxDimension = maxDimension)
            val outputFile = FileUtils.createTempOutputFile(context, "shrunk", targetFormat.extension)
            val toWrite = if (targetFormat == ImageOutputFormat.JPEG && original.hasAlpha()) flattenOnWhite(original) else original
            writeBitmap(toWrite, outputFile, targetFormat, quality)
            if (!stripExif && targetFormat == ImageOutputFormat.JPEG) {
                runCatching { copyCameraExif(inputUri.file, outputFile) }
            }
            outputFile
        }
    }

    /**
     * Compresses to strictly <= targetKb (JPEG), searching quality first and then dimensions. The
     * file is kept under targetKb * 1000 bytes, which satisfies both KB readings.
     */
    suspend fun shrinkToTargetKb(
        context: Context,
        inputUri: Uri,
        targetKb: Int,
        maxDimension: Int = 1920,
        stripExif: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(targetKb > 0) { "Target size must be greater than 0 KB" }
            val decoded = BitmapDecoder.decode(context, inputUri, maxDimension = maxDimension)
            val current = if (decoded.hasAlpha()) flattenOnWhite(decoded) else decoded
            val limitBytes = targetKb * 1000L
            val searchKb = (limitBytes / 1024L).toInt().coerceAtLeast(1)
            val config = TargetKbSearchEngine.SearchConfig(targetKb = searchKb, initialWidth = current.width, initialHeight = current.height)

            var scaled: Bitmap = current
            val result = TargetKbSearchEngine.findOptimalParameters(config) { width, height, quality ->
                if (scaled.width != width || scaled.height != height) scaled = Bitmap.createScaledBitmap(current, width, height, true)
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.size().toLong()
            }
            val outputFile = FileUtils.createTempOutputFile(context, "target_${targetKb}kb", ".jpg")
            val finalBitmap = if (current.width == result.width && current.height == result.height) current
            else Bitmap.createScaledBitmap(current, result.width, result.height, true)
            FileOutputStream(outputFile).use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, result.quality, it) }

            if (!stripExif) {
                val before = outputFile.readBytes()
                runCatching { copyCameraExif(inputUri.file, outputFile) }
                // Keeping camera details must never push the file over the portal's limit.
                if (outputFile.length() > limitBytes) outputFile.writeBytes(before)
            }
            outputFile
        }
    }

    suspend fun convertFormat(
        context: Context,
        inputUri: Uri,
        targetFormat: ImageOutputFormat,
        quality: Int = 90
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val original = BitmapDecoder.decode(context, inputUri)
            val outputFile = FileUtils.createTempOutputFile(context, "converted", targetFormat.extension)
            val toWrite = if (targetFormat == ImageOutputFormat.JPEG && original.hasAlpha()) flattenOnWhite(original) else original
            writeBitmap(toWrite, outputFile, targetFormat, quality)
            outputFile
        }
    }

    private fun writeBitmap(bitmap: Bitmap, outputFile: File, format: ImageOutputFormat, quality: Int) {
        val compressFormat = when (format) {
            ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageOutputFormat.WEBP -> if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            ImageOutputFormat.HEIC -> throw UnsupportedOperationException("Saving as HEIC isn't available on desktop. Pick JPEG, PNG or WEBP instead.")
        }
        FileOutputStream(outputFile).use { out ->
            if (!bitmap.compress(compressFormat, quality.coerceIn(1, 100), out)) {
                throw IllegalStateException("Could not encode the image as ${format.name}")
            }
        }
    }

    /** Copies Make/Model/DateTime/WhiteBalance/FocalLength/Flash from [source] into the JPEG [target]. */
    private fun copyCameraExif(source: File, target: File) {
        val meta = ImageMetadataReader.readMetadata(source)
        val ifd0 = meta.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val sub = meta.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val focal = sub?.takeIf { it.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH) }
            ?.getRational(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)?.let { it.numerator to it.denominator }
        val fields = JpegExif.ExifFields(
            make = ifd0?.getString(ExifIFD0Directory.TAG_MAKE)?.trim(),
            model = ifd0?.getString(ExifIFD0Directory.TAG_MODEL)?.trim(),
            dateTime = ifd0?.getString(ExifIFD0Directory.TAG_DATETIME)?.trim(),
            whiteBalance = sub?.getInteger(ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE),
            flash = sub?.getInteger(ExifSubIFDDirectory.TAG_FLASH),
            focalLength = focal
            // Orientation is deliberately not copied: the decoder already rotated the pixels.
        )
        if (fields.isEmpty) return
        target.writeBytes(JpegExif.rewrite(target.readBytes(), fields))
    }

    /**
     * Strips all metadata (camera, location, timestamps). JPEGs are cleaned losslessly (the image
     * data is copied untouched and only the orientation tag is kept); PNG and WEBP are re-saved in
     * their own format; other formats become JPEG.
     */
    suspend fun stripExif(context: Context, inputUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = FileUtils.getFileName(context, inputUri)
            val mime = context.contentResolver.getType(inputUri)?.lowercase()
            val ext = name.substringAfterLast('.', "").lowercase()
            val bytes = inputUri.file.readBytes()
            val isJpeg = JpegExif.isJpeg(bytes) || mime in listOf("image/jpeg", "image/jpg") || ext in listOf("jpg", "jpeg", "jpe")
            val isPng = mime == "image/png" || ext == "png"
            val isWebp = mime == "image/webp" || ext == "webp"

            fun reencode(prefix: String, format: Bitmap.CompressFormat, extension: String, quality: Int): File {
                val outputFile = FileUtils.createTempOutputFile(context, prefix, extension)
                val bitmap = BitmapDecoder.decode(context, inputUri)
                val toWrite = if (format == Bitmap.CompressFormat.JPEG && bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
                FileOutputStream(outputFile).use { toWrite.compress(format, quality, it) }
                return outputFile
            }

            when {
                isJpeg -> {
                    val outputFile = FileUtils.createTempOutputFile(context, "clean_exif", ".jpg")
                    try {
                        val orientation = BitmapDecoder.orientationOf(inputUri.file).takeIf { it != 1 }
                        outputFile.writeBytes(JpegExif.rewrite(bytes, JpegExif.ExifFields(orientation = orientation)))
                        val verify = readExif(outputFile)
                        if (verify.latitude != null || verify.cameraMake != null) {
                            throw java.io.IOException("Metadata could not be stripped losslessly")
                        }
                        outputFile
                    } catch (e: Exception) {
                        outputFile.delete()
                        reencode("clean_exif_fallback", Bitmap.CompressFormat.JPEG, ".jpg", 95)
                    }
                }
                isPng -> reencode("clean_exif", Bitmap.CompressFormat.PNG, ".png", 100)
                isWebp -> reencode("clean_exif", Bitmap.CompressFormat.WEBP_LOSSLESS, ".webp", 100)
                else -> reencode("clean_exif", Bitmap.CompressFormat.JPEG, ".jpg", 95)
            }
        }
    }

    suspend fun getExifData(context: Context, inputUri: Uri): ExifInfo = withContext(Dispatchers.IO) {
        runCatching { readExif(inputUri.file) }.getOrDefault(ExifInfo())
    }

    private fun Directory.str(tag: Int): String? = if (containsTag(tag)) getString(tag)?.trim()?.takeIf { it.isNotEmpty() } else null

    /** Number formatting close to Android's ExifInterface (e.g. "1.8", "0.008", "100"). */
    private fun Directory.decimal(tag: Int): String? = if (!containsTag(tag)) null else runCatching {
        val r = getRational(tag)
        val v = r.toDouble()
        if (v == Math.floor(v)) v.toLong().toString() else "%.6f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
    }.getOrNull() ?: str(tag)

    internal fun readExif(file: File): ExifInfo {
        val meta: Metadata = ImageMetadataReader.readMetadata(file)
        val ifd0 = meta.getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        val sub = meta.getFirstDirectoryOfType(ExifSubIFDDirectory::class.java)
        val gps = meta.getFirstDirectoryOfType(GpsDirectory::class.java)?.geoLocation?.takeIf { !it.isZero }
        return ExifInfo(
            cameraMake = ifd0?.str(ExifIFD0Directory.TAG_MAKE),
            cameraModel = ifd0?.str(ExifIFD0Directory.TAG_MODEL),
            dateTime = ifd0?.str(ExifIFD0Directory.TAG_DATETIME) ?: sub?.str(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL),
            iso = sub?.str(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT),
            fNumber = sub?.decimal(ExifSubIFDDirectory.TAG_FNUMBER),
            exposureTime = sub?.decimal(ExifSubIFDDirectory.TAG_EXPOSURE_TIME),
            latitude = gps?.latitude,
            longitude = gps?.longitude
        )
    }

    suspend fun enhanceDocumentScan(
        context: Context,
        inputUri: Uri,
        contrastMultiplier: Float = 1.8f,
        convertToMonochrome: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val original = BitmapDecoder.decode(context, inputUri, maxPixels = 16_000_000L)
            val outputBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint()
            val cm = ColorMatrix()
            if (convertToMonochrome) cm.setSaturation(0f)
            val scale = contrastMultiplier
            val translate = (-0.5f * scale + 0.5f) * 255f
            cm.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(original, 0f, 0f, paint)
            val outputFile = FileUtils.createTempOutputFile(context, "scanned_doc", ".jpg")
            FileOutputStream(outputFile).use { outputBitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            outputFile
        }
    }

    suspend fun extractColorPalette(context: Context, inputUri: Uri, count: Int = 6): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val original = BitmapDecoder.decode(context, inputUri, maxDimension = 256)
            val scaled = Bitmap.createScaledBitmap(original, 64, 64, true)
            val colorCounts = mutableMapOf<Int, Int>()
            for (x in 0 until scaled.width step 2) {
                for (y in 0 until scaled.height step 2) {
                    val pixel = scaled.getPixel(x, y)
                    if ((pixel ushr 24) < 128) continue
                    val r = (pixel shr 16 and 0xFF) / 16 * 16
                    val g = (pixel shr 8 and 0xFF) / 16 * 16
                    val b = (pixel and 0xFF) / 16 * 16
                    val quantized = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    colorCounts[quantized] = (colorCounts[quantized] ?: 0) + 1
                }
            }
            colorCounts.entries.sortedByDescending { it.value }.take(count).map { String.format("#%06X", (0xFFFFFF and it.key)) }
        }
    }
}
