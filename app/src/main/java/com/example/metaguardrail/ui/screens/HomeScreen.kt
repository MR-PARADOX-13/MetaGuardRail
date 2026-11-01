package com.example.metaguardrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.DailySummaryCalculator
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.TrendAnalyticsCalculator
import com.example.metaguardrail.data.Severity as DataSeverity
import com.example.metaguardrail.monitor.LiveProtectionStatus
import com.example.metaguardrail.ui.components.GuardRailCard
import com.example.metaguardrail.ui.components.GuardRailTopBar
import com.example.metaguardrail.ui.components.ProtectionHero
import com.example.metaguardrail.ui.components.SectionLabel
import com.example.metaguardrail.ui.components.SeverityBadge
import com.example.metaguardrail.ui.components.StatusGrid
import com.example.metaguardrail.ui.components.StatusItem
import com.example.metaguardrail.ui.components.Severity as UiSeverity
import com.example.metaguardrail.ui.theme.GuardRailColors
import java.util.concurrent.TimeUnit

private fun DataSeverity.toUi(): UiSeverity = when (this) {
    DataSeverity.SAFE -> UiSeverity.SAFE
    DataSeverity.REVIEW -> UiSeverity.REVIEW
    DataSeverity.WARNING -> UiSeverity.WARNING
    DataSeverity.HIGH_RISK -> UiSeverity.HIGH_RISK
}

private fun formatTimeAgo(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    if (diff < 0) return "just now"
    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (mins < 1) return "just now"
    if (mins < 60) return "${mins}m ago"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours < 24) return "${hours}h ago"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days < 7) return "${days}d ago"
    return "${days / 7}w ago"
}

/**
 * Home tab — hero + status grid + permission hints + stats + recent activity.
 */
