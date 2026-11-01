package com.example.metaguardrail.ui.theme

import androidx.compose.ui.graphics.Color

// === Meta GuardRail Premium Cyber Dashboard Palette ===
object GuardRailColors {
    // Backgrounds
    val DarkBg = Color(0xFF0B1020)
    val DarkSurface = Color(0xFF151E32)
    val DarkElevated = Color(0xFF1C2940)
    val DarkCardBorder = Color(0xFF233554)

    // Light theme
    val LightBg = Color(0xFFF0F2FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightElevated = Color(0xFFF6F7FD)
    val LightCardBorder = Color(0xFFE2E6F0)

    // Primary actions
    val Primary = Color(0xFF398BFF)
    val PrimaryHover = Color(0xFF5AA0FF)
    val PrimaryMuted = Color(0x1A398BFF)
    val PrimaryBorder = Color(0x33398BFF)
    val Secondary = Color(0xFF9575FF)
    val SecondaryMuted = Color(0x1A9575FF)

    // Severity (used in both themes)
    val Safe = Color(0xFF22C55E)
    val SafeMuted = Color(0x1422C55E)
    val SafeBorder = Color(0x3322C55E)
    val Review = Color(0xFFEAB308)
    val ReviewMuted = Color(0x14EAB308)
    val ReviewBorder = Color(0x33EAB308)
    val Warning = Color(0xFFF97316)
    val WarningMuted = Color(0x14F97316)
    val WarningBorder = Color(0x33F97316)
    val HighRisk = Color(0xFFEF4444)
    val HighRiskMuted = Color(0x14EF4444)
    val HighRiskBorder = Color(0x33EF4444)

    // Text - dark theme
    val DarkPrimaryText = Color(0xFFFFFFFF)
    val DarkSecondaryText = Color(0xFFAAB8D0)
    val DarkMutedText = Color(0xFF6B7E9F)
    val DarkHintText = Color(0xFF4A5A7A)

    // Text - light theme
    val LightPrimaryText = Color(0xFF0F172A)
    val LightSecondaryText = Color(0xFF475569)
    val LightMutedText = Color(0xFF94A3B8)
    val LightHintText = Color(0xFFCBD5E1)

    // Accent - gradients (use sparingly)
    val GradientPrimaryStart = Color(0xFF398BFF)
    val GradientPrimaryEnd = Color(0xFF9575FF)
    val GradientSurfaceStart = Color(0xFF151E32)
    val GradientSurfaceEnd = Color(0xFF1C2940)

    // Status / protection
    val ProtectionActive = Color(0xFF22C55E)
    val ProtectionInactive = Color(0xFF64748B)
    val ProtectionActiveGlow = Color(0x2222C55E)
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
