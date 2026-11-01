package com.example.metaguardrail

import com.example.metaguardrail.data.LinkSafetyAnalyzer
import com.example.metaguardrail.data.LinkStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for [LinkSafetyAnalyzer].
 *
 * Covers all deterministic heuristics documented in LinkSafetyAnalyzer:
 * - empty / malformed / missing scheme → INVALID
 * - https clean → SAFE
 * - http clean → REVIEW
 * - IP address → WARNING
 * - punycode xn-- → REVIEW
 * - unicode host → REVIEW
 * - deep subdomain (>3 dots) → REVIEW
 * - brand impersonation via typo (instagran.com) → WARNING
 * - brand impersonation via contains (instagram.com.evil.com) → WARNING
 * - suspicious keyword with http → WARNING
 * - redirect param → UNKNOWN (or REVIEW with note)
 * - https indicator field correctness
 * - suspicious keyword with https should NOT be WARNING
 */
class LinkSafetyAnalyzerTest {

    private lateinit var analyzer: LinkSafetyAnalyzer

    @Before
    fun setUp() {
        analyzer = LinkSafetyAnalyzer()
    }

    // 1. empty -> INVALID
    @Test
    fun emptyInput_returnsInvalid() {
        val result = analyzer.analyze("")
        assertEquals(LinkStatus.INVALID, result.status)
        assertNull(result.https)
        assertTrue(result.explanation.isNotBlank())
    }

    @Test
    fun blankInput_returnsInvalid() {
        val result = analyzer.analyze("   ")
        assertEquals(LinkStatus.INVALID, result.status)
        assertNull(result.https)
    }

    // 2. malformed -> INVALID
    @Test
    fun malformedUri_returnsInvalid() {
        // Illegal URI characters / unparseable
        val result = analyzer.analyze("ht!tp://:::")
        assertEquals(LinkStatus.INVALID, result.status)
        // scheme is not http/https so https must be null per spec
        // Note: if scheme parsed as ht!tp it falls to unsupported scheme branch → https null
        // For truly unparseable, https also null
        assertTrue(result.explanation.isNotBlank())
    }

    @Test
    fun malformedMissingHost_returnsInvalid() {
        val result = analyzer.analyze("https://")
        assertEquals(LinkStatus.INVALID, result.status)
    }

    // 3. missingScheme -> INVALID
    @Test
    fun missingScheme_returnsInvalid() {
        val result = analyzer.analyze("www.example.com/path")
        assertEquals(LinkStatus.INVALID, result.status)
        assertNull(result.https)
        assertTrue(result.explanation.contains("scheme", ignoreCase = true))
    }

    @Test
    fun missingScheme_bareHost_returnsInvalid() {
        val result = analyzer.analyze("example.com")
        assertEquals(LinkStatus.INVALID, result.status)
        assertNull(result.https)
    }

    @Test
    fun unsupportedScheme_returnsInvalid() {
        val result = analyzer.analyze("ftp://example.com/file")
        assertEquals(LinkStatus.INVALID, result.status)
        assertNull(result.https)
    }

    // 4. httpsClean -> SAFE
    @Test
    fun httpsClean_returnsSafe() {
        val result = analyzer.analyze("https://example.com")
        assertEquals(LinkStatus.SAFE, result.status)
        assertEquals(true, result.https)
        assertTrue(result.explanation.isNotBlank())
    }

    @Test
    fun httpsClean_withPath_returnsSafe() {
        val result = analyzer.analyze("https://www.example.com/path?query=1")
        assertEquals(LinkStatus.SAFE, result.status)
        assertEquals(true, result.https)
    }

    // 5. httpClean -> REVIEW
    @Test
    fun httpClean_returnsReview() {
        val result = analyzer.analyze("http://example.com")
        assertEquals(LinkStatus.REVIEW, result.status)
        assertEquals(false, result.https)
        assertTrue(result.explanation.contains("HTTP", ignoreCase = true))
    }

    @Test
    fun httpClean_withPath_returnsReview() {
        val result = analyzer.analyze("http://example.com/about")
        assertEquals(LinkStatus.REVIEW, result.status)
        assertEquals(false, result.https)
    }

    // 6. ipAddress -> WARNING
    @Test
    fun ipAddress_http_returnsWarning() {
        val result = analyzer.analyze("http://192.168.1.1/login")
        assertEquals(LinkStatus.WARNING, result.status)
        assertTrue(result.explanation.contains("IP address", ignoreCase = true))
    }

    @Test
    fun ipAddress_https_returnsWarning() {
        val result = analyzer.analyze("https://10.0.0.5/secure")
        assertEquals(LinkStatus.WARNING, result.status)
        // https true but IP still wins
        assertEquals(true, result.https)
    }

