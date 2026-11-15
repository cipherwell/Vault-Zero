package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberEmerald,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF0C2A22),
    onPrimaryContainer = CyberEmeraldMuted,
    secondary = CyberCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF14232C),
    onSecondaryContainer = CyberCyan,
    tertiary = RoyalPurple,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = Color(0xFF0D1016),
    surfaceContainer = Color(0xFF10141A),
    surfaceContainerHigh = Color(0xFF141922),
    surfaceContainerHighest = ObsidianSurfaceVariant,
    error = DangerRed,
    onError = Color.White,
    outline = ObsidianCardBorder,
    outlineVariant = Color(0xFF1E2530)
)

private val LightColorScheme = lightColorScheme(
    primary = CyberEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F8E5),
    onPrimaryContainer = Color(0xFF003820),
    secondary = CyberCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBAF0FF),
    onSecondaryContainer = Color(0xFF003344),
    tertiary = RoyalPurple,
    onTertiary = Color.White,
    background = CleanWhiteBg,
    onBackground = TextPrimaryLight,
    surface = CleanWhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CleanWhiteSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    error = DangerRed,
    onError = Color.White,
    outline = CleanWhiteBorder,
    outlineVariant = Color(0xFFCBD5E1)
)

@Composable
fun VaultZeroTheme(
    darkTheme: Boolean = true, // Default to Dark Obsidian theme for security vault aesthetics
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultTypography,
        content = content
    )
}
