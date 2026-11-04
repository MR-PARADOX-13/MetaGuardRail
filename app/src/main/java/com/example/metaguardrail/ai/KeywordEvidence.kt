package com.example.metaguardrail.ai

import com.example.metaguardrail.data.SafetyCategory

/**
 * Supporting signal extracted from keyword/phrase matching.
 * Keywords alone must NOT drive HIGH_RISK — this is input to ContextAwareSafetyEngine.
 */
data class KeywordEvidence(
    val category: SafetyCategory?,
    val phrases: List<String>,
    val normalizedSpan: String,
    val customCategory: String? = null
)
