package com.example.metaguardrail.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phase 1 / Phase 5 — JSON export and FileProvider share helper.
 * Pure logic: no network, no DataStore mutation, no permissions.
 * SAF export is handled by the caller via CreateDocument; share uses cache file.
 */
object ReportExporter {

    /** ISO-8601 UTC formatter, e.g. 2025-05-16T12:34:56.789Z */
    fun isoFor(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }

    /** No-arg overload for spec compliance — formats now. */
    fun isoFor(): String = isoFor(System.currentTimeMillis())

    /**
     * Suggested file name for SAF CreateDocument and cache share.
     * UTC-based to avoid locale variance: metaguardrail-report-YYYYMMDD-HHmmss.json
     */
    fun suggestedFileName(nowMs: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return "metaguardrail-report-${sdf.format(Date(nowMs))}.json"
    }

    /** No-arg overload — uses current time. */
    fun suggestedFileName(): String = suggestedFileName(System.currentTimeMillis())

    /**
     * Builds canonical JSON report for the given events.
     * Top-level: generatedAt (ISO UTC), generatedAtMs, app, version, count, events[]
     * Each event: id, timestampMs, timestampIso, title, category, severity (name+label),
     * source (name+label), explanation, confidence
     */
    fun buildReportJson(events: List<ActivityEvent>): String {
        val nowMs = System.currentTimeMillis()
        val root = JSONObject()
        root.put("generatedAt", isoFor(nowMs))
        root.put("generatedAtMs", nowMs)
        root.put("app", "Meta Guard Rail")
        root.put("version", 1)
        root.put("count", events.size)
        val arr = JSONArray()
        for (e in events) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("timestampMs", e.timestampMs)
            obj.put("timestampIso", isoFor(e.timestampMs))
            obj.put("title", e.title)
            obj.put("category", e.category)
            obj.put("severity", e.severity.name)
            obj.put("severityLabel", e.severity.label)
            obj.put("source", e.source.name)
            obj.put("sourceLabel", e.source.label)
            obj.put("explanation", e.explanation)
            if (e.confidence != null) {
                obj.put("confidence", e.confidence.toDouble())
            } else {
                obj.put("confidence", JSONObject.NULL)
            }
            arr.put(obj)
        }
        root.put("events", arr)
        return root.toString(2)
    }

    /**
     * Writes [json] to cacheDir/reports/<suggestedFileName> and returns the File.
     * Caller is responsible for granting URI permission via FileProvider.
     * This is the required helper for ACTION_SEND share.
     */
    fun prepareShareCacheFile(context: Context, json: String): File {
        return prepareShareCacheFile(context, json, System.currentTimeMillis())
    }

    /** Testable overload with explicit timestamp. */
    fun prepareShareCacheFile(context: Context, json: String, nowMs: Long): File {
        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, suggestedFileName(nowMs))
        file.writeText(json, Charsets.UTF_8)
        return file
    }
}
