package com.example.metaguardrail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.ui.components.neo.GuardRailCard
import com.example.metaguardrail.ui.components.neo.GuardRailPrimaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSecondaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSeverityCard
import com.example.metaguardrail.ui.components.neo.GuardRailStatCard
import com.example.metaguardrail.ui.components.neo.GuardRailStatusBadge
import com.example.metaguardrail.ui.components.neo.GuardRailToggleRow
import com.example.metaguardrail.ui.components.Severity
import com.example.metaguardrail.ui.components.SeverityBadge
import com.example.metaguardrail.ui.theme.GuardRailColors
import com.example.metaguardrail.ui.theme.MetaGuardRailTheme
import com.example.metaguardrail.ui.theme.AppThemeMode

// ── Wrapper helper ──
@Composable
private fun PreviewWrapper(
    dark: Boolean = false,
    content: @Composable () -> Unit
) {
    MetaGuardRailTheme(themeMode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            content()
        }
    }
}

// ── 1. All severity badges ──
@Preview(name = "Severity Badges - Light", showBackground = true, widthDp = 400, heightDp = 320)
@Composable
fun PreviewSeverityBadges() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Severity Badges", style = MaterialTheme.typography.titleMedium)
            SeverityBadge(severity = Severity.SAFE)
            SeverityBadge(severity = Severity.REVIEW)
            SeverityBadge(severity = Severity.WARNING)
            SeverityBadge(severity = Severity.HIGH_RISK)
            Spacer(Modifier.height(8.dp))
            // With neo status badges for comparison
            GuardRailStatusBadge(label = "SAFE", color = GuardRailColors.BrutalMint)
            GuardRailStatusBadge(label = "WARNING", color = GuardRailColors.BrutalWarning)
            GuardRailStatusBadge(label = "HIGH RISK", color = GuardRailColors.BrutalRisk)
        }
    }
}

@Preview(name = "Severity Badges - Dark", showBackground = true, widthDp = 400, heightDp = 320)
@Composable
fun PreviewSeverityBadgesDark() {
    PreviewWrapper(dark = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Severity Badges — Dark", style = MaterialTheme.typography.titleMedium, color = Color.White)
            SeverityBadge(severity = Severity.SAFE)
            SeverityBadge(severity = Severity.REVIEW)
            SeverityBadge(severity = Severity.WARNING)
            SeverityBadge(severity = Severity.HIGH_RISK)
        }
    }
}

// ── 2. All button variants ──
@Preview(name = "Buttons - Light", showBackground = true, widthDp = 400, heightDp = 300)
@Composable
fun PreviewButtons() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Button Variants", style = MaterialTheme.typography.titleMedium)
            GuardRailPrimaryButton(text = "Primary Button", onClick = {})
            GuardRailSecondaryButton(text = "Secondary Button", onClick = {})
            GuardRailPrimaryButton(text = "Disabled", onClick = {}, enabled = false)
            GuardRailSecondaryButton(text = "Disabled Secondary", onClick = {}, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                GuardRailPrimaryButton(text = "Yes", onClick = {}, modifier = Modifier.weight(1f))
                GuardRailSecondaryButton(text = "No", onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── 3. Card variations ──
@Preview(name = "Cards - Light", showBackground = true, widthDp = 400, heightDp = 700)
@Composable
fun PreviewCards() {
    PreviewWrapper {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Card Variations", style = MaterialTheme.typography.titleMedium)
            GuardRailCard {
                Text("Simple card content", style = MaterialTheme.typography.bodyMedium)
            }
            GuardRailSeverityCard(title = "Safe content", severity = "SAFE", description = "No issues detected in this content.")
            GuardRailSeverityCard(title = "Review suggested", severity = "WARNING", description = "This may need human review.")
            GuardRailSeverityCard(title = "Blocked", severity = "HIGH_RISK", description = "High-risk content detected.")
            GuardRailStatCard(value = "1,234", label = "Total scans")
            GuardRailStatCard(value = "98%", label = "Safe rate", valueColor = GuardRailColors.Safe)
            GuardRailStatCard(value = "7", label = "Flagged", valueColor = GuardRailColors.HighRisk)
        }
    }
}

// ── 4. Toggle rows ──
@Preview(name = "Toggles - Light", showBackground = true, widthDp = 400, heightDp = 400)
@Composable
fun PreviewToggles() {
    PreviewWrapper {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Toggle Rows", style = MaterialTheme.typography.titleMedium)
            GuardRailToggleRow(title = "Enable protection", subtitle = "Monitor content in real time", checked = true, onCheckedChange = {})
            GuardRailToggleRow(title = "Notifications", subtitle = "Get alerts for threats", checked = false, onCheckedChange = {})
            GuardRailToggleRow(title = "Dark mode", checked = true, onCheckedChange = {})
            GuardRailToggleRow(title = "Auto-block", subtitle = "Block high-risk content", checked = false, onCheckedChange = {})
        }
    }
}

