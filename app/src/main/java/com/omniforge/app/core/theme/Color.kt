package com.omniforge.app.core.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Braun / Dieter Rams Industrial Palette
// ==========================================

// Braun Signal Orange - Tactile focal point (ET66 calculator, TP1 power switch)
val BraunOrange = Color(0xFFFF5500)
val BraunSignalOrange = BraunOrange
val BraunSignalOrangeDark = Color(0xFFCC4400)

// Light Mode - Classic Ivory (SK4 / T3 hardware matte ivory casing)
val IvoryBackground = Color(0xFFF5F3EC)
val IvorySurface = Color(0xFFEBE7DD)
val IvorySurfaceElevated = Color(0xFFFFFFFF)
val IvoryBorder = Color(0xFFD6D1C4)
val IvoryTextPrimary = Color(0xFF191919)
val IvoryTextSecondary = Color(0xFF6A6A6A)
val IvoryTextTertiary = Color(0xFF9E9E9E)

// Dark Mode - Atelier Charcoal (Precision instrument anodized dark chassis)
val CharcoalBackground = Color(0xFF161618)
val CharcoalSurface = Color(0xFF1E1E22)
val CharcoalSurfaceElevated = Color(0xFF26262B)
val CharcoalBorder = Color(0xFF2E2E34)
val CharcoalTextPrimary = Color(0xFFF0F0F0)
val CharcoalTextSecondary = Color(0xFF8E8E93)
val CharcoalTextTertiary = Color(0xFF5E5E64)

// Functional Technical Indicators
val TechnicalIndicatorGreen = Color(0xFF2E7D32)
val TechnicalIndicatorYellow = Color(0xFFF9A825)
val TechnicalIndicatorRed = Color(0xFFC62828)
val TechnicalIndicatorBlue = Color(0xFF1565C0)

// Backward compatibility tokens for legacy screens
val AmoledBlack = Color(0xFF000000)
val DarkSurface = CharcoalBackground
val DarkSurfaceElevated = CharcoalSurfaceElevated
val DarkSurfaceCard = CharcoalSurface
val DarkBorder = CharcoalBorder
val DarkSurfaceHighlight = CharcoalSurfaceElevated

val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF8E8E93)
val TextTertiary = Color(0xFF636366)

// Category Technical Indicator Accents
val AccentVideo = Color(0xFF007ACC)
val AccentVideoDark = Color(0xFF004D80)
val AccentImage = Color(0xFF2E7D32)
val AccentImageDark = Color(0xFF1B5E20)
val AccentPdf = Color(0xFFC62828)
val AccentPdfDark = Color(0xFF8E0000)
val AccentUtility = Color(0xFFF57C00)
val AccentUtilityDark = Color(0xFFB25000)

val PrimaryTeal = BraunOrange
val SecondaryPurple = TechnicalIndicatorBlue
val ErrorRed = TechnicalIndicatorRed
val SuccessGreen = TechnicalIndicatorGreen
