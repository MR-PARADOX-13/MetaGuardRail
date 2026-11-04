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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.tooling.preview.Preview
import com.example.metaguardrail.data.Severity as DataSeverity
import com.example.metaguardrail.monitor.VisualContentClassifier
import com.example.metaguardrail.ui.ImageSafetyCard
import com.example.metaguardrail.ui.components.GuardRailTopBar
import com.example.metaguardrail.ui.components.neo.GuardRailPrimaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSecondaryButton
import com.example.metaguardrail.ui.theme.BrutalistTokens
import com.example.metaguardrail.ui.theme.GuardRailColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun DataSeverity.toUi(): com.example.metaguardrail.ui.components.Severity = when (this) {
    DataSeverity.SAFE -> com.example.metaguardrail.ui.components.Severity.SAFE
    DataSeverity.REVIEW -> com.example.metaguardrail.ui.components.Severity.REVIEW
    DataSeverity.WARNING -> com.example.metaguardrail.ui.components.Severity.WARNING
    DataSeverity.HIGH_RISK -> com.example.metaguardrail.ui.components.Severity.HIGH_RISK
}

private fun linkStatusColor(status: LinkStatus): Color = when (status) {
    LinkStatus.SAFE -> GuardRailColors.Safe
    LinkStatus.WARNING -> GuardRailColors.HighRisk
    LinkStatus.REVIEW -> GuardRailColors.Warning
    LinkStatus.UNKNOWN -> GuardRailColors.ProtectionInactive
    LinkStatus.INVALID -> GuardRailColors.ProtectionInactive
}

// ── Brutal primitives local to this screen ──
@Composable
private fun BrutalCard(
    modifier: Modifier = Modifier,
    bgColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = BrutalistTokens.ShadowOffset, y = BrutalistTokens.ShadowOffset)
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(Color.Black)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(bgColor)
                .border(2.dp, Color.Black, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .padding(16.dp)
        ) { content() }
    }
}

@Composable
private fun BrutalHintCard(text: String) {
    BrutalCard(bgColor = Color.White) {
        Text(text, color = GuardRailColors.BrutalBlack, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
    }
}

@Composable
private fun ResultBrutalCard(severity: DataSeverity, content: @Composable () -> Unit) {
    val bg = when (severity) {
        DataSeverity.SAFE -> Color(0xFFBBF7D0)
        DataSeverity.REVIEW, DataSeverity.WARNING -> Color(0xFFFEF08A)
        DataSeverity.HIGH_RISK -> Color(0xFFFECACA)
    }
    BrutalCard(bgColor = bg) { content() }
}

@Composable
private fun BrutalBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.5.dp, Color.Black, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text.uppercase(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
    }
}