@Preview(name = "Toggles - Dark", showBackground = true, widthDp = 400, heightDp = 400)
@Composable
fun PreviewTogglesDark() {
    PreviewWrapper(dark = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Toggle Rows — Dark", style = MaterialTheme.typography.titleMedium, color = Color.White)
            GuardRailToggleRow(title = "Enable protection", subtitle = "Monitor content", checked = true, onCheckedChange = {})
            GuardRailToggleRow(title = "Notifications", checked = false, onCheckedChange = {})
        }
    }
}

// ── 5. Combined showcase (QA overview) ──
@Preview(name = "QA Showcase - Light", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
fun PreviewBrutalistShowcase() {
    PreviewWrapper {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Brutalist QA Showcase", style = MaterialTheme.typography.titleLarge)
            // Badges row
            Text("Badges", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(severity = Severity.SAFE)
                SeverityBadge(severity = Severity.WARNING)
                SeverityBadge(severity = Severity.HIGH_RISK)
            }
            // Buttons
            Text("Buttons", style = MaterialTheme.typography.titleSmall)
            GuardRailPrimaryButton(text = "Primary Action", onClick = {})
            GuardRailSecondaryButton(text = "Secondary Action", onClick = {})
            // Card
            Text("Cards", style = MaterialTheme.typography.titleSmall)
            GuardRailCard { Text("Card body text goes here.", style = MaterialTheme.typography.bodyMedium) }
            GuardRailSeverityCard(title = "Example finding", severity = "WARNING", description = "Sample description for QA.")
            // Toggle
            Text("Toggles", style = MaterialTheme.typography.titleSmall)
            GuardRailToggleRow(title = "Feature toggle", subtitle = "Description", checked = true, onCheckedChange = {})
            // Stat
            Text("Stats", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GuardRailStatCard(value = "42", label = "Items", modifier = Modifier.weight(1f))
                GuardRailStatCard(value = "3", label = "Alerts", valueColor = GuardRailColors.Warning, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(name = "QA Showcase - Dark", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
fun PreviewBrutalistShowcaseDark() {
    PreviewWrapper(dark = true) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Brutalist QA Showcase — Dark", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityBadge(severity = Severity.SAFE)
                SeverityBadge(severity = Severity.WARNING)
                SeverityBadge(severity = Severity.HIGH_RISK)
            }
            GuardRailPrimaryButton(text = "Primary Action", onClick = {})
            GuardRailSecondaryButton(text = "Secondary Action", onClick = {})
            GuardRailCard { Text("Card body in dark mode.", style = MaterialTheme.typography.bodyMedium) }
            GuardRailToggleRow(title = "Feature toggle", checked = true, onCheckedChange = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GuardRailStatCard(value = "42", label = "Items", modifier = Modifier.weight(1f))
                GuardRailStatCard(value = "3", label = "Alerts", valueColor = GuardRailColors.Warning, modifier = Modifier.weight(1f))
            }
        }
    }
}
