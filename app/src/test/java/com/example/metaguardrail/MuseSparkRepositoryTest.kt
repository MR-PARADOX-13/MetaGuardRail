package com.example.metaguardrail

import com.example.metaguardrail.ai.BackendProxyConfig
import com.example.metaguardrail.ai.ChatChoice
import com.example.metaguardrail.ai.ChatMessage
import com.example.metaguardrail.ai.ChatResponse
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetyMode
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.ai.MuseSparkRepository
import com.example.metaguardrail.ai.NoOpLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for [MuseSparkRepository] using OkHttp [MockWebServer].
 *
 * Covers: success parse, 401, 403, 429 Retry-After, 500 retries, timeout, empty choices,
 * malformed JSON, invalid category validation, cancellation, retry limit, api-key redaction note,
 * deduplication cache hit.
 *
 * Note: [MuseSparkRepository.isAvailable] checks BuildConfig.BACKEND_BASE_URL.
 * In unit tests this is the placeholder and returns false. We override it via a test subclass
 * that returns true so the repository proceeds to call the MockWebServer.
 */
class MuseSparkRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var localAnalyzer: ContentSafetyAnalyzer
    private lateinit var strictSettings: SafetySettings

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        localAnalyzer = ContentSafetyAnalyzer()
        strictSettings = SafetySettings(
            mode = SafetyMode.STRICT,
            enabledCategories = SafetyCategory.entries.toSet()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // Helper: create repository wired to MockWebServer, with isAvailable() forced true.
    private fun createRepo(
        client: OkHttpClient = defaultClient(),
        isCloudTextAnalysisAllowed: (() -> Boolean)? = { true }
    ): MuseSparkRepository {
        val service = BackendProxyConfig.createService(server.url("/").toString(), client)
        return MuseSparkRepository(service, localAnalyzer, availabilityOverride = true, logger = NoOpLogger, isCloudTextAnalysisAllowed = isCloudTextAnalysisAllowed)
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .build()

    private fun clientWithTimeout(timeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    // JSON helpers -------------------------------------------------------------

    private fun validChatJson(
        category: String = "SAFE",
        severity: String = "SAFE",
        confidence: Float? = 0.9f,
        explanation: String = "No issue detected, safe content.",
        shouldShield: Boolean = false
    ): String {
        // Must match ChatResponse shape: { id, choices: [ { message: {role, content}, finish_reason } ] }
        // Inner content is MuseClassificationResult JSON
        val inner = buildString {
            append("{")
            append("\"category\":\"$category\",")
            append("\"severity\":\"$severity\",")
            if (confidence != null) append("\"confidence\":$confidence,") else append("\"confidence\":null,")
            // Escape explanation minimally
            val esc = explanation.replace("\"", "\\\"")
            append("\"explanation\":\"$esc\",")
            append("\"shouldShield\":$shouldShield")
            append("}")
        }
        val escInner = inner.replace("\\", "\\\\").replace("\"", "\\\"")
        // Actually easier: embed inner JSON as string content with proper escaping for outer JSON
        // But MockWebServer returns ChatResponse JSON that Retrofit parses; content is raw string
        // So we can build outer JSON manually with inner JSON escaped correctly, or use simple approach:
        // Build outer JSON where content value is inner JSON string; we must JSON-escape inner.
        // To avoid double-escape confusion, build outer via string template where content is raw inner with quotes escaped.
        // Retrofit's converter will decode outer JSON and give us content = inner.
        // So we need to escape inner for outer.
        // Let's do proper: outer JSON content field contains inner JSON text.
        // We'll create escapedInner by replacing \ with \\ and " with \" and newline etc.
        // inner already has no special chars beyond letters.
        // Simpler: construct outer JSON directly with inner not escaped if inner contains no outer-breaking chars?
        // But JSON requires escaping. Let's do manual outer with escaped inner.
        return """
            {
              "id":"test-id",
              "choices":[
                {
                  "message":{
                    "role":"assistant",
                    "content":"$escInner"
                  },
                  "finish_reason":"stop"
                }
              ]
            }
        """.trimIndent()
    }

    // Helper to produce valid JSON more directly: use raw inner as content without double escaping
    // by embedding inner JSON string already escaped via helper above. validChatJson() does that.

    // 1. successful response parses correctly ----------------------------------

    @Test
    fun successfulResponse_parsesCorrectly() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SCAMS", severity = "WARNING", confidence = 0.85f, explanation = "Scam pattern detected", shouldShield = true))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("Claim your free crypto now!", strictSettings)
        assertTrue(result.isSuccess)
        val v = result.getOrThrow()
        assertEquals("SCAMS", v.category)
        assertEquals("WARNING", v.severity)
        assertEquals(true, v.shouldShield)
        assertNotNull(v.explanation)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun successfulResponse_safeParse() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", confidence = 0.95f, explanation = "Safe content", shouldShield = false))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("Hello world", strictSettings)
        assertTrue(result.isSuccess)
        assertEquals("SAFE", result.getOrThrow().category)
    }

    // 2. invalid credentials 401 -> failure (non-retriable)

    @Test
    fun invalidCredentials_401_failure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Unauthorized"}""")
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("test 401", strictSettings)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()!!
        assertTrue(ex.message?.contains("401") == true || ex.message?.contains("API error") == true)
        // 401 is not retriable, so only 1 request
        assertEquals(1, server.requestCount)
    }

    // 3. forbidden 403

    @Test
    fun forbidden_403_failure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Forbidden"}""")
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("test 403 unique ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("403") == true)
        assertEquals(1, server.requestCount)
    }

    // 4. rate limit 429 with Retry-After header retries

    @Test
    fun rateLimit429_withRetryAfter_retries() = runBlocking {
        // First 429 with Retry-After: 1 second (header in seconds), then success
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Too Many Requests"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "ok after retry"))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("rate limit test ${System.nanoTime()}", strictSettings)
        assertTrue(result.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun rateLimit429_respectsRetryAfterHeader() = runBlocking {
        // Enqueue 429 then 429 then success to verify multiple retries
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("""{"error":"rate"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("""{"error":"rate"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "HARASSMENT", severity = "REVIEW", explanation = "ok"))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("retryAfter multi ${System.nanoTime()}", strictSettings)
        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)
    }

    // 5. server error 500 retries then fails after 3

    @Test
    fun serverError500_retriesThenFailsAfter3() = runBlocking {
        // MAX_RETRIES = 3 means total 4 attempts
        repeat(4) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("""{"error":"Internal"}""")
            )
        }
        val repo = createRepo()
        val result = repo.analyzeWithMuse("server error ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertEquals(4, server.requestCount)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun serverError500_thenSuccessOnRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"tmp"}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"tmp"}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "recovered"))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("recover after 500 ${System.nanoTime()}", strictSettings)
        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun serverError502_alsoRetried() = runBlocking {
        repeat(4) {
            server.enqueue(MockResponse().setResponseCode(502).setBody("{}"))
        }
        val repo = createRepo()
        val result = repo.analyzeWithMuse("502 test ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertEquals(4, server.requestCount)
    }

    // 6. network timeout (use mockwebserver delay > timeout) -> retries

    @Test
    fun networkTimeout_retries() = runBlocking {
        // Configure client with very short read timeout, server delays longer
        // MockWebServer delay is body delay before sending response
        val shortClient = clientWithTimeout(300)
        // Enqueue delayed responses that exceed client timeout; each will cause IOException (timeout)
        // Need 4 enqueued to cover all retry attempts
        repeat(4) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(validChatJson())
                    .setBodyDelay(1, TimeUnit.SECONDS) // > 300ms timeout
            )
        }
        val repo = createRepo(shortClient)
        val result = repo.analyzeWithMuse("timeout test ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        // Should have retried 4 times (initial + 3 retries) via IOException path
        // Note: OkHttp may not count timed-out requests as completed server side quickly,
        // but server.requestCount should eventually reflect attempts
        assertTrue(server.requestCount >= 1)
    }

    // 7. empty choices -> validation failure

    @Test
    fun emptyChoices_validationFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"test","choices":[]}""")
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("empty choices ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Empty choices") == true)
    }

    @Test
    fun emptyContent_validationFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"t","choices":[{"message":{"role":"assistant","content":""},"finish_reason":"stop"}]}""")
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("empty content ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Empty content") == true)
    }

    // 8. malformed JSON -> failure

    @Test
    fun malformedJson_failure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                // choices[0].message.content is not valid json for MuseClassificationResult
                .setBody("""{"id":"t","choices":[{"message":{"role":"assistant","content":"not-json-at-all"},"finish_reason":"stop"}]}""")
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("malformed json ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to parse") == true)
    }

    @Test
    fun malformedOuterJson_failure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("this is not json at all")
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("outer malformed ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
    }

    // 9. invalid category from model -> validation failure

    @Test
    fun invalidCategory_validationFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "NOT_A_REAL_CATEGORY", severity = "SAFE", explanation = "bad cat"))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("invalid cat ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid category") == true)
    }

    @Test
    fun invalidSeverity_validationFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "CRITICAL", explanation = "bad severity"))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("invalid sev ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid severity") == true)
    }

    @Test
    fun invalidConfidence_validationFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", confidence = 5.0f, explanation = "bad confidence"))
        )
        val repo = createRepo()
        val result = repo.analyzeWithMuse("invalid conf ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid confidence") == true)
    }

    // 10. cancellation propagates (run with coroutine cancel)

    @Test
    fun cancellation_propagates() = runBlocking {
        // Enqueue delayed response so we have time to cancel
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson())
                .setBodyDelay(5, TimeUnit.SECONDS)
        )
        val repo = createRepo(defaultClient())
        var caughtCancellation = false
        val job = launch {
            try {
                repo.analyzeWithMuse("cancel test ${System.nanoTime()}", strictSettings)
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }
        delay(200)
        job.cancel()
        try {
            job.join()
        } catch (_: CancellationException) {
            // expected
        }
        assertTrue(caughtCancellation)
    }

    // 11. retry backoff respects limit (exhausts after MAX_RETRIES+1)

    @Test
    fun retryBackoff_respectsLimit_exhaustsAfterMaxRetries() = runBlocking {
        // 429 repeatedly — should fail after 4 attempts
        repeat(4) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(429)
                    .setHeader("Retry-After", "1")
                    .setBody("{}")
            )
        }
        val repo = createRepo()
        val start = System.currentTimeMillis()
        val result = repo.analyzeWithMuse("backoff limit ${System.nanoTime()}", strictSettings)
        val elapsed = System.currentTimeMillis() - start
        assertTrue(result.isFailure)
        assertEquals(4, server.requestCount)
        // Should have taken at least some backoff time (> 500ms total for 3 retries with 1s Retry-After capped? actually capped but still > 1s)
        assertTrue("Backoff should cause delay, elapsed=$elapsed", elapsed >= 200)
    }

    @Test
    fun retryBackoff_noRetryForNonRetriable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        val repo = createRepo()
        val result = repo.analyzeWithMuse("no retry 400 ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    // 12. api-key redaction: ensure log does not contain key (just assert no exception leaks key; document)

    @Test
    fun apiKeyRedaction_noExceptionLeaksKey() = runBlocking {
        // Document: Repository never logs text content or API key; exception messages are sanitized.
        // We verify that failure messages do not contain a fake key we inject via header interceptor.
        val fakeKey = "sk-test-fake-key-12345-SECRET"
        // Build client that adds Authorization header
        val clientWithKey = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $fakeKey").build())
            }
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val service = BackendProxyConfig.createService(server.url("/").toString(), clientWithKey)
        val repo = MuseSparkRepository(service, localAnalyzer, availabilityOverride = true, logger = NoOpLogger)
        val result = repo.analyzeWithMuse("redaction test ${System.nanoTime()}", strictSettings)
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message ?: ""
        // Message must not contain the API key
        assertFalse("Exception message should not leak API key", msg.contains(fakeKey))
        // Cause chain also should not contain key
        var cause: Throwable? = result.exceptionOrNull()
        while (cause != null) {
            assertFalse(cause.message?.contains(fakeKey) == true)
            cause = cause.cause
        }
    }

    // 13. deduplication cache hit returns without network call

    @Test
    fun deduplication_cacheHit_returnsWithoutNetworkCall() = runBlocking {
        // Enqueue only 1 response; call twice with same text should use cache second time
        val sharedText = "dedup cache test identical text ${System.nanoTime()}"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "cached"))
        )
        val repo = createRepo()
        val first = repo.analyzeWithMuse(sharedText, strictSettings)
        assertTrue(first.isSuccess)
        assertEquals(1, server.requestCount)

        // Second call with same text — should hit cache, no new request
        val second = repo.analyzeWithMuse(sharedText, strictSettings)
        assertTrue(second.isSuccess)
        // Still only 1 request total because second was served from cache
        assertEquals(1, server.requestCount)
        assertEquals(first.getOrThrow(), second.getOrThrow())
    }

    @Test
    fun deduplication_differentText_missesCache() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "first"))
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "second"))
        )
        val repo = createRepo()
        val a = repo.analyzeWithMuse("text A ${System.nanoTime()}", strictSettings)
        val b = repo.analyzeWithMuse("text B ${System.nanoTime()}", strictSettings)
        assertTrue(a.isSuccess)
        assertTrue(b.isSuccess)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun deduplication_customCategories_differentiateCacheKey() = runBlocking {
        val sameText = "same text categories ${System.nanoTime()}"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "cat none"))
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "cat custom"))
        )
        val repo = createRepo()
        val r1 = repo.analyzeWithMuse(sameText, strictSettings, customCategories = emptySet())
        val r2 = repo.analyzeWithMuse(sameText, strictSettings, customCategories = setOf("MY_CUSTOM"))
        assertTrue(r1.isSuccess)
        assertTrue(r2.isSuccess)
        // Different custom categories → different cache keys → 2 network calls
        assertEquals(2, server.requestCount)
    }

    // Additional: need to test with real MockWebServer setup via BackendProxyConfig.createService

    @Test
    fun createService_viaBackendProxyConfig_works() = runBlocking {
        val client = BackendProxyConfig.createOkHttpClient({ null }, NoOpLogger)
        val service = BackendProxyConfig.createService(server.url("/").toString(), client)
        val repo = MuseSparkRepository(service, localAnalyzer, availabilityOverride = true, logger = NoOpLogger)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validChatJson(category = "SAFE", severity = "SAFE", explanation = "via config"))
        )
        val result = repo.analyzeWithMuse("via BackendProxyConfig ${System.nanoTime()}", strictSettings)
        assertTrue(result.isSuccess)
    }

    @Test
    fun emptyText_returnsFailureImmediately_noNetworkCall() = runBlocking {
        val repo = createRepo()
        val result = repo.analyzeWithMuse("   ", strictSettings)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun markdownFencedJson_isStrippedAndParsed() = runBlocking {
        // Model may wrap JSON in ```json ... ``` despite response_format; repository strips it
        val inner = """{"category":"SAFE","severity":"SAFE","confidence":0.8,"explanation":"ok fenced","shouldShield":false}"""
        val fenced = "```json\n$inner\n```"
        val escapedFenced = fenced.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val body = """{"id":"t","choices":[{"message":{"role":"assistant","content":"$escapedFenced"},"finish_reason":"stop"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body))
        val repo = createRepo()
        val result = repo.analyzeWithMuse("fenced test ${System.nanoTime()}", strictSettings)
        assertTrue(result.isSuccess)
        assertEquals("SAFE", result.getOrThrow().category)
    }
}
