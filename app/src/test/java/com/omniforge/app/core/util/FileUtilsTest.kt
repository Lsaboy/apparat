package com.omniforge.app.core.util

import com.omniforge.app.engine.image.ImageOutputFormat
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
}
