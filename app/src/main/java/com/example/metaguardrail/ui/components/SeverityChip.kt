package com.example.metaguardrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.ui.theme.GuardRailColors

enum class Severity {
    SAFE,
    REVIEW,
    WARNING,
    HIGH_RISK
}

// ---------------------------------------------------------------------------
// Mappers
// ---------------------------------------------------------------------------

/** Maps an arbitrary backend label string to [Severity]. */
fun severityFromLabel(label: String): Severity = when (label.trim().uppercase()) {
    "SAFE", "SAFE_GUARD", "OK" -> Severity.SAFE
    "REVIEW", "REVIEW_SUGGESTED", "NO MATCHING RULE", "NEEDS_REVIEW" -> Severity.REVIEW
    "WARNING", "WARN" -> Severity.WARNING
    "HIGH_RISK", "HIGH RISK", "CRITICAL", "BLOCKED" -> Severity.HIGH_RISK
    else -> Severity.REVIEW
}

/** Human-readable label for a [Severity]. */
fun severityLabel(severity: Severity): String = when (severity) {
    Severity.SAFE -> "Safe"
    Severity.REVIEW -> "Review"
    Severity.WARNING -> "Warning"
    Severity.HIGH_RISK -> "High risk"
}

/** Primary (solid) color for a [Severity] — always [GuardRailColors] source-of-truth. */
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.SAFE -> GuardRailColors.Safe
    Severity.REVIEW -> GuardRailColors.Review
    Severity.WARNING -> GuardRailColors.Warning
    Severity.HIGH_RISK -> GuardRailColors.HighRisk
}

/** Muted background color for a [Severity] badge. */
fun severityMutedColor(severity: Severity): Color = when (severity) {
    Severity.SAFE -> GuardRailColors.SafeMuted
    Severity.REVIEW -> GuardRailColors.ReviewMuted
    Severity.WARNING -> GuardRailColors.WarningMuted
    Severity.HIGH_RISK -> GuardRailColors.HighRiskMuted
}

/** Border color for a [Severity] badge. */
fun severityBorderColor(severity: Severity): Color = when (severity) {
    Severity.SAFE -> GuardRailColors.SafeBorder
    Severity.REVIEW -> GuardRailColors.ReviewBorder
    Severity.WARNING -> GuardRailColors.WarningBorder
    Severity.HIGH_RISK -> GuardRailColors.HighRiskBorder
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * Pill badge whose color is driven by [severity].
 * Uses muted background + solid text/border from [GuardRailColors] so the
 * badge remains legible in both dark and light themes.
 */
@Composable
fun SeverityBadge(
    severity: Severity,
    modifier: Modifier = Modifier
) {
    val muted = severityMutedColor(severity)
    val border = severityBorderColor(severity)
    val solid = severityColor(severity)

    // Use muted bg in both themes; text uses solid for contrast.
    // On dark bg the muted is subtle tint, on light bg same — scheme handles outer surface.
    // Text uses solid so it pops.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(muted)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = severityLabel(severity).uppercase(),
            color = solid,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            lineHeight = 12.sp
        )
    }
}

// ---------------------------------------------------------------------------
// Deprecated alias (backwards compat)
// ---------------------------------------------------------------------------

/** Legacy string-based helper — delegates to [severityColor(Severity)]. */
fun severityColor(label: String): Color = severityColor(severityFromLabel(label))
