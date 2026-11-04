package com.example.metaguardrail.data

/**
 * Phase 4 — Safety Trend Analytics.
 * Pure Kotlin helper — no Android UI dependencies, no I/O, no network, no DataStore mutation.
 * Works over the latest 50 retained ActivityEvent objects in memory.
 */

data class CategoryCount(val category: String, val count: Int)

data class TrendAnalytics(
    val totalCount: Int,
    val severityOrdered: List<Pair<Severity, Int>>, // SAFE, REVIEW, WARNING, HIGH_RISK
    val severityPercents: List<Pair<Severity, Int>>, // same order, percent 0..100
    val topCategories: List<CategoryCount>, // at most 5, by descending count
    val sourceCounts: List<Pair<DetectionSource, Int>>, // only sources with count > 0
    val insight: String
)

object TrendAnalyticsCalculator {

    private val SEVERITY_ORDER = listOf(Severity.SAFE, Severity.REVIEW, Severity.WARNING, Severity.HIGH_RISK)

    fun calculate(events: List<ActivityEvent>): TrendAnalytics {
        val total = events.size

        // Severity — fixed order, zero-safe
        val severityOrdered: List<Pair<Severity, Int>> = SEVERITY_ORDER.map { sev ->
            sev to events.count { it.severity == sev }
        }
        val severityPercents: List<Pair<Severity, Int>> = severityOrdered.map { (sev, count) ->
            sev to if (total == 0) 0 else (count * 100 / total)
        }

        // Categories — group by label, sort by count desc, cap 5
        val topCategories: List<CategoryCount> = events
            .groupBy { it.category }
            .map { (cat, list) -> CategoryCount(category = cat, count = list.size) }
            .sortedByDescending { it.count }
            .take(5)

        // Detection sources — only those with count > 0, sorted by count desc
        val sourceCounts: List<Pair<DetectionSource, Int>> = DetectionSource.entries
            .map { src -> src to events.count { it.source == src } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val insight = buildInsight(
            total = total,
            severityOrdered = severityOrdered,
            topCategories = topCategories,
            sourceCounts = sourceCounts
        )

        return TrendAnalytics(
            totalCount = total,
            severityOrdered = severityOrdered,
            severityPercents = severityPercents,
            topCategories = topCategories,
            sourceCounts = sourceCounts,
            insight = insight
        )
    }

    private fun buildInsight(
        total: Int,
        severityOrdered: List<Pair<Severity, Int>>,
        topCategories: List<CategoryCount>,
        sourceCounts: List<Pair<DetectionSource, Int>>
    ): String {
        if (total == 0) return ""
        // Need at least a couple of distinct items for a meaningful insight
        if (total < 2) return "Keep using MetaGuardRail — insights will appear after more safety activity is recorded."

        val topCategory = topCategories.firstOrNull()
        val topSource = sourceCounts.firstOrNull()

        // Never invent — only describe actual counts; never call SAFE an incident
        val parts = mutableListOf<String>()
        if (topCategory != null && topCategory.count >= 2) {
            parts.add("Most frequent safety area: ${topCategory.category} (${topCategory.count} of $total).")
        }
        // Only mention severity if non-SAFE is meaningful and at least 2 of that severity
        val meaningfulSeverities = severityOrdered.filter { it.first != Severity.SAFE && it.second >= 2 }
        val mostCommonMeaningful = meaningfulSeverities.maxByOrNull { it.second }
        if (mostCommonMeaningful != null) {
            parts.add("Most common severity: ${mostCommonMeaningful.first.label} (${mostCommonMeaningful.second}).")
        }
        if (topSource != null && topSource.second >= 2) {
            parts.add("Top detection source: ${topSource.first.label} (${topSource.second}).")
        }

        if (parts.isEmpty()) {
            return "Not enough repeated patterns to highlight a trend — continue using the app and check back."
        }
        return parts.joinToString(" ")
    }
}
