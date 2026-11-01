package com.example.metaguardrail

import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.DetectionSource
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetyMode
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.Severity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Expanded tests for [ContentSafetyAnalyzer].
 *
 * Covers:
 * - every required category flagged (sexual, harassment, scam, grooming, self-harm, violent)
 * - safe educational text not flagged
 * - ambiguous content (medical discussion with suggestive term but with educational context)
 * - Telugu examples (at least 2)
 * - mixed-language En+Telugu
 * - empty and blank input -> No Content
 * - very long input (>5000 chars) still handled
 * - malformed unicode (zero-width, FEFF)
 * - severity mapping correct (self-harm WARNING, grooming HIGH_RISK, harassment REVIEW etc)
 * - confidence null for local rules
 * - source LOCAL_RULE
 * - custom category match
 * - emoji handling (e.g., vulgar with emoji)
 * - obfuscation with zero-width shouldn't hide vulgar? normalized still catches vulgar
 * - puny check (indirect via link logic: test vulgar normalization)
 */
class ContentSafetyAnalyzerExpandedTest {

    private lateinit var analyzer: ContentSafetyAnalyzer
    private lateinit var strictSettings: SafetySettings

    @Before
    fun setUp() {
        analyzer = ContentSafetyAnalyzer()
        strictSettings = SafetySettings(
            mode = SafetyMode.STRICT,
            enabledCategories = SafetyCategory.entries.toSet()
        )
    }

    // ----------------------------------------------------------
    // 1. Every required category flagged
    // ----------------------------------------------------------

    @Test
    fun sexualCategory_flagged_adultContent() {
        val result = analyzer.analyzeContent("This is porn explicit content xxx", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.ADULT_CONTENT, result.categoryMatched)
    }

