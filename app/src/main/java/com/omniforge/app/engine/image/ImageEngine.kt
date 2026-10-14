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
import kotlin.math.max

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
)

object ImageEngine {

    /**
     * Shrinks and optimizes image with custom quality, dimension scaling, and optional EXIF stripping.
     */
    suspend fun shrinkImage(
        context: Context,
        inputUri: Uri,
        quality: Int = 80,
        maxDimension: Int = 1920,
        stripExif: Boolean = true
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmap(context, inputUri, maxDimension)
                ?: throw IllegalStateException("Failed to decode image")

            val outputFile = FileUtils.createTempOutputFile(context, "shrunk", ".jpg")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            }
            bitmap.recycle()

            if (!stripExif) {
                // Copy EXIF attributes
                context.contentResolver.openInputStream(inputUri)?.use { originalStream ->
                    val srcExif = ExifInterface(originalStream)
                    val dstExif = ExifInterface(outputFile.absolutePath)
                    copyExifTags(srcExif, dstExif)
                    dstExif.saveAttributes()
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
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Could not open input image")
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw IllegalStateException("Could not decode image")
            inputStream.close()

            val outputFile = FileUtils.createTempOutputFile(context, "converted", targetFormat.extension)
            FileOutputStream(outputFile).use { out ->
                val format = when (targetFormat) {
                    ImageOutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                    ImageOutputFormat.WEBP -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                    }
                }
                bitmap.compress(format, quality.coerceIn(1, 100), out)
            }
            bitmap.recycle()
            outputFile
        }
    }

    /**
     * Strips all EXIF metadata (camera, location, timestamps) for privacy.
     */
    suspend fun stripExif(
        context: Context,
        inputUri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw IllegalStateException("Could not open input image")
            val bitmap = BitmapFactory.decodeStream(inputStream)
                ?: throw IllegalStateException("Could not decode image")
            inputStream.close()

            // Re-encoding directly strips all original metadata
            val outputFile = FileUtils.createTempOutputFile(context, "clean_exif", ".jpg")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()
            outputFile
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

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        var sampleSize = 1
        val origMax = max(options.outWidth, options.outHeight)
        if (origMax > maxDimension) {
            sampleSize = (origMax.toFloat() / maxDimension.toFloat()).toInt()
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }

    private fun copyExifTags(src: ExifInterface, dst: ExifInterface) {
        val tags = arrayOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FLASH
        )
        for (tag in tags) {
            src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
        }
    }
}