    @Test
    fun ipAddress_bare_returnsWarning() {
        val result = analyzer.analyze("https://172.16.0.1")
        assertEquals(LinkStatus.WARNING, result.status)
    }

    // 7. punycodeXn -> REVIEW
    @Test
    fun punycodeXn_returnsReview() {
        val result = analyzer.analyze("https://xn--p1ai.ru")
        assertEquals(LinkStatus.REVIEW, result.status)
        assertEquals(true, result.https)
        assertTrue(result.explanation.contains("punycode", ignoreCase = true))
    }

    @Test
    fun punycodeXn_subdomain_returnsReview() {
        val result = analyzer.analyze("https://xn--example-abc.com/path")
        assertEquals(LinkStatus.REVIEW, result.status)
    }

    // 8. unicodeHost -> REVIEW
    @Test
    fun unicodeHost_returnsReview() {
        // Use unicode in host; URI may encode but analyzer detects non-ascii via host check
        val result = analyzer.analyze("https://münchen.de")
        assertEquals(LinkStatus.REVIEW, result.status)
        assertEquals(true, result.https)
        assertTrue(result.explanation.contains("Unicode", ignoreCase = true))
    }

    @Test
    fun unicodeHost_alt_returnsReview() {
        val result = analyzer.analyze("https://café.example.com")
        assertEquals(LinkStatus.REVIEW, result.status)
    }

    // 9. deepSubdomain -> REVIEW
    @Test
    fun deepSubdomain_returnsReview() {
        // >3 dots in host
        val result = analyzer.analyze("https://a.b.c.d.e.example.com/path")
        assertEquals(LinkStatus.REVIEW, result.status)
        assertTrue(result.explanation.contains("subdomain", ignoreCase = true))
    }

    @Test
    fun deepSubdomain_https_returnsReview() {
        val result = analyzer.analyze("https://one.two.three.four.example.com")
        assertEquals(LinkStatus.REVIEW, result.status)
    }

    @Test
    fun normalSubdomain_notFlaggedAsDeep() {
        // 3 dots or fewer should not be deep (e.g., www.example.com = 2 dots)
        val result = analyzer.analyze("https://www.example.com")
        // Should be SAFE, not REVIEW for deep
        assertEquals(LinkStatus.SAFE, result.status)
    }

    // 10. brandImpersonationTypo (e.g., instagran.com) -> WARNING
    @Test
    fun brandImpersonationTypo_returnsWarning() {
        val result = analyzer.analyze("https://instagran.com/login")
        assertEquals(LinkStatus.WARNING, result.status)
        assertTrue(result.explanation.contains("brand", ignoreCase = true))
    }

    @Test
    fun brandImpersonationTypo_gooogle_returnsWarning() {
        val result = analyzer.analyze("https://gooogle.com")
        assertEquals(LinkStatus.WARNING, result.status)
    }

    @Test
    fun brandImpersonationTypo_facebok_returnsWarning() {
        val result = analyzer.analyze("https://facebok.com")
        assertEquals(LinkStatus.WARNING, result.status)
    }

    // 11. brandImpersonationContains (e.g., instagram.com.evil.com) -> WARNING
    @Test
    fun brandImpersonationContains_returnsWarning() {
        val result = analyzer.analyze("https://instagram.com.evil.com")
        assertEquals(LinkStatus.WARNING, result.status)
        assertTrue(result.explanation.contains("brand", ignoreCase = true))
    }

    @Test
    fun brandImpersonationContains_facebook_evil_returnsWarning() {
        val result = analyzer.analyze("https://facebook.com.evil.com/login")
        assertEquals(LinkStatus.WARNING, result.status)
    }

    @Test
    fun legitimateSubdomain_notImpersonation() {
        // Legitimate subdomain should NOT be flagged as impersonation
        val result = analyzer.analyze("https://m.facebook.com")
        // Ends with .facebook.com → not impersonation, should be SAFE (https clean)
        assertEquals(LinkStatus.SAFE, result.status)
    }

    // 12. suspiciousKeywordWithHttp -> WARNING
    @Test
    fun suspiciousKeywordWithHttp_returnsWarning() {
        val result = analyzer.analyze("http://example.com/login")
        assertEquals(LinkStatus.WARNING, result.status)
        assertEquals(false, result.https)
        assertTrue(result.explanation.contains("login", ignoreCase = true) || result.explanation.contains("sensitive", ignoreCase = true))
    }

