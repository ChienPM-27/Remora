package com.remora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = RemoraNavyDark,
    onPrimary = Color.White,
    primaryContainer = RemoraBlueSoft,
    onPrimaryContainer = RemoraNavyDark,
    secondary = RemoraBlue,
    onSecondary = Color.White,
    secondaryContainer = RemoraBlueBadge,
    onSecondaryContainer = RemoraNavyDark,
    background = RemoraBg,
    onBackground = RemoraNavyDark,
    surface = RemoraSurface,
    onSurface = RemoraNavyDark,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = RemoraSubtleText,
    outline = RemoraBorder
)

@Composable
fun RemoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // For MVP 24h, we stick to the crisp minimalist light theme defined in the mockups
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
