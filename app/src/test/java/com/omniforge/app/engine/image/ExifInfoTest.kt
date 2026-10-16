package com.omniforge.app.engine.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifInfoTest {

    @Test
    fun hasAnyMetadata_returnsFalseWhenAllFieldsNullOrBlank() {
        val emptyInfo = ExifInfo()
        assertFalse(emptyInfo.hasAnyMetadata())

        val blankInfo = ExifInfo(
            cameraMake = "",
            cameraModel = "   ",
            dateTime = "  ",
            iso = "",
            fNumber = null,
            exposureTime = "",
            latitude = null,
            longitude = null
        )
        assertFalse(blankInfo.hasAnyMetadata())
    }

    @Test
    fun hasAnyMetadata_returnsTrueWhenCameraMakePresent() {
        val info = ExifInfo(cameraMake = "Google")
        assertTrue(info.hasAnyMetadata())
    }

    @Test
    fun hasAnyMetadata_returnsTrueWhenCameraModelPresent() {
        val info = ExifInfo(cameraModel = "Pixel 8 Pro")
        assertTrue(info.hasAnyMetadata())
    }

    @Test
    fun hasAnyMetadata_returnsTrueWhenDateTimePresent() {
        val info = ExifInfo(dateTime = "2026:10:17 12:00:00")
        assertTrue(info.hasAnyMetadata())
    }

    @Test
    fun hasAnyMetadata_returnsTrueWhenExposureDetailsPresent() {
        assertTrue(ExifInfo(iso = "100").hasAnyMetadata())
        assertTrue(ExifInfo(fNumber = "1.8").hasAnyMetadata())
        assertTrue(ExifInfo(exposureTime = "1/250").hasAnyMetadata())
    }

    @Test
    fun hasAnyMetadata_requiresBothLatAndLong() {
        val latOnly = ExifInfo(latitude = 37.7749, longitude = null)
        assertFalse(latOnly.hasAnyMetadata())

        val longOnly = ExifInfo(latitude = null, longitude = -122.4194)
        assertFalse(longOnly.hasAnyMetadata())

        val bothGps = ExifInfo(latitude = 37.7749, longitude = -122.4194)
        assertTrue(bothGps.hasAnyMetadata())
    }
}
