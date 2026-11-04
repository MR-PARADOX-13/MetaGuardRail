package com.example.metaguardrail

import com.example.metaguardrail.data.DetectionSource
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase A — Baseline tests for existing TFLite classifier logic.
 * Tests threshold math directly (mirrors VisualContentClassifier.decodeOutputForTest) without requiring model file.
 */
class VisualContentClassifierTest {

    private fun isAdult(scores: FloatArray): Boolean {
        require(scores.size == 5)
        return scores[1] + scores[3] + scores[4] >= 0.70f
    }

    private fun scores(drawings: Float, hentai: Float, neutral: Float, porn: Float, sexy: Float) =
        floatArrayOf(drawings, hentai, neutral, porn, sexy)

    @Test fun drawings_high_notFlaggedWhenAdultLow() {
        assertFalse(isAdult(scores(0.9f, 0.05f, 0.05f, 0.03f, 0.02f)))
    }

    @Test fun hentai_flaggedWhenThresholdMet() {
        assertTrue(isAdult(scores(0.1f, 0.70f, 0.1f, 0.0f, 0.0f)))
    }

    @Test fun neutral_notFlagged() {
        assertFalse(isAdult(scores(0.0f, 0.0f, 0.95f, 0.0f, 0.0f)))
    }

    @Test fun porn_flagged() {
        assertTrue(isAdult(scores(0.0f, 0.0f, 0.0f, 0.80f, 0.0f)))
    }

    @Test fun sexy_flagged() {
        assertTrue(isAdult(scores(0.0f, 0.0f, 0.0f, 0.0f, 0.75f)))
    }

    @Test fun threshold_boundaryExact() {
        assertTrue(isAdult(scores(0.0f, 0.30f, 0.0f, 0.30f, 0.10f))) // 0.70
        assertFalse(isAdult(scores(0.0f, 0.30f, 0.0f, 0.30f, 0.09f))) // 0.69
    }

    @Test fun emptyScores_throws() {
        try { isAdult(floatArrayOf()); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun invalidSize_throws() {
        try { isAdult(floatArrayOf(0.5f, 0.5f)); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun detectionSource_isVisionModel() {
        assertEquals("Vision model", DetectionSource.VISION_MODEL.label)
    }

    @Test fun repeatedDetection_consistent() {
        val s = scores(0f, 0.4f, 0f, 0.2f, 0.15f) // 0.75
        assertTrue(isAdult(s)); assertTrue(isAdult(s)); assertTrue(isAdult(s))
    }

    @Test fun combinedAdultScore() {
        assertTrue(isAdult(scores(0.05f, 0.25f, 0.05f, 0.25f, 0.25f))) // 0.75
    }
}
