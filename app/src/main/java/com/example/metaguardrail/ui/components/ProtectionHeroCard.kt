package com.example.metaguardrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.ui.theme.GuardRailColors

/**
 * Large hero card reflecting real protection state.
 * Gradient background adapts to dark/light via [MaterialTheme.colorScheme] + [isSystemInDarkTheme].
 *
 * @param isRunning real running state — drives colors, labels, and button action
 * @param message primary status message to display
 * @param finding optional secondary finding/detail text (may be null)
 * @param onStart called when user taps Start
 * @param onStop called when user taps Stop
 */
@Composable
fun ProtectionHero(
    isRunning: Boolean,
    message: String,
    finding: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    // Gradient-like background adapting to theme.
    val gradientColors = if (isDark) {
        listOf(GuardRailColors.DarkSurface, GuardRailColors.DarkElevated)
        // Alternatively GuardRailColors.GradientSurfaceStart -> GradientSurfaceEnd (same values)
    } else {
        listOf(GuardRailColors.LightSurface, GuardRailColors.LightElevated)
    }
    val borderColor = if (isDark) GuardRailColors.DarkCardBorder else GuardRailColors.LightCardBorder

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(colors = gradientColors))
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Shield icon placeholder — circular badge with shield glyph
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRunning) GuardRailColors.ProtectionActiveGlow else Color(0x1464748B)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isRunning) GuardRailColors.SafeBorder else Color(0x3364748B),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Secondary inner circle for premium layered look
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRunning) GuardRailColors.Safe else GuardRailColors.ProtectionInactive
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◈",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status pill: LIVE when running, STANDBY when stopped
            LivePill(isLive = isRunning)
        }

        // Status texts — reflect real isRunning/message/finding (no fake strings)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isRunning) "Protection active" else "Protection paused",
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp
            )
            Text(
                text = message,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (!finding.isNullOrBlank()) {
                Text(
                    text = finding,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Big Start/Stop button — Primary when start, HighRisk when stop
        Button(
            onClick = if (isRunning) onStop else onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) GuardRailColors.HighRisk else GuardRailColors.Primary,
                contentColor = Color.White
            )
        ) {
            Text(
                text = if (isRunning) "Stop Protection" else "Start Protection",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}
