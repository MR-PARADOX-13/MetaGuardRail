package com.example.metaguardrail.ai

/**
 * Abstraction for Llama Guard inference. ExecuTorch on-device primary, cloud secondary.
 * Only official Meta models.
 */
data class LlamaGuardRawResult(
    val category: String, // e.g. "S1", "SAFE", or SafetyCategory name
    val severity: String, // SAFE/REVIEW/WARNING/HIGH_RISK
    val confidence: Float,
    val explanation: String,
    val modelId: String
)

interface LlamaGuardClassifier {
    suspend fun classify(contextWindow: String, evidence: List<KeywordEvidence>): LlamaGuardRawResult
    val modelId: String
}

/**
 * Stub classifier for Phase 1 scaffolding — delegates to heuristic until ExecuTorch model available.
 * Preserves existing behavior with context awareness; replaced when .pte is present.
 */
class HeuristicLlamaGuardClassifier : LlamaGuardClassifier {
    override val modelId: String = "heuristic-v1"

    override suspend fun classify(contextWindow: String, evidence: List<KeywordEvidence>): LlamaGuardRawResult {
        val lower = contextWindow.lowercase()
        val hasBenign = TextPreprocessor.hasEducationalContext(contextWindow) ||
            TextPreprocessor.hasFictionalContext(contextWindow) ||
            TextPreprocessor.hasQuotation(contextWindow) ||
            TextPreprocessor.isHypothetical(contextWindow)
        val hasNegation = TextPreprocessor.hasNegation(contextWindow)

        // Negation check first — "not hurt" should not be threat
        val hasNegationLocal = hasNegation
        // Direct threat patterns — high confidence (but not if negated)
        val threatPhrases = listOf("i am going to hurt you", "i will hurt you", "i will kill you", "how to attack", "here is how to")
        for (phrase in threatPhrases) {
            if (lower.contains(phrase) && !hasNegationLocal) {
                return LlamaGuardRawResult(evidence.firstOrNull()?.category?.name ?: "HARASSMENT", "HIGH_RISK", 0.88f, "Direct threatening language detected.", modelId)
            }
        }
        // Negated or benign context downgrade
        if (hasNegationLocal) {
            return LlamaGuardRawResult("SAFE", "SAFE", 0.72f, "Negated statement — no harmful intent.", modelId)
        }
        if (hasBenign && evidence.isNotEmpty()) {
            return LlamaGuardRawResult("SAFE", "SAFE", 0.72f, "Sensitive term in benign context (educational/fictional/quoted).", modelId)
        }
        // Fallback to evidence-based REVIEW
        if (evidence.isNotEmpty()) {
            return LlamaGuardRawResult(evidence.first().category?.name ?: "SAFE", "REVIEW", 0.55f, "Keyword evidence present; context unclear — review required.", modelId)
        }
        return LlamaGuardRawResult("SAFE", "SAFE", 0.92f, "No safety concern detected.", modelId)
    }
}