    @Test
    fun suspiciousKeywordWithHttp_verify_returnsWarning() {
        val result = analyzer.analyze("http://example.com/verify?account=1")
        assertEquals(LinkStatus.WARNING, result.status)
    }

    @Test
    fun suspiciousKeywordWithHttp_securePath_returnsWarning() {
        val result = analyzer.analyze("http://example.com/secure/update")
        assertEquals(LinkStatus.WARNING, result.status)
    }

    // 13. redirectParam -> UNKNOWN or REVIEW (test both)
    @Test
    fun redirectParam_https_returnsUnknown() {
        // HTTPS clean + redirect param → UNKNOWN per decision tree
        val result = analyzer.analyze("https://example.com/page?redirect=https://evil.com")
        assertTrue(
            result.status == LinkStatus.UNKNOWN || result.status == LinkStatus.REVIEW
        )
        // For https clean, expected UNKNOWN
        assertEquals(LinkStatus.UNKNOWN, result.status)
        assertEquals(true, result.https)
        assertTrue(result.explanation.contains("redirect", ignoreCase = true))
    }

    @Test
    fun redirectParam_urlParam_returnsUnknown() {
        val result = analyzer.analyze("https://example.com/out?url=https://evil.com")
        assertEquals(LinkStatus.UNKNOWN, result.status)
    }

    @Test
    fun redirectParam_withHttp_returnsReviewOrWarning() {
        // HTTP + redirect param: HTTP branch wins first, but includes redirect note
        val result = analyzer.analyze("http://example.com/page?redirect=https://evil.com")
        // Should be REVIEW (http) or WARNING if suspicious keyword also present
        // But redirect note should be present
        assertTrue(result.status == LinkStatus.REVIEW || result.status == LinkStatus.WARNING || result.status == LinkStatus.UNKNOWN)
        assertTrue(result.explanation.contains("redirect", ignoreCase = true))
    }

    // 14. httpsIndicator field correct (true for https, false for http, null for invalid)
    @Test
    fun httpsIndicator_fieldCorrect() {
        val httpsResult = analyzer.analyze("https://example.com")
        assertEquals(true, httpsResult.https)

        val httpResult = analyzer.analyze("http://example.com")
        assertEquals(false, httpResult.https)

        val invalidEmpty = analyzer.analyze("")
        assertNull(invalidEmpty.https)

        val invalidNoScheme = analyzer.analyze("example.com")
        assertNull(invalidNoScheme.https)

        val invalidScheme = analyzer.analyze("ftp://example.com")
        assertNull(invalidScheme.https)

        val malformed = analyzer.analyze("ht!tp://:::")
        assertNull(malformed.https)
    }

    @Test
    fun httpsIndicator_preservedAcrossWarningCases() {
        val ipHttps = analyzer.analyze("https://192.168.1.1")
        assertEquals(true, ipHttps.https)
        assertEquals(LinkStatus.WARNING, ipHttps.status)

        val ipHttp = analyzer.analyze("http://192.168.1.1")
        assertEquals(false, ipHttp.https)
        assertEquals(LinkStatus.WARNING, ipHttp.status)
    }

    // 15. suspicious keyword with https should NOT be WARNING (test safe)
    @Test
    fun suspiciousKeywordWithHttps_notWarning() {
        val result = analyzer.analyze("https://example.com/login")
        // HTTPS + keyword alone should NOT be WARNING (needs http or punycode/unicode)
        assertNotEquals(LinkStatus.WARNING, result.status)
        // Should be SAFE (or UNKNOWN if redirect, but not warning)
        assertTrue(result.status == LinkStatus.SAFE || result.status == LinkStatus.UNKNOWN)
        assertEquals(true, result.https)
    }

    @Test
    fun suspiciousKeywordWithHttps_verify_notWarning() {
        val result = analyzer.analyze("https://example.com/verify")
        assertNotEquals(LinkStatus.WARNING, result.status)
    }

    @Test
    fun suspiciousKeywordWithHttps_secure_notWarning() {
        // Note: keyword check is on path/query, not host; host "secure.example.com" alone is not checked
        val hostKeyword = analyzer.analyze("https://secure.example.com/account")
        assertNotEquals(LinkStatus.WARNING, hostKeyword.status)
        val withPath = analyzer.analyze("https://example.com/secure")
        assertNotEquals(LinkStatus.WARNING, withPath.status)
    }

    @Test
    fun suspiciousKeywordWithHttpsAndPunycode_shouldBeWarning() {
        // Contrast: https + keyword + punycode SHOULD be WARNING
        val result = analyzer.analyze("https://xn--example.com/login")
        assertEquals(LinkStatus.WARNING, result.status)
    }
}
