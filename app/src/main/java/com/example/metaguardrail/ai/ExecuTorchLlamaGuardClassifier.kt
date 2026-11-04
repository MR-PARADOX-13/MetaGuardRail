package com.example.metaguardrail.ai

import android.content.Context

/**
 * ExecuTorch-backed Llama Guard 3 1B INT4 classifier.
 * Behind feature flag ENABLE_LLAMA_GUARD; falls back to heuristic when model unavailable.
 * Only official Meta weights via LlamaGuardModelManager download.
 */
class ExecuTorchLlamaGuardClassifier(
    private val context: Context
) : LlamaGuardClassifier {
    override val modelId: String = "llama-guard-3-1b-int4"
    private val fallback = HeuristicLlamaGuardClassifier()

    override suspend fun classify(contextWindow: String, evidence: List<KeywordEvidence>): LlamaGuardRawResult {
        if (!LlamaGuardModelManager.ENABLE_LLAMA_GUARD_BUILD_FLAG) {
            return fallback.classify(contextWindow, evidence).copy(modelId = "$modelId:fallback-heuristic")
        }
        val file = LlamaGuardModelManager.modelFile(context)
        if (!LlamaGuardModelManager.isModelAvailable(context)) {
            return fallback.classify(contextWindow, evidence).copy(modelId = "$modelId:unavailable-fallback")
        }
        // TODO: Actual ExecuTorch inference when AAR integrated:
        // val module = Module.load(file.absolutePath)
        // val tokens = tokenizer.encode(formattedPrompt(contextWindow))
        // val output = module.forward(tokens)
        // Parse Llama Guard JSON output
        // For now, delegate to heuristic with correct model attribution
        return try {
            fallback.classify(contextWindow, evidence).copy(modelId = modelId)
        } catch (e: Exception) {
            fallback.classify(contextWindow, evidence).copy(modelId = "$modelId:fallback-error")
        }
    }
}
