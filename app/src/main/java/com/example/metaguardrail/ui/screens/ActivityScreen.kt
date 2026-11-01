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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.DetectionSource
import com.example.metaguardrail.data.ReportExporter
import com.example.metaguardrail.data.Severity as DataSeverity
import com.example.metaguardrail.ui.components.GuardRailCard
import com.example.metaguardrail.ui.components.GuardRailTopBar
import com.example.metaguardrail.ui.components.SectionLabel
import com.example.metaguardrail.ui.components.SeverityBadge
import com.example.metaguardrail.ui.components.Severity as UiSeverity
import com.example.metaguardrail.ui.theme.GuardRailColors
import java.util.concurrent.TimeUnit

private fun DataSeverity.toUi(): UiSeverity = when (this) {
    DataSeverity.SAFE -> UiSeverity.SAFE
    DataSeverity.REVIEW -> UiSeverity.REVIEW
    DataSeverity.WARNING -> UiSeverity.WARNING
    DataSeverity.HIGH_RISK -> UiSeverity.HIGH_RISK
}
private fun severityBorderColor(sev: DataSeverity) = when (sev) {
    DataSeverity.SAFE -> GuardRailColors.SafeBorder
    DataSeverity.REVIEW -> GuardRailColors.ReviewBorder
    DataSeverity.WARNING -> GuardRailColors.WarningBorder
    DataSeverity.HIGH_RISK -> GuardRailColors.HighRiskBorder
}
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
    Column(modifier = Modifier.fillMaxSize().background(scheme.background)) {
        GuardRailTopBar(title = "Activity", subtitle = "${events.size} events logged")
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SectionLabel(text = "Filter by severity") }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (sev in DataSeverity.entries) {
                        val sel = sev in selectedSeverities
                        FilterChip(selected = sel, onClick = { selectedSeverities = if (sel) selectedSeverities - sev else selectedSeverities + sev }, label = { Text(text = sev.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (sev) { DataSeverity.SAFE -> GuardRailColors.SafeMuted; DataSeverity.REVIEW -> GuardRailColors.ReviewMuted; DataSeverity.WARNING -> GuardRailColors.WarningMuted; DataSeverity.HIGH_RISK -> GuardRailColors.HighRiskMuted },
                                selectedLabelColor = when (sev) { DataSeverity.SAFE -> GuardRailColors.Safe; DataSeverity.REVIEW -> GuardRailColors.Review; DataSeverity.WARNING -> GuardRailColors.Warning; DataSeverity.HIGH_RISK -> GuardRailColors.HighRisk }))
                    }
                }
            }
            if (allCategories.isNotEmpty()) {
                item { SectionLabel(text = "Filter by category") }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (cat in allCategories) {
                            val sel = cat in selectedCategories
                            FilterChip(selected = sel, onClick = { selectedCategories = if (sel) selectedCategories - cat else selectedCategories + cat }, label = { Text(text = cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                }
            }
            if (allSources.isNotEmpty()) {
                item { SectionLabel(text = "Filter by detection source") }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (src in allSources) {
                            val sel = src in selectedSources
                            FilterChip(selected = sel, onClick = { selectedSources = if (sel) selectedSources - src else selectedSources + src }, label = { Text(text = src.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                }
            }
            if (events.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.weight(1f)) { Text("Clear activity") }
                        OutlinedButton(onClick = { exportLauncher.launch(ReportExporter.suggestedFileName()) }, modifier = Modifier.weight(1f)) { Text("Export JSON", fontSize = 12.sp) }
                        OutlinedButton(onClick = { confirmShare = true }, modifier = Modifier.weight(1f)) { Text("Share", fontSize = 12.sp) }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    GuardRailCard {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (events.isEmpty()) "No events yet" else "No matching events", color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(text = if (events.isEmpty()) "Run a check in Analyze or enable live protection to log events." else "Adjust filters to see more events.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { event -> ActivityCard(event = event, onClick = { detailEvent = event }) }
            }
        }
    }
    if (detailEvent != null) {
        val ev = detailEvent!!
        AlertDialog(onDismissRequest = { detailEvent = null }, title = { Text(ev.title.ifBlank { ev.category }, fontWeight = FontWeight.SemiBold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Category: ${ev.category}", fontSize = 12.sp); Text("Severity: ${ev.severity.label}", fontSize = 12.sp); Text("Source: ${ev.source.label}", fontSize = 12.sp); if (ev.confidence != null) Text("Confidence: ${(ev.confidence * 100).toInt()}%", fontSize = 12.sp); if (ev.explanation.isNotBlank()) Text(ev.explanation, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp) } },
            confirmButton = { TextButton(onClick = { detailEvent = null }) { Text("Close") } })
    }
    if (confirmClear) {
        AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear activity history?") }, text = { Text("Incident records will be deleted. This cannot be undone. Safety preferences and custom categories are not affected.", style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
    }
    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            title = { Text("Share activity report?", fontWeight = FontWeight.SemiBold) },
            text = { Text("This report contains user-derived text and safety labels from your activity history. Only share with someone you trust. The recipient will be able to see the content of flagged events.", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp) },
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

@Composable
private fun ActivityCard(event: ActivityEvent, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = severityBorderColor(event.severity)
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).border(1.dp, borderColor, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = formatTimeAgo(event.timestampMs), color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                SeverityBadge(severity = event.severity.toUi())
            }
            Text(text = event.title.ifBlank { event.category }, color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            Text(text = "Category: ${event.category}", color = scheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Source: ${event.source.label}", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                if (event.confidence != null) Text(text = "Confidence: ${(event.confidence * 100).toInt()}%", color = scheme.onSurfaceVariant, fontSize = 11.sp)
            }
            if (event.explanation.isNotBlank()) Text(text = event.explanation, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
        }
    }
}
