package com.example.metaguardrail.data
import java.util.UUID
data class ActivityEvent(val id: String=UUID.randomUUID().toString(), val timestampMs: Long=System.currentTimeMillis(), val title: String, val category: String, val severity: Severity, val source: DetectionSource, val explanation: String, val confidence: Float? = null)
