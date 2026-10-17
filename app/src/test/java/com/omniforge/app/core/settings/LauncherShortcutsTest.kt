package com.omniforge.app.core.settings

import com.omniforge.app.core.model.ALL_TOOLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShortcutsTest {

    @Test
    fun testDefaultShortcutsExistInAllTools() {
        val defaultShortcuts = setOf("pdf_unlock", "image_shrink", "video_shrink", "video_trim")
        val tools = ALL_TOOLS.filter { it.id in defaultShortcuts }
        assertEquals(4, tools.size)
        // Check that order follows ALL_TOOLS list
        assertEquals(listOf("pdf_unlock", "video_shrink", "video_trim", "image_shrink"), tools.map { it.id })
    }

    @Test
    fun testDynamicShortcutCappingAtFour() {
        val selected = setOf("pdf_unlock", "pdf_shrink", "pdf_split", "image_shrink", "video_shrink", "qr_studio")
        val effective = ALL_TOOLS.filter { it.id in selected }.take(4)
        assertEquals(4, effective.size)
        assertEquals(listOf("pdf_unlock", "pdf_shrink", "pdf_split", "video_shrink"), effective.map { it.id })
    }

    @Test
    fun testEmptyShortcutFallback() {
        val selected = emptySet<String>()
        val effective = if (selected.isNotEmpty()) {
            ALL_TOOLS.filter { it.id in selected }.take(4)
        } else {
            ALL_TOOLS.filter { it.id in setOf("pdf_unlock", "image_shrink", "video_shrink", "video_trim") }.take(4)
        }
        assertEquals(4, effective.size)
        assertTrue(effective.any { it.id == "pdf_unlock" })
    }
}