    @Test
    fun sexualCategory_flagged_suggestive() {
        val result = analyzer.analyzeContent("This reel is a thirst trap.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.ADULT_CONTENT, result.categoryMatched)
    }

    @Test
    fun harassmentCategory_flagged() {
        val result = analyzer.analyzeContent("You are a complete idiot and worthless.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.HARASSMENT, result.categoryMatched)
    }

    @Test
    fun harassmentCategory_flagged_killYourself() {
        val result = analyzer.analyzeContent("You should kill yourself", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.HARASSMENT, result.categoryMatched)
    }

    @Test
    fun scamCategory_flagged() {
        val result = analyzer.analyzeContent("Click this link to claim your free crypto reward guaranteed return!", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SCAMS, result.categoryMatched)
    }

    @Test
    fun scamCategory_flagged_lotteryWinner() {
        val result = analyzer.analyzeContent("You are a lottery winner, send money to claim", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SCAMS, result.categoryMatched)
    }

    @Test
    fun groomingCategory_flagged() {
        val result = analyzer.analyzeContent("Let's keep this secret chat between us and don't tell your parents.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SOLICITATION_GROOMING, result.categoryMatched)
    }

    @Test
    fun groomingCategory_flagged_meetAlone() {
        val result = analyzer.analyzeContent("meet me alone and send photos", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SOLICITATION_GROOMING, result.categoryMatched)
    }

    @Test
    fun selfHarmCategory_flagged() {
        val result = analyzer.analyzeContent("I want to end my life.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SELF_HARM, result.categoryMatched)
        assertTrue(result.isSupportive)
    }

    @Test
    fun selfHarmCategory_flagged_suicide() {
        val result = analyzer.analyzeContent("I am thinking about suicide", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SELF_HARM, result.categoryMatched)
    }

    @Test
    fun violentCategory_flagged() {
        val result = analyzer.analyzeContent("The clip shows graphic violence and torture.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.DISTRESSING_CONTENT, result.categoryMatched)
    }

    @Test
    fun violentCategory_flagged_gore() {
        val result = analyzer.analyzeContent("This video contains gore and mutilation", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.DISTRESSING_CONTENT, result.categoryMatched)
    }

    @Test
    fun vulgarCategory_flagged() {
        val result = analyzer.analyzeContent("That was a fucking awful comment.", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, result.categoryMatched)
    }

    // ----------------------------------------------------------
    // 2. Safe educational text not flagged
    // ----------------------------------------------------------

    @Test
    fun safeEducationalText_notFlagged() {
        val result = analyzer.analyzeContent(
            "An educational biology class discussing human anatomy and medical recovery.",
            strictSettings
        )
        assertFalse(result.isFlagged)
        assertEquals("No Matching Rule", result.riskLabel)
    }

    @Test
    fun safeEducationalWithDoctor_notFlagged() {
        val result = analyzer.analyzeContent(
            "The doctor explained the clinical therapy for mental health recovery in health class.",
            strictSettings
        )
        assertFalse(result.isFlagged)
    }

    @Test
    fun safeNormalConversation_notFlagged() {
        val result = analyzer.analyzeContent("Hello everyone, hope you are having a wonderful day!", strictSettings)
        assertFalse(result.isFlagged)
        assertEquals("No Matching Rule", result.riskLabel)
    }

    // ----------------------------------------------------------
    // 3. Ambiguous content (medical discussion with suggestive term but educational context)
    // ----------------------------------------------------------

    @Test
    fun ambiguousMedicalWithSuggestiveTerm_notFlagged_dueToExemption() {
        // Contains adult keyword "sexual content" but also educational context -> exemption
        val text = "In health class education, we learned about sexual content and anatomy for medical purposes."
        val result = analyzer.analyzeContent(text, strictSettings)
        // Educational/medical context exempts ADULT_CONTENT
        assertFalse(result.isFlagged)
    }

    @Test
    fun ambiguousMedicalNudeWithContext_notFlagged() {
        val text = "The biology textbook shows medical nudity for educational anatomy purposes in health class."
        val result = analyzer.analyzeContent(text, strictSettings)
        assertFalse(result.isFlagged)
    }

    @Test
    fun ambiguousWithoutExemption_stillFlagged() {
        // Same adult term without educational context should be flagged
        val text = "Check out this hot nude photo collection"
        val result = analyzer.analyzeContent(text, strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.ADULT_CONTENT, result.categoryMatched)
    }

    // ----------------------------------------------------------
    // 4. Telugu examples (at least 2)
    // ----------------------------------------------------------

    @Test
    fun telugu_scam_flagged() {
        // Telugu scam keywords: ఉచిత క్రిప్టో, లాటరీ గెలిచారు, డబ్బు పంపండి
        val result = analyzer.analyzeContent("మీరు లాటరీ గెలిచారు డబ్బు పంపండి", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SCAMS, result.categoryMatched)
    }

    @Test
    fun telugu_adultContent_flagged() {
        val result = analyzer.analyzeContent("అశ్లీల వీడియో చూడండి", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.ADULT_CONTENT, result.categoryMatched)
    }

    @Test
    fun telugu_grooming_flagged() {
        val result = analyzer.analyzeContent("మీ అమ్మానాన్నలకు చెప్పొద్దు రహస్యంగా కలుద్దాం", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SOLICITATION_GROOMING, result.categoryMatched)
    }

    @Test
    fun telugu_selfHarm_flagged() {
        val result = analyzer.analyzeContent("నాకు ఆత్మహత్య చేసుకోవాలని ఉంది", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SELF_HARM, result.categoryMatched)
    }

    @Test
    fun telugu_harassment_flagged() {
        val result = analyzer.analyzeContent("నువ్వు పనికిరానివాడివి మూర్ఖుడు", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.HARASSMENT, result.categoryMatched)
    }

    // ----------------------------------------------------------
    // 5. Mixed-language En+Telugu
    // ----------------------------------------------------------

    @Test
    fun mixedLanguageEnTelugu_scam_flagged() {
        val result = analyzer.analyzeContent("Hello friend, మీరు లాటరీ గెలిచారు please send money", strictSettings)
        assertTrue(result.isFlagged)
        // Should match scam via Telugu term
        assertEquals(SafetyCategory.SCAMS, result.categoryMatched)
    }

    @Test
    fun mixedLanguageEnTelugu_harassment_flagged() {
        val result = analyzer.analyzeContent("You are పనికిరానివాడివి and worthless", strictSettings)
        assertTrue(result.isFlagged)
        // Could match either harassment list; at least flagged
        assertNotNull(result.categoryMatched)
    }

    @Test
    fun mixedLanguageEnTelugu_grooming_flagged() {
        val result = analyzer.analyzeContent("Don't tell anyone ఎవరికీ చెప్పొద్దు send photos", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SOLICITATION_GROOMING, result.categoryMatched)
    }

    // ----------------------------------------------------------
    // 6. Empty and blank -> No Content
    // ----------------------------------------------------------

    @Test
    fun emptyInput_noContent() {
        val result = analyzer.analyzeContent("", strictSettings)
        assertFalse(result.isFlagged)
        assertEquals("No Content", result.riskLabel)
        assertTrue(result.explanation.contains("Enter text"))
        assertNull(result.categoryMatched)
        assertEquals(Severity.SAFE, result.severity)
    }

    @Test
    fun blankInput_noContent() {
        val result = analyzer.analyzeContent("   ", strictSettings)
        assertFalse(result.isFlagged)
        assertEquals("No Content", result.riskLabel)
        assertTrue(result.explanation.contains("Enter text"))
    }

    @Test
    fun blankInput_tabsAndNewlines_noContent() {
        val result = analyzer.analyzeContent("\t\n  \n", strictSettings)
        assertFalse(result.isFlagged)
        assertEquals("No Content", result.riskLabel)
    }

    // ----------------------------------------------------------
    // 7. Very long input (>5000 chars) still handled
    // ----------------------------------------------------------

    @Test
    fun veryLongInput_stillHandled_flagged() {
        val longText = "Hello world. ".repeat(500) + " fucking awful comment" // >5000 + vulgar
        assertTrue(longText.length > 5000)
        val result = analyzer.analyzeContent(longText, strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, result.categoryMatched)
    }

    @Test
    fun veryLongInput_safe_stillNotFlagged() {
        val longText = "This is a safe educational paragraph about biology and health class. ".repeat(200)
        assertTrue(longText.length > 5000)
        val result = analyzer.analyzeContent(longText, strictSettings)
        // Educational context present, no vulgar/scam etc; should not flag
        assertFalse(result.isFlagged)
    }

    @Test
    fun veryLongInput_noCrash() {
        val longText = "a".repeat(10000)
        val result = analyzer.analyzeContent(longText, strictSettings)
        // Should not throw; just no match
        assertFalse(result.isFlagged)
        assertNotNull(result.riskLabel)
        assertNotNull(result.explanation)
    }

    // ----------------------------------------------------------
    // 8. Malformed unicode (zero-width, FEFF)
    // ----------------------------------------------------------

    @Test
    fun malformedUnicode_zeroWidth_notHidingContent() {
        // Zero-width joiner/non-joiner and FEFF should be filtered in normalize
        val textWithZeroWidth = "Hello\u200B world"
        val result = analyzer.analyzeContent(textWithZeroWidth, strictSettings)
        assertFalse(result.isFlagged) // no trigger word, just zero-width
        assertNotNull(result)
    }

    @Test
    fun malformedUnicode_feff_filtered() {
        val textWithFeff = "\uFEFFHello everyone"
        val result = analyzer.analyzeContent(textWithFeff, strictSettings)
        assertFalse(result.isFlagged)
    }

    @Test
    fun malformedUnicode_combined_zeroWidthAndContent() {
        // Text with zero-width inside still should normalize correctly
        val text = "This is\u200B a test\uFEFF with\u200C content"
        val result = analyzer.analyzeContent(text, strictSettings)
        assertFalse(result.isFlagged)
    }

    // ----------------------------------------------------------
    // 9. Severity mapping correct
    // ----------------------------------------------------------

    @Test
    fun severity_selfHarm_isWarning() {
        val result = analyzer.analyzeContent("I want to end my life.", strictSettings)
        assertEquals(Severity.WARNING, result.severity)
    }

    @Test
    fun severity_grooming_isHighRisk() {
        val result = analyzer.analyzeContent("keep this secret don't tell your parents", strictSettings)
        assertEquals(Severity.HIGH_RISK, result.severity)
    }

    @Test
    fun severity_harassment_isReview() {
        val result = analyzer.analyzeContent("You are a complete idiot", strictSettings)
        assertEquals(Severity.REVIEW, result.severity)
    }

    @Test
    fun severity_vulgar_isReview() {
        val result = analyzer.analyzeContent("That was a fucking awful comment.", strictSettings)
        assertEquals(Severity.REVIEW, result.severity)
    }

    @Test
    fun severity_scam_isWarning() {
        val result = analyzer.analyzeContent("Click this link to claim your free crypto reward", strictSettings)
        assertEquals(Severity.WARNING, result.severity)
    }

    @Test
    fun severity_adultContent_isWarning() {
        val result = analyzer.analyzeContent("This is porn explicit content", strictSettings)
        assertEquals(Severity.WARNING, result.severity)
    }

    @Test
    fun severity_distressing_isWarning() {
        val result = analyzer.analyzeContent("The clip shows graphic violence and torture.", strictSettings)
        assertEquals(Severity.WARNING, result.severity)
    }

    @Test
    fun severity_safe_isSafe() {
        val result = analyzer.analyzeContent("Hello everyone, nice day!", strictSettings)
        assertEquals(Severity.SAFE, result.severity)
    }

    @Test
    fun severity_noContent_isSafe() {
        val result = analyzer.analyzeContent("", strictSettings)
        assertEquals(Severity.SAFE, result.severity)
    }

    // ----------------------------------------------------------
    // 10. Confidence null for local rules
    // ----------------------------------------------------------

    @Test
    fun confidence_null_forLocalRules_flagged() {
        val result = analyzer.analyzeContent("You are a complete idiot", strictSettings)
        assertNull(result.confidence)
    }

    @Test
    fun confidence_null_forLocalRules_safe() {
        val result = analyzer.analyzeContent("Hello world", strictSettings)
        assertNull(result.confidence)
    }

    @Test
    fun confidence_null_forLocalRules_noContent() {
        val result = analyzer.analyzeContent("", strictSettings)
        assertNull(result.confidence)
    }

    // ----------------------------------------------------------
    // 11. Source LOCAL_RULE
    // ----------------------------------------------------------

    @Test
    fun source_isLocalRule_flagged() {
        val result = analyzer.analyzeContent("You are a complete idiot", strictSettings)
        assertEquals(DetectionSource.LOCAL_RULE, result.source)
    }

    @Test
    fun source_isLocalRule_safe() {
        val result = analyzer.analyzeContent("Hello world", strictSettings)
        assertEquals(DetectionSource.LOCAL_RULE, result.source)
    }

    @Test
    fun source_isLocalRule_noContent() {
        val result = analyzer.analyzeContent("", strictSettings)
        assertEquals(DetectionSource.LOCAL_RULE, result.source)
    }

    // ----------------------------------------------------------
    // 12. Custom category match
    // ----------------------------------------------------------

    @Test
    fun customCategory_match_flagged() {
        val settingsWithCustom = strictSettings.copy(customCategories = setOf("crypto"))
        val result = analyzer.analyzeContent("This discusses crypto investments", settingsWithCustom)
        assertTrue(result.isFlagged)
        assertEquals(Severity.REVIEW, result.severity)
        assertEquals(DetectionSource.LOCAL_RULE, result.source)
        // custom category leaves categoryMatched null per implementation
        assertNull(result.categoryMatched)
        assertTrue(result.explanation.contains("crypto", ignoreCase = true))
    }

    @Test
    fun customCategory_match_caseInsensitive() {
        val settingsWithCustom = strictSettings.copy(customCategories = setOf("MyCustomCat"))
        val result = analyzer.analyzeContent("This is about mycustomcat topic", settingsWithCustom)
        assertTrue(result.isFlagged)
    }

    @Test
    fun customCategory_noMatch_notFlaggedForCustom() {
        val settingsWithCustom = strictSettings.copy(customCategories = setOf("mykeyword123"))
        val result = analyzer.analyzeContent("Hello world unrelated", settingsWithCustom)
        assertFalse(result.isFlagged)
    }

    @Test
    fun customCategory_disabledKnownCategoryButCustomStillFlags() {
        // Disable vulgar, but custom "fuck" should still flag via custom check
        val settingsNoVulgar = strictSettings.copy(
            enabledCategories = strictSettings.enabledCategories - SafetyCategory.VULGAR_LANGUAGE,
            customCategories = setOf("fuck")
        )
        // vulgar disabled so direct vulgar check skipped, but custom "fuck" should catch it
        // Note: "fucking" contains "fuck" but regex word boundary: "fuck" surrounded by non-word chars
        // "fucking" is "fuck" + "ing" -> boundary check fails; so we test exact word
        val result2 = analyzer.analyzeContent("This is fuck", settingsNoVulgar)
        assertTrue(result2.isFlagged)
        assertNull(result2.categoryMatched)
    }

    // ----------------------------------------------------------
    // 13. Emoji handling (e.g., vulgar with emoji)
    // ----------------------------------------------------------

    @Test
    fun emojiHandling_vulgarWithEmoji_flagged() {
        val result = analyzer.analyzeContent("You are a fucking idiot 😡💩", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, result.categoryMatched)
    }

    @Test
    fun emojiHandling_onlyEmojis_notFlagged() {
        val result = analyzer.analyzeContent("😀😃😄😁 Hello!", strictSettings)
        assertFalse(result.isFlagged)
    }

    @Test
    fun emojiHandling_scamWithEmoji_flagged() {
        val result = analyzer.analyzeContent("Click this link to claim your free crypto reward 🎉💰", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SCAMS, result.categoryMatched)
    }

    @Test
    fun emojiHandling_selfHarmWithEmoji_flagged() {
        val result = analyzer.analyzeContent("I want to end my life 😢", strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.SELF_HARM, result.categoryMatched)
        assertTrue(result.isSupportive)
    }

    // ----------------------------------------------------------
    // 14. Obfuscation with zero-width shouldn't hide vulgar
    // ----------------------------------------------------------

    @Test
    fun obfuscation_zeroWidth_insideVulgarStillCatches() {
        // "fuck" with zero-width between letters should be normalized to "fuck" and caught
        val obfuscated = "That was a fu\u200Bcking awful comment."
        val result = analyzer.analyzeContent(obfuscated, strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, result.categoryMatched)
    }

    @Test
    fun obfuscation_multipleZeroWidths_stillCatches() {
        val obfuscated = "You\u200B are\u200C a fucking\u200D idiot."
        val result = analyzer.analyzeContent(obfuscated, strictSettings)
        assertTrue(result.isFlagged)
    }

    @Test
    fun obfuscation_feff_insideVulgar_stillCatches() {
        val obfuscated = "That was a fu\uFEFFcking awful comment."
        val result = analyzer.analyzeContent(obfuscated, strictSettings)
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, result.categoryMatched)
    }

    // ----------------------------------------------------------
    // 15. Puny check (normalization punycode-like obfuscation)
    // ----------------------------------------------------------

    @Test
    fun normalization_unicodeNfkc_applied() {
        // NFKC: various unicode forms normalized; ensure no crash and flag still works
        // e.g., fullwidth characters should be normalized and matched if they form keywords
        val fullwidth = "That was a \uFF46\uFF55\uFF43\uFF4B\uFF49\uFF4E\uFF47 awful comment." // fullwidth "fucking"
        val result = analyzer.analyzeContent(fullwidth, strictSettings)
        // After NFKC, fullwidth becomes ascii "fucking" -> should be flagged
        assertTrue(result.isFlagged)
        assertEquals(SafetyCategory.VULGAR_LANGUAGE, result.categoryMatched)
    }

    @Test
    fun normalization_teluguNfkc_preserved() {
        val result = analyzer.analyzeContent("అశ్లీల", strictSettings)
        assertTrue(result.isFlagged)
    }

    @Test
    fun normalization_doesNotCreateFalsePositive() {
        // Text with zero-width splitted safe words should not falsely combine into bad words
        // "fun" + zw + "ck" is not a real word without normalization; but "fu" + zw + "ck" = "fuck" should be caught
        // Safe: "fun" should not be flagged
        val safeText = "We had fun at the park"
        val result = analyzer.analyzeContent(safeText, strictSettings)
        assertFalse(result.isFlagged)
    }

    // ----------------------------------------------------------
    // 16. Disabled category not reported
    // ----------------------------------------------------------

    @Test
    fun disabledCategory_notReported() {
        val noScam = strictSettings.copy(enabledCategories = strictSettings.enabledCategories - SafetyCategory.SCAMS)
        val result = analyzer.analyzeContent("Click this link to claim your free crypto reward!", noScam)
        assertFalse(result.isFlagged)
        assertEquals("No Matching Rule", result.riskLabel)
    }

    // ----------------------------------------------------------
    // 17. Supportive flag only for self-harm
    // ----------------------------------------------------------

    @Test
    fun supportive_onlyForSelfHarm() {
        val selfHarm = analyzer.analyzeContent("I want to end my life.", strictSettings)
        assertTrue(selfHarm.isSupportive)
        assertTrue(selfHarm.explanation.contains("local emergency services"))

        val harassment = analyzer.analyzeContent("You are a complete idiot", strictSettings)
        assertFalse(harassment.isSupportive)

        val vulgar = analyzer.analyzeContent("That was a fucking awful comment.", strictSettings)
        assertFalse(vulgar.isSupportive)
    }
}
