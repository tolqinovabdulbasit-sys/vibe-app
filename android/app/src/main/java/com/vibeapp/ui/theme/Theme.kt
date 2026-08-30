package com.vibeapp.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

val VibeLinkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryDim,
    onPrimaryContainer = PrimaryVariant,
    secondary = ColorConnected,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = ColorFailed
)

@Composable
fun VibeLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeLinkColorScheme,
        typography = VibeLinkTypography,
        content = content
    )
}
