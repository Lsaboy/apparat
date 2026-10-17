package com.vedant.apparat.engine.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

class TargetKbSearchEngineTest {

    /**
     * Simulated realistic JPEG compression model:
     * sizeBytes ≈ (width * height * (0.05 + 0.35 * (quality / 100)^2))
     */
    private fun simulateJpegCompression(width: Int, height: Int, quality: Int): Long {
        val qFactor = quality / 100.0
        val bytesPerPixel = 0.05 + 0.35 * (qFactor * qFactor)
        return (width.toLong() * height.toLong() * bytesPerPixel).roundToLong()
    }

    @Test
    fun testAlreadyUnderBudgetKeepsMaxQualityAndFullDimensions() {
        val config = TargetKbSearchEngine.SearchConfig(
            targetKb = 200, // 200 KB budget
            initialWidth = 800,
            initialHeight = 600
        )
        // At 800x600, q92 ≈ 800*600*(0.05 + 0.35*0.846) = 480000 * 0.346 = ~166 KB <= 200 KB
        val result = TargetKbSearchEngine.findOptimalParameters(config) { w, h, q ->
            simulateJpegCompression(w, h, q)
        }

        assertTrue(result.reachedTarget)
        assertEquals(800, result.width)
        assertEquals(600, result.height)
        assertEquals(TargetKbSearchEngine.DEFAULT_MAX_QUALITY, result.quality)
        assertEquals(1, result.iterations) // Exits on step 1
        assertTrue(result.finalSizeBytes <= 200L * 1024L)
    }

    @Test
    fun testQualityFirstPreservesFullResolutionWhenPossible() {
        val config = TargetKbSearchEngine.SearchConfig(
            targetKb = 100, // 100 KB budget
            initialWidth = 1200,
            initialHeight = 800
        )
        // At 1200x800:
        // q92 ≈ 960000 * 0.346 = ~332 KB (> 100 KB)
        // q30 ≈ 960000 * (0.05 + 0.35 * 0.09) = 960000 * 0.0815 = ~78 KB (<= 100 KB!)
        // Because q30 fits in 100 KB, the algorithm MUST NOT downscale dimensions!
        val result = TargetKbSearchEngine.findOptimalParameters(config) { w, h, q ->
            simulateJpegCompression(w, h, q)
        }

        assertTrue("Must reach target", result.reachedTarget)
        assertEquals("Width must remain 100% untouched", 1200, result.width)
        assertEquals("Height must remain 100% untouched", 800, result.height)
        assertTrue("Quality must be >= 30", result.quality >= 30)
        assertTrue("Final size must be <= 100 KB", result.finalSizeBytes <= 100L * 1024L)
        assertTrue("Iterations must be <= 12", result.iterations <= 12)
    }

    @Test
    fun testDimensionDownscalingWhenQualityFloorExceedsBudget() {
        val config = TargetKbSearchEngine.SearchConfig(
            targetKb = 50, // Strict 50 KB budget (typical government portal limit)
            initialWidth = 4000,
            initialHeight = 3000
        )
        // At 4000x3000 (12 MP):
        // Even at q30: 12000000 * 0.0815 = ~978 KB, which is 20x larger than 50 KB!
        // The algorithm MUST downscale dimensions.
        val result = TargetKbSearchEngine.findOptimalParameters(config) { w, h, q ->
            simulateJpegCompression(w, h, q)
        }

        assertTrue("Must reach target", result.reachedTarget)
        assertTrue("Width must be scaled down", result.width < 4000)
        assertTrue("Height must be scaled down", result.height < 3000)
        assertTrue("Final size must strictly be <= 50 KB", result.finalSizeBytes <= 50L * 1024L)
        assertTrue("Must converge in reasonable iterations", result.iterations <= TargetKbSearchEngine.MAX_SEARCH_ITERATIONS)
    }

    @Test
    fun testMinimumDimensionFloorClamping() {
        val config = TargetKbSearchEngine.SearchConfig(
            targetKb = 1, // Impossibly small 1 KB
            initialWidth = 1000,
            initialHeight = 1000,
            minDimension = 100
        )

        val result = TargetKbSearchEngine.findOptimalParameters(config) { w, h, q ->
            simulateJpegCompression(w, h, q)
        }

        // Must not drop below minDimension
        assertTrue(result.width >= 100)
        assertTrue(result.height >= 100)
        assertTrue(result.iterations <= TargetKbSearchEngine.MAX_SEARCH_ITERATIONS)
    }

    @Test
    fun testAspectRatiosPreservedDuringDownscaling() {
        val config = TargetKbSearchEngine.SearchConfig(
            targetKb = 30,
            initialWidth = 3000,
            initialHeight = 1500 // 2:1 aspect ratio
        )

        val result = TargetKbSearchEngine.findOptimalParameters(config) { w, h, q ->
            simulateJpegCompression(w, h, q)
        }

        assertTrue(result.reachedTarget)
        val initialRatio = 3000.0 / 1500.0
        val finalRatio = result.width.toDouble() / result.height.toDouble()
        assertEquals(initialRatio, finalRatio, 0.05) // Aspect ratio preserved within rounding tolerance
    }
}
