package com.vedant.apparat.engine.pdf

import com.vedant.apparat.feature.pdf.RangeValidation
import com.vedant.apparat.feature.pdf.validateCustomRanges
import com.vedant.apparat.feature.pdf.validateEveryN
import com.vedant.apparat.feature.pdf.validateSingleRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSplitTest {

    @Test
    fun computeEveryNChunksZeroOrNegativeHandledGracefully() {
        assertTrue(PdfEngine.computeEveryNChunks(0, 1).isEmpty())
        assertTrue(PdfEngine.computeEveryNChunks(10, 0).isEmpty())
        assertTrue(PdfEngine.computeEveryNChunks(10, -2).isEmpty())
        assertTrue(PdfEngine.computeEveryNChunks(-5, 2).isEmpty())
    }

    @Test
    fun computeEveryNChunksExactDivisible() {
        val chunks = PdfEngine.computeEveryNChunks(10, 2)
        val expected = listOf(1..2, 3..4, 5..6, 7..8, 9..10)
        assertEquals(expected, chunks)
    }

    @Test
    fun computeEveryNChunksWithRemainder() {
        val chunks = PdfEngine.computeEveryNChunks(10, 3)
        val expected = listOf(1..3, 4..6, 7..9, 10..10)
        assertEquals(expected, chunks)
    }

    @Test
    fun computeEveryNChunksSinglePages() {
        val chunks = PdfEngine.computeEveryNChunks(4, 1)
        val expected = listOf(1..1, 2..2, 3..3, 4..4)
        assertEquals(expected, chunks)
    }

    @Test
    fun computeEveryNChunksNExceedsTotalPages() {
        val chunks = PdfEngine.computeEveryNChunks(5, 10)
        assertEquals(listOf(1..5), chunks)
    }

    @Test
    fun computeEveryNChunksLargeDocument() {
        val chunks = PdfEngine.computeEveryNChunks(150, 50)
        val expected = listOf(1..50, 51..100, 101..150)
        assertEquals(expected, chunks)
    }

    @Test
    fun validateCustomRangesValid() {
        val result = validateCustomRanges("1-3, 4-6, 8", 10)
        assertTrue(result is RangeValidation.Valid)
        val valid = result as RangeValidation.Valid
        assertEquals(listOf(1..3, 4..6, 8..8), valid.ranges)
        assertEquals("Will create 3 PDF files", valid.previewText)
    }

    @Test
    fun validateCustomRangesEmpty() {
        val result = validateCustomRanges("   ", 10)
        assertTrue(result is RangeValidation.Invalid)
        assertEquals("Enter at least one page range (e.g. 1-3, 4-6)", (result as RangeValidation.Invalid).errorMessage)
    }

    @Test
    fun validateCustomRangesMalformed() {
        val result = validateCustomRanges("abc", 10)
        assertTrue(result is RangeValidation.Invalid)
        assertEquals("Invalid page number 'abc'", (result as RangeValidation.Invalid).errorMessage)

        val resultMultiDash = validateCustomRanges("1-2-3", 10)
        assertTrue(resultMultiDash is RangeValidation.Invalid)
    }

    @Test
    fun validateCustomRangesInverted() {
        val result = validateCustomRanges("5-3", 10)
        assertTrue(result is RangeValidation.Invalid)
        assertTrue((result as RangeValidation.Invalid).errorMessage.contains("cannot be greater than end page"))
    }

    @Test
    fun validateCustomRangesExceedsTotalPages() {
        val result = validateCustomRanges("12-15", 10)
        assertTrue(result is RangeValidation.Invalid)
        assertTrue((result as RangeValidation.Invalid).errorMessage.contains("exceeds document length"))

        // When totalPages is null (file not opened yet), syntax is valid and upper bound is not checked
        val resultWithoutTotal = validateCustomRanges("12-15", null)
        assertTrue(resultWithoutTotal is RangeValidation.Valid)
    }

    @Test
    fun validateEveryNInterval() {
        val valid = validateEveryN("3", 10)
        assertTrue(valid is RangeValidation.Valid)
        assertEquals(4, (valid as RangeValidation.Valid).ranges.size)
        assertEquals("Will create 4 PDF files", valid.previewText)

        val invalidZero = validateEveryN("0", 10)
        assertTrue(invalidZero is RangeValidation.Invalid)

        val invalidNonNumber = validateEveryN("xyz", 10)
        assertTrue(invalidNonNumber is RangeValidation.Invalid)
    }

    @Test
    fun validateSingleRangeCases() {
        val valid = validateSingleRange("2", "5", 10)
        assertTrue(valid is RangeValidation.Valid)
        assertEquals(listOf(2..5), (valid as RangeValidation.Valid).ranges)
        assertEquals("Extracts pages 2 to 5 (4 pages)", valid.previewText)

        val inverted = validateSingleRange("6", "3", 10)
        assertTrue(inverted is RangeValidation.Invalid)

        val outOfBounds = validateSingleRange("1", "15", 10)
        assertTrue(outOfBounds is RangeValidation.Invalid)

        val zeroStart = validateSingleRange("0", "5", 10)
        assertTrue(zeroStart is RangeValidation.Invalid)
    }
}
