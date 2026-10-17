package com.omniforge.app.feature.home

import com.omniforge.app.core.model.ALL_TOOLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSearchTest {

    private fun searchTools(query: String) = ALL_TOOLS.filter { tool ->
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        tokens.isEmpty() || tokens.all { token ->
            tool.title.contains(token, ignoreCase = true) ||
            tool.description.contains(token, ignoreCase = true) ||
            tool.keywords.any { it.contains(token, ignoreCase = true) }
        }
    }

    @Test
    fun testAllToolsCount() {
        assertEquals(21, ALL_TOOLS.size)
    }

    @Test
    fun testMultiWordSearchMatchesKeywords() {
        val results = searchTools("merge pdf")
        assertTrue(results.any { it.id == "images_to_pdf" })
    }

    @Test
    fun testTrailingSpaceSearch() {
        val results = searchTools("jpg ")
        assertTrue(results.any { it.id == "image_convert" })
        assertTrue(results.any { it.id == "image_shrink" })
        assertTrue(results.any { it.id == "images_to_pdf" })
    }

    @Test
    fun testVerbSearch() {
        val resizeResults = searchTools("resize")
        assertTrue(resizeResults.any { it.id == "image_shrink" })
        assertTrue(resizeResults.any { it.id == "video_shrink" })

        val cutResults = searchTools("cut")
        assertTrue(cutResults.any { it.id == "video_trim" })
        assertTrue(cutResults.any { it.id == "pdf_split" })
    }

    @Test
    fun testEmptySearchMatchesAll() {
        assertEquals(21, searchTools("").size)
        assertEquals(21, searchTools("   ").size)
    }
}
