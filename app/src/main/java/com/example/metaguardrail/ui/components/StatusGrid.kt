package com.example.metaguardrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.ui.theme.GuardRailColors

data class StatusItem(
    val title: String,
    val subtitle: String,
    val ok: Boolean
)

/**
 * 2-column grid of [StatusItem] small cards.
 * Implemented with chunked Rows for reliable compilation without experimental LazyGrid opt-ins.
 * Each card: dot indicator (green/gray), title, truncated subtitle.
 * Colors adapt to dark/light via [MaterialTheme.colorScheme] + [GuardRailColors].
 */
@Composable
fun StatusGrid(
    items: List<StatusItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val rows = items.chunked(2)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (rowItems in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (item in rowItems) {
                    val dotColor = if (item.ok) GuardRailColors.Safe else GuardRailColors.ProtectionInactive

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(scheme.surface)
                            .border(
                                width = 1.dp,
                                color = scheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = item.title,
                                color = scheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = item.subtitle,
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Balance row when odd count: fill remaining weight with spacer
                if (rowItems.size == 1) {
                    // Placeholder to keep grid aligned (invisible spacer with same weight)
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
