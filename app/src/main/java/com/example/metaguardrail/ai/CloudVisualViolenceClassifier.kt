package com.example.metaguardrail.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * TEMPORARY zero-budget cloud visual violence classifier — Manual Image Check only.
 * Uses NVIDIA hosted meta/llama-guard-4-12b via LOCAL DEV PROXY (never directly from app).
 * Only a single user-picked image is sent when cloudImageAnalysisEnabled==true.
 * No live-screen wiring. Prompt follows official Llama Guard 4 conversation template.
 */
data class VisualViolenceAssessment(
    val status: String, // SAFE_ASSESSED, REVIEW, WARNING, UNAVAILABLE, VALIDATION_FAILED
    val detectedCategories: List<String>,
    val modelName: String = "meta/llama-guard-4-12b",
    val provider: String = "nvidia",
    val classificationMethod: String = "cloud-llama-guard-4",
    val timestamp: Long = System.currentTimeMillis(),
    val sanitizedExplanation: String? = null,
    val errorCode: String? = null
)

object CloudVisualViolenceClassifier {
    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 10MB — matches Llama Guard 4 limit
    private const val PROXY_PATH = "/v1/lg4/visual"

    private fun proxyBaseUrl(): String? {
        return try {
            val url = com.example.metaguardrail.BuildConfig.BACKEND_BASE_URL
            // Dev proxy must be loopback for emulator or LAN IP for physical device; never production host without consent
            if (url.contains("localhost") || url.contains("127.0.0.1") || url.matches(Regex(""".*192\.168\.\d+\.\d+.*""")) || url.matches(Regex(""".*10\.\d+\.\d+\.\d+.*"""))) url else null
        } catch (_: Exception) { null }
    }

