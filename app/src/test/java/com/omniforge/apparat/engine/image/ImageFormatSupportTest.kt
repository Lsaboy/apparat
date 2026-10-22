package com.omniforge.apparat.engine.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFormatSupportTest {

    @Test
    fun heicHiddenWhenPhoneCannotEncodeIt() {
        val formats = ImageOutputFormat.availableFor(canEncodeHeic = false)
        assertFalse(ImageOutputFormat.HEIC in formats)
        assertEquals(listOf(ImageOutputFormat.JPEG, ImageOutputFormat.PNG, ImageOutputFormat.WEBP), formats)
    }

    @Test
    fun heicOfferedWhenPhoneCanEncodeIt() {
        assertTrue(ImageOutputFormat.HEIC in ImageOutputFormat.availableFor(canEncodeHeic = true))
    }

    @Test
    fun fromExtensionMapsPickerLabels() {
        assertEquals(ImageOutputFormat.JPEG, ImageOutputFormat.fromExtension("jpg"))
        assertEquals(ImageOutputFormat.PNG, ImageOutputFormat.fromExtension("PNG"))
        assertEquals(ImageOutputFormat.WEBP, ImageOutputFormat.fromExtension(".webp"))
        assertEquals(ImageOutputFormat.HEIC, ImageOutputFormat.fromExtension("heic"))
        assertEquals(ImageOutputFormat.JPEG, ImageOutputFormat.fromExtension("xyz"))
    }

    @Test
    fun detectsAvifByMimeOrName() {
        assertTrue(BitmapDecoder.isAvifType("image/avif", null))
        assertTrue(BitmapDecoder.isAvifType("IMAGE/AVIF-SEQUENCE", "x"))
        assertTrue(BitmapDecoder.isAvifType(null, "photo.AVIF"))
        assertFalse(BitmapDecoder.isAvifType("image/heic", "photo.heic"))
        assertFalse(BitmapDecoder.isAvifType(null, "avif"))
        assertFalse(BitmapDecoder.isAvifType(null, null))
    }
}
