package com.omniforge.app.engine.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class UtilityEngineTest {

    @Test
    fun testDigestVerification() {
        val input = "Hello, Apparat!"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(sha256.isNotEmpty())
        assertEquals(64, sha256.length)

        val md5 = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(32, md5.length)

        val sha1 = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(40, sha1.length)

        val sha512 = MessageDigest.getInstance("SHA-512").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(128, sha512.length)
    }

    @Test
    fun testCaseConversion() {
        val original = "hello_world_test"
        assertEquals("HELLO_WORLD_TEST", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.UPPERCASE))
        assertEquals("hello_world_test", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.LOWERCASE))
        assertEquals("Hello World Test", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.TITLE_CASE))
        assertEquals("helloWorldTest", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.CAMEL_CASE))
        assertEquals("hello-world-test", UtilityEngine.convertCase(original, UtilityEngine.CaseMode.KEBAB_CASE))
    }

    @Test
    fun testStripDataUrlPrefix() {
        assertEquals("SGVsbG8=", UtilityEngine.stripDataUrlPrefix("data:image/png;base64,SGVsbG8="))
        assertEquals("SGVsbG8=", UtilityEngine.stripDataUrlPrefix("data:application/pdf;base64,SGVsbG8="))
        assertEquals("SGVsbG8=", UtilityEngine.stripDataUrlPrefix("  data:text/plain;base64, SGVsbG8=  "))
        assertEquals("SGVsbG8=", UtilityEngine.stripDataUrlPrefix("SGVsbG8="))
    }

    @Test
    fun testDetectBinaryType() {
        // PNG header: 89 50 4E 47 0D 0A 1A 0A
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            0x00, 0x00, 0x00, 0x0D
        )
        assertEquals(Pair("image/png", ".png"), UtilityEngine.detectBinaryType(pngBytes))

        // JPEG header: FF D8 FF E0 ...
        val jpgBytes = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        )
        assertEquals(Pair("image/jpeg", ".jpg"), UtilityEngine.detectBinaryType(jpgBytes))

        // WebP header: RIFF....WEBP
        val webpBytes = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
            0x24, 0x00, 0x00, 0x00,                                     // Size
            0x57.toByte(), 0x42.toByte(), 0x42.toByte(), 0x50.toByte()  // WEBP
        )
        assertEquals(Pair("image/webp", ".webp"), UtilityEngine.detectBinaryType(webpBytes))

        // WAV header: RIFF....WAVE (must NOT match WebP)
        val wavBytes = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
            0x24, 0x00, 0x00, 0x00,
            0x57.toByte(), 0x41.toByte(), 0x56.toByte(), 0x45.toByte()  // WAVE
        )
        assertEquals(null, UtilityEngine.detectBinaryType(wavBytes))

        // PDF header: %PDF-1.4
        val pdfBytes = "%PDF-1.4 sample document bytes".toByteArray(Charsets.UTF_8)
        assertEquals(Pair("application/pdf", ".pdf"), UtilityEngine.detectBinaryType(pdfBytes))

        // GIF header: GIF89a
        val gifBytes = "GIF89a sample bytes here".toByteArray(Charsets.UTF_8)
        assertEquals(Pair("image/gif", ".gif"), UtilityEngine.detectBinaryType(gifBytes))

        // Too short (<12 bytes)
        val shortBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        assertEquals(null, UtilityEngine.detectBinaryType(shortBytes))
    }

    @Test
    fun testWifiQrFormattingAndEscaping() {
        val escaped = UtilityEngine.escapeWifiField("My\\Home;Net:work,Test\"")
        // \ -> \\, ; -> \;, , -> \,, : -> \:, " -> \"
        assertEquals("My\\\\Home\\;Net\\:work\\,Test\\\"", escaped)

        val qrWpa = UtilityEngine.formatWifiQr("MyNet", "secret123", "WPA")
        assertEquals("WIFI:T:WPA;S:MyNet;P:secret123;;", qrWpa)

        val qrOpen = UtilityEngine.formatWifiQr("CoffeeShop", "", "nopass")
        assertEquals("WIFI:T:nopass;S:CoffeeShop;;", qrOpen)

        val qrHidden = UtilityEngine.formatWifiQr("HiddenNet", "pass", "WPA", hidden = true)
        assertEquals("WIFI:T:WPA;S:HiddenNet;P:pass;H:true;;", qrHidden)
    }

    @Test
    fun testUpiQrFormatting() {
        val qr = UtilityEngine.formatUpiQr("merchant@upi", "Ram & Sons", "250.50", "Lunch bill")
        assertTrue(qr.startsWith("upi://pay?"))
        assertTrue(qr.contains("pa=merchant%40upi"))
        assertTrue(qr.contains("pn=Ram%20%26%20Sons"))
        assertTrue(qr.contains("am=250.50"))
        assertTrue(qr.contains("cu=INR"))
        assertTrue(qr.contains("tn=Lunch%20bill"))
    }

    @Test
    fun testVCardQrFormatting() {
        val vcard = UtilityEngine.formatVCardQr("Jane Doe, Dr.", "+123456789", "jane@example.com")
        assertTrue(vcard.startsWith("BEGIN:VCARD"))
        assertTrue(vcard.contains("FN:Jane Doe\\, Dr."))
        assertTrue(vcard.contains("TEL;TYPE=CELL:+123456789"))
        assertTrue(vcard.contains("EMAIL;TYPE=INTERNET:jane@example.com"))
        assertTrue(vcard.endsWith("END:VCARD"))
    }
}

