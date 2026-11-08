package com.example.vitala.ui.theme

import androidx.compose.ui.graphics.Color

// Colour tokens from the visual style section of the brief (AHS-inspired; independent app, no AHS branding).
val BrandBlue = Color(0xFF007AC2)
val BrandBlueDark = Color(0xFF005A8F)
val BrandGreen = Color(0xFF00B374)
val GreenDark = Color(0xFF00704A)
val GreenTint = Color(0xFFE6F7F0)
val TextPrimary = Color(0xFF333333)
val TextSecondary = Color(0xFF595959)
val Panel = Color(0xFFF2F6F9)
val BorderGrey = Color(0xFFC9D3DB)
val FocusAmber = Color(0xFFFEBA35)
val AmberTint = Color(0xFFFFF4D6)
val AmberText = Color(0xFF6B4A00)
val Alert = Color(0xFFB42318)
val AlertTint = Color(0xFFFCECEA)

// 0-10 bands (green -> red). Numbers and a legend always accompany them (never colour alone).
val Band01 = Color(0xFFD3EEDF)
val Band23 = Color(0xFFE4F1C9)
val Band46 = Color(0xFFFBEBA9)
val Band78 = Color(0xFFF6D0A4)
val Band910 = Color(0xFFEDBABA)
val NoData = Color(0xFFEEF1F3)

fun bandColor(v: Int): Color = when (v) {
    in 0..1 -> Band01
    in 2..3 -> Band23
    in 4..6 -> Band46
    in 7..8 -> Band78
    else -> Band910
}
