package com.example.metaguardrail

import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetyMode
import com.example.metaguardrail.data.SafetySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSafetyAnalyzerTest {

    private val analyzer = ContentSafetyAnalyzer()
    private val strictSettings = SafetySettings(
        mode = SafetyMode.STRICT,
        enabledCategories = SafetyCategory.entries.toSet()
    )

    @Test
    fun testSafeContent() {
        val result = analyzer.analyzeContent("Hello everyone, hope you are having a wonderful day!", strictSettings)
        assertFalse(result.isFlagged)
        assertEquals("No Matching Rule", result.riskLabel)
        assertTrue(result.explanation.contains("not a guarantee"))
    }

    @Test
    fun testHarassmentDetection() {
        val result = analyzer.analyzeContent("You are a complete idiot and worthless.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.HARASSMENT, result.categoryMatched)
    }

    @Test
    fun testScamDetection() {
        val result = analyzer.analyzeContent("Click this link to claim your free crypto reward guaranteed return!", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SCAMS, result.categoryMatched)
    }

    @Test
    fun testGroomingConcernHandling() {
        val result = analyzer.analyzeContent("Let's keep this secret chat between us and don't tell your parents.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SOLICITATION_GROOMING, result.categoryMatched)
        assertTrue(result.riskLabel.contains("Possible Concern"))
    }

    @Test
    fun testSelfHarmSupportiveResponse() {
        val result = analyzer.analyzeContent("I want to end my life.", strictSettings)
        assertTrue(result.isFlagged)
        assertTrue(result.isSupportive)
        assertEquals(SafetyCategory.SELF_HARM, result.categoryMatched)
        assertTrue(result.explanation.contains("local emergency services"))
        assertTrue(result.explanation.contains("someone you trust"))
    }

    @Test
    fun testEducationalMedicalExemption() {
        val result = analyzer.analyzeContent("An educational biology class discussing human anatomy and medical recovery.", strictSettings)
        assertFalse(result.isFlagged)
    }

    @Test
    fun disabledCategoryIsNotReported() {
        val settingsWithoutScams = strictSettings.copy(
            enabledCategories = strictSettings.enabledCategories - SafetyCategory.SCAMS
        )

        val result = analyzer.analyzeContent(
            "Click this link to claim your free crypto reward!",
            settingsWithoutScams
        )

        assertFalse(result.isFlagged)
        assertEquals("No Matching Rule", result.riskLabel)
    }

    @Test
    fun demoFlagsSexualizedVulgarAndViolentText() {
        val vulgar = analyzer.analyzeContent("That was a fucking awful comment.", strictSettings)
        assertTrue(vulgar.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, vulgar.categoryMatched)

        val sexualized = analyzer.analyzeContent("This reel is a thirst trap.", strictSettings)
        assertTrue(sexualized.isFlagged)
        assertEquals(SafetyCategory.ADULT_CONTENT, sexualized.categoryMatched)

        val violent = analyzer.analyzeContent("The clip shows graphic violence and torture.", strictSettings)
        assertTrue(violent.isFlagged)
        assertEquals(SafetyCategory.DISTRESSING_CONTENT, violent.categoryMatched)
    }

    @Test
    fun blankInputRequestsTextInsteadOfClaimingSafety() {
        val result = analyzer.analyzeContent("   ", strictSettings)

        assertFalse(result.isFlagged)
        assertEquals("No Content", result.riskLabel)
        assertTrue(result.explanation.contains("Enter text"))
    }
}
