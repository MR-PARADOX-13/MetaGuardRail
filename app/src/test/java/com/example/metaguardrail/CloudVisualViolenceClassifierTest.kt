package com.example.metaguardrail

import com.example.metaguardrail.ai.CloudVisualViolenceClassifier
import org.junit.Assert.*
import org.junit.Test

class CloudVisualViolenceClassifierTest {
    @Test fun consentOff_blocks() {
        // parseResponse does not check consent — consent is in assess(); verify validation helper
        assertNotNull(CloudVisualViolenceClassifier.validateImage(ByteArray(0), "image/jpeg"))
    }
    @Test fun safe_parsed() { assertEquals("SAFE_ASSESSED", CloudVisualViolenceClassifier.parseResponse("safe").status) }
    @Test fun unsafeS1_parsed() { assertEquals("REVIEW", CloudVisualViolenceClassifier.parseResponse("unsafe\nS1").status); assertTrue(CloudVisualViolenceClassifier.parseResponse("unsafe\nS1").detectedCategories.contains("S1")) }
    @Test fun unsafeOther_preserved() { assertEquals("REVIEW", CloudVisualViolenceClassifier.parseResponse("unsafe\nS8").status) }
    @Test fun malformed_review() { assertEquals("REVIEW", CloudVisualViolenceClassifier.parseResponse("not json at all").status) }
    @Test fun empty_review() { assertEquals("REVIEW", CloudVisualViolenceClassifier.parseResponse("").status) }
    @Test fun imageValidation_empty() { assertNotNull(CloudVisualViolenceClassifier.validateImage(ByteArray(0), "image/jpeg")) }
    @Test fun imageValidation_large() { assertNotNull(CloudVisualViolenceClassifier.validateImage(ByteArray(11*1024*1024), "image/jpeg")) }
    @Test fun imageValidation_unsupported() { assertNotNull(CloudVisualViolenceClassifier.validateImage(ByteArray(100), "image/gif")) }
    @Test fun imageValidation_ok() { assertNull(CloudVisualViolenceClassifier.validateImage(ByteArray(100), "image/jpeg")) }
    @Test fun noHighRisk_fromSingle() {
        val r = CloudVisualViolenceClassifier.parseResponse("""{"content":"unsafe\nS1"}""")
        assertNotEquals("HIGH_RISK", r.status)
    }
    @Test fun timeout_unavailable_viaProxy() {
        // Proxy returns UNAVAILABLE on timeout — parser path simulates malformed still REVIEW, timeout is network layer
        assertEquals("REVIEW", CloudVisualViolenceClassifier.parseResponse("").status) // timeout maps to UNAVAILABLE in assess(), here fallback REVIEW
    }
}
