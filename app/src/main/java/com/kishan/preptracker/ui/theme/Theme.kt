package com.kishan.preptracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Orange from the Grit launcher icon. Not Material You — Samsung teal was swallowing the brand. */
val GritOrange = Color(0xFFE4572E)
val GritOrangeDim = Color(0xFF3A1A10)

private val Dark = darkColorScheme(
    primary = GritOrange,
    onPrimary = Color.White,
    primaryContainer = GritOrangeDim,
    onPrimaryContainer = Color(0xFFFFC4A8),
    secondary = Color(0xFFD4A574),
    onSecondary = Color(0xFF1A120C),
    tertiary = Color(0xFFE8C36A),
    background = Color(0xFF0E0E0E),
    onBackground = Color(0xFFF3F3F3),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFF3F3F3),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF3A3A3A),
    inverseSurface = Color(0xFFEAEAEA),
    inverseOnSurface = Color(0xFF121212),
)

private val Light = lightColorScheme(
    primary = GritOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D4),
    onPrimaryContainer = Color(0xFF3A1208),
    secondary = Color(0xFF8A5A32),
    tertiary = Color(0xFF9A6B12),
    background = Color(0xFFF7F4F0),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF7F4F0),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF5C5C5C),
    outline = Color(0xFFD0C8C2),
)

@Composable
fun PrepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
