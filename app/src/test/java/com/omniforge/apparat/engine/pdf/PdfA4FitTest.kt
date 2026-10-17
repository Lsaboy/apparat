package com.omniforge.apparat.engine.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfA4FitTest {

    private val a4Width = 595.28f
    private val a4Height = 841.89f
    private val margin = 20f

    @Test
    fun landscapePhotoUsesLandscapePageAndFitsWithinMargins() {
        // 4000x3000 landscape
        val fit = calculateA4Fit(4000, 3000, margin, a4Short = a4Width, a4Long = a4Height)

        // Landscape orientation
        assertEquals(a4Height, fit.pageWidth, 0.01f)
        assertEquals(a4Width, fit.pageHeight, 0.01f)

        // Aspect ratio preserved (4:3)
        assertEquals(4.0 / 3.0, (fit.drawWidth / fit.drawHeight).toDouble(), 0.01)

        // Fits within printable bounds
        assertTrue(fit.drawWidth <= fit.pageWidth - 2 * margin + 0.01f)
        assertTrue(fit.drawHeight <= fit.pageHeight - 2 * margin + 0.01f)

        // Horizontally and vertically centered
        assertTrue(fit.x >= margin - 0.01f)
        assertTrue(fit.y >= margin - 0.01f)
        assertEquals(fit.x, fit.pageWidth - (fit.x + fit.drawWidth), 0.05f)
        assertEquals(fit.y, fit.pageHeight - (fit.y + fit.drawHeight), 0.05f)
    }

    @Test
    fun portraitPhotoUsesPortraitPageAndFitsWithinMargins() {
        // 3000x4000 portrait
        val fit = calculateA4Fit(3000, 4000, margin, a4Short = a4Width, a4Long = a4Height)

        // Portrait orientation
        assertEquals(a4Width, fit.pageWidth, 0.01f)
        assertEquals(a4Height, fit.pageHeight, 0.01f)

        // Aspect ratio preserved (3:4)
        assertEquals(3.0 / 4.0, (fit.drawWidth / fit.drawHeight).toDouble(), 0.01)

        // Fits within printable bounds
        assertTrue(fit.drawWidth <= fit.pageWidth - 2 * margin + 0.01f)
        assertTrue(fit.drawHeight <= fit.pageHeight - 2 * margin + 0.01f)

        // Centered
        assertTrue(fit.x >= margin - 0.01f)
        assertTrue(fit.y >= margin - 0.01f)
        assertEquals(fit.x, fit.pageWidth - (fit.x + fit.drawWidth), 0.05f)
        assertEquals(fit.y, fit.pageHeight - (fit.y + fit.drawHeight), 0.05f)
    }

    @Test
    fun squarePhotoUsesPortraitPageAndCentersBothAxes() {
        // 2000x2000 square
        val fit = calculateA4Fit(2000, 2000, margin, a4Short = a4Width, a4Long = a4Height)

        // Portrait page
        assertEquals(a4Width, fit.pageWidth, 0.01f)
        assertEquals(a4Height, fit.pageHeight, 0.01f)

        // 1:1 aspect ratio
        assertEquals(fit.drawWidth, fit.drawHeight, 0.01f)

        // Constrained by width
        val expectedWidth = a4Width - 2 * margin
        assertEquals(expectedWidth, fit.drawWidth, 0.01f)
        assertEquals(margin, fit.x, 0.01f)

        // Centered vertically with equal top/bottom margins
        val expectedY = (a4Height - expectedWidth) / 2f
        assertEquals(expectedY, fit.y, 0.01f)
        assertEquals(fit.y, fit.pageHeight - (fit.y + fit.drawHeight), 0.05f)
    }
}
