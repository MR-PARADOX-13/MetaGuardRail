package com.example.metaguardrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.AnalysisResult
import com.example.metaguardrail.ai.ContextAwareSafetyEngine
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.LinkSafetyAnalyzer
import com.example.metaguardrail.data.LinkSafetyResult
import com.example.metaguardrail.data.LinkStatus
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.Severity as DataSeverity
import com.example.metaguardrail.monitor.VisualContentClassifier
import com.example.metaguardrail.ui.ImageSafetyCard
import com.example.metaguardrail.ui.components.GuardRailCard
import com.example.metaguardrail.ui.components.GuardRailTopBar
import com.example.metaguardrail.ui.components.SectionLabel
import com.example.metaguardrail.ui.components.SeverityBadge
import com.example.metaguardrail.ui.components.Severity as UiSeverity
import com.example.metaguardrail.ui.theme.GuardRailColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun DataSeverity.toUi(): UiSeverity = when (this) {
    DataSeverity.SAFE -> UiSeverity.SAFE
    DataSeverity.REVIEW -> UiSeverity.REVIEW
    DataSeverity.WARNING -> UiSeverity.WARNING
    DataSeverity.HIGH_RISK -> UiSeverity.HIGH_RISK
}

private fun linkStatusColor(status: LinkStatus): Color = when (status) {
    LinkStatus.SAFE -> GuardRailColors.Safe
    LinkStatus.WARNING -> GuardRailColors.HighRisk
    LinkStatus.REVIEW -> GuardRailColors.Warning
    LinkStatus.UNKNOWN -> GuardRailColors.ProtectionInactive
    LinkStatus.INVALID -> GuardRailColors.ProtectionInactive
}

@Composable
fun AnalyzeScreen(
    settings: SafetySettings,
    onAddEvent: (ActivityEvent) -> Unit,
    visualClassifierProvider: () -> VisualContentClassifier?,
    sharedText: String = "",
    onSharedTextConsumed: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text Analyze", "Link Check", "Image")

    androidx.compose.runtime.LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) { selectedTab = 0 }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
    ) {
        GuardRailTopBar(title = "Analyze", subtitle = "On-device text, link, and image checks")
        if (sharedText.isNotBlank()) {
            Text(
                text = "Shared text ready — review and tap Check text.",
                color = GuardRailColors.Primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
            indicator = { tabPositions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = GuardRailColors.Primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    selectedContentColor = GuardRailColors.Primary,
                    unselectedContentColor = scheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> TextAnalyzeSection(settings = settings, onAddEvent = onAddEvent, initialText = sharedText, onConsumed = onSharedTextConsumed)
                1 -> LinkCheckSection()
                2 -> ImageCheckSection(visualClassifierProvider = visualClassifierProvider)
            }
        }
    }
}

