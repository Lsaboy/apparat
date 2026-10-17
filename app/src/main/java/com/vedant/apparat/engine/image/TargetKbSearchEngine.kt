package com.vedant.apparat.engine.image

import kotlin.math.sqrt

/**
 * Pure Kotlin search algorithm for compressing images to strictly <= target KB.
 *
 * Resolves Audit Issue B6:
 * Strategy: "Quality first, then size"
 * 1. Explores JPEG quality range [minQuality..maxQuality] at current dimensions.
 * 2. If quality 30 can meet targetKb, binary searches for the maximum possible quality.
 * 3. Only downscales image dimensions if quality=30 still exceeds targetKb.
 * 4. Area-based dimensional scaling converges in minimal iterations (typically 3-6 total).
 */
object TargetKbSearchEngine {

    const val DEFAULT_MIN_QUALITY = 30
    const val DEFAULT_MAX_QUALITY = 92
    const val DEFAULT_MIN_DIMENSION = 100
    const val MAX_SEARCH_ITERATIONS = 12

    data class SearchConfig(
        val targetKb: Int,
        val initialWidth: Int,
        val initialHeight: Int,
        val minQuality: Int = DEFAULT_MIN_QUALITY,
        val maxQuality: Int = DEFAULT_MAX_QUALITY,
        val minDimension: Int = DEFAULT_MIN_DIMENSION
    )

    data class SearchResult(
        val width: Int,
        val height: Int,
        val quality: Int,
        val finalSizeBytes: Long,
        val iterations: Int,
        val reachedTarget: Boolean
    )

    /**
     * Executes the quality-first search using a provided measurement lambda.
     * [measureSize] takes (width, height, quality) and returns the resulting byte size.
     */
    fun findOptimalParameters(
        config: SearchConfig,
        measureSize: (width: Int, height: Int, quality: Int) -> Long
    ): SearchResult {
        val targetBytes = config.targetKb.toLong() * 1024L
        require(targetBytes > 0) { "targetKb must be positive" }

        var curWidth = config.initialWidth
        var curHeight = config.initialHeight
        var iterations = 0

        var bestWidth = curWidth
        var bestHeight = curHeight
        var bestQuality = config.minQuality
        var bestSizeBytes = Long.MAX_VALUE

        while (iterations < MAX_SEARCH_ITERATIONS) {
            // Step 1: Check size at maxQuality
            iterations++
            val maxQSize = measureSize(curWidth, curHeight, config.maxQuality)
            if (maxQSize <= targetBytes) {
                return SearchResult(
                    width = curWidth,
                    height = curHeight,
                    quality = config.maxQuality,
                    finalSizeBytes = maxQSize,
                    iterations = iterations,
                    reachedTarget = true
                )
            }

            // Step 2: Check size at minQuality
            iterations++
            val minQSize = measureSize(curWidth, curHeight, config.minQuality)
            if (minQSize <= targetBytes) {
                // Target is reachable at current dimensions! Binary search for highest quality in [minQuality, maxQuality]
                if (minQSize < bestSizeBytes) {
                    bestSizeBytes = minQSize
                    bestQuality = config.minQuality
                    bestWidth = curWidth
                    bestHeight = curHeight
                }

                var low = config.minQuality
                var high = config.maxQuality - 1
                var optimalQ = config.minQuality
                var optimalSize = minQSize

                while (low <= high && iterations < MAX_SEARCH_ITERATIONS) {
                    val mid = (low + high) / 2
                    iterations++
                    val midSize = measureSize(curWidth, curHeight, mid)
                    if (midSize <= targetBytes) {
                        optimalQ = mid
                        optimalSize = midSize
                        low = mid + 1 // Try higher quality
                    } else {
                        high = mid - 1 // Reduce quality
                    }
                }

                return SearchResult(
                    width = curWidth,
                    height = curHeight,
                    quality = optimalQ,
                    finalSizeBytes = optimalSize,
                    iterations = iterations,
                    reachedTarget = true
                )
            }

            // If we reached here, even minQuality exceeds targetBytes!
            // Update best fallback result seen so far
            if (minQSize < bestSizeBytes) {
                bestSizeBytes = minQSize
                bestQuality = config.minQuality
                bestWidth = curWidth
                bestHeight = curHeight
            }

            // Step 3: Quality is exhausted at 30. We must downscale dimensions.
            val ratio = targetBytes.toDouble() / minQSize.toDouble()
            // Estimate scale from area ratio (with 5% safety margin), bounded between 25% and 85%
            val areaScale = (sqrt(ratio) * 0.95).coerceIn(0.25, 0.85)

            // Scale both sides by the same factor (keeps the aspect ratio of narrow images such as
            // signature strips), but never below minDimension on the shorter side.
            val shortSide = minOf(curWidth, curHeight)
            val scale = maxOf(areaScale, config.minDimension.toDouble() / shortSide)
            if (scale >= 1.0) break // already at the minimum size
            val nextWidth = maxOf(1, (curWidth * scale).toInt())
            val nextHeight = maxOf(1, (curHeight * scale).toInt())
            if (nextWidth >= curWidth && nextHeight >= curHeight) break

            curWidth = nextWidth
            curHeight = nextHeight
        }

        return SearchResult(
            width = bestWidth,
            height = bestHeight,
            quality = bestQuality,
            finalSizeBytes = bestSizeBytes,
            iterations = iterations,
            reachedTarget = bestSizeBytes <= targetBytes
        )
    }
}
