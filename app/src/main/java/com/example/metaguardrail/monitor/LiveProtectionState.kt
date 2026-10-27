package com.example.metaguardrail.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-safe status for the live checker. Captured screen text and audio transcripts are not stored. */
data class LiveProtectionStatus(
    val isRunning: Boolean = false,
    val message: String = "Automatic checking is off",
    val finding: String? = null,
    val source: String? = null
)

object LiveProtectionState {
    private val mutableState = MutableStateFlow(LiveProtectionStatus())
    val state = mutableState.asStateFlow()

    fun starting() {
        mutableState.value = LiveProtectionStatus(message = "Starting the approved screen check…")
    }

    fun running(message: String = "Checking text and sampled images in Instagram and Facebook") {
        mutableState.value = mutableState.value.copy(isRunning = true, message = message)
    }

    fun waitingForSocialApp() {
        mutableState.value = LiveProtectionStatus(
            isRunning = true,
            message = "Waiting for Instagram or Facebook"
        )
    }

    fun flagged(category: String, source: String = "content check") {
        mutableState.value = mutableState.value.copy(
            isRunning = true,
            message = "Potentially concerning content found",
            finding = category,
            source = source
        )
    }

    fun clearFinding() {
        if (mutableState.value.isRunning) {
            mutableState.value = mutableState.value.copy(finding = null, source = null)
        }
    }

    fun stopped(message: String = "Automatic checking is off") {
        mutableState.value = LiveProtectionStatus(message = message)
    }
}
