package com.example.metaguardrail
import org.junit.Test
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import com.example.metaguardrail.ai.MuseSparkRepository
import com.example.metaguardrail.ai.BackendProxyConfig
import com.example.metaguardrail.ai.NoOpLogger
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import com.example.metaguardrail.data.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
class MuseSparkConsentTest {
    private fun validJson() = """{"id":"t","choices":[{"message":{"role":"assistant","content":"{\"category\":\"SAFE\",\"severity\":\"SAFE\",\"confidence\":0.9,\"explanation\":\"ok\",\"shouldShield\":false}"},"finish_reason":"stop"}]}"""
    @Test fun consentOff_zeroRequests() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val svc = BackendProxyConfig.createService(server.url("/").toString(), OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build())
            val repo = MuseSparkRepository(svc, ContentSafetyAnalyzer(), availabilityOverride=true, logger=NoOpLogger, isCloudTextAnalysisAllowed={false})
            val res = repo.analyzeWithMuse("hello ${System.nanoTime()}", SafetySettings(mode=SafetyMode.STRICT, enabledCategories=SafetyCategory.entries.toSet()))
            assertTrue(res.isFailure); assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }
    @Test fun consentOn_permitsRequest() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type","application/json").setBody(validJson()))
            val svc = BackendProxyConfig.createService(server.url("/").toString(), OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).build())
            val repo = MuseSparkRepository(svc, ContentSafetyAnalyzer(), availabilityOverride=true, logger=NoOpLogger, isCloudTextAnalysisAllowed={true})
            val res = repo.analyzeWithMuse("hello2 ${System.nanoTime()}", SafetySettings(mode=SafetyMode.STRICT, enabledCategories=SafetyCategory.entries.toSet()))
            assertTrue(res.isSuccess); assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
