package com.omniforge.apparat.feature.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitDefaultsTest {
    @Test fun `default ranges fit the document`() {
        assertEquals("1", defaultRangesFor(1))
        assertEquals("1, 2", defaultRangesFor(2))
        assertEquals("1, 2-3", defaultRangesFor(3))
        assertEquals("1-2, 3-4", defaultRangesFor(4))
        assertEquals("1-5, 6-10", defaultRangesFor(10))
    }

    @Test fun `default ranges always validate`() {
        for (n in 1..40) {
            assertTrue("pages=$n", validateCustomRanges(defaultRangesFor(n), n) is RangeValidation.Valid)
        }
    }
}
