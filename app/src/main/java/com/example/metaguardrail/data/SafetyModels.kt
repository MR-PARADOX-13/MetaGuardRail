package com.example.metaguardrail.data

/**
 * Core domain severity for content and link safety.
 * Aligned with Muse Spark allowed severities: SAFE / REVIEW / WARNING / HIGH_RISK.
 */
enum class Severity(val label: String) {
    SAFE("Safe"),
    REVIEW("Review suggested"),
    WARNING("Warning"),
    HIGH_RISK("High risk");

    companion object {
        fun fromValue(value: String?): Severity? = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        }
    }
}

/** Source that produced a safety signal. */
enum class DetectionSource(val label: String) {
    LOCAL_RULE("Local rule"),
    VISION_MODEL("Vision model"),
    AUDIO_TRANSCRIPT("Audio transcript"),
    MUSE_SPARK("Muse Spark")
}

/** Action the UI should take for a finding. */
enum class RecommendedAction(val label: String) {
    NONE("No action"),
    REVIEW_CONTENT("Review content"),
    SHOW_WARNING("Show warning"),
    SHOW_SHIELD("Show shield"),
    SEEK_SUPPORT("Seek support");

    companion object {
        /** Simple mapping from severity to recommended action. */
        fun fromSeverity(severity: Severity): RecommendedAction = when (severity) {
            Severity.SAFE -> NONE
            Severity.REVIEW -> REVIEW_CONTENT
            Severity.WARNING -> SHOW_WARNING
            Severity.HIGH_RISK -> SHOW_SHIELD
        }
    }
}

/**
 * Result of a deterministic, local link safety check.
 * @param url original input (trimmed for display, original preserved for INVALID cases)
 * @param status overall link status
 * @param explanation human-readable explanation; must not be blank
 * @param https true if scheme is https, false if http, null otherwise (invalid/missing/unsupported)
 */
data class LinkSafetyResult(
    val url: String,
    val status: LinkStatus,
    val explanation: String,
    val https: Boolean?
)

/** Link-specific status. Separate from Severity to keep link UX distinct. */
enum class LinkStatus(val label: String) {
    SAFE("Safe"),
    REVIEW("Review suggested"),
    WARNING("Warning"),
    UNKNOWN("Unknown"),
    INVALID("Invalid");

    companion object {
        fun fromValue(value: String?): LinkStatus? = entries.firstOrNull {
            it.name.equals(value?.trim(), ignoreCase = true)
        }
    }
}
