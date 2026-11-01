package com.example.metaguardrail.ai

import com.example.metaguardrail.BuildConfig
import com.example.metaguardrail.data.AnalysisResult
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.LinkedHashMap
import kotlin.math.pow
import kotlin.random.Random

/**
 * Repository for Muse Spark Open classification via backend proxy / Baseten.
 */
open class MuseSparkRepository(
    private val service: MuseSparkService,
    private val localAnalyzer: ContentSafetyAnalyzer,
    private val modelId: String = BuildConfig.MUSE_SPARK_MODEL,
    private val availabilityOverride: Boolean? = null,
    private val logger: Logger = AndroidLogger,
    private val isCloudTextAnalysisAllowed: (() -> Boolean)? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private data class CacheEntry(val result: MuseClassificationResult, val timestampMs: Long)

    private val cacheLock = Any()
    private val dedupCache: LinkedHashMap<String, CacheEntry> = object : LinkedHashMap<String, CacheEntry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val rateLimitLock = Any()
    private val requestTimestampsMs: MutableList<Long> = mutableListOf()

    companion object {
        private const val TAG = "MuseSparkRepo"
        private const val MAX_CACHE_SIZE = 100
        private const val CACHE_TTL_MS = 60_000L
        private const val MAX_RETRIES = 3
        private const val RATE_LIMIT_PER_MINUTE = 6
        private const val RATE_WINDOW_MS = 60_000L
        private const val BASE_BACKOFF_MS = 500L
        private const val MAX_BACKOFF_MS = 8_000L
        private const val MAX_PROMPT_TEXT_LEN = 8000
    }

    open fun isAvailable(): Boolean = availabilityOverride ?: BackendProxyConfig.isBackendConfigured()

    /** For tests: clear rate limit and cache */
    fun clearCacheForTest() {
        synchronized(cacheLock) { dedupCache.clear() }
        synchronized(rateLimitLock) { requestTimestampsMs.clear() }
    }

    suspend fun analyzeWithMuse(
        text: String,
        settings: SafetySettings,
        customCategories: Set<String> = emptySet()
    ): Result<MuseClassificationResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("text must not be empty"))
        }
        if (isCloudTextAnalysisAllowed?.invoke() == false) {
            logger.d(TAG, "Cloud text analysis disabled by preference; local-only textLen=${trimmed.length}")
            return Result.failure(IllegalStateException("Cloud text analysis is disabled. Enable it in Settings to allow Muse Spark analysis."))
        }
        if (!isAvailable()) {
            logger.w(TAG, "Backend not configured; model=$modelId textLen=${trimmed.length}")
            return Result.failure(IllegalStateException("Backend not configured. Set backend.baseUrl in local.properties"))
        }

        val cacheKey = buildCacheKey(trimmed, settings, customCategories)
        synchronized(cacheLock) {
            val cached = dedupCache[cacheKey]
            if (cached != null) {
                val age = System.currentTimeMillis() - cached.timestampMs
                if (age <= CACHE_TTL_MS) {
                    logger.d(TAG, "Cache hit category=${cached.result.category} severity=${cached.result.severity} ageMs=$age textLen=${trimmed.length}")
                    return Result.success(cached.result)
                } else {
                    dedupCache.remove(cacheKey)
                }
            }
        }

        val rateLimited = synchronized(rateLimitLock) {
            val now = System.currentTimeMillis()
            requestTimestampsMs.removeAll { now - it > RATE_WINDOW_MS }
            if (requestTimestampsMs.size >= RATE_LIMIT_PER_MINUTE) {
                true
            } else {
                requestTimestampsMs.add(now)
                false
            }
        }
        if (rateLimited) {
            logger.w(TAG, "Client rate limit exceeded (6/min) textLen=${trimmed.length} model=$modelId")
            return Result.failure(IllegalStateException("Rate limited: max $RATE_LIMIT_PER_MINUTE requests per minute. Try again shortly."))
        }

        val prompt = buildPrompt(trimmed, customCategories)
        val request = ChatRequest(
            model = modelId,
            messages = listOf(
                ChatMessage(role = "system", content = buildSystemPrompt(settings, customCategories)),
                ChatMessage(role = "user", content = prompt)
            ),
            temperature = 0.2,
            response_format = mapOf("type" to "json_object")
        )

        var attempt = 0
        var lastError: Throwable? = null
        while (true) {
            try {
                logger.d(TAG, "Request attempt=${attempt + 1} model=$modelId textLen=${trimmed.length} enabledCategories=${settings.enabledCategories.size} customCategories=${customCategories.size}")
                val response = service.chatCompletions(request)
                val result = parseAndValidateResponse(response, customCategories)
                if (result.isFailure) {
                    val ex = result.exceptionOrNull()!!
                    logger.w(TAG, "Validation failed: ${ex.message} textLen=${trimmed.length}")
                    return Result.failure(ex)
                }
                val classified = result.getOrThrow()
                synchronized(cacheLock) {
                    dedupCache[cacheKey] = CacheEntry(classified, System.currentTimeMillis())
                }
                logger.d(TAG, "Success category=${classified.category} severity=${classified.severity} shouldShield=${classified.shouldShield} textLen=${trimmed.length}")
                return Result.success(classified)

            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                logger.w(TAG, "IOException attempt=${attempt + 1} type=${e.javaClass.simpleName} textLen=${trimmed.length}")
                if (attempt >= MAX_RETRIES) break
                val backoff = computeBackoff(attempt, retryAfterMs = null)
                delay(backoff)
                attempt++
                continue
            } catch (e: retrofit2.HttpException) {
                lastError = e
                val code = e.code()
                val retryAfterMs = parseRetryAfterMs(e)
                logger.w(TAG, "HTTP $code attempt=${attempt + 1} retryAfterMs=${retryAfterMs ?: -1} textLen=${trimmed.length} model=$modelId")
                val isRetriable = code == 429 || code in 500..599
                if (!isRetriable) {
                    return Result.failure(IOException("API error $code: ${e.message()}", e))
                }
                if (attempt >= MAX_RETRIES) break
                val backoff = computeBackoff(attempt, retryAfterMs)
                delay(backoff)
                attempt++
                continue
            } catch (e: Exception) {
                logger.w(TAG, "Non-retriable error ${e.javaClass.simpleName}: ${e.message} textLen=${trimmed.length}")
                return Result.failure(e)
            }
        }

        val msg = when (lastError) {
            is retrofit2.HttpException -> {
                val code = lastError.code()
                "API request failed after ${MAX_RETRIES + 1} attempts (HTTP $code). Last: ${lastError.message()}"
            }
            is IOException -> "Network error after ${MAX_RETRIES + 1} attempts: ${lastError.message}"
            else -> "API request failed after ${MAX_RETRIES + 1} attempts: ${lastError?.message}"
        }
        logger.w(TAG, "$msg textLen=${trimmed.length}")
        return Result.failure(IOException(msg, lastError))
    }

    fun toLocalFallback(text: String, settings: SafetySettings): AnalysisResult =
        localAnalyzer.analyzeContent(text, settings)

    private fun buildSystemPrompt(settings: SafetySettings, customCategories: Set<String>): String {
        val allowedCats = (MuseClassificationResult.allowedCategories(customCategories)).sorted().joinToString(", ")
        val enabledLabels = settings.enabledCategories.joinToString(", ") { it.name }
        return """
            You are a content safety classifier for Meta Guard Rail.
            You MUST return a single JSON object with exactly these fields:
              - "category": one of [$allowedCats]
              - "severity": one of [SAFE, REVIEW, WARNING, HIGH_RISK]
              - "confidence": number 0.0 to 1.0 (or null if uncertain)
              - "explanation": short 1-2 sentence reason, no disallowed content reproduction
              - "shouldShield": boolean (true if user should be warned/shielded, false otherwise)

            Strict rules:
            - Do NOT invent categories. Use ONLY the allowlist above. If no category applies, use "SAFE".
            - Do NOT reproduce or repeat harmful instructions. Summarize safely.
            - Severity mapping: SAFE=no issue, REVIEW=possible concern needs context, WARNING=likely issue, HIGH_RISK=severe/imminent harm.
            - For self-harm, never provide harmful detail; suggest supportive resources.
            - Respond with JSON only, no markdown, no extra keys.
            Enabled user categories: $enabledLabels
        """.trimIndent()
    }

    private fun buildPrompt(text: String, customCategories: Set<String>): String {
        val allowedCats = MuseClassificationResult.allowedCategories(customCategories).sorted().joinToString(", ")
        val safeText = if (text.length > MAX_PROMPT_TEXT_LEN) text.take(MAX_PROMPT_TEXT_LEN) + "…[truncated]" else text
        return """
            Classify the following user text for safety.

            Allowed categories: [$allowedCats]
            Allowed severities: [SAFE, REVIEW, WARNING, HIGH_RISK]

            User text to classify:
            <<<TEXT_START
            $safeText
            TEXT_END>>>

            Return JSON object with keys: category, severity, confidence, explanation, shouldShield.
            Remember: do NOT invent categories outside the allowlist. If unsure or no issue, return category="SAFE" severity="SAFE".
        """.trimIndent()
    }

    private fun parseAndValidateResponse(response: ChatResponse, customCategories: Set<String>): Result<MuseClassificationResult> {
        val choice = response.choices.firstOrNull()
            ?: return Result.failure(IllegalStateException("Empty choices in API response"))
        val rawContent = choice.message.content.trim()
        if (rawContent.isBlank()) {
            return Result.failure(IllegalStateException("Empty content in API response"))
        }
        val jsonString = extractJsonObject(rawContent)

        val decoded = try {
            json.decodeFromString<MuseClassificationResult>(jsonString)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Failed to parse model JSON: ${e.message}. Raw length=${rawContent.length}", e))
        }

        val normalized = decoded.normalized()
        val validation = normalized.validate(customCategories)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }
        return Result.success(normalized)
    }

    private fun extractJsonObject(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trimStart()
            val lastFence = s.lastIndexOf("```")
            if (lastFence >= 0) {
                s = s.substring(0, lastFence).trim()
            }
        }
        if (!s.startsWith("{")) {
            val start = s.indexOf('{')
            val end = s.lastIndexOf('}')
            if (start >= 0 && end > start) {
                s = s.substring(start, end + 1)
            }
        }
        return s
    }

    private fun computeBackoff(attempt: Int, retryAfterMs: Long?): Long {
        if (retryAfterMs != null && retryAfterMs > 0) {
            val capped = retryAfterMs.coerceAtMost(MAX_BACKOFF_MS)
            return capped + Random.nextLong(0, 200)
        }
        val exp = BASE_BACKOFF_MS * (2.0.pow(attempt.toDouble())).toLong()
        val jitter = Random.nextLong(0, 250)
        return (exp + jitter).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun parseRetryAfterMs(e: retrofit2.HttpException): Long? {
        return try {
            val header = e.response()?.headers()?.get("Retry-After") ?: return null
            header.trim().toLongOrNull()?.let { it * 1000L }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCacheKey(text: String, settings: SafetySettings, customCategories: Set<String>): String {
        val enabled = settings.enabledCategories.map { it.name }.sorted().joinToString(",")
        val custom = customCategories.sorted().joinToString(",")
        return "${text.hashCode()}|len=${text.length}|model=$modelId|enabled=$enabled|custom=$custom"
    }
}
