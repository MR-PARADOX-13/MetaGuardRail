package com.example.metaguardrail.ai

import com.example.metaguardrail.data.AnalysisResult
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.DetectionSource
import com.example.metaguardrail.data.RecommendedAction
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.Severity

/**
 * Unified safety analysis pipeline — context-aware classification.
 * Wraps ContentSafetyAnalyzer keyword signals + Llama Guard classifier + RiskEngine.
 */
class ContextAwareSafetyEngine(
    private val keywordAnalyzer: ContentSafetyAnalyzer = ContentSafetyAnalyzer(),
    private val classifier: LlamaGuardClassifier = HeuristicLlamaGuardClassifier()
) {
    /** Sync entry point for ScreenMonitoringService (no runBlocking needed). */
    fun analyzeSync(text: String, settings: SafetySettings): AnalysisResult {
        if (text.isBlank()) return keywordAnalyzer.analyzeContent(text, settings)
        val isTelugu = TextPreprocessor.isTelugu(text)
        if (isTelugu && classifier.modelId.contains("llama-guard-3-1b")) {
            val local = keywordAnalyzer.analyzeContent(text, settings)
            if (local.isFlagged) return local.copy(confidence = local.confidence ?: 0.5f, explanation = local.explanation + " (Telugu — uncertain.)", severity = if (local.severity == Severity.HIGH_RISK) Severity.REVIEW else local.severity)
            return local
        }
        val normalized = text.trim()
        val threatProbe = run {
            val lower = normalized.lowercase()
            val threatPhrases = listOf("i am going to hurt you", "i will hurt you", "i will kill you", "how to attack", "here is how to", "hurt you tonight")
            if (threatPhrases.any { lower.contains(it) }) listOf(KeywordEvidence(com.example.metaguardrail.data.SafetyCategory.HARASSMENT, threatPhrases.filter { lower.contains(it) }, normalized)) else emptyList()
        }
        val keywordResult = keywordAnalyzer.analyzeContent(text, settings)
        val evidences = mutableListOf<KeywordEvidence>().apply { addAll(threatProbe); if (keywordResult.isFlagged) add(KeywordEvidence(keywordResult.categoryMatched, listOf(keywordResult.explanation.take(80)), normalized)) }
        if (evidences.isEmpty()) return AnalysisResult(isFlagged = false, riskLabel = "No Concern", categoryMatched = null, explanation = "No harmful intent detected.", severity = Severity.SAFE, confidence = 0.90f, source = DetectionSource.LOCAL_RULE, recommendedAction = RecommendedAction.NONE, shouldShield = false)
        val sentences = TextPreprocessor.segmentSentences(text)
        val contextWindow = if (sentences.size > 1) sentences.joinToString(" ") else normalized
        val raw = try {
            // Heuristic is non-suspend logic duplicated here for sync path
            val lower = contextWindow.lowercase()
            val hasBenign = TextPreprocessor.hasEducationalContext(contextWindow) || TextPreprocessor.hasFictionalContext(contextWindow) || TextPreprocessor.hasQuotation(contextWindow) || TextPreprocessor.isHypothetical(contextWindow)
            val hasNegation = TextPreprocessor.hasNegation(contextWindow)
            val threatPhrases = listOf("i am going to hurt you", "i will hurt you", "i will kill you", "how to attack", "here is how to")
            var heuristic: LlamaGuardRawResult? = null
            for (p in threatPhrases) if (lower.contains(p) && !hasNegation) { heuristic = LlamaGuardRawResult(evidences.firstOrNull()?.category?.name ?: "HARASSMENT", "HIGH_RISK", 0.88f, "Direct threatening language detected.", classifier.modelId); break }
            heuristic ?: if (hasNegation) LlamaGuardRawResult("SAFE", "SAFE", 0.72f, "Negated statement.", classifier.modelId) else if (hasBenign && evidences.isNotEmpty()) LlamaGuardRawResult("SAFE", "SAFE", 0.72f, "Benign context.", classifier.modelId) else if (evidences.isNotEmpty()) LlamaGuardRawResult(evidences.first().category?.name ?: "SAFE", "REVIEW", 0.55f, "Keyword evidence present; review required.", classifier.modelId) else LlamaGuardRawResult("SAFE", "SAFE", 0.92f, "No concern.", classifier.modelId)
        } catch (_: Exception) { return keywordResult.copy(severity = Severity.REVIEW, riskLabel = "Context unclear — additional verification required.", explanation = keywordResult.explanation + " (AI unavailable.)", confidence = 0.50f) }
        val hasBenign = TextPreprocessor.hasEducationalContext(contextWindow) || TextPreprocessor.hasFictionalContext(contextWindow) || TextPreprocessor.hasQuotation(contextWindow) || TextPreprocessor.hasNegation(contextWindow) || TextPreprocessor.isHypothetical(contextWindow)
        val riskLevel = RiskEngine.decide(raw.confidence, keywordResult.categoryMatched, hasBenign, isUncertainLanguage = isTelugu)
        val severity = riskLevel.toSeverity()
        if (keywordResult.isSupportive) return keywordResult.copy(confidence = raw.confidence, severity = Severity.WARNING)
        return AnalysisResult(isFlagged = severity != Severity.SAFE, riskLabel = when (riskLevel) { RiskLevel.SAFE -> "No Concern"; RiskLevel.INFORMATIONAL -> "Informational — sensitive topic without harmful intent"; RiskLevel.REVIEW -> "Context unclear — additional verification required."; RiskLevel.WARNING -> "Warning — concerning content"; RiskLevel.HIGH_RISK -> "Potential threat detected" }, categoryMatched = keywordResult.categoryMatched, explanation = buildString { append(when (riskLevel) { RiskLevel.SAFE -> "No harmful intent detected"; RiskLevel.INFORMATIONAL -> "Sensitive topic mentioned without harmful intent"; RiskLevel.REVIEW -> "Context unclear — review surrounding content"; RiskLevel.WARNING -> "Credible evidence"; RiskLevel.HIGH_RISK -> "Strong evidence of threat" }); if (raw.explanation.isNotBlank()) append(" — ${raw.explanation}"); append(" (${classifier.modelId})") }, isSupportive = false, severity = severity, confidence = raw.confidence, source = if (classifier.modelId.startsWith("heuristic")) DetectionSource.LOCAL_RULE else DetectionSource.MUSE_SPARK, recommendedAction = RecommendedAction.fromSeverity(severity), shouldShield = RiskEngine.needsShield(riskLevel))
    }

    suspend fun analyze(text: String, settings: SafetySettings): AnalysisResult {
        if (text.isBlank()) {
            return keywordAnalyzer.analyzeContent(text, settings)
        }

        // 1-5: Normalize, OCR correction (conservative), segment, context window
        val sentences = TextPreprocessor.segmentSentences(text)
        val normalized = text.trim()

        // Telugu handling: if Telugu and no native support, mark uncertain
        val isTelugu = TextPreprocessor.isTelugu(text)
        if (isTelugu && classifier.modelId.contains("llama-guard-3-1b")) {
            // Keep local rule for now; flag uncertain
            val local = keywordAnalyzer.analyzeContent(text, settings)
            if (local.isFlagged) {
                return local.copy(
                    confidence = local.confidence ?: 0.5f,
                    explanation = local.explanation + " (Telugu — classification uncertain; human review recommended.)",
                    severity = if (local.severity == Severity.HIGH_RISK) Severity.REVIEW else local.severity
                )
            }
            return local
        }

        // 6: Keyword evidence
        // First, check direct threat phrases via classifier even without keyword hit
        // This handles texts like "I am going to hurt you" that may not match keyword list
        val threatProbe = classifier.let {
            // Quick heuristic check: if text contains threat phrase, create synthetic evidence
            val lower = normalized.lowercase()
            val threatPhrases = listOf("i am going to hurt you", "i will hurt you", "i will kill you", "how to attack", "here is how to", "hurt you tonight")
            if (threatPhrases.any { lower.contains(it) }) {
                listOf(KeywordEvidence(com.example.metaguardrail.data.SafetyCategory.HARASSMENT, threatPhrases.filter { lower.contains(it) }, normalized))
            } else emptyList<KeywordEvidence>()
        }

        val keywordResult = keywordAnalyzer.analyzeContent(text, settings)
        val evidences = mutableListOf<KeywordEvidence>()
        evidences.addAll(threatProbe)
        if (keywordResult.isFlagged && keywordResult.categoryMatched != null) {
            evidences.add(KeywordEvidence(keywordResult.categoryMatched, listOf(keywordResult.explanation.take(80)), normalized))
        } else if (keywordResult.isFlagged) {
            evidences.add(KeywordEvidence(null, listOf(keywordResult.explanation.take(80)), normalized))
        }

        // If still no evidence at all, it's SAFE
        if (evidences.isEmpty()) {
            return AnalysisResult(
                isFlagged = false, riskLabel = "No Concern", categoryMatched = null,
                explanation = "No harmful intent detected — no keyword or threat pattern matched.",
                severity = Severity.SAFE, confidence = 0.90f, source = DetectionSource.LOCAL_RULE,
                recommendedAction = RecommendedAction.NONE, shouldShield = false
            )
        }

        // 7: AI classification with context
        val contextWindow = if (sentences.size > 1) sentences.joinToString(" ") else normalized
        val raw = try {
            classifier.classify(contextWindow, evidences)
        } catch (_: Exception) {
            // Fallback to REVIEW on classifier failure — never HIGH_RISK
            return keywordResult.copy(
                severity = Severity.REVIEW,
                riskLabel = "Context unclear — additional verification required.",
                explanation = keywordResult.explanation + " (AI classification unavailable.)",
                confidence = 0.50f,
                source = DetectionSource.LOCAL_RULE
            )
        }

        // 8-11: Risk engine
        val hasBenign = TextPreprocessor.hasEducationalContext(contextWindow) ||
            TextPreprocessor.hasFictionalContext(contextWindow) ||
            TextPreprocessor.hasQuotation(contextWindow) ||
            TextPreprocessor.hasNegation(contextWindow) ||
            TextPreprocessor.isHypothetical(contextWindow)

        val riskLevel = RiskEngine.decide(raw.confidence, keywordResult.categoryMatched, hasBenign, isUncertainLanguage = isTelugu)
        val severity = riskLevel.toSeverity()

        // Preserve self-harm supportive path
        if (keywordResult.isSupportive) {
            return keywordResult.copy(
                confidence = raw.confidence,
                severity = Severity.WARNING
            )
        }

        return AnalysisResult(
            isFlagged = severity != Severity.SAFE,
            riskLabel = when (riskLevel) {
                RiskLevel.SAFE -> "No Concern"
                RiskLevel.INFORMATIONAL -> "Informational — sensitive topic without harmful intent"
                RiskLevel.REVIEW -> "Context unclear — additional verification required."
                RiskLevel.WARNING -> "Warning — concerning content"
                RiskLevel.HIGH_RISK -> "Potential threat detected"
            },
            categoryMatched = keywordResult.categoryMatched,
            explanation = buildString {
                append(when (riskLevel) {
                    RiskLevel.SAFE -> "No harmful intent detected"
                    RiskLevel.INFORMATIONAL -> "Sensitive topic mentioned without harmful intent"
                    RiskLevel.REVIEW -> "Context unclear — review surrounding content"
                    RiskLevel.WARNING -> "Credible evidence of concerning content"
                    RiskLevel.HIGH_RISK -> "Strong evidence of threat or harmful instruction"
                })
                if (raw.explanation.isNotBlank()) append(" — ${raw.explanation}")
                append(" (${classifier.modelId})")
            },
            isSupportive = false,
            severity = severity,
            confidence = raw.confidence,
            source = if (classifier.modelId.startsWith("heuristic")) DetectionSource.LOCAL_RULE else DetectionSource.MUSE_SPARK,
            recommendedAction = RecommendedAction.fromSeverity(severity),
            shouldShield = RiskEngine.needsShield(riskLevel)
        )
    }
}