@Composable
fun HomeScreen(
    settings: SafetySettings,
    protectionStatus: LiveProtectionStatus,
    activityEvents: List<ActivityEvent>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    val statusItems = remember(settings, protectionStatus) {
        listOf(
            StatusItem(
                title = "Live Protection",
                subtitle = if (protectionStatus.isRunning) "Active" else "Paused",
                ok = protectionStatus.isRunning
            ),
            StatusItem(title = "OCR", subtitle = "Ready", ok = true),
            StatusItem(title = "Image detection", subtitle = "Ready", ok = true),
            StatusItem(
                title = "Audio language",
                subtitle = settings.speechLanguage.label,
                ok = true
            )
        )
    }

    val sortedEvents = remember(activityEvents) {
        activityEvents.sortedByDescending { it.timestampMs }
    }
    val recentEvents = remember(sortedEvents) { sortedEvents.take(5) }

    val totalCount = activityEvents.size
    val safeCount = remember(activityEvents) { activityEvents.count { it.severity == DataSeverity.SAFE } }
    val flaggedCount = remember(activityEvents) { activityEvents.count { it.severity != DataSeverity.SAFE } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        GuardRailTopBar(
            title = "META GUARD RAIL",
            subtitle = "Personal digital safety"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Protection hero — real state
            ProtectionHero(
                isRunning = protectionStatus.isRunning,
                message = protectionStatus.message,
                finding = protectionStatus.finding,
                onStart = onStart,
                onStop = onStop
            )

            // 2. Status grid — 4 items
            SectionLabel(text = "System status")
            StatusGrid(items = statusItems)

            // 3. Permission readiness card (placeholder hints)
            SectionLabel(text = "Permissions")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Permission status",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "These permissions enable on-device checks. Manage them in Settings.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                    PermissionHintRow(label = "Display over other apps", hint = "Required for shield overlay")
                    PermissionHintRow(label = "Usage access", hint = "Limits checks to Instagram & Facebook")
                    PermissionHintRow(label = "Notifications", hint = "Required for foreground service on Android 13+")
                }
            }

            // 4. Stats row — 3 cards
            SectionLabel(text = "Activity overview")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = totalCount.toString(),
                    label = "Total events"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = flaggedCount.toString(),
                    label = "Flagged"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = safeCount.toString(),
                    label = "Safe"
                )
            }

            // 5. Daily Protection Summary — Phase 2
            SectionLabel(text = "Daily Protection Summary")
            DailySummarySection(activityEvents = activityEvents)

            // 6. Safety Trend Analytics — Phase 4
            SectionLabel(text = "Safety Trend Analytics")
            TrendAnalyticsSection(activityEvents = activityEvents)

            // 7. Recent activity — up to 5
            SectionLabel(text = "Recent activity")
            if (recentEvents.isEmpty()) {
                GuardRailCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No activity yet",
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Run a check in Analyze or wait for live protection to log events.",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (event in recentEvents) {
                        RecentActivityRow(
                            event = event,
                            onClick = { onNavigate("activity") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendAnalyticsSection(activityEvents: List<ActivityEvent>) {
    val scheme = MaterialTheme.colorScheme
    if (activityEvents.isEmpty()) {
        GuardRailCard {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "No trend analytics yet", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(text = "Run safety checks or let protection run — trends will appear after events are recorded.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                Text(text = "Summary is based on the latest 50 retained events, not necessarily all activity.", color = scheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
            }
        }
        return
    }
    val analytics = remember(activityEvents) { TrendAnalyticsCalculator.calculate(activityEvents) }
    val total = analytics.totalCount

    GuardRailCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Severity distribution — fixed SAFE, REVIEW, WARNING, HIGH_RISK
            Text(text = "Severity distribution", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((sev, count) in analytics.severityOrdered) {
                    val pct = analytics.severityPercents.firstOrNull { it.first == sev }?.second ?: 0
                    val sevColor = when (sev) { DataSeverity.SAFE -> GuardRailColors.Safe; DataSeverity.REVIEW -> GuardRailColors.Review; DataSeverity.WARNING -> GuardRailColors.Warning; DataSeverity.HIGH_RISK -> GuardRailColors.HighRisk }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50.dp)).background(sevColor))
                                Text(text = sev.label, color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(text = "$count · $pct%", color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50.dp)).background(scheme.outlineVariant)) {
                            if (total > 0 && count > 0) Box(modifier = Modifier.fillMaxWidth(count.toFloat() / total.toFloat()).height(6.dp).clip(RoundedCornerShape(50.dp)).background(sevColor))
                        }
                    }
                }
            }
            Text(text = "Total retained: $total", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)

            // Top 5 categories — horizontal bars
            Text(text = "Top safety categories", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val topCats = analytics.topCategories
            if (topCats.isEmpty()) {
                Text(text = "No categories recorded.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                val maxCat = topCats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cc in topCats) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = cc.category, color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text(text = cc.count.toString(), color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50.dp)).background(scheme.outlineVariant)) {
                                Box(modifier = Modifier.fillMaxWidth(cc.count.toFloat() / maxCat.toFloat()).height(8.dp).clip(RoundedCornerShape(50.dp)).background(GuardRailColors.Primary))
                            }
                        }
                    }
                }
            }

            // Detection source breakdown — only available counts
            Text(text = "Detection sources", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (analytics.sourceCounts.isEmpty()) {
                Text(text = "No detection sources recorded.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((src, cnt) in analytics.sourceCounts) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = src.label, color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(text = cnt.toString(), color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Insight
            Text(text = analytics.insight, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
            Text(text = "Summary is based on the latest 50 retained events, not necessarily all activity.", color = scheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun DailySummarySection(activityEvents: List<ActivityEvent>) {
    val scheme = MaterialTheme.colorScheme
    val summary = remember(activityEvents) { DailySummaryCalculator.calculate(activityEvents) }
    val todayTotal = summary.todayTotal
    val todayHighRisk = summary.todayHighRisk
    val todayWarning = summary.todayWarning
    val buckets = summary.sevenDayBuckets
    val allZero = buckets.all { it.count == 0 }

    if (activityEvents.isEmpty() || allZero) {
        GuardRailCard {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (activityEvents.isEmpty()) "No events yet" else "No incidents in the last 7 days", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(text = summary.insight, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                Text(text = "Summary is based on the latest 50 retained events, not necessarily all activity.", color = scheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
            }
        }
        return
    }
    GuardRailCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(modifier = Modifier.weight(1f), value = todayTotal.toString(), label = "Today total")
                StatCard(modifier = Modifier.weight(1f), value = todayHighRisk.toString(), label = "High-risk today")
                StatCard(modifier = Modifier.weight(1f), value = todayWarning.toString(), label = "Warnings today")
            }
            // Simple 7-day bar chart — local Compose, no chart lib
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                val max = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
                for (b in buckets) {
                    val h = (b.count * 48 / max).coerceAtLeast(if (b.count > 0) 6 else 2)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = if (b.count == 0) "" else b.count.toString(), color = scheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 12.sp)
                        Box(modifier = Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(6.dp)).background(if (b.count == 0) scheme.outlineVariant else GuardRailColors.Primary))
                        Text(text = b.label, color = scheme.onSurfaceVariant, fontSize = 9.sp, lineHeight = 10.sp)
                    }
                }
            }
            Text(text = summary.insight, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
            Text(text = "Summary is based on the latest 50 retained events, not necessarily all activity.", color = scheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun PermissionHintRow(label: String, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = scheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp
            )
            Text(
                text = hint,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(GuardRailColors.ProtectionInactive)
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = scheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )
        Text(
            text = label,
            color = scheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun RecentActivityRow(event: ActivityEvent, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = event.title.ifBlank { event.category },
                color = scheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.category,
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SeverityBadge(severity = event.severity.toUi())
            Text(
                text = formatTimeAgo(event.timestampMs),
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