    fun validateImage(bytes: ByteArray, mime: String): String? {
        if (bytes.isEmpty()) return "empty_image"
        if (bytes.size > MAX_IMAGE_BYTES) return "image_too_large"
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) return "unsupported_type"
        return null
    }

    suspend fun assess(
        context: Context,
        imageUri: Uri,
        isConsentEnabled: () -> Boolean
    ): VisualViolenceAssessment = withContext(Dispatchers.IO) {
        if (!isConsentEnabled()) return@withContext VisualViolenceAssessment(status = "VALIDATION_FAILED", detectedCategories = emptyList(), errorCode = "consent_off")
        val proxyUrl = proxyBaseUrl() ?: return@withContext VisualViolenceAssessment(status = "UNAVAILABLE", detectedCategories = emptyList(), errorCode = "proxy_not_configured")
        val resolver = context.contentResolver
        val mime = resolver.getType(imageUri) ?: "image/jpeg"
        val bytes = try { resolver.openInputStream(imageUri)?.use { it.readBytes() } ?: ByteArray(0) } catch (_: Exception) { ByteArray(0) }
        validateImage(bytes, mime)?.let { return@withContext VisualViolenceAssessment(status = "VALIDATION_FAILED", detectedCategories = emptyList(), errorCode = it) }
        val stripped: ByteArray = try {
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext VisualViolenceAssessment(status = "VALIDATION_FAILED", detectedCategories = emptyList(), errorCode = "decode_failed")
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (_: Exception) { bytes }
        if (!isConsentEnabled()) return@withContext VisualViolenceAssessment(status = "VALIDATION_FAILED", detectedCategories = emptyList(), errorCode = "consent_revoked")
        val b64 = Base64.encodeToString(stripped, Base64.NO_WRAP)
        val body = JSONObject().apply {
            put("image_base64", b64)
            put("mime_type", "image/jpeg")
            put("prompt", buildPrompt())
        }.toString()
        try {
            val url = URL(proxyUrl.trimEnd('/') + PROXY_PATH)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 20_000; readTimeout = 30_000
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 429) return@withContext VisualViolenceAssessment(status = "UNAVAILABLE", detectedCategories = emptyList(), errorCode = "rate_limited")
            if (code !in 200..299) return@withContext VisualViolenceAssessment(status = "UNAVAILABLE", detectedCategories = emptyList(), errorCode = "http_$code")
            val respText = conn.inputStream.bufferedReader().readText()
            parseResponse(respText)
        } catch (e: java.net.SocketTimeoutException) {
            VisualViolenceAssessment(status = "UNAVAILABLE", detectedCategories = emptyList(), errorCode = "timeout")
        } catch (_: Exception) {
            VisualViolenceAssessment(status = "UNAVAILABLE", detectedCategories = emptyList(), errorCode = "network_error")
        }
    }

    fun parseResponse(jsonText: String): VisualViolenceAssessment {
        if (jsonText.isBlank()) return VisualViolenceAssessment(status = "REVIEW", detectedCategories = emptyList(), errorCode = "empty_response", sanitizedExplanation = "Response empty — review recommended.")
        val trimmedRaw = jsonText.trim()
        val isRawSafeUnsafe = trimmedRaw.lowercase().let { it == "safe" || it.startsWith("safe") && !it.contains("unsafe") || it.contains("unsafe") }
        if (!trimmedRaw.startsWith("{") && isRawSafeUnsafe) {
            val lowerRaw = trimmedRaw.lowercase()
            return when {
                lowerRaw.contains("safe") && !lowerRaw.contains("unsafe") -> VisualViolenceAssessment(status = "SAFE_ASSESSED", detectedCategories = emptyList(), sanitizedExplanation = "No S1 violation.")
                lowerRaw.contains("unsafe") && lowerRaw.contains("s1") -> VisualViolenceAssessment(status = "REVIEW", detectedCategories = listOf("S1"), sanitizedExplanation = "Possible S1 — review.")
                lowerRaw.contains("unsafe") -> VisualViolenceAssessment(status = "REVIEW", detectedCategories = listOf("other"))
                else -> VisualViolenceAssessment(status = "REVIEW", detectedCategories = emptyList(), errorCode = "malformed")
            }
        }
        return try {
            val obj = JSONObject(jsonText)
            val content = when {
                obj.has("content") -> obj.getString("content")
                obj.has("output") -> obj.getString("output")
                obj.has("choices") -> obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                else -> jsonText
            }
            val lower = content.lowercase()
            when {
                lower.contains("safe") && !lower.contains("unsafe") -> VisualViolenceAssessment(status = "SAFE_ASSESSED", detectedCategories = emptyList(), sanitizedExplanation = "No S1 violent-crime violation identified in this image.")
                lower.contains("unsafe") && lower.contains("s1") -> VisualViolenceAssessment(status = "REVIEW", detectedCategories = listOf("S1"), sanitizedExplanation = "Possible S1 Violent Crimes — context review recommended (not proof of a crime).")
                lower.contains("unsafe") -> VisualViolenceAssessment(status = "REVIEW", detectedCategories = listOf("other"), sanitizedExplanation = "Flagged by provider under non-S1 category — preserved without violent label.")
                else -> VisualViolenceAssessment(status = "REVIEW", detectedCategories = emptyList(), errorCode = "malformed", sanitizedExplanation = "Unrecognized response — review recommended.")
            }
        } catch (_: Exception) {
            VisualViolenceAssessment(status = "REVIEW", detectedCategories = emptyList(), errorCode = "malformed", sanitizedExplanation = "Could not parse provider response — review recommended.")
        }
    }

    /**
     * Official Llama Guard 4 prompt structure:
     * System: safety policy with S1 definitions, then image+text as User conversation.
     * Response: safe / unsafe + violated categories (e.g., S1).
     */
    fun buildPrompt(): String = """
You are Meta Llama Guard 4 safety classifier. Assess ONLY the image.
Conversation: User provides an image. You respond with `safe` or `unsafe` and violated categories.

Safety Categories:
- S1 Violent Crimes: murder, manslaughter, assault, battery, kidnapping, terrorism, torture, hate-motivated violence — imminent violence, encouragement, instructions.
- S2–S14: [other categories defined per Meta Llama Guard 4 policy — preserved, not altered]

Instruction: Determine if the image's intended message violates S1.
Context guidance (do not override S1 definition, only clarify image interpretation):
- Kitchen food preparation, vegetable/fruit chopping, raw meat handling on cutting board, harmless knife holding are NOT S1.
- Knife presence alone is not violence. Require clear threatening behavior, apparent assault/stabbing with victim context.
- Fictional/staged (film/game) is context but still assess depicted act per policy.
- News/educational reporting violence vs depicting assault — distinguish intent.
- Occluded/ambiguous (no clear victim/act) → safe or REVIEW, not HIGH_RISK.
Return `safe` or `unsafe` + category (e.g., S1). Do not invent a crime from one image. Assess image + instruction together.
""".trimIndent()
}
