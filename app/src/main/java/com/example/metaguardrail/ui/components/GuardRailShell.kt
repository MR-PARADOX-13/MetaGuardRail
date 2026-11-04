package com.example.metaguardrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.ui.theme.GuardRailColors

/**
 * Premium shell primitives for the Meta GuardRail dashboard.
 * All colors respect dark/light via [MaterialTheme.colorScheme].
 */

@Composable
fun GuardRailTopBar(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(scheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // MG shield logo — Circle in Primary with "MG" text
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GuardRailColors.Primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "MG",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = scheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun GuardRailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .border(
                width = 1.dp,
                color = scheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        modifier = modifier,
        text = text.uppercase(),
        color = scheme.onSurfaceVariant,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 14.sp
    )
}

/**
 * Pill indicating live protection state.
 * LIVE  -> green dot + [GuardRailColors.SafeMuted] bg + [GuardRailColors.Safe] dot
 * STANDBY -> gray dot + muted gray bg + [GuardRailColors.ProtectionInactive] dot
 */
@Composable
fun LivePill(
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isLive) GuardRailColors.Safe else GuardRailColors.ProtectionInactive
    val bgColor = if (isLive) GuardRailColors.SafeMuted else Color(0x1464748B)
    val label = if (isLive) "LIVE" else "STANDBY"
    val textColor = if (isLive) GuardRailColors.Safe else GuardRailColors.ProtectionInactive

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            lineHeight = 12.sp
        )
    }
}
