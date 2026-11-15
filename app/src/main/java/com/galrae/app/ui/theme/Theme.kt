package com.galrae.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GalraeColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = TextPrimary,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondary,
    outline = OutlineGray,
)

@Composable
fun GALRAETheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GalraeColors, typography = Typography, content = content)
}
