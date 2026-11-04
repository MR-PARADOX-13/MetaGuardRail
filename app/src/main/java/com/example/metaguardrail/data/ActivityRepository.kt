package com.example.metaguardrail.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.activityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meta_guard_rail_activity"
)

/**
 * Persists ActivityEvent history to DataStore as a JSON array under key "activity_events_json".
 * Keeps max 50 events FIFO (oldest dropped first when over limit).
 * Uses manual org.json encoding (no kotlinx.serialization needed for this store).
 */
class ActivityRepository(context: Context) {
    private val dataStore = context.applicationContext.activityDataStore

    val events: Flow<List<ActivityEvent>> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences())
            else throw error
        }
        .map { prefs -> decode(prefs[Keys.eventsJson]) }

    suspend fun addEvent(event: ActivityEvent) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.eventsJson])
            val updated = (current + event).let { list ->
                if (list.size > MAX_EVENTS) list.takeLast(MAX_EVENTS) else list
            }
            prefs[Keys.eventsJson] = encode(updated)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs -> prefs[Keys.eventsJson] = encode(emptyList()) }
    }

    private fun decode(json: String?): List<ActivityEvent> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        ActivityEvent(
                            id = obj.optString("id", ""),
                            timestampMs = obj.optLong("timestampMs", 0L),
                            title = obj.optString("title", ""),
                            category = obj.optString("category", ""),
                            severity = Severity.fromValue(obj.optString("severity", "")) ?: Severity.SAFE,
                            source = DetectionSource.entries.firstOrNull {
                                it.name == obj.optString("source", "")
                            } ?: DetectionSource.LOCAL_RULE,
                            explanation = obj.optString("explanation", ""),
                            confidence = if (obj.has("confidence") && !obj.isNull("confidence"))
                                obj.optDouble("confidence", Double.NaN).let { if (it.isNaN()) null else it.toFloat() }
                            else null,
                            classificationMethod = obj.optString("classificationMethod", null).takeIf { it?.isNotBlank() == true },
                            modelVersion = obj.optString("modelVersion", null).takeIf { it?.isNotBlank() == true }
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encode(events: List<ActivityEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("timestampMs", e.timestampMs)
            obj.put("title", e.title)
            obj.put("category", e.category)
            obj.put("severity", e.severity.name)
            obj.put("source", e.source.name)
            obj.put("explanation", e.explanation)
            if (e.confidence != null) obj.put("confidence", e.confidence.toDouble()) else obj.put("confidence", JSONObject.NULL)
            if (e.classificationMethod != null) obj.put("classificationMethod", e.classificationMethod)
            if (e.modelVersion != null) obj.put("modelVersion", e.modelVersion)
            arr.put(obj)
        }
        return arr.toString()
    }

    private object Keys {
        val eventsJson = stringPreferencesKey("activity_events_json")
    }

    companion object {
        const val MAX_EVENTS = 50
    }
}
