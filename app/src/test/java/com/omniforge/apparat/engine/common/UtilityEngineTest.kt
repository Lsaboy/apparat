package com.omniforge.apparat.engine.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(qr.contains("pa=merchant@upi"))
        assertTrue(qr.contains("pn=Ram%20%26%20Sons"))
        assertTrue(qr.contains("am=250.50"))
        assertTrue(qr.contains("cu=INR"))
        assertTrue(qr.contains("tn=Lunch%20bill"))

        // Amount validation: valid
        assertTrue(UtilityEngine.isValidUpiAmount("500"))
        assertTrue(UtilityEngine.isValidUpiAmount("150.50"))
        assertTrue(UtilityEngine.isValidUpiAmount("10.5"))

        // Amount validation: invalid should be rejected / omitted
        assertFalse(UtilityEngine.isValidUpiAmount("1,000"))
        assertFalse(UtilityEngine.isValidUpiAmount("-50"))
        assertFalse(UtilityEngine.isValidUpiAmount("0"))
        assertFalse(UtilityEngine.isValidUpiAmount("abc"))

        // A typed but invalid amount gives no QR at all (never a QR with a payer-chosen amount)
        assertEquals("", UtilityEngine.formatUpiQr("merchant@upi", "Ram & Sons", "1,000", "Lunch bill"))
        // A malformed UPI ID gives no QR
        assertEquals("", UtilityEngine.formatUpiQr("merchantupi", "Ram", "100", ""))
        assertTrue(UtilityEngine.isValidUpiId("ram.sons-1@okhdfcbank"))
        assertFalse(UtilityEngine.isValidUpiId("ram@"))
        assertFalse(UtilityEngine.isValidUpiId("ram@ok bank"))
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

    @Test
    fun testCalculateTextChecksums() {
        val input = "The quick brown fox jumps over the lazy dog"
        val checksums = UtilityEngine.calculateTextChecksums(input)

        assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", checksums["SHA-256"])
        assertEquals("9e107d9d372bb6826bd81d3542a419d6", checksums["MD5"])
        assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", checksums["SHA-1"])
        assertEquals(
            "07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6",
            checksums["SHA-512"]
        )

        // Empty string test vector
        val emptyChecksums = UtilityEngine.calculateTextChecksums("")
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyChecksums["SHA-256"])
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", emptyChecksums["MD5"])
    }

    @Test
    fun `expected hashes are found in common pasted formats`() {
        val h = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(h, UtilityEngine.normalizeExpectedHash(h.uppercase()))
        assertEquals(h, UtilityEngine.normalizeExpectedHash("$h  hello.txt"))
        assertEquals(h, UtilityEngine.normalizeExpectedHash("SHA256: $h"))
        assertEquals(h, UtilityEngine.normalizeExpectedHash(h.chunked(2).joinToString(":")))
        assertEquals(h, UtilityEngine.normalizeExpectedHash(h.chunked(8).joinToString(" ")))
        assertEquals("not a hash", UtilityEngine.normalizeExpectedHash("  not a hash "))
    }
}
