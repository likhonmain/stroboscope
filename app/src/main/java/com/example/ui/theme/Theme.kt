package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StrobeDarkColorScheme = darkColorScheme(
    primary = StrobeCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = StrobeCyanDim,
    onPrimaryContainer = StrobeCyan,
    secondary = TachAmber,
    onSecondary = Color(0xFF2B1D00),
    secondaryContainer = TachAmberDim,
    onSecondaryContainer = TachAmber,
    tertiary = FineEmerald,
    onTertiary = Color(0xFF002912),
    tertiaryContainer = FineEmeraldDim,
    onTertiaryContainer = FineEmerald,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    error = DangerRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StrobeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
