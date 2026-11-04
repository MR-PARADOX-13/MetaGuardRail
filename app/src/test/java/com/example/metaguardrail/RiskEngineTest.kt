package com.example.metaguardrail

import com.example.metaguardrail.ai.RiskEngine
import com.example.metaguardrail.ai.RiskLevel
import com.example.metaguardrail.data.SafetyCategory
import org.junit.Assert.*
import org.junit.Test

class RiskEngineTest {
    @Test fun safe_lowConfidence() { assertEquals(RiskLevel.SAFE, RiskEngine.decide(0.1f, null, false)) }
    @Test fun highRisk_highConfidenceNoBenign() { assertEquals(RiskLevel.HIGH_RISK, RiskEngine.decide(0.90f, null, false)) }
    @Test fun uncertainLanguage_goesToReview() { assertEquals(RiskLevel.REVIEW, RiskEngine.decide(0.90f, null, false, isUncertainLanguage = true)) }
    @Test fun benignContext_requiresHigherEvidence() {
        assertNotEquals(RiskLevel.HIGH_RISK, RiskEngine.decide(0.82f, null, hasBenignContext = true).name.let { RiskLevel.valueOf(it) }.name.let { it })
        // 0.82 with benign needs +0.10 buffer, so should be WARNING not HIGH_RISK
        assertEquals(RiskLevel.WARNING, RiskEngine.decide(0.82f, null, true))
    }
    @Test fun thresholds_existForSelfHarm() {
        val t = RiskEngine.thresholdsFor(SafetyCategory.SELF_HARM)
        assertTrue(t.warning < t.highRisk)
    }
}
