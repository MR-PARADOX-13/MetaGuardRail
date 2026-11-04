package com.example.metaguardrail.ai

import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.Severity

/**
 * Precision-based risk engine: separates confidence vs severity.
 */
enum class RiskLevel(val label: String) {
    SAFE("Safe"),
    INFORMATIONAL("Informational"),
    REVIEW("Review needed"),
    WARNING("Warning"),
    HIGH_RISK("High risk");

    fun toSeverity(): Severity = when (this) {
        SAFE, INFORMATIONAL -> Severity.SAFE
        REVIEW -> Severity.REVIEW
        WARNING -> Severity.WARNING
        HIGH_RISK -> Severity.HIGH_RISK
    }
}

data class RiskThresholds(
    val informational: Float = 0.35f,
    val review: Float = 0.50f,
    val warning: Float = 0.70f,
    val highRisk: Float = 0.82f
)

object RiskEngine {

    private val perCategoryThresholds: Map<SafetyCategory, RiskThresholds> = mapOf(
        SafetyCategory.SELF_HARM to RiskThresholds(warning = 0.55f, highRisk = 0.75f),
        SafetyCategory.SOLICITATION_GROOMING to RiskThresholds(warning = 0.60f, highRisk = 0.78f),
        SafetyCategory.HARASSMENT to RiskThresholds(highRisk = 0.82f),
        SafetyCategory.DISTRESSING_CONTENT to RiskThresholds(highRisk = 0.85f)
    )

    fun thresholdsFor(category: SafetyCategory?): RiskThresholds =
        if (category != null) perCategoryThresholds[category] ?: RiskThresholds() else RiskThresholds()

    /**
     * Decides RiskLevel from confidence and context.
     * Rules: uncertainty → REVIEW not HIGH_RISK; stronger evidence for HIGH_RISK when benign context present.
     */
    fun decide(
        confidence: Float,
        category: SafetyCategory?,
        hasBenignContext: Boolean,
        isUncertainLanguage: Boolean = false
    ): RiskLevel {
        if (isUncertainLanguage) return RiskLevel.REVIEW
        val t = thresholdsFor(category)
        // If benign context (educational/news/fiction/negation/quotation) and confidence not very high, cap at REVIEW
        if (hasBenignContext) {
            if (confidence >= t.highRisk + 0.10f) return RiskLevel.HIGH_RISK
            if (confidence >= t.warning) return RiskLevel.WARNING
            if (confidence >= t.review) return RiskLevel.REVIEW
            if (confidence >= t.informational) return RiskLevel.INFORMATIONAL
            return RiskLevel.SAFE
        }
        if (confidence >= t.highRisk) return RiskLevel.HIGH_RISK
        if (confidence >= t.warning) return RiskLevel.WARNING
        if (confidence >= t.review) return RiskLevel.REVIEW
        if (confidence >= t.informational) return RiskLevel.INFORMATIONAL
        return RiskLevel.SAFE
    }

    fun needsShield(level: RiskLevel): Boolean = level == RiskLevel.WARNING || level == RiskLevel.HIGH_RISK
}
