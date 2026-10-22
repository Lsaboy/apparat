package com.omniforge.apparat.core.util

import com.omniforge.apparat.engine.image.ImageOutputFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUtilsTest {

    @Test
    fun isImageFormatMatchingTests() {
        // JPEG tests
        assertTrue(FileUtils.isImageFormatMatching("image/jpeg", "photo.jpg", ImageOutputFormat.JPEG))
        assertTrue(FileUtils.isImageFormatMatching("image/jpg", "photo.jpeg", ImageOutputFormat.JPEG))
        assertTrue(FileUtils.isImageFormatMatching(null, "photo.jpg", ImageOutputFormat.JPEG))
        assertFalse(FileUtils.isImageFormatMatching("image/jpeg", "photo.jpg", ImageOutputFormat.PNG))
        assertFalse(FileUtils.isImageFormatMatching("image/jpeg", "photo.jpg", ImageOutputFormat.WEBP))

        // PNG tests
        assertTrue(FileUtils.isImageFormatMatching("image/png", "document.png", ImageOutputFormat.PNG))
        assertTrue(FileUtils.isImageFormatMatching(null, "document.png", ImageOutputFormat.PNG))
        assertFalse(FileUtils.isImageFormatMatching("image/png", "document.png", ImageOutputFormat.JPEG))

        // WEBP tests
        assertTrue(FileUtils.isImageFormatMatching("image/webp", "sticker.webp", ImageOutputFormat.WEBP))
        assertTrue(FileUtils.isImageFormatMatching(null, "sticker.webp", ImageOutputFormat.WEBP))
        assertFalse(FileUtils.isImageFormatMatching("image/webp", "sticker.webp", ImageOutputFormat.JPEG))
    }

    @Test
    fun isVideoMp4Tests() {
        assertTrue(FileUtils.isVideoMp4("video/mp4", "clip.mp4"))
        assertTrue(FileUtils.isVideoMp4(null, "clip.mp4"))
        assertTrue(FileUtils.isVideoMp4("video/mp4", "clip.m4v"))
        assertFalse(FileUtils.isVideoMp4("video/x-matroska", "movie.mkv"))
        assertFalse(FileUtils.isVideoMp4(null, "movie.mkv"))
        assertFalse(FileUtils.isVideoMp4("video/webm", "movie.webm"))
    }

    @Test
    fun testCleanStaleCacheDir() {
        val tempDir = java.nio.file.Files.createTempDirectory("cache_test").toFile()
        try {
            val appStartTime = 1000000L
            val pdfboxDir = java.io.File(tempDir, "pdfbox").apply { mkdirs() }
            val oldPdfbox = java.io.File(pdfboxDir, "scratch1.tmp").apply { writeText("old"); setLastModified(appStartTime - 10000) }
            val newPdfbox = java.io.File(pdfboxDir, "scratch2.tmp").apply { writeText("new"); setLastModified(appStartTime + 5000) }

            val oldInput = java.io.File(tempDir, "input_old.mp4").apply { writeText("old"); setLastModified(appStartTime - 5000) }
            val newInput = java.io.File(tempDir, "input_new.mp4").apply { writeText("new"); setLastModified(appStartTime + 5000) }

            val oldDecrypted = java.io.File(tempDir, "decrypted_old.pdf").apply { writeText("old"); setLastModified(appStartTime - 5000) }
            val newDecrypted = java.io.File(tempDir, "decrypted_new.pdf").apply { writeText("new"); setLastModified(appStartTime + 5000) }

            val oldBackup = java.io.File(tempDir, "backup_old").apply { writeText("old"); setLastModified(appStartTime - 5000) }
            val newBackup = java.io.File(tempDir, "backup_new").apply { writeText("new"); setLastModified(appStartTime + 5000) }

            val outputsDir = java.io.File(tempDir, "outputs").apply { mkdirs() }
            val maxAge = 86400000L // 24 hours
            val now = System.currentTimeMillis()
            val oldOutput = java.io.File(outputsDir, "output_old.mp4").apply { writeText("old"); setLastModified(now - maxAge - 5000) }
            val freshOutput = java.io.File(outputsDir, "output_fresh.mp4").apply { writeText("fresh"); setLastModified(now - 1000) }

            FileUtils.cleanStaleCacheDir(tempDir, maxAgeMs = maxAge, appStartTime = appStartTime)

            // Old scratch / inputs before app start must be deleted
            assertFalse(oldPdfbox.exists())
            assertFalse(oldInput.exists())
            assertFalse(oldDecrypted.exists())
            assertFalse(oldBackup.exists())

            // Scratch / inputs created during / after app start must NOT be deleted
            assertTrue(newPdfbox.exists())
            assertTrue(newInput.exists())
            assertTrue(newDecrypted.exists())
            assertTrue(newBackup.exists())

            // Output older than 24h must be deleted; fresh output kept
            assertFalse(oldOutput.exists())
            assertTrue(freshOutput.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun heicFormatMatching() {
        assertTrue(FileUtils.isImageFormatMatching("image/heic", "IMG_1.HEIC", ImageOutputFormat.HEIC))
        assertTrue(FileUtils.isImageFormatMatching("image/heif-sequence", "burst", ImageOutputFormat.HEIC))
        assertTrue(FileUtils.isImageFormatMatching(null, "a.heic", ImageOutputFormat.HEIC))
        assertTrue(FileUtils.isImageFormatMatching(null, "a.HEIF", ImageOutputFormat.HEIC))
        assertFalse(FileUtils.isImageFormatMatching("image/jpeg", "a.jpg", ImageOutputFormat.HEIC))
        assertFalse(FileUtils.isImageFormatMatching("image/avif", "a.avif", ImageOutputFormat.HEIC))
        assertFalse(FileUtils.isImageFormatMatching("image/heic", "a.heic", ImageOutputFormat.JPEG))
    }
}
