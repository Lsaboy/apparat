package com.vedant.apparat.core.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test fun `contrast ratio matches WCAG reference values`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.05f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 0.001f)
    }

    @Test fun `accent text is lifted to 4_5 to 1 on both themes`() {
        for (accent in listOf(AccentUtility, AccentPdf, AccentVideo, AccentImage, SuccessGreen, BraunOrange)) {
            val onIvory = readableTextColor(accent, IvorySurface, IvoryTextPrimary)
            val onCharcoal = readableTextColor(accent, CharcoalSurface, CharcoalTextPrimary)
            assertTrue("ivory $accent", contrastRatio(onIvory, IvorySurface) >= 4.5f)
            assertTrue("charcoal $accent", contrastRatio(onCharcoal, CharcoalSurface) >= 4.5f)
        }
    }

    @Test fun `button text on every accent swatch reaches 4_5 to 1 where possible`() {
        for (swatch in listOf(BraunOrange, TechnicalIndicatorBlue, TechnicalIndicatorGreen, TechnicalIndicatorRed, Color(0xFF5E6572), AccentPdf, AccentVideo, AccentImage)) {
            assertTrue("$swatch", contrastRatio(onPrimaryForAccent(swatch), swatch) >= 4.5f)
        }
    }
}
