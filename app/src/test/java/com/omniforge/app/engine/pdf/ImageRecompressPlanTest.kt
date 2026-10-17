package com.omniforge.app.engine.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageRecompressPlanTest {

    private val a4W = 595f
    private val a4H = 842f

    private fun plan(
        w: Int, h: Int, bpc: Int = 8, stencil: Boolean = false, mask: Boolean = false
    ) = planImageRecompression(w, h, bpc, stencil, mask, a4W, a4H)

    @Test
    fun `600 dpi A4 scan is fitted to about 150 dpi`() {
        val p = assertNotNullPlan(plan(4960, 7016))
        // 150 DPI on A4 is ~1240 x 1754
        assertTrue(p.targetWidth in 1230..1245)
        assertTrue(p.targetHeight in 1745..1760)
        // decoded at a quarter of the size, never smaller than the target
        assertEquals(4, p.subsampling)
    }

    @Test
    fun `small images are never upscaled`() {
        val p = assertNotNullPlan(plan(800, 600))
        assertEquals(800, p.targetWidth)
        assertEquals(600, p.targetHeight)
        assertEquals(1, p.subsampling)
    }

    @Test
    fun `aspect ratio is kept`() {
        val p = assertNotNullPlan(plan(6000, 3000))
        val ratio = p.targetWidth.toFloat() / p.targetHeight
        assertEquals(2f, ratio, 0.01f)
    }

    @Test
    fun `one-bit scans are left alone`() {
        assertNull(plan(4960, 7016, bpc = 1))
    }

    @Test
    fun `stencil and masked images are left alone`() {
        assertNull(plan(2000, 2000, stencil = true))
        assertNull(plan(2000, 2000, mask = true))
    }

    @Test
    fun `tiny images and bad sizes are left alone`() {
        assertNull(plan(64, 64))
        assertNull(plan(0, 100))
    }

    @Test
    fun `huge images are subsampled under the memory budget`() {
        // 20000 x 20000 = 400 MP; target ~1240 x 1240 at 150 DPI; decode must stay <= 16 MP
        val p = assertNotNullPlan(plan(20000, 20000))
        val decoded = (20000L / p.subsampling) * (20000L / p.subsampling)
        assertTrue("decoded $decoded px", decoded <= 16_000_000L)
        assertTrue(p.targetWidth <= 20000 / p.subsampling + 1)
    }

    private fun assertNotNullPlan(p: ImageRecompressPlan?): ImageRecompressPlan {
        assertNotNull(p)
        return p!!
    }
}
