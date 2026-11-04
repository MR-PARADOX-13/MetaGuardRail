package com.example.metaguardrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetyMode
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.SpeechLanguage
import com.example.metaguardrail.data.UserPreferencesRepository
import com.example.metaguardrail.ui.components.neo.GuardRailCard
import com.example.metaguardrail.ui.components.neo.GuardRailOutlinedTextField
import com.example.metaguardrail.ui.components.neo.GuardRailPrimaryButton
import com.example.metaguardrail.ui.components.neo.GuardRailSectionHeader
import com.example.metaguardrail.ui.theme.BrutalistTokens
import com.example.metaguardrail.ui.theme.GuardRailColors
import kotlinx.coroutines.launch

// ── Internal: hard-shadow wrapper ──
@Composable
private fun BrutalShadowBox(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = BrutalistTokens.CornerRadius,
    shadowOffset: androidx.compose.ui.unit.Dp = BrutalistTokens.ShadowOffset,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(RoundedCornerShape(cornerRadius))
                .background(GuardRailColors.BrutalBlack)
        )
        content()
    }
}

// ── Brutalist segmented pill chip ──
@Composable
private fun BrutalPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) GuardRailColors.BrutalPrimary else GuardRailColors.BrutalSurface
    val fg = if (selected) Color.White else GuardRailColors.BrutalBlack
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(50.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 32.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.3.sp
        )
    }
}

// ── Severity hint pill ──
@Composable
private fun SeverityPill(text: String) {
    val bg = when (text) {
        "High risk" -> GuardRailColors.BrutalRisk
        "Warning (supportive)", "Warning" -> GuardRailColors.BrutalWarning
        else -> GuardRailColors.BrutalMint // Review
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.5.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = GuardRailColors.BrutalBlack,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp
        )
    }
}

