package com.example.metaguardrail.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.metaguardrail.MainActivity
import com.example.metaguardrail.R
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.Severity

object IncidentNotifier {
    const val CHANNEL_HIGH_RISK = "safety_high_risk"
    const val CHANNEL_WARNING = "safety_warning"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Existing foreground channel "live_protection" is created in ScreenMonitoringService — keep independent
        val highRisk = NotificationChannel(
            CHANNEL_HIGH_RISK,
            "Safety alerts — High risk",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Immediate attention for high-risk incidents"
            enableVibration(true)
            enableLights(true)
        }
        val warning = NotificationChannel(
            CHANNEL_WARNING,
            "Safety alerts — Warnings",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Warnings for potentially concerning incidents"
        }
        nm.createNotificationChannel(highRisk)
        nm.createNotificationChannel(warning)
    }

    fun notifyForEvent(context: Context, event: ActivityEvent) {
        ensureChannels(context)
        val channelId = when (event.severity) {
            Severity.HIGH_RISK -> CHANNEL_HIGH_RISK
            Severity.WARNING -> CHANNEL_WARNING
            else -> return
        }
        val title = when (event.severity) {
            Severity.HIGH_RISK -> "High-risk incident"
            Severity.WARNING -> "Safety warning"
            else -> return
        }
        // Privacy-conscious: generic category only; custom names never shown (mapped to generic)
        val text = sanitizeCategoryForNotification(event.category, event.source)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_INCIDENT
            putExtra(MainActivity.EXTRA_INCIDENT_ID, event.id)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied or notifications disabled — respect denial
        }
    }

    private fun sanitizeCategoryForNotification(category: String, @Suppress("UNUSED_PARAMETER") source: com.example.metaguardrail.data.DetectionSource): String {
        if (category.isBlank()) return "Safety incident"
        // Built-in categories have known labels; anything not matching a built-in label is treated as custom
        val builtInLabels = setOf(
            "Harassment and bullying", "Vulgar language", "Scams and manipulation",
            "Distressing content", "Adult content", "Solicitation & grooming concerns",
            "Self-harm references", "Safety concern", "Safety incident"
        )
        // Also accept normalized lowercase comparison for safety
        val normalized = category.trim()
        val isBuiltIn = builtInLabels.any { it.equals(normalized, ignoreCase = true) }
        return if (isBuiltIn) normalized else "Custom category"
    }
}
