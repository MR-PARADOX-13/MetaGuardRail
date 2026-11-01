package com.example.metaguardrail.ai

import com.example.metaguardrail.data.SafetyCategory
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat models for Baseten Muse Spark Open.
 * Backend proxy forwards to Baseten with same shape.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.2,
    val response_format: Map<String, String>? = mapOf("type" to "json_object")
)

@Serializable
data class ChatChoice(
    val message: ChatMessage,
    val finish_reason: String? = null
)

@Serializable
data class ChatUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0
)

@Serializable
data class ChatResponse(
    val id: String = "",
    val choices: List<ChatChoice>,
    val usage: ChatUsage? = null
)

/**
 * Structured classification returned by Muse Spark.
 * Category allowlist is SafetyCategory.entries names + any custom categories.
 * Severity allowlist is SAFE / REVIEW / WARNING / HIGH_RISK.
 */
@Serializable
data class MuseClassificationResult(
    val category: String,
    val severity: String,
    val confidence: Float? = null,
    val explanation: String,
    val shouldShield: Boolean = false
) {
    companion object {
        val ALLOWED_SEVERITIES: Set<String> = setOf("SAFE", "REVIEW", "WARNING", "HIGH_RISK")

        /**
         * Build allowlist for category validation.
         * Base is SafetyCategory.entries names + optional SAFE sentinel + custom categories.
         * SAFE is included as sentinel for non-flagged content even though it is not a SafetyCategory.
         */
        fun allowedCategories(customCategories: Set<String> = emptySet()): Set<String> {
            val base = SafetyCategory.entries.map { it.name }.toSet()
            // Include SAFE as valid category for "no issue" responses, plus any custom categories.
            return base + setOf("SAFE") + customCategories
        }

        fun isValidCategory(category: String, customCategories: Set<String> = emptySet()): Boolean {
            return allowedCategories(customCategories).contains(category.uppercase().trim())
        }

        fun isValidSeverity(severity: String): Boolean {
            return ALLOWED_SEVERITIES.contains(severity.uppercase().trim())
        }

        fun isValidConfidence(confidence: Float?): Boolean {
            if (confidence == null) return true // optional
            return confidence in 0.0f..1.0f
        }

        fun isValidShouldShield(value: Boolean?): Boolean = true // always valid boolean
    }

    /** Validate category against allowlist (SafetyCategory.names + customCategories + SAFE). */
    fun isValidCategory(customCategories: Set<String> = emptySet()): Boolean =
        isValidCategory(category, customCategories)

    /** Validate severity is one of SAFE/REVIEW/WARNING/HIGH_RISK (case-insensitive). */
    fun isValidSeverity(): Boolean = isValidSeverity(severity)

    /** Validate confidence is null or in 0..1. */
    fun isValidConfidence(): Boolean = isValidConfidence(confidence)

    /**
     * Full validation returning Result. Use for defensive parsing after decoding model JSON.
     * @param customCategories additional allowed categories supplied by caller
     */
    fun validate(customCategories: Set<String> = emptySet()): Result<Unit> {
        val normalizedCategory = category.uppercase().trim()
        val normalizedSeverity = severity.uppercase().trim()

        if (category.isBlank()) {
            return Result.failure(IllegalArgumentException("category must not be blank"))
        }
        if (!isValidCategory(customCategories)) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid category '$category'. Allowed: ${allowedCategories(customCategories).sorted()}"
                )
            )
        }
        if (severity.isBlank()) {
            return Result.failure(IllegalArgumentException("severity must not be blank"))
        }
        if (!isValidSeverity()) {
            return Result.failure(
                IllegalArgumentException(
                    "Invalid severity '$severity'. Allowed: $ALLOWED_SEVERITIES"
                )
            )
        }
        if (!isValidConfidence()) {
            return Result.failure(
                IllegalArgumentException("Invalid confidence $confidence. Must be in 0..1")
            )
        }
        if (explanation.isBlank()) {
            return Result.failure(IllegalArgumentException("explanation must not be blank"))
        }
        // shouldShield is boolean, no extra validation needed
        return Result.success(Unit)
    }

    /** Normalized copy with uppercase category/severity, trimmed explanation. */
    fun normalized(): MuseClassificationResult = copy(
        category = category.uppercase().trim(),
        severity = severity.uppercase().trim(),
        explanation = explanation.trim()
    )
}
