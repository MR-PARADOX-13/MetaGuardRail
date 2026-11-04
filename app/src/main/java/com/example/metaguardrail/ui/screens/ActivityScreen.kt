package com.example.metaguardrail.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.DetectionSource
import com.example.metaguardrail.data.ReportExporter
import com.example.metaguardrail.data.Severity as DataSeverity
import com.example.metaguardrail.ui.components.neo.GuardRailCard
import com.example.metaguardrail.ui.components.neo.GuardRailPrimaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSecondaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSectionHeader
import com.example.metaguardrail.ui.components.neo.GuardRailStatusBadge
import com.example.metaguardrail.ui.theme.BrutalistTokens
import com.example.metaguardrail.ui.theme.GuardRailColors
import java.util.concurrent.TimeUnit

private fun DataSeverity.toBadgeLabel(): String = when (this) {
    DataSeverity.SAFE -> "SAFE"
    DataSeverity.REVIEW -> "REVIEW"
    DataSeverity.WARNING -> "WARNING"
    DataSeverity.HIGH_RISK -> "HIGH RISK"
}

private fun DataSeverity.stripeColor(): Color = when (this) {
    DataSeverity.SAFE -> GuardRailColors.BrutalMint // #BBF7D0
    DataSeverity.REVIEW -> GuardRailColors.BrutalWarning // #FEF08A
    DataSeverity.WARNING -> Color(0xFFFDBA74) // orange muted
    DataSeverity.HIGH_RISK -> GuardRailColors.BrutalRisk // #FECACA
}

private fun DataSeverity.badgeColor(): Color = stripeColor()

