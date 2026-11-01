package com.example.metaguardrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetyMode
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.SpeechLanguage
import com.example.metaguardrail.data.UserPreferencesRepository
import com.example.metaguardrail.ui.components.GuardRailCard
import com.example.metaguardrail.ui.components.GuardRailTopBar
import com.example.metaguardrail.ui.components.SectionLabel
import com.example.metaguardrail.ui.theme.GuardRailColors
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(
    settings: SafetySettings,
    repository: UserPreferencesRepository,
    onModeSelected: (SafetyMode) -> Unit,
    onCategoryChanged: (SafetyCategory, Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var newCategoryInput by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        GuardRailTopBar(title = "Rules", subtitle = "Modes, language, and categories")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Safety mode chips first
            SectionLabel(text = "Safety mode")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose how strict content checks should be.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (mode in SafetyMode.entries) {
                            FilterChip(
                                selected = settings.mode == mode,
                                onClick = { onModeSelected(mode) },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 2. Playback speech language chips
            SectionLabel(text = "Playback audio language")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Selected language for live audio checks (when playback capture is allowed).",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (lang in SpeechLanguage.entries) {
                            FilterChip(
                                selected = settings.speechLanguage == lang,
                                onClick = {
                                    scope.launch { repository.setSpeechLanguage(lang) }
                                },
                                label = {
                                    Text(
                                        text = lang.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 3. All 7 categories with name/description/switch + severity hint
            SectionLabel(text = "Protection categories")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (category in SafetyCategory.entries) {
                        val enabled = category in settings.enabledCategories
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(scheme.surfaceContainer)
                                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.label,
                                        color = scheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 16.sp
                                    )
                                    Text(
                                        text = category.description,
                                        color = scheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = { checked -> onCategoryChanged(category, checked) }
                                )
                            }
                            // Severity hint — inferred from category
                            Text(
                                text = "Severity: ${severityHintForCategory(category)}",
                                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }

            // 4. Custom categories section
            SectionLabel(text = "Custom categories")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Add your own keywords to flag (max 5). 2-30 chars: a-z, 0-9, space, _ or -.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "They work together with your enabled categories.",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )

                    if (settings.customCategories.isEmpty()) {
                        Text(
                            text = "No custom categories yet.",
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        // Horizontal chips with X
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Wrap via Column with multiple Rows if needed — simple single row with manual wrap using Flow-like Column
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val chunks = settings.customCategories.toList().chunked(3)
                                for (chunk in chunks) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (cat in chunk) {
                                            AssistChip(
                                                onClick = {
                                                    scope.launch { repository.removeCustomCategory(cat) }
                                                },
                                                label = { Text(cat, fontSize = 12.sp) },
                                                trailingIcon = {
                                                    Text(
                                                        text = "✕",
                                                        color = scheme.onSurfaceVariant,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryInput,
                            onValueChange = {
                                newCategoryInput = it
                                customError = null
                            },
                            placeholder = { Text("e.g. crypto scam", color = scheme.onSurfaceVariant) },
                            modifier = Modifier.weight(1f),
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
                        Button(
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
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GuardRailColors.Primary)
                        ) {
                            Text(text = "Add", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (customError != null) {
                        Text(
                            text = customError!!,
                            color = GuardRailColors.HighRisk,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GuardRailColors.HighRiskMuted)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    Text(
                        text = "Max 5 custom categories. Names are lowercased and must be 2-30 chars.",
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

private fun severityHintForCategory(category: SafetyCategory): String = when (category) {
    SafetyCategory.SOLICITATION_GROOMING -> "High risk"
    SafetyCategory.SELF_HARM -> "Warning (supportive)"
    SafetyCategory.SCAMS -> "Warning"
    SafetyCategory.ADULT_CONTENT -> "Warning"
    SafetyCategory.DISTRESSING_CONTENT -> "Warning"
    SafetyCategory.HARASSMENT -> "Review"
    SafetyCategory.VULGAR_LANGUAGE -> "Review"
}
