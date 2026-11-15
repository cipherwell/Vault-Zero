package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// VaultZero brand palette — obsidian surfaces, one emerald accent.
// Deliberately restrained: no neon fills, no rainbow type colors. Accent is for
// primary actions, selection and live secrets only; everything else is neutral.

val ObsidianBg = Color(0xFF0A0C10)
val ObsidianSurface = Color(0xFF10141A)
val ObsidianSurfaceVariant = Color(0xFF171C24)
val ObsidianCardBorder = Color(0xFF262E3A)

val CyberEmerald = Color(0xFF16C098)
val CyberEmeraldDark = Color(0xFF0E8A6B)

/** Legible mint for disabled emerald controls (WCAG-friendly on the dark canvas). */
val CyberEmeraldMuted = Color(0xFF7FD9BC)

val CyberCyan = Color(0xFF5E9FC4)
val CyberCyanDark = Color(0xFF2F7099)
val RoyalPurple = Color(0xFF8A79D6)
val ElectricBlue = Color(0xFF5A8FD6)

val DangerRed = Color(0xFFE5484D)
val DangerRedDark = Color(0xFF8F2B30)
val WarningAmber = Color(0xFFD9A73F)
val SuccessGreen = Color(0xFF23B575)

val TextPrimaryDark = Color(0xFFE8EDF5)
val TextSecondaryDark = Color(0xFF9AA6B8)
val TextTertiaryDark = Color(0xFF67738A)

// Light Palette (kept for completeness; the vault ships dark-only)
val CleanWhiteBg = Color(0xFFF8FAFC)
val CleanWhiteSurface = Color(0xFFFFFFFF)
val CleanWhiteSurfaceVariant = Color(0xFFF1F5F9)
val CleanWhiteBorder = Color(0xFFE2E8F0)

val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextTertiaryLight = Color(0xFF94A3B8)
