package com.example.metaguardrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.tooling.preview.Preview
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
import com.example.metaguardrail.ui.components.neo.GuardRailCard
import com.example.metaguardrail.ui.components.neo.GuardRailPrimaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSecondaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSectionHeader
import com.example.metaguardrail.ui.components.neo.GuardRailStatCard
import com.example.metaguardrail.ui.components.neo.GuardRailStatusBadge
import com.example.metaguardrail.ui.theme.BrutalistTokens
import com.example.metaguardrail.ui.theme.GuardRailColors
import java.util.concurrent.TimeUnit

private fun DataSeverity.toBadgeColor() = when (this) {
    DataSeverity.SAFE -> GuardRailColors.BrutalMint
    DataSeverity.REVIEW -> GuardRailColors.BrutalWarning
    DataSeverity.WARNING -> GuardRailColors.BrutalWarning
    DataSeverity.HIGH_RISK -> GuardRailColors.BrutalRisk
}

private fun DataSeverity.toDotColor() = when (this) {
    DataSeverity.SAFE -> GuardRailColors.Safe
    DataSeverity.REVIEW -> GuardRailColors.Review
    DataSeverity.WARNING -> GuardRailColors.Warning
    DataSeverity.HIGH_RISK -> GuardRailColors.HighRisk
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
 * Home tab — Neo-Brutalist redesign.
 * Preserves all parameters and calculator logic; only UI restyled.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun HomeScreen(
    settings: SafetySettings,
    protectionStatus: LiveProtectionStatus,
    activityEvents: List<ActivityEvent>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme

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
        // ── Header: strong branded ──
        BrutalistHeader()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Safety status card
            SafetyStatusCard(
                protectionStatus = protectionStatus,
                onStart = onStart,
                onStop = onStop,
                onNavigateAnalyze = { onNavigate("analyze") }
            )

            // 2. Stats row — 3 stat cards
            GuardRailSectionHeader(title = "Activity overview")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GuardRailStatCard(
                    modifier = Modifier.weight(1f),
                    value = totalCount.toString(),
                    label = "TOTAL"
                )
                GuardRailStatCard(
                    modifier = Modifier.weight(1f),
                    value = flaggedCount.toString(),
                    label = "FLAGGED",
                    valueColor = GuardRailColors.HighRisk
                )
                GuardRailStatCard(
                    modifier = Modifier.weight(1f),
                    value = safeCount.toString(),
                    label = "SAFE",
                    valueColor = GuardRailColors.Safe
                )
            }

            // 3. Permissions notice card
            GuardRailSectionHeader(title = "Permissions")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "PERMISSION STATUS",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        letterSpacing = 0.5.sp
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

            // 4. Daily Protection Summary
            GuardRailSectionHeader(title = "Daily Protection Summary")
            DailySummarySection(activityEvents = activityEvents)

            // 5. Safety Trend Analytics
            GuardRailSectionHeader(title = "Safety Trend Analytics")
            TrendAnalyticsSection(activityEvents = activityEvents)

            // 6. Recent activity — timeline style
            GuardRailSectionHeader(
                title = "Recent activity",
                actionLabel = if (recentEvents.isNotEmpty()) "View all" else null,
                onActionClick = if (recentEvents.isNotEmpty()) ({ onNavigate("activity") }) else null
            )
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
                            fontWeight = FontWeight.Bold
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
                GuardRailCard {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        for ((index, event) in recentEvents.withIndex()) {
                            RecentActivityTimelineRow(
                                event = event,
                                isLast = index == recentEvents.lastIndex,
                                onClick = { onNavigate("activity") }
                            )
                        }
                    }
                }
            }

            // Bottom spacer for nav bar
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BrutalistHeader() {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(width = BrutalistTokens.BorderWidth, color = GuardRailColors.BrutalBlack)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(GuardRailColors.BrutalPrimary)
                    .border(2.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "MG", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "META GUARD RAIL",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    fontSize = 22.sp,
                    letterSpacing = 0.5.sp,
                    lineHeight = 22.sp
                )
                Text(
                    text = "Personal digital safety  •  On-device protection",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SafetyStatusCard(
    protectionStatus: LiveProtectionStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onNavigateAnalyze: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isRunning = protectionStatus.isRunning
    val badgeColor = if (isRunning) GuardRailColors.BrutalMint else GuardRailColors.BrutalWarning
    val badgeLabel = if (isRunning) "LIVE" else "PAUSED"

    GuardRailCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRunning) "LIVE PROTECTION ACTIVE" else "LIVE PROTECTION PAUSED",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    fontSize = 14.sp,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.weight(1f)
                )
                GuardRailStatusBadge(label = badgeLabel, color = badgeColor)
            }
            if (protectionStatus.message.isNotBlank()) {
                Text(
                    text = protectionStatus.message,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )
            }
            val finding = protectionStatus.finding
            if (!finding.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.surfaceVariant)
                        .border(1.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = finding,
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                }
            }
            // Primary action — 48dp electric blue
            if (isRunning) {
                GuardRailPrimaryButton(
                    text = "STOP PROTECTION",
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                GuardRailPrimaryButton(
                    text = "START PROTECTION",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            GuardRailSecondaryButton(
                text = "Go to Analyze →",
                onClick = onNavigateAnalyze,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionHintRow(label: String, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
            .background(scheme.surfaceVariant)
            .border(1.5.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
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
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(GuardRailColors.ProtectionInactive)
                .border(1.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(50.dp))
        )
    }
}

@Composable
private fun RecentActivityTimelineRow(event: ActivityEvent, isLast: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timeline dot + line
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(event.severity.toDotColor())
                    .border(1.5.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(50.dp))
            )
            if (!isLast) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(GuardRailColors.BrutalBlack.copy(alpha = 0.15f))
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = event.title.ifBlank { event.category },
                color = scheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
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
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GuardRailStatusBadge(label = event.severity.name, color = event.severity.toBadgeColor())
            Text(
                text = formatTimeAgo(event.timestampMs),
                color = scheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun TrendAnalyticsSection(activityEvents: List<ActivityEvent>) {
    val scheme = MaterialTheme.colorScheme
    if (activityEvents.isEmpty()) {
        GuardRailCard {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "No trend analytics yet", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
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
            Text(text = "Severity distribution", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((sev, count) in analytics.severityOrdered) {
                    val pct = analytics.severityPercents.firstOrNull { it.first == sev }?.second ?: 0
                    val sevColor = when (sev) { DataSeverity.SAFE -> GuardRailColors.Safe; DataSeverity.REVIEW -> GuardRailColors.Review; DataSeverity.WARNING -> GuardRailColors.Warning; DataSeverity.HIGH_RISK -> GuardRailColors.HighRisk }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50.dp)).background(sevColor).border(1.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(50.dp)))
                                Text(text = sev.label, color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "$count · $pct%", color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.outlineVariant).border(1.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(4.dp))) {
                            if (total > 0 && count > 0) Box(modifier = Modifier.fillMaxWidth(count.toFloat() / total.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(sevColor))
                        }
                    }
                }
            }
            Text(text = "Total retained: $total", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)

            Text(text = "Top safety categories", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            val topCats = analytics.topCategories
            if (topCats.isEmpty()) {
                Text(text = "No categories recorded.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                val maxCat = topCats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cc in topCats) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = cc.category, color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(text = cc.count.toString(), color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(scheme.outlineVariant).border(1.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(4.dp))) {
                                Box(modifier = Modifier.fillMaxWidth(cc.count.toFloat() / maxCat.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(GuardRailColors.BrutalPrimary))
                            }
                        }
                    }
                }
            }

            Text(text = "Detection sources", color = scheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            if (analytics.sourceCounts.isEmpty()) {
                Text(text = "No detection sources recorded.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((src, cnt) in analytics.sourceCounts) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = src.label, color = scheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(text = cnt.toString(), color = scheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

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
                Text(text = if (activityEvents.isEmpty()) "No events yet" else "No incidents in the last 7 days", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(text = summary.insight, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                Text(text = "Summary is based on the latest 50 retained events, not necessarily all activity.", color = scheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
            }
        }
        return
    }
    GuardRailCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrutalMiniStat(modifier = Modifier.weight(1f), value = todayTotal.toString(), label = "TODAY")
                BrutalMiniStat(modifier = Modifier.weight(1f), value = todayHighRisk.toString(), label = "HIGH-RISK")
                BrutalMiniStat(modifier = Modifier.weight(1f), value = todayWarning.toString(), label = "WARNINGS")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                val max = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
                for (b in buckets) {
                    val h = (b.count * 48 / max).coerceAtLeast(if (b.count > 0) 6 else 2)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = if (b.count == 0) "" else b.count.toString(), color = scheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 12.sp)
                        Box(modifier = Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(6.dp)).background(if (b.count == 0) scheme.outlineVariant else GuardRailColors.BrutalPrimary).border(1.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(6.dp)))
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
private fun BrutalMiniStat(modifier: Modifier = Modifier, value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
            .background(scheme.surfaceVariant)
            .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = GuardRailColors.BrutalBlack, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
        Text(text = label, color = scheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    }
}

@Preview(showBackground = true, name = "HomeScreen Light", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewHomeScreen() {
    com.example.metaguardrail.ui.theme.MetaGuardRailTheme(themeMode = com.example.metaguardrail.ui.theme.AppThemeMode.LIGHT) {
        HomeScreen(
            settings = com.example.metaguardrail.data.SafetySettings(),
            protectionStatus = com.example.metaguardrail.monitor.LiveProtectionStatus(isRunning = true),
            activityEvents = listOf(
                com.example.metaguardrail.data.ActivityEvent(title = "Flagged message", category = "Harassment", severity = com.example.metaguardrail.data.Severity.HIGH_RISK, source = com.example.metaguardrail.data.DetectionSource.LOCAL_RULE, explanation = "Preview"),
                com.example.metaguardrail.data.ActivityEvent(title = "Safe link", category = "Scam", severity = com.example.metaguardrail.data.Severity.SAFE, source = com.example.metaguardrail.data.DetectionSource.MUSE_SPARK, explanation = "OK")
            ),
            onStart = {}, onStop = {}, onNavigate = {}
        )
    }
}
