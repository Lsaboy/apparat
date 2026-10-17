package com.vedant.apparat.engine.video

import android.graphics.Bitmap
import java.io.OutputStream

/**
 * Minimal animated GIF89a writer. Each frame gets its own 256-colour palette (median cut on a
 * 15-bit histogram) and is LZW-compressed. Loops forever.
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val frameDelayMs: Int
) {
    private var started = false
    private val pixels = IntArray(width * height)
    private val indices = ByteArray(width * height)

    fun addFrame(bitmap: Bitmap) {
        require(bitmap.width == width && bitmap.height == height) { "Frame size mismatch" }
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        addFrame(pixels)
    }

    /** [argb] holds width × height pixels, row by row. */
    fun addFrame(argb: IntArray) {
        require(argb.size == width * height) { "Frame size mismatch" }
        if (!started) writeHeader()
        val pixels = argb

        val palette = buildPalette(pixels)
        mapToPalette(pixels, palette, indices)

        // Graphic control extension: frame delay in 1/100 s.
        val delay = (frameDelayMs / 10).coerceAtLeast(2)
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0, (delay and 0xFF).toByte(), (delay shr 8).toByte(), 0, 0))
        // Image descriptor with a local colour table of 256 entries.
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0x87)
        val table = ByteArray(256 * 3)
        for (i in palette.indices) {
            table[i * 3] = (palette[i] shr 16 and 0xFF).toByte()
            table[i * 3 + 1] = (palette[i] shr 8 and 0xFF).toByte()
            table[i * 3 + 2] = (palette[i] and 0xFF).toByte()
        }
        out.write(table)
        lzwEncode(indices)
    }

    fun finish() {
        if (!started) writeHeader()
        out.write(0x3B)
        out.flush()
    }

    private fun writeHeader() {
        started = true
        out.write("GIF89a".toByteArray())
        writeShort(width); writeShort(height)
        out.write(byteArrayOf(0x70, 0, 0)) // no global colour table
        // NETSCAPE2.0 loop extension, loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 11))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write(v shr 8 and 0xFF)
    }

    // ------------------------------------------------------------ palette (median cut)

    private val histogram = IntArray(32768)

    private fun buildPalette(px: IntArray): IntArray {
        histogram.fill(0)
        for (c in px) histogram[rgb15(c)]++
        val colors = (0 until 32768).filter { histogram[it] > 0 }.toIntArray()

        val boxes = ArrayList<IntArray>()
        boxes += colors
        while (boxes.size < 256) {
            // Split the box with the largest channel range (weighted by pixel count).
            var best = -1
            var bestScore = 0L
            for ((i, box) in boxes.withIndex()) {
                if (box.size < 2) continue
                val score = channelRange(box).first.toLong() * box.sumOf { histogram[it].toLong() }
                if (score > bestScore) { bestScore = score; best = i }
            }
            if (best < 0) break
            val box = boxes.removeAt(best)
            val shift = channelRange(box).second
            val sorted = box.sortedBy { (it shr shift) and 31 }
            // Split at the weighted median.
            val total = sorted.sumOf { histogram[it].toLong() }
            var acc = 0L
            var cut = 1
            for ((i, c) in sorted.withIndex()) {
                acc += histogram[c]
                if (acc * 2 >= total) { cut = (i + 1).coerceIn(1, sorted.size - 1); break }
            }
            boxes += sorted.subList(0, cut).toIntArray()
            boxes += sorted.subList(cut, sorted.size).toIntArray()
        }
        return IntArray(boxes.size) { averageColor(boxes[it]) }
    }

    /** Returns (range, bit shift of that channel in the 15-bit value). */
    private fun channelRange(box: IntArray): Pair<Int, Int> {
        var rMin = 31; var rMax = 0; var gMin = 31; var gMax = 0; var bMin = 31; var bMax = 0
        for (c in box) {
            val r = c shr 10 and 31; val g = c shr 5 and 31; val b = c and 31
            if (r < rMin) rMin = r; if (r > rMax) rMax = r
            if (g < gMin) gMin = g; if (g > gMax) gMax = g
            if (b < bMin) bMin = b; if (b > bMax) bMax = b
        }
        val rr = rMax - rMin; val gr = gMax - gMin; val br = bMax - bMin
        return when {
            gr >= rr && gr >= br -> gr to 5
            rr >= br -> rr to 10
            else -> br to 0
        }
    }

    private fun averageColor(box: IntArray): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (c in box) {
            val w = histogram[c].toLong()
            r += (c shr 10 and 31) * w; g += (c shr 5 and 31) * w; b += (c and 31) * w; n += w
        }
        if (n == 0L) return 0
        fun expand(v: Long) = ((v * 255) / (n * 31)).toInt().coerceIn(0, 255)
        return (expand(r) shl 16) or (expand(g) shl 8) or expand(b)
    }

    private val lookup = IntArray(32768)

    private fun mapToPalette(px: IntArray, palette: IntArray, dst: ByteArray) {
        lookup.fill(-1)
        for (i in px.indices) {
            val key = rgb15(px[i])
            var idx = lookup[key]
            if (idx < 0) {
                idx = nearest(key, palette)
                lookup[key] = idx
            }
            dst[i] = idx.toByte()
        }
    }

    private fun nearest(key: Int, palette: IntArray): Int {
        val r = (key shr 10 and 31) * 255 / 31
        val g = (key shr 5 and 31) * 255 / 31
        val b = (key and 31) * 255 / 31
        var best = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val p = palette[i]
            val dr = (p shr 16 and 0xFF) - r
            val dg = (p shr 8 and 0xFF) - g
            val db = (p and 0xFF) - b
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    private fun rgb15(c: Int): Int = ((c shr 19 and 31) shl 10) or ((c shr 11 and 31) shl 5) or (c shr 3 and 31)

    // ------------------------------------------------------------ LZW

    private fun lzwEncode(data: ByteArray) {
        val minCodeSize = 8
        out.write(minCodeSize)
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1

        val block = ByteArray(255)
        var blockLen = 0
        var bitBuffer = 0
        var bitCount = 0
        var codeSize = minCodeSize + 1

        fun emit(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                block[blockLen++] = (bitBuffer and 0xFF).toByte()
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
                if (blockLen == 255) { out.write(255); out.write(block, 0, 255); blockLen = 0 }
            }
        }

        // Dictionary keyed by (prefixCode shl 8 | nextByte).
        val dict = HashMap<Int, Int>(8192)
        var nextCode = eoiCode + 1
        emit(clearCode)
        var prefix = data[0].toInt() and 0xFF
        for (i in 1 until data.size) {
            val k = data[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            emit(prefix)
            if (nextCode < 4096) {
                dict[key] = nextCode++
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clearCode)
                dict.clear()
                nextCode = eoiCode + 1
                codeSize = minCodeSize + 1
            }
            prefix = k
        }
        emit(prefix)
        emit(eoiCode)
        if (bitCount > 0) {
            block[blockLen++] = (bitBuffer and 0xFF).toByte()
            if (blockLen == 255) { out.write(255); out.write(block, 0, 255); blockLen = 0 }
        }
        if (blockLen > 0) { out.write(blockLen); out.write(block, 0, blockLen) }
        out.write(0) // block terminator
    }
}