@Composable
private fun TextAnalyzeSection(
    settings: SafetySettings,
    onAddEvent: (ActivityEvent) -> Unit,
    initialText: String = "",
    onConsumed: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf(initialText) }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val analyzer = remember { ContentSafetyAnalyzer() }
    val contextEngine = remember { ContextAwareSafetyEngine() }
    androidx.compose.runtime.LaunchedEffect(initialText) { if (initialText.isNotBlank()) { textInput = initialText; onConsumed() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(text = "Text safety")
        OutlinedTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                result = null
                reported = false
            },
            placeholder = { Text("Paste text to analyze…", color = scheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GuardRailColors.Primary,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                focusedTextColor = scheme.onSurface,
                unfocusedTextColor = scheme.onSurface
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
                    Button(
                onClick = {
                    if (textInput.isBlank()) return@Button
                    isChecking = true
                    scope.launch {
                        val res = withContext(Dispatchers.Default) { contextEngine.analyzeSync(textInput, settings) }
                        result = res
                        isChecking = false
                        onAddEvent(
                            ActivityEvent(
                                title = textInput.take(80),
                                category = res.categoryMatched?.label ?: "Text check",
                                severity = res.severity,
                                source = res.source,
                                explanation = res.explanation,
                                confidence = res.confidence
                            )
                        )
                    }
                },
                enabled = !isChecking && textInput.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GuardRailColors.Primary)
            ) {
                Text(
                    text = if (isChecking) "Checking…" else "Check text",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(
                onClick = {
                    textInput = ""
                    result = null
                    reported = false
                }
            ) {
                Text("Clear")
            }
        }

        if (result != null) {
            val res = result!!
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = res.riskLabel,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        SeverityBadge(severity = res.severity.toUi())
                    }
                    if (res.categoryMatched != null) {
                        Text(
                            text = "Category: ${res.categoryMatched.label}",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "Source: ${res.source.label}",
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (res.confidence != null) {
                        Text(
                            text = "Confidence: ${(res.confidence * 100).toInt()}%",
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "Action: ${res.recommendedAction.label}",
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (res.shouldShield) {
                        Text(
                            text = "Shield recommended — cover sensitive content",
                            color = GuardRailColors.HighRisk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = res.explanation,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                    if (!res.isSupportive) {
                        OutlinedButton(
                            onClick = { reported = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (reported) "Reported (local)" else "Report false result")
                        }
                        if (reported) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GuardRailColors.SafeMuted)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Feedback saved locally — thank you.",
                                    color = GuardRailColors.Safe,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GuardRailColors.SafeMuted)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "If you or someone you know needs help, contact a trusted crisis-support service or local emergency services.",
                                color = GuardRailColors.Safe,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCheckSection() {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var linkInput by remember { mutableStateOf("") }
    var linkResult by remember { mutableStateOf<LinkSafetyResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val analyzer = remember { LinkSafetyAnalyzer() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel(text = "Link safety")
        Text(
            text = "Paste a URL to check for phishing tricks. Offline only — link is never opened.",
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 16.sp
        )
        OutlinedTextField(
            value = linkInput,
            onValueChange = {
                linkInput = it
                linkResult = null
            },
            placeholder = { Text("https://example.com", color = scheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GuardRailColors.Primary,
                unfocusedBorderColor = scheme.outlineVariant,
                focusedContainerColor = scheme.surface,
                unfocusedContainerColor = scheme.surface,
                focusedTextColor = scheme.onSurface,
                unfocusedTextColor = scheme.onSurface
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (linkInput.isBlank()) return@Button
                    isChecking = true
                    scope.launch {
                        val res = withContext(Dispatchers.Default) { analyzer.analyze(linkInput) }
                        linkResult = res
                        isChecking = false
                    }
                },
                enabled = !isChecking && linkInput.isNotBlank(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GuardRailColors.Primary)
            ) {
                Text(
                    text = if (isChecking) "Checking…" else "Check link",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(onClick = { linkInput = ""; linkResult = null }) {
                Text("Clear")
            }
        }

        if (linkResult != null) {
            val res = linkResult!!
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = res.status.label,
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        StatusPill(text = res.status.label, color = linkStatusColor(res.status))
                    }
                    Text(
                        text = res.explanation,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Connection:",
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        when (res.https) {
                            true -> StatusPill(text = "HTTPS secure", color = GuardRailColors.Safe)
                            false -> StatusPill(text = "HTTP insecure", color = GuardRailColors.HighRisk)
                            null -> StatusPill(text = "No HTTPS info", color = GuardRailColors.ProtectionInactive)
                        }
                    }
                    Text(
                        text = "Unknown = cannot verify reputation without network. Link was not visited.",
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(scheme.surfaceContainer)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageCheckSection(visualClassifierProvider: () -> VisualContentClassifier?) {
    val classifier = remember { visualClassifierProvider() }
    ImageSafetyCard(
        visualClassifier = classifier,
        onResult = {}
    )
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}