private fun formatTimeAgo(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
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

@Composable
fun ActivityScreen(
    events: List<ActivityEvent>,
    onClear: () -> Unit,
    highlightEventId: String? = null,
    onHighlightConsumed: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var selectedSeverities by remember { mutableStateOf(setOf(DataSeverity.SAFE, DataSeverity.REVIEW, DataSeverity.WARNING, DataSeverity.HIGH_RISK)) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedSources by remember { mutableStateOf(setOf<DetectionSource>()) }
    var detailEvent by remember { mutableStateOf<ActivityEvent?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmShare by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val json = ReportExporter.buildReportJson(events)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            } catch (_: Exception) {}
        }
    }

    val allCategories = remember(events) { events.map { it.category }.distinct().sorted() }
    val allSources = remember(events) { events.map { it.source }.distinct().sortedBy { it.name } }

    val filtered = remember(events, selectedSeverities, selectedCategories, selectedSources) {
        events.filter { ev ->
            (selectedSeverities.isEmpty() || ev.severity in selectedSeverities) &&
                (selectedCategories.isEmpty() || ev.category in selectedCategories) &&
                (selectedSources.isEmpty() || ev.source in selectedSources)
        }.sortedByDescending { it.timestampMs }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(highlightEventId, events) {
        if (!highlightEventId.isNullOrBlank()) {
            val target = events.firstOrNull { it.id == highlightEventId }
            if (target != null) {
                detailEvent = target
                val idx = filtered.indexOfFirst { it.id == highlightEventId }
                if (idx >= 0) listState.scrollToItem(idx)
                onHighlightConsumed?.invoke()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardRailColors.BrutalBg)
    ) {
        // Brutalist top bar - thick outline bottom border + bold header
        BrutalistTopBar(title = "Activity", subtitle = "${events.size} events logged")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { GuardRailSectionHeader(title = "Filter by severity") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (sev in DataSeverity.entries) {
                        val sel = sev in selectedSeverities
                        BrutalFilterChip(
                            label = sev.label,
                            selected = sel,
                            onClick = { selectedSeverities = if (sel) selectedSeverities - sev else selectedSeverities + sev },
                            selectedColor = when (sev) {
                                DataSeverity.SAFE -> GuardRailColors.BrutalMint
                                DataSeverity.REVIEW -> GuardRailColors.BrutalWarning
                                DataSeverity.WARNING -> Color(0xFFFDBA74)
                                DataSeverity.HIGH_RISK -> GuardRailColors.BrutalRisk
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (allCategories.isNotEmpty()) {
                item { GuardRailSectionHeader(title = "Filter by category") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (cat in allCategories) {
                            val sel = cat in selectedCategories
                            BrutalFilterChip(
                                label = cat,
                                selected = sel,
                                onClick = { selectedCategories = if (sel) selectedCategories - cat else selectedCategories + cat },
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }
            if (allSources.isNotEmpty()) {
                item { GuardRailSectionHeader(title = "Filter by detection source") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (src in allSources) {
                            val sel = src in selectedSources
                            BrutalFilterChip(
                                label = src.label,
                                selected = sel,
                                onClick = { selectedSources = if (sel) selectedSources - src else selectedSources + src },
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }
            if (events.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GuardRailSecondaryButton(
                            text = "Clear",
                            onClick = { confirmClear = true },
                            modifier = Modifier.weight(1f)
                        )
                        GuardRailPrimaryButton(
                            text = "Export JSON",
                            onClick = { exportLauncher.launch(ReportExporter.suggestedFileName()) },
                            modifier = Modifier.weight(1f)
                        )
                        GuardRailSecondaryButton(
                            text = "Share",
                            onClick = { confirmShare = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    BrutalistEmptyState(isNoEvents = events.isEmpty())
                }
            } else {
                itemsIndexed(filtered, key = { _, it -> it.id }) { index, event ->
                    TimelineEventRow(
                        event = event,
                        isFirst = index == 0,
                        isLast = index == filtered.lastIndex,
                        onClick = { detailEvent = event }
                    )
                }
            }
        }
    }
    if (detailEvent != null) {
        val ev = detailEvent!!
        AlertDialog(
            onDismissRequest = { detailEvent = null },
            title = { Text(ev.title.ifBlank { ev.category }, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Category: ${ev.category}", fontSize = 12.sp)
                    Text("Severity: ${ev.severity.label}", fontSize = 12.sp)
                    Text("Source: ${ev.source.label}", fontSize = 12.sp)
                    if (ev.confidence != null) Text(
                        "Confidence: ${(ev.confidence * 100).toInt()}% (threshold varies by category)",
                        fontSize = 12.sp
                    )
                    if (ev.explanation.isNotBlank()) Text(
                        ev.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                    if (!ev.classificationMethod.isNullOrBlank()) Text(
                        "Method: ${ev.classificationMethod}" + (ev.modelVersion?.let { " ($it)" } ?: ""),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { detailEvent = null }) { Text("Close") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear activity history?", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "Incident records will be deleted. This cannot be undone. Safety preferences and custom categories are not affected.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("Share activity report?", fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This report contains user-derived text and safety labels from your activity history. Only share with someone you trust. The recipient will be able to see the content of flagged events.",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    try {
                        val json = ReportExporter.buildReportJson(events)
                        val file = ReportExporter.prepareShareCacheFile(context, json)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share report"))
                    } catch (_: Exception) {}
                }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text("Cancel") } }
        )
    }
}

// ── Brutalist header bar ──
@Composable
private fun BrutalistTopBar(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(width = BrutalistTokens.BorderWidth, color = GuardRailColors.BrutalBlack)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 0.5.sp),
            color = GuardRailColors.BrutalBlack
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = GuardRailColors.BrutalMutedText
        )
    }
}

// ── Brutal pill filter chip ──
@Composable
private fun BrutalFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = GuardRailColors.BrutalPrimary
) {
    // Outer shadow + chip
    Box(modifier = modifier.height(BrutalistTokens.MinTouchTarget)) {
        // hard shadow
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 2.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GuardRailColors.BrutalBlack)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(if (selected) selectedColor else Color.White)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(24.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (selected) {
                    // Use black text on light selected backgrounds, white on primary blue
                    if (selectedColor == GuardRailColors.BrutalPrimary) Color.White else GuardRailColors.BrutalBlack
                } else {
                    GuardRailColors.BrutalBlack
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

// ── Timeline row with vertical line + dot ──
@Composable
private fun TimelineEventRow(
    event: ActivityEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline gutter: vertical line + dot
        Box(
            modifier = Modifier
                .width(20.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Vertical line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(96.dp)
                    .background(GuardRailColors.BrutalBlack)
                    .align(Alignment.TopCenter)
            )
            // Dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(event.severity.stripeColor())
                    .border(2.dp, GuardRailColors.BrutalBlack, CircleShape)
                    .align(Alignment.TopCenter)
            )
        }
        Spacer(Modifier.width(6.dp))
        BrutalistEventCard(event = event, onClick = onClick, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BrutalistEventCard(
    event: ActivityEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val stripe = event.severity.stripeColor()
    // Hard shadow wrapper
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = BrutalistTokens.ShadowOffset, y = BrutalistTokens.ShadowOffset)
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(GuardRailColors.BrutalBlack)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(scheme.surface)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .clickable(onClick = onClick)
        ) {
            // Left severity stripe 4dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(120.dp)
                    .background(stripe)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimeAgo(event.timestampMs),
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    GuardRailStatusBadge(label = event.severity.toBadgeLabel(), color = event.severity.badgeColor())
                }
                Text(
                    text = event.title.ifBlank { event.category },
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 18.sp
                )
                Text(
                    text = "Category: ${event.category}",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Source: ${event.source.label}", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                    if (event.confidence != null) Text(
                        text = "Confidence: ${(event.confidence * 100).toInt()}%",
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                if (event.explanation.isNotBlank()) Text(
                    text = event.explanation,
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun BrutalistEmptyState(isNoEvents: Boolean) {
    GuardRailCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon placeholder: dashed brutal box with text
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GuardRailColors.BrutalBg)
                    .border(2.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "◯", fontSize = 22.sp, fontWeight = FontWeight.Black, color = GuardRailColors.BrutalBlack)
            }
            Text(
                text = if (isNoEvents) "No events yet" else "No matching events",
                color = GuardRailColors.BrutalBlack,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
            Text(
                text = if (isNoEvents) "Run a check in Analyze or enable live protection to log events." else "Adjust filters to see more events.",
                color = GuardRailColors.BrutalMutedText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 16.sp
            )
        }
    }
}

@Preview(showBackground = true, name = "ActivityScreen Light", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewActivityScreen() {
    com.example.metaguardrail.ui.theme.MetaGuardRailTheme(themeMode = com.example.metaguardrail.ui.theme.AppThemeMode.LIGHT) {
        ActivityScreen(events = listOf(com.example.metaguardrail.data.ActivityEvent(title = "Flagged", category = "Scam", severity = com.example.metaguardrail.data.Severity.HIGH_RISK, source = com.example.metaguardrail.data.DetectionSource.LOCAL_RULE, explanation = "Preview")), onClear = {})
    }
}
