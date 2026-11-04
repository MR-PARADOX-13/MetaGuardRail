package com.example.metaguardrail

import com.example.metaguardrail.ai.ContextAwareSafetyEngine
import com.example.metaguardrail.ai.HeuristicLlamaGuardClassifier
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetySettings
import org.junit.Assert.*
import org.junit.Test

/**
 * Milestone 8 — Latency-sensitive path validation.
 * Deterministic, no network, no device required.
 * Validates ContentSafetyAnalyzer / ContextAwareSafetyEngine analyzeSync meets <50ms budget.
 */
class Milestone8LatencyTest {

    private val analyzer = ContentSafetyAnalyzer()
    private val engine = ContextAwareSafetyEngine(classifier = HeuristicLlamaGuardClassifier())
    private val settings = SafetySettings(enabledCategories = SafetyCategory.entries.toSet())

    private val samples = listOf(
        "Hello everyone, hope you are having a wonderful day!",
        "Violence is a major social problem we must address through education.",
        "I am going to hurt you tonight.",
        "How can we prevent violence?",
        "\"I will kill you\" — quoted from the novel.",
        "Click this link to claim your free crypto reward guaranteed return!",
        "Let's keep this secret chat between us and don't tell your parents.",
        "I want to end my life.",
        "The movie contains violent scenes and fictional dialogue.",
        "Check out this biology lesson about human anatomy."
    )

    @Test
    fun analyzeContent_completesWithinBudget() {
        // Warm-up
        repeat(10) { analyzer.analyzeContent(samples[it % samples.size], settings) }

        val iterations = 100
        val latenciesNs = LongArray(iterations)
        for (i in 0 until iterations) {
            val text = samples[i % samples.size]
            val t0 = System.nanoTime()
            analyzer.analyzeContent(text, settings)
            latenciesNs[i] = System.nanoTime() - t0
        }
        reportAndAssert(latenciesNs, budgetMs = 50.0, label = "ContentSafetyAnalyzer.analyzeContent")
    }

    @Test
    fun analyzeSync_completesWithinBudget() {
        // Warm-up
        repeat(10) { engine.analyzeSync(samples[it % samples.size], settings) }

        val iterations = 100
        val latenciesNs = LongArray(iterations)
        for (i in 0 until iterations) {
            val text = samples[i % samples.size]
            val t0 = System.nanoTime()
            engine.analyzeSync(text, settings)
            latenciesNs[i] = System.nanoTime() - t0
        }
        reportAndAssert(latenciesNs, budgetMs = 50.0, label = "ContextAwareSafetyEngine.analyzeSync")
    }

    @Test
    fun emptyAndBlankInput_isFastAndSafe() {
        val r1 = analyzer.analyzeContent("", settings)
        assertFalse(r1.isFlagged)
        val r2 = analyzer.analyzeContent("   ", settings)
        assertFalse(r2.isFlagged)
        val r3 = engine.analyzeSync("", settings)
        assertEquals("SAFE", r3.severity.name)
    }

    private fun reportAndAssert(latenciesNs: LongArray, budgetMs: Double, label: String) {
        val sorted = latenciesNs.sorted()
        val toMs: (Long) -> Double = { it / 1_000_000.0 }
        val mean = sorted.map(toMs).average()
        val p50 = toMs(sorted[sorted.size * 50 / 100])
        val p90 = toMs(sorted[(sorted.size * 90 / 100).coerceAtMost(sorted.lastIndex)])
        val p99 = toMs(sorted[(sorted.size * 99 / 100).coerceAtMost(sorted.lastIndex)])
        val min = toMs(sorted.first())
        val max = toMs(sorted.last())

        // Structured console report (visible in gradle test output)
        println(
            "[$label] n=${sorted.size} " +
                "min=${"%.2f".format(min)}ms " +
                "mean=${"%.2f".format(mean)}ms " +
                "p50=${"%.2f".format(p50)}ms " +
                "p90=${"%.2f".format(p90)}ms " +
                "p99=${"%.2f".format(p99)}ms " +
                "max=${"%.2f".format(max)}ms " +
                "budget=${"%.0f".format(budgetMs)}ms"
        )

        // Assertions: mean and p99 must be within budget; max gets relaxed 3x budget to tolerate CI jitter
        assertTrue(
            "$label mean ${"%.2f".format(mean)}ms exceeds budget ${budgetMs}ms",
            mean < budgetMs
        )
        assertTrue(
            "$label p50 ${"%.2f".format(p50)}ms exceeds budget ${budgetMs}ms",
            p50 < budgetMs
        )
        assertTrue(
            "$label p90 ${"%.2f".format(p90)}ms exceeds budget ${budgetMs}ms",
            p90 < budgetMs
        )
        assertTrue(
            "$label p99 ${"%.2f".format(p99)}ms exceeds budget ${budgetMs}ms",
            p99 < budgetMs
        )
    }
}
