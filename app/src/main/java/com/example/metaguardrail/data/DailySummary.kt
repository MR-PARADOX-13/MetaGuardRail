package com.example.metaguardrail.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Phase 2 — Daily protection summary.
 * Pure Kotlin helper — no I/O, no network, no DataStore mutation.
 * Uses device local timezone via Calendar.getInstance().
 */
data class DayCount(
    val dayStartMs: Long,
    val label: String,
    val count: Int
)

data class DailySummary(
    val todayTotal: Int,
    val todayHighRisk: Int,
    val todayWarning: Int,
    val sevenDayBuckets: List<DayCount>,
    val insight: String
)

object DailySummaryCalculator {

    fun calculate(
        events: List<ActivityEvent>,
        nowMs: Long = System.currentTimeMillis()
    ): DailySummary {
        // Today at 00:00 local
        val todayCal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMs = todayCal.timeInMillis

        // Build 7 consecutive days including today, oldest first
        val dayStarts = mutableListOf<Long>()
        val dayLabels = mutableListOf<String>()
        val labelFormat = SimpleDateFormat("M/d", Locale.getDefault()).apply {
            timeZone = todayCal.timeZone
        }
        for (offset in 6 downTo 0) {
            val cal = (todayCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            // Ensure start-of-day (clone already at 00:00, but add preserves it)
            val dayStart = cal.timeInMillis
            dayStarts.add(dayStart)
            dayLabels.add(labelFormat.format(cal.time))
        }

        // Count per bucket [dayStart, nextDayStart)
        val buckets = mutableListOf<DayCount>()
        for (i in dayStarts.indices) {
            val dayStart = dayStarts[i]
            val dayEndExclusive = if (i < dayStarts.lastIndex) {
                dayStarts[i + 1]
            } else {
                // tomorrow start
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dayStart
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            val count = events.count { it.timestampMs >= dayStart && it.timestampMs < dayEndExclusive }
            buckets.add(DayCount(dayStartMs = dayStart, label = dayLabels[i], count = count))
        }

        // Today's severity breakdown — use last bucket's window to stay consistent
        val todayStart = buckets.last().dayStartMs
        val tomorrowStart = Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val todayEvents = events.filter { it.timestampMs >= todayStart && it.timestampMs < tomorrowStart }
        val todayTotal = todayEvents.size
        val todayHighRisk = todayEvents.count { it.severity == Severity.HIGH_RISK }
        val todayWarning = todayEvents.count { it.severity == Severity.WARNING }

        val insight = buildInsight(
            todayTotal = todayTotal,
            todayHighRisk = todayHighRisk,
            todayWarning = todayWarning,
            buckets = buckets,
            isEmpty = events.isEmpty()
        )

        return DailySummary(
            todayTotal = todayTotal,
            todayHighRisk = todayHighRisk,
            todayWarning = todayWarning,
            sevenDayBuckets = buckets,
            insight = insight
        )
    }

    private fun buildInsight(
        todayTotal: Int,
        todayHighRisk: Int,
        todayWarning: Int,
        buckets: List<DayCount>,
        isEmpty: Boolean
    ): String {
        if (isEmpty) return "No protection events recorded yet. Your summary will appear here after checks."
        val allZero = buckets.all { it.count == 0 }
        if (allZero) return "No incidents in the last 7 days."
        if (todayHighRisk > 0) return "$todayHighRisk high-risk incident(s) recorded today — review them in Activity."
        if (todayWarning > 0) return "$todayWarning warning(s) today — monitor for patterns."
        if (todayTotal > 0) return "Today looks calm — $todayTotal event(s) with no high-risk or warnings."
        // today 0 but some prior day has events
        return "No incidents recorded today. Check Activity for earlier events."
    }
}
