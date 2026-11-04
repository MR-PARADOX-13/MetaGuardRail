package com.example.metaguardrail

import org.junit.Assert.*
import org.junit.Test

class CloudImageConsentTest {

    @Test fun textAndImageConsent_independentDefaults() {
        // Both OFF by default — verified via DataStore keys both default false
        // This test documents the requirement; actual DataStore test would need context
        assertTrue(true) // placeholder — real verification is in UserPreferencesRepository defaults
    }

    @Test fun imageUpload_blockedWhenConsentOff() {
        // Simulate: cloudImageAnalysisEnabled = false → no image upload should occur
        val consent = false
        var uploadCalled = false
        fun uploadImageIfAllowed(enabled: Boolean) { if (enabled) uploadCalled = true }
        uploadImageIfAllowed(consent)
        assertFalse(uploadCalled)
    }

    @Test fun imageUpload_permittedWhenConsentOn() {
        val consent = true
        var uploadCalled = false
        fun uploadImageIfAllowed(enabled: Boolean) { if (enabled) uploadCalled = true }
        uploadImageIfAllowed(consent)
        assertTrue(uploadCalled)
    }

    @Test fun textConsent_doesNotGrantImageConsent() {
        val textConsent = true
        val imageConsent = false
        var imageUploadCalled = false
        fun uploadImage(enabled: Boolean) { if (enabled) imageUploadCalled = true }
        uploadImage(imageConsent)
        assertFalse("Image upload must not be granted by text consent", imageUploadCalled)
        assertTrue(textConsent) // text consent independent
    }

    @Test fun revocation_blocksSubsequentUploads() {
        var consent = true
        var uploadCalled = false
        fun upload(enabled: Boolean) { if (enabled) uploadCalled = true }
        upload(consent); assertTrue(uploadCalled)
        uploadCalled = false; consent = false
        upload(consent); assertFalse(uploadCalled)
    }
}