// ── Custom category brutalist pill with X ──
@Composable
private fun CustomCategoryPill(
    label: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color.White)
            .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(50.dp))
            .clickable(onClick = onRemove)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .semantics { contentDescription = "Remove $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = GuardRailColors.BrutalBlack,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(GuardRailColors.BrutalBlack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RulesScreen(
    settings: SafetySettings,
    repository: UserPreferencesRepository,
    onModeSelected: (SafetyMode) -> Unit,
    onCategoryChanged: (SafetyCategory, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var newCategoryInput by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GuardRailColors.BrutalBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header — bold Neo-Brutalist title
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "RULES",
                color = GuardRailColors.BrutalBlack,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                letterSpacing = 1.sp
            )
            Text(
                text = "Modes, language, and categories",
                color = GuardRailColors.BrutalSecondaryText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp
            )
        }

        // 1. Safety mode
        GuardRailSectionHeader(
            title = "Safety Mode",
            subtitle = "Choose how strict content checks should be."
        )
        GuardRailCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (mode in SafetyMode.entries) {
                    // Selected chip gets shadow wrapper
                    val isSelected = settings.mode == mode
                    if (isSelected) {
                        BrutalShadowBox(
                            cornerRadius = 50.dp,
                            shadowOffset = 2.dp
                        ) {
                            BrutalPillChip(
                                label = mode.label,
                                selected = true,
                                onClick = { onModeSelected(mode) }
                            )
                        }
                    } else {
                        BrutalPillChip(
                            label = mode.label,
                            selected = false,
                            onClick = { onModeSelected(mode) }
                        )
                    }
                }
            }
        }

        // 2. Playback audio language
        GuardRailSectionHeader(
            title = "Playback Audio",
            subtitle = "Selected language for live audio checks (when playback capture is allowed)."
        )
        GuardRailCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (lang in SpeechLanguage.entries) {
                    val isSelected = settings.speechLanguage == lang
                    if (isSelected) {
                        BrutalShadowBox(cornerRadius = 50.dp, shadowOffset = 2.dp) {
                            BrutalPillChip(
                                label = lang.label,
                                selected = true,
                                onClick = { scope.launch { repository.setSpeechLanguage(lang) } }
                            )
                        }
                    } else {
                        BrutalPillChip(
                            label = lang.label,
                            selected = false,
                            onClick = { scope.launch { repository.setSpeechLanguage(lang) } }
                        )
                    }
                }
            }
        }

        // 3. Protection categories
        GuardRailSectionHeader(
            title = "Protection Categories",
            subtitle = "Toggle categories to customize your shield."
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (category in SafetyCategory.entries) {
                val enabled = category in settings.enabledCategories
                val severityLabel = severityHintForCategory(category)
                BrutalShadowBox {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                            .background(Color.White)
                            .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Icon placeholder — geometric square
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (enabled) GuardRailColors.BrutalPrimary else GuardRailColors.BrutalSurfaceVariant)
                                .border(1.5.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (enabled) Color.White else GuardRailColors.BrutalBlack)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = category.label,
                                color = GuardRailColors.BrutalBlack,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = category.description,
                                color = GuardRailColors.BrutalSecondaryText,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            SeverityPill(text = severityLabel)
                        }

                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked -> onCategoryChanged(category, checked) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
                                .semantics { contentDescription = "${category.label} toggle" },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = GuardRailColors.BrutalPrimary,
                                checkedBorderColor = GuardRailColors.BrutalBlack,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFE2E8F0),
                                uncheckedBorderColor = GuardRailColors.BrutalBlack
                            )
                        )
                    }
                }
            }
        }

        // 4. Custom categories
        GuardRailSectionHeader(
            title = "Custom Categories",
            subtitle = "Add your own keywords to flag (max 5). 2–30 chars: a-z, 0-9, space, _ or -."
        )
        GuardRailCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "They work together with your enabled categories.",
                    color = GuardRailColors.BrutalSecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 16.sp
                )

                if (settings.customCategories.isEmpty()) {
                    Text(
                        text = "No custom categories yet.",
                        color = GuardRailColors.BrutalSecondaryText.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (cat in settings.customCategories) {
                            CustomCategoryPill(label = cat, onRemove = {
                                scope.launch { repository.removeCustomCategory(cat) }
                            })
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GuardRailOutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = {
                            newCategoryInput = it
                            customError = null
                        },
                        placeholder = "e.g. crypto scam",
                        modifier = Modifier.weight(1f)
                    )
                    // Small brutalist Add button
                    Box(modifier = Modifier.width(80.dp).height(BrutalistTokens.ButtonHeightSm)) {
                        GuardRailPrimaryButton(
                            text = "ADD",
                            onClick = {
                                scope.launch {
                                    val res = repository.addCustomCategory(newCategoryInput)
                                    if (res.isFailure) {
                                        customError = res.exceptionOrNull()?.message ?: "Could not add category"
                                    } else {
                                        customError = null
                                        newCategoryInput = ""
                                    }
                                }
                            }
                        )
                    }
                }

                if (customError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                            .background(GuardRailColors.BrutalRisk)
                            .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = customError!!,
                            color = GuardRailColors.BrutalBlack,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    text = "Max 5 custom categories. Names are lowercased and must be 2-30 chars.",
                    color = GuardRailColors.BrutalSecondaryText.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 14.sp
                )
            }
        }
        // Bottom padding for nav bar
        Spacer(modifier = Modifier.height(12.dp))
    }
}

fun severityHintForCategory(category: SafetyCategory): String = when (category) {
    SafetyCategory.SOLICITATION_GROOMING -> "High risk"
    SafetyCategory.SELF_HARM -> "Warning (supportive)"
    SafetyCategory.SCAMS -> "Warning"
    SafetyCategory.ADULT_CONTENT -> "Warning"
    SafetyCategory.DISTRESSING_CONTENT -> "Warning"
    SafetyCategory.HARASSMENT -> "Review"
    SafetyCategory.VULGAR_LANGUAGE -> "Review"
}

@Preview(showBackground = true, name = "RulesScreen Light", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewRulesScreen() {
    com.example.metaguardrail.ui.theme.MetaGuardRailTheme(themeMode = com.example.metaguardrail.ui.theme.AppThemeMode.LIGHT) {
        RulesScreen(settings = com.example.metaguardrail.data.SafetySettings(), repository = com.example.metaguardrail.data.UserPreferencesRepository(androidx.compose.ui.platform.LocalContext.current), onModeSelected = {}, onCategoryChanged = { _, _ -> })
    }
}
