package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ── One UI palette ────────────────────────────────────────────────────────────
// Samsung One UI: two grounds only (#000000 dark / #FAFAFA light), a single blue
// accent, neutral grays for elevated surfaces, semantic red/green reserved for
// destructive and success states. No other hues in the chrome.
//
// The historical token names are kept so existing screens compile; the values are
// now the One UI tokens.

// Dark ground + elevated surfaces (true black for OLED)
val ObsidianBg = Color(0xFF000000)
val ObsidianSurface = Color(0xFF121214)
val ObsidianSurfaceVariant = Color(0xFF1C1C1E)
val ObsidianCardBorder = Color(0xFF2C2C2E)

// Accent: One UI blue (dark-theme variant is lighter for contrast on black)
val CyberEmerald = Color(0xFF4DA6FF)      // accent (dark)
val CyberEmeraldDark = Color(0xFF1976D2)  // accent (light)
val CyberEmeraldMuted = Color(0xFF9BCBFF) // disabled / secondary accent content

// Secondary hues are used only as muted iconography, never as chrome
val CyberCyan = Color(0xFF64B5F6)
val CyberCyanDark = Color(0xFF1565C0)
val RoyalPurple = Color(0xFFB39DDB)
val ElectricBlue = Color(0xFF90CAF9)

val DangerRed = Color(0xFFFF453A)
val DangerRedDark = Color(0xFFB3261E)
val WarningAmber = Color(0xFFFFB74D)
val SuccessGreen = Color(0xFF4CAF50)

// Text on dark
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFA1A1A6)
val TextTertiaryDark = Color(0xFF6E6E73)

// Light ground + surfaces
val CleanWhiteBg = Color(0xFFFAFAFA)
val CleanWhiteSurface = Color(0xFFFFFFFF)
val CleanWhiteSurfaceVariant = Color(0xFFF2F2F7)
val CleanWhiteBorder = Color(0xFFE5E5EA)

val TextPrimaryLight = Color(0xFF000000)
val TextSecondaryLight = Color(0xFF6B6B70)
val TextTertiaryLight = Color(0xFF8E8E93)