@Composable
private fun BrutalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = 1,
    singleLine: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp)
                .padding(vertical = 8.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.Black),
            minLines = minLines,
            maxLines = if (singleLine) 1 else maxLines,
            singleLine = singleLine,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun BrutalSegmentedTabs(selected: Int, onSelected: (Int) -> Unit, tabs: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
            .background(Color.White),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF155EEF) else Color.White)
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                // shadow for selected tab
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF155EEF) else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyzeScreen(
    settings: SafetySettings,
    onAddEvent: (ActivityEvent) -> Unit,
    visualClassifierProvider: () -> VisualContentClassifier?,
    sharedText: String = "",
    onSharedTextConsumed: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text Analyze", "Link Check", "Image")

    androidx.compose.runtime.LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) { selectedTab = 0 }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
    ) {
        GuardRailTopBar(title = "Analyze", subtitle = "On-device text, link, and image checks")
        if (sharedText.isNotBlank()) {
            val isLink = isLinkOnly(sharedText)
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isLink) "This share contains a link only. Guard Rail does not open links. Paste the post text or caption here to check it." else "Shared text is ready. Review it, then tap Check text.",
                    color = Color(0xFF155EEF),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BrutalSegmentedTabs(selected = selectedTab, onSelected = { selectedTab = it }, tabs = tabs)
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
    val scope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf(initialText) }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    val analyzer = remember { ContentSafetyAnalyzer() }
    val contextEngine = remember { ContextAwareSafetyEngine() }
    androidx.compose.runtime.LaunchedEffect(initialText) { if (initialText.isNotBlank()) { textInput = initialText; onConsumed() } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("TEXT SAFETY", color = Color.Black, fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.ExtraBold)
        BrutalTextField(
            value = textInput,
            onValueChange = {
                textInput = it
                result = null
                reported = false
            },
            placeholder = "Paste text to analyze…",
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            singleLine = false
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GuardRailPrimaryButton(
                    text = if (isChecking) "Checking…" else "Check text",
                    onClick = {
                        if (textInput.isBlank()) return@GuardRailPrimaryButton
                        if (isLinkOnly(textInput)) {
                            result = AnalysisResult(
                                isFlagged = false,
                                riskLabel = "Link only — no text to analyze",
                                categoryMatched = null,
                                explanation = "This share contains a link only. Guard Rail does not open links. Paste the post text or caption here to check it.",
                                severity = DataSeverity.REVIEW,
                                source = com.example.metaguardrail.data.DetectionSource.LOCAL_RULE,
                                recommendedAction = com.example.metaguardrail.data.RecommendedAction.NONE
                            )
                            return@GuardRailPrimaryButton
                        }
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
                    enabled = !isChecking && textInput.isNotBlank()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                GuardRailSecondaryButton(
                    text = "Clear",
                    onClick = {
                        textInput = ""
                        result = null
                        reported = false
                    }
                )
            }
        }

        if (result == null && !isChecking) {
            BrutalHintCard("Paste text and tap Check text to see result here.")
        }

        if (result != null) {
            val res = result!!
            ResultBrutalCard(severity = res.severity) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = res.riskLabel,
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        BrutalBadge(
                            text = res.severity.label,
                            color = when (res.severity) {
                                DataSeverity.SAFE -> Color(0xFFBBF7D0)
                                DataSeverity.REVIEW -> Color(0xFFFEF08A)
                                DataSeverity.WARNING -> Color(0xFFFED7AA)
                                DataSeverity.HIGH_RISK -> Color(0xFFFECACA)
                            }
                        )
                    }
                    if (res.categoryMatched != null) {
                        Text(text = "Category: ${res.categoryMatched.label}", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(text = "Source: ${res.source.label}", color = Color(0xFF475569), fontSize = 11.sp)
                    if (res.confidence != null) {
                        Text(text = "Confidence: ${(res.confidence * 100).toInt()}%", color = Color(0xFF475569), fontSize = 11.sp)
                    }
                    Text(text = "Action: ${res.recommendedAction.label}", color = Color(0xFF475569), fontSize = 11.sp)
                    if (res.shouldShield) {
                        Text(text = "Shield recommended — cover sensitive content", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(text = res.explanation, color = Color.Black, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                    if (!res.isSupportive) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            GuardRailSecondaryButton(
                                text = if (reported) "Reported (local)" else "Report false result",
                                onClick = { reported = true }
                            )
                        }
                        if (reported) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFBBF7D0))
                                    .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(text = "Feedback saved locally — thank you.", color = Color.Black, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFBBF7D0))
                                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "If you or someone you know needs help, contact a trusted crisis-support service or local emergency services.",
                                color = Color.Black,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
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
    val scope = rememberCoroutineScope()
    var linkInput by remember { mutableStateOf("") }
    var linkResult by remember { mutableStateOf<LinkSafetyResult?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    val analyzer = remember { LinkSafetyAnalyzer() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("LINK SAFETY", color = Color.Black, fontSize = 10.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "Paste a URL to check for phishing tricks. Offline only — link is never opened.",
            color = Color(0xFF475569),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 16.sp
        )
        BrutalTextField(
            value = linkInput,
            onValueChange = {
                linkInput = it
                linkResult = null
            },
            placeholder = "https://example.com",
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                GuardRailPrimaryButton(
                    text = if (isChecking) "Checking…" else "Check link",
                    onClick = {
                        if (linkInput.isBlank()) return@GuardRailPrimaryButton
                        isChecking = true
                        scope.launch {
                            val res = withContext(Dispatchers.Default) { analyzer.analyze(linkInput) }
                            linkResult = res
                            isChecking = false
                        }
                    },
                    enabled = !isChecking && linkInput.isNotBlank()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                GuardRailSecondaryButton(text = "Clear", onClick = { linkInput = ""; linkResult = null })
            }
        }

        if (linkResult == null && !isChecking) {
            BrutalHintCard("Paste a URL and tap Check link to see result here.")
        }

        if (linkResult != null) {
            val res = linkResult!!
            val linkBg = when (res.status) {
                LinkStatus.SAFE -> Color(0xFFBBF7D0)
                LinkStatus.WARNING -> Color(0xFFFECACA)
                LinkStatus.REVIEW -> Color(0xFFFEF08A)
                LinkStatus.UNKNOWN, LinkStatus.INVALID -> Color.White
            }
            BrutalCard(bgColor = linkBg) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = res.status.label, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        StatusPill(text = res.status.label, color = linkStatusColor(res.status))
                    }
                    Text(text = res.explanation, color = Color.Black, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Connection:", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        when (res.https) {
                            true -> StatusPill(text = "HTTPS secure", color = GuardRailColors.Safe)
                            false -> StatusPill(text = "HTTP insecure", color = GuardRailColors.HighRisk)
                            null -> StatusPill(text = "No HTTPS info", color = GuardRailColors.ProtectionInactive)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Unknown = cannot verify reputation without network. Link was not visited.",
                            color = Color(0xFF475569),
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCheckSection(visualClassifierProvider: () -> VisualContentClassifier?) {
    val classifier = remember { visualClassifierProvider() }
    BrutalCard(bgColor = Color.White) {
        ImageSafetyCard(
            visualClassifier = classifier,
            onResult = {}
        )
    }
}

private fun isLinkOnly(value: String): Boolean {
    val t = value.trim()
    if (t.isEmpty()) return false
    val lower = t.lowercase()
    return (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) && !t.contains(" ") && !t.contains("\n")
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
        Text(text = text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
    }
}

@Preview(showBackground = true, name = "AnalyzeScreen Light", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewAnalyzeScreen() {
    com.example.metaguardrail.ui.theme.MetaGuardRailTheme(themeMode = com.example.metaguardrail.ui.theme.AppThemeMode.LIGHT) {
        AnalyzeScreen(settings = com.example.metaguardrail.data.SafetySettings(), onAddEvent = {}, visualClassifierProvider = { null })
    }
}
