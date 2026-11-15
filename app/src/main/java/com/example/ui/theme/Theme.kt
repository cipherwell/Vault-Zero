package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// One UI shapes: generously rounded containers (12–26dp) and pills for anything
// that is pressed. Colour comes from Color.kt; no extra hues.
private val OneUiShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

private val DarkColorScheme = darkColorScheme(
    primary = CyberEmerald,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF10243A),
    onPrimaryContainer = CyberEmeraldMuted,
    secondary = CyberEmerald,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1C1C1E),
    onSecondaryContainer = TextPrimaryDark,
    tertiary = CyberEmeraldMuted,
    onTertiary = Color.Black,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianBg,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = Color(0xFF0C0C0E),
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceVariant,
    surfaceContainerHighest = Color(0xFF242426),
    error = DangerRed,
    onError = Color.Black,
    outline = ObsidianCardBorder,
    outlineVariant = Color(0xFF232325),
)

private val LightColorScheme = lightColorScheme(
    primary = CyberEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E9FF),
    onPrimaryContainer = Color(0xFF0B3D66),
    secondary = CyberEmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = TextPrimaryLight,
    tertiary = CyberCyanDark,
    onTertiary = Color.White,
    background = CleanWhiteBg,
    onBackground = TextPrimaryLight,
    surface = CleanWhiteBg,
    onSurface = TextPrimaryLight,
    surfaceVariant = CleanWhiteSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = CleanWhiteSurface,
    surfaceContainerHigh = CleanWhiteSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE9E9EE),
    error = DangerRedDark,
    onError = Color.White,
    outline = CleanWhiteBorder,
    outlineVariant = Color(0xFFEFEFF4),
)

@Composable
fun VaultZeroTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = VaultTypography,
        shapes = OneUiShapes,
        content = content,
    )
}
