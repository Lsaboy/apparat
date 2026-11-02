package com.omniforge.apparat.desktop.platform

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lossless JPEG metadata surgery: drops EXIF/XMP/IPTC/comment segments without touching the
 * compressed image data, and can write a minimal EXIF block back (orientation and a few
 * camera fields).
 */
object JpegExif {

    data class ExifFields(
        val orientation: Int? = null,
        val make: String? = null,
        val model: String? = null,
        val dateTime: String? = null,
        val whiteBalance: Int? = null,
        val flash: Int? = null,
        /** Focal length as numerator/denominator. */
        val focalLength: Pair<Long, Long>? = null
    ) {
        val isEmpty get() = orientation == null && make == null && model == null && dateTime == null &&
            whiteBalance == null && flash == null && focalLength == null
    }

    fun isJpeg(bytes: ByteArray): Boolean = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

    /**
     * Rewrites [jpeg] without APP1 (EXIF/XMP), APP12/APP13 (IPTC, Photoshop) and COM segments,
     * keeping JFIF, ICC colour profiles (APP2) and Adobe (APP14). If [keep] has fields, a fresh
     * minimal EXIF segment with just those is inserted. Throws if the file isn't a well-formed JPEG.
     */
    fun rewrite(jpeg: ByteArray, keep: ExifFields = ExifFields()): ByteArray {
        require(isJpeg(jpeg)) { "Not a JPEG file" }
        val out = ByteArrayOutputStream(jpeg.size)
        out.write(0xFF); out.write(0xD8)
        var inserted = false
        fun insertExif() {
            if (inserted) return
            inserted = true
            if (!keep.isEmpty) writeSegment(out, 0xE1, buildExif(keep))
        }
        var i = 2
        while (i < jpeg.size) {
            require(jpeg[i] == 0xFF.toByte()) { "Malformed JPEG (segment marker expected at $i)" }
            var marker = jpeg[i + 1].toInt() and 0xFF
            while (marker == 0xFF) { i++; marker = jpeg[i + 1].toInt() and 0xFF } // fill bytes
            if (marker == 0xD9) { out.write(jpeg, i, 2); return out.toByteArray() }
            if (marker == 0xDA) {
                // Start of scan: everything from here (entropy data + rest) is copied verbatim.
                insertExif()
                out.write(jpeg, i, jpeg.size - i)
                return out.toByteArray()
            }
            if (marker in 0xD0..0xD7 || marker == 0x01) { out.write(jpeg, i, 2); i += 2; continue }
            val len = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            require(len >= 2 && i + 2 + len <= jpeg.size) { "Malformed JPEG segment" }
            val drop = marker == 0xE1 || marker == 0xEC || marker == 0xED || marker == 0xFE
            val isJfif = marker == 0xE0
            if (!isJfif) insertExif() // EXIF goes right after SOI/JFIF
            if (!drop) out.write(jpeg, i, 2 + len)
            i += 2 + len
        }
        throw IllegalArgumentException("Malformed JPEG (no image data)")
    }

    private fun writeSegment(out: ByteArrayOutputStream, marker: Int, payload: ByteArray) {
        val len = payload.size + 2
        require(len <= 0xFFFF) { "EXIF segment too large" }
        out.write(0xFF); out.write(marker); out.write(len shr 8); out.write(len and 0xFF)
        out.write(payload)
    }

    private class Entry(val tag: Int, val type: Int, val count: Int, val data: ByteArray)

    private fun ascii(tag: Int, s: String): Entry {
        val b = (s + "\u0000").toByteArray(Charsets.ISO_8859_1)
        return Entry(tag, 2, b.size, b)
    }

    private fun short(tag: Int, v: Int) = Entry(tag, 3, 1, byteArrayOf((v shr 8).toByte(), v.toByte()))
    private fun long(tag: Int, v: Long) = Entry(tag, 4, 1, ByteBuffer.allocate(4).putInt(v.toInt()).array())
    private fun rational(tag: Int, n: Long, d: Long) = Entry(tag, 5, 1, ByteBuffer.allocate(8).putInt(n.toInt()).putInt(d.toInt()).array())

    /** "Exif\0\0" + big-endian TIFF with IFD0 (+ Exif sub-IFD when needed). */
    internal fun buildExif(f: ExifFields): ByteArray {
        val ifd0 = mutableListOf<Entry>()
        f.make?.let { ifd0 += ascii(0x010F, it) }
        f.model?.let { ifd0 += ascii(0x0110, it) }
        f.orientation?.let { ifd0 += short(0x0112, it) }
        f.dateTime?.let { ifd0 += ascii(0x0132, it) }
        val sub = mutableListOf<Entry>()
        f.flash?.let { sub += short(0x9209, it) }
        f.focalLength?.let { sub += rational(0x920A, it.first, it.second) }
        f.whiteBalance?.let { sub += short(0xA403, it) }

        fun ifdSize(n: Int, entries: List<Entry>) = 2 + n * 12 + 4 + entries.sumOf { if (it.data.size > 4) it.data.size + (it.data.size and 1) else 0 }
        val n0 = ifd0.size + if (sub.isNotEmpty()) 1 else 0
        val ifd0Offset = 8
        val subOffset = ifd0Offset + ifdSize(n0, ifd0)
        if (sub.isNotEmpty()) ifd0 += long(0x8769, subOffset.toLong())
        ifd0.sortBy { it.tag }
        sub.sortBy { it.tag }
        val total = subOffset + if (sub.isNotEmpty()) ifdSize(sub.size, sub) else 0
        val buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        buf.put('M'.code.toByte()); buf.put('M'.code.toByte()); buf.putShort(42); buf.putInt(ifd0Offset)

        fun writeIfd(start: Int, entries: List<Entry>) {
            var dataOffset = start + 2 + entries.size * 12 + 4
            buf.position(start)
            buf.putShort(entries.size.toShort())
            val blobs = mutableListOf<Pair<Int, ByteArray>>()
            for (e in entries) {
                buf.putShort(e.tag.toShort()); buf.putShort(e.type.toShort()); buf.putInt(e.count)
                if (e.data.size <= 4) {
                    val padded = e.data.copyOf(4)
                    buf.put(padded)
                } else {
                    buf.putInt(dataOffset)
                    blobs += dataOffset to e.data
                    dataOffset += e.data.size + (e.data.size and 1)
                }
            }
            buf.putInt(0) // no next IFD
            for ((off, data) in blobs) { buf.position(off); buf.put(data) }
        }
        writeIfd(ifd0Offset, ifd0)
        if (sub.isNotEmpty()) writeIfd(subOffset, sub)
        return "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + buf.array()
    }
}
