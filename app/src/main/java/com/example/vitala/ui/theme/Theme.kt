package com.example.vitala.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val LocalHighContrast = compositionLocalOf { false }

/**
 * Light theme only on purpose: the brief asks for mostly white with blue actions and high contrast.
 * [textScale] is the in-app text size (Normal / Large / Extra large) and multiplies the phone's own setting.
 */
@Composable
fun VitalaTheme(
    textScale: Float = 1f,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val action = if (highContrast) BrandBlueDark else BrandBlue
    val text = if (highContrast) Color.Black else TextPrimary
    val scheme = lightColorScheme(
        primary = action, onPrimary = Color.White,
        secondary = BrandBlueDark, onSecondary = Color.White,
        background = Color.White, onBackground = text,
        surface = Color.White, onSurface = text,
        surfaceVariant = Panel, onSurfaceVariant = if (highContrast) Color.Black else TextSecondary,
        outline = if (highContrast) Color.Black else BorderGrey,
        error = Alert, onError = Color.White
    )
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalDensity provides Density(density.density, density.fontScale * textScale)
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
