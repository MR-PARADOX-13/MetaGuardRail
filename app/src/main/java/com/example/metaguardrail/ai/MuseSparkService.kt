package com.example.metaguardrail.ai

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for Baseten Muse Spark Open (OpenAI-compatible).
 * Base URL is injected via BackendProxyConfig.createService().
 * Backend proxy should forward chat/completions to Baseten.
 */
interface MuseSparkService {

    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatRequest): ChatResponse
}
