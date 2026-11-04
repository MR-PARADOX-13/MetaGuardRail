package com.example.metaguardrail.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// === Meta GuardRail Neo-Brutalist Palette ===
object GuardRailColors {
    // ── Neo-Brutalist core tokens ──
    val BrutalBg = Color(0xFFF8FAFC)          // Off-white background
    val BrutalSurface = Color(0xFFFFFFFF)     // White cards
    val BrutalBlack = Color(0xFF111827)       // Near-black (text / border / shadow)
    val BrutalBorder = Color(0xFF111827)
    val BrutalPrimary = Color(0xFF155EEF)     // Electric Blue
    val BrutalPrimaryLight = Color(0xFFBFDBFE) // Light Blue
    val BrutalMint = Color(0xFFBBF7D0)        // Mint (safe)
    val BrutalWarning = Color(0xFFFEF08A)     // Warning yellow
    val BrutalRisk = Color(0xFFFECACA)        // Risk red
    val BrutalSurfaceVariant = Color(0xFFF1F5F9)
    val BrutalMutedText = Color(0xFF64748B)
    val BrutalSecondaryText = Color(0xFF475569)

    // Brutalist dimensional constants (also mirrored in BrutalistTokens for single source)
    val BrutalBorderWidth = 2.dp
    val BrutalShadowOffset = 4.dp
    val BrutalCornerRadius = 8.dp

    // ── Dark counterparts (dark navy) ──
    val BrutalDarkBg = Color(0xFF0B1426)
    val BrutalDarkSurface = Color(0xFF162040)
    val BrutalDarkElevated = Color(0xFF1E2F50)
    val BrutalDarkBorder = Color(0xFF2A3F66)
    val BrutalDarkOnSurface = Color(0xFFF1F5F9)
    val BrutalDarkSecondaryText = Color(0xFF94A3B8)

    // ── Backward-compat mappings — preserve existing names ──
    // Backgrounds
    val DarkBg = BrutalDarkBg
    val DarkSurface = BrutalDarkSurface
    val DarkElevated = BrutalDarkElevated
    val DarkCardBorder = BrutalDarkBorder

    // Light theme
    val LightBg = BrutalBg
    val LightSurface = BrutalSurface
    val LightElevated = BrutalSurfaceVariant
    val LightCardBorder = BrutalBorder

    // Primary actions (map to Electric Blue)
    val Primary = BrutalPrimary
    val PrimaryHover = Color(0xFF1A4FD1)
    val PrimaryMuted = Color(0x1A155EEF)
    val PrimaryBorder = Color(0x33155EEF)
    val Secondary = Color(0xFF7C3AED)
    val SecondaryMuted = Color(0x1A7C3AED)

    // Severity (brutalist tints)
    val Safe = Color(0xFF16A34A)
    val SafeMuted = Color(0xFFBBF7D0)
    val SafeBorder = BrutalBlack
    val Review = Color(0xFFCA8A04)
    val ReviewMuted = BrutalWarning
    val ReviewBorder = BrutalBlack
    val Warning = Color(0xFFEA580C)
    val WarningMuted = Color(0xFFFED7AA)
    val WarningBorder = BrutalBlack
    val HighRisk = Color(0xFFDC2626)
    val HighRiskMuted = BrutalRisk
    val HighRiskBorder = BrutalBlack

    // Text - dark theme
    val DarkPrimaryText = BrutalDarkOnSurface
    val DarkSecondaryText = BrutalDarkSecondaryText
    val DarkMutedText = Color(0xFF6B7E9F)
    val DarkHintText = Color(0xFF4A5A7A)

    // Text - light theme
    val LightPrimaryText = BrutalBlack
    val LightSecondaryText = BrutalSecondaryText
    val LightMutedText = BrutalMutedText
    val LightHintText = Color(0xFFCBD5E1)

    // Accent - gradients (kept for compat, use sparingly)
    val GradientPrimaryStart = BrutalPrimary
    val GradientPrimaryEnd = BrutalPrimaryLight
    val GradientSurfaceStart = BrutalDarkSurface
    val GradientSurfaceEnd = BrutalDarkElevated

    // Status / protection
    val ProtectionActive = Color(0xFF16A34A)
    val ProtectionInactive = Color(0xFF64748B)
    val ProtectionActiveGlow = Color(0x2216A34A)
}

// Backwards compat aliases
val GuardRailBlue = GuardRailColors.Primary
val GuardRailTeal = GuardRailColors.Secondary
val GuardRailLavender = GuardRailColors.Secondary

fun severityColor(isDark: Boolean, label: String): Color = when (label.uppercase()) {
    "SAFE", "SAFE_GUARD" -> GuardRailColors.Safe
    "REVIEW", "REVIEW_SUGGESTED", "NO MATCHING RULE" -> GuardRailColors.Review
    "WARNING" -> GuardRailColors.Warning
    "HIGH_RISK", "HIGH RISK" -> GuardRailColors.HighRisk
    else -> if (isDark) GuardRailColors.DarkSecondaryText else GuardRailColors.LightSecondaryText
}
