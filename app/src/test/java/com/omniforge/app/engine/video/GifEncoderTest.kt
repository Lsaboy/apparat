package com.omniforge.app.engine.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

class GifEncoderTest {

    /** Small spec-following GIF decoder: returns each frame as RGB ints. */
    private fun decode(bytes: ByteArray): List<IntArray> {
        var pos = 0
        fun u8() = bytes[pos++].toInt() and 0xFF
        fun u16() = u8() or (u8() shl 8)
        assertEquals("GIF89a", String(bytes, 0, 6)); pos = 6
        val w = u16(); val h = u16()
        val flags = u8(); u8(); u8()
        if (flags and 0x80 != 0) pos += 3 * (2 shl (flags and 7))
        val frames = mutableListOf<IntArray>()
        while (true) {
            when (u8()) {
                0x21 -> { u8(); while (true) { val n = u8(); if (n == 0) break; pos += n } }
                0x2C -> {
                    u16(); u16(); assertEquals(w, u16()); assertEquals(h, u16())
                    val f = u8()
                    val table = IntArray(2 shl (f and 7)) { (u8() shl 16) or (u8() shl 8) or u8() }
                    val minCode = u8()
                    val data = ByteArrayOutputStream()
                    while (true) { val n = u8(); if (n == 0) break; data.write(bytes, pos, n); pos += n }
                    frames += lzwDecode(data.toByteArray(), minCode, w * h).map { table[it] }.toIntArray()
                }
                0x3B -> return frames
                else -> error("bad block at $pos")
            }
        }
    }

    private fun lzwDecode(data: ByteArray, minCode: Int, count: Int): IntArray {
        val clear = 1 shl minCode; val eoi = clear + 1
        val out = IntArray(count); var outPos = 0
        val prefix = IntArray(4096); val suffix = IntArray(4096); val len = IntArray(4096)
        var codeSize = minCode + 1; var next = eoi + 1; var prev = -1
        var bitPos = 0
        fun read(): Int {
            var v = 0
            for (i in 0 until codeSize) {
                val b = (data[(bitPos + i) / 8].toInt() shr ((bitPos + i) % 8)) and 1
                v = v or (b shl i)
            }
            bitPos += codeSize; return v
        }
        fun first(c: Int): Int { var x = c; while (x >= clear + 2) x = prefix[x]; return x }
        fun emit(c: Int) {
            val l = if (c < clear) 1 else len[c]
            var x = c
            for (i in l - 1 downTo 0) {
                out[outPos + i] = if (x < clear) x else suffix[x]
                if (x >= clear) x = prefix[x]
            }
            outPos += l
        }
        while (true) {
            val code = read()
            if (code == clear) { codeSize = minCode + 1; next = eoi + 1; prev = -1; continue }
            if (code == eoi) break
            if (prev == -1) { emit(code); prev = code; continue }
            val k = if (code < next) first(code) else first(prev)
            if (next < 4096) {
                prefix[next] = prev; suffix[next] = k
                len[next] = (if (prev < clear) 1 else len[prev]) + 1
                next++
                if (next == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            emit(code); prev = code
        }
        assertEquals(count, outPos)
        return out
    }

    @Test
    fun roundTripsGradientNoiseAndFlatFrames() {
        val w = 160
        val h = 90
        val frames = listOf(
            IntArray(w * h) { i -> val x = i % w; val y = i / w; (0xFF shl 24) or ((x * 255 / w) shl 16) or ((y * 255 / h) shl 8) or 128 },
            Random(1).let { r -> IntArray(w * h) { (0xFF shl 24) or r.nextInt(0xFFFFFF) } }, // forces dictionary resets
            IntArray(w * h) { 0xFF336699.toInt() }
        )
        val out = ByteArrayOutputStream()
        val enc = GifEncoder(out, w, h, 100)
        frames.forEach { enc.addFrame(it) }
        enc.finish()
        System.getenv("GIF_TEST_OUT")?.let { File(it).writeBytes(out.toByteArray()) }

        val decoded = decode(out.toByteArray())
        assertEquals(3, decoded.size)
        for (i in 0 until w * h) {
            for (shift in intArrayOf(16, 8, 0)) {
                val a = frames[0][i] shr shift and 0xFF
                val b = decoded[0][i] shr shift and 0xFF
                assertTrue("pixel $i", abs(a - b) <= 24)
            }
        }
        // Noise frame: palette can't be exact, but the average error must stay modest.
        val err = (0 until w * h).sumOf { i -> abs((frames[1][i] shr 8 and 0xFF) - (decoded[1][i] shr 8 and 0xFF)) } / (w * h)
        assertTrue("noise error $err", err < 40)
        assertTrue(decoded[2].all { abs((it and 0xFF) - 0x99) <= 8 && abs((it shr 16 and 0xFF) - 0x33) <= 8 })
    }
}
