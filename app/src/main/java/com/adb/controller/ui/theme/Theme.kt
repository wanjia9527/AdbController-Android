package com.adb.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Accent,
    tertiary = PrimaryLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = CardDark,
    onPrimary = TextPrimary,
    onSecondary = BackgroundDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = TextHint,
    error = Offline
)

@Composable
fun AdbControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
