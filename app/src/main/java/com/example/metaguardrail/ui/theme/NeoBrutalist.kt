package com.example.metaguardrail.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object BrutalistTokens {
    // Spacing scale
    val SpacingXs: Dp = 4.dp
    val SpacingSm: Dp = 8.dp
    val SpacingMd: Dp = 12.dp
    val SpacingLg: Dp = 16.dp
    val SpacingXl: Dp = 20.dp
    val Spacing2Xl: Dp = 24.dp

    // Border & shape
    val BorderWidth: Dp = 2.dp
    val CornerRadius: Dp = 8.dp
    val CornerRadiusSm: Dp = 6.dp
    val CornerRadiusLg: Dp = 12.dp

    // Hard shadow
    val ShadowOffset: Dp = 4.dp
    val ShadowOffsetSm: Dp = 2.dp

    // Touch target
    val MinTouchTarget: Dp = 48.dp
    val ButtonHeight: Dp = 48.dp
    val ButtonHeightSm: Dp = 36.dp
    val NavBarHeight: Dp = 64.dp
    val IconSize: Dp = 24.dp
    val IconSizeSm: Dp = 16.dp

    // Color aliases referencing GuardRailColors
    val Bg: Color get() = GuardRailColors.BrutalBg
    val Surface: Color get() = GuardRailColors.BrutalSurface
    val Black: Color get() = GuardRailColors.BrutalBlack
    val Border: Color get() = GuardRailColors.BrutalBorder
    val Primary: Color get() = GuardRailColors.BrutalPrimary
    val PrimaryLight: Color get() = GuardRailColors.BrutalPrimaryLight
    val Mint: Color get() = GuardRailColors.BrutalMint
    val Warning: Color get() = GuardRailColors.BrutalWarning
    val Risk: Color get() = GuardRailColors.BrutalRisk
    val SurfaceVariant: Color get() = GuardRailColors.BrutalSurfaceVariant
}
