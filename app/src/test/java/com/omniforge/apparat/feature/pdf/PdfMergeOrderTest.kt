package com.omniforge.apparat.feature.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PdfMergeOrderTest {

    private val list = listOf("a", "b", "c", "d")

    @Test
    fun `move up swaps with the item above`() {
        assertEquals(listOf("a", "c", "b", "d"), list.moved(2, 1))
    }

    @Test
    fun `move down swaps with the item below`() {
        assertEquals(listOf("b", "a", "c", "d"), list.moved(0, 1))
    }

    @Test
    fun `moving the last item to the top works`() {
        assertEquals(listOf("d", "a", "b", "c"), list.moved(3, 0))
    }

    @Test
    fun `out of range or same index changes nothing`() {
        assertSame(list, list.moved(0, -1))
        assertSame(list, list.moved(3, 4))
        assertSame(list, list.moved(2, 2))
        assertSame(list, list.moved(-1, 1))
    }

    @Test
    fun `original list is not modified`() {
        list.moved(0, 3)
        assertEquals(listOf("a", "b", "c", "d"), list)
    }
}
