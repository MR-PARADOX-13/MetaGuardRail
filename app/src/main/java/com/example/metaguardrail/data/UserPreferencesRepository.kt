package com.example.metaguardrail.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException
import java.util.Locale

private val Context.metaGuardRailDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meta_guard_rail_preferences"
)

/** Stores the user's safety preferences locally on this device. */
class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.metaGuardRailDataStore

    val settings: Flow<SafetySettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            SafetySettings(
                mode = SafetyMode.fromValue(preferences[Keys.mode]),
                speechLanguage = SpeechLanguage.fromValue(preferences[Keys.speechLanguage]),
                enabledCategories = SafetyCategory.entries.filter { category ->
                    preferences[category.key] ?: category.enabledByDefault
                }.toSet(),
                customCategories = parseCustomCategories(preferences[Keys.customCategoriesJson])
            )
        }

    /** Flow of custom categories only, decoded from JSON under key "custom_categories_json". */
    val customCategories: Flow<Set<String>> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            parseCustomCategories(preferences[Keys.customCategoriesJson])
        }

    suspend fun setMode(mode: SafetyMode) {
        dataStore.edit { preferences ->
            preferences[Keys.mode] = mode.value
            when (mode) {
                SafetyMode.BALANCED -> SafetyCategory.entries.forEach { category ->
                    preferences[category.key] = category.enabledByDefault
                }

                SafetyMode.STRICT -> SafetyCategory.entries.forEach { category ->
                    preferences[category.key] = true
                }

                SafetyMode.CUSTOM -> Unit
            }
        }
    }

    suspend fun setCategoryEnabled(category: SafetyCategory, enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[category.key] = enabled
            preferences[Keys.mode] = SafetyMode.CUSTOM.value
        }
    }

    suspend fun setSpeechLanguage(language: SpeechLanguage) {
        dataStore.edit { preferences ->
            preferences[Keys.speechLanguage] = language.value
        }
    }

    /**
     * Adds a custom category.
     * Validation: trimmed lowercased, regex ^[a-z0-9 _-]{2,30}$, max 5, duplicate check (case-insensitive).
     * @return Result.success(true) on success, Result.failure(IllegalArgumentException) on validation failure.
     */
    suspend fun addCustomCategory(name: String): Result<Boolean> {
        return try {
            val normalized = name.trim().lowercase(Locale.ROOT)
            // Validate synchronously before reading datastore to fail fast on regex
            validateCustomCategoryNameForAdd(normalized, emptySet(), checkDuplicateAgainstEmpty = false)

            dataStore.edit { preferences ->
                val current = parseCustomCategories(preferences[Keys.customCategoriesJson])
                // Re-validate against actual current set inside transaction
                validateCustomCategoryNameForAdd(normalized, current, checkDuplicateAgainstEmpty = true)
                val updated = current + normalized
                preferences[Keys.customCategoriesJson] = encodeCustomCategories(updated)
            }
            Result.success(true)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes a custom category (case-insensitive).
     * @return Result.success(true) if removed, Result.failure(IllegalArgumentException) if not found or invalid.
     */
    suspend fun removeCustomCategory(name: String): Result<Boolean> {
        return try {
            val normalized = name.trim().lowercase(Locale.ROOT)
            if (normalized.isEmpty()) {
                throw IllegalArgumentException("Category name must not be blank.")
            }
            dataStore.edit { preferences ->
                val current = parseCustomCategories(preferences[Keys.customCategoriesJson])
                if (current.none { it.equals(normalized, ignoreCase = true) }) {
                    throw IllegalArgumentException("Category '$normalized' does not exist and cannot be removed.")
                }
                val updated = current.filterNot { it.equals(normalized, ignoreCase = true) }.toSet()
                preferences[Keys.customCategoriesJson] = encodeCustomCategories(updated)
            }
            Result.success(true)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCustomCategories(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val array = JSONArray(json)
            val result = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim().lowercase(Locale.ROOT)
                if (item.isNotEmpty()) result.add(item)
            }
            result
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun encodeCustomCategories(categories: Set<String>): String {
        val array = JSONArray()
        // Sort for deterministic output
        categories.sorted().forEach { array.put(it) }
        return array.toString()
    }

    private fun validateCustomCategoryNameForAdd(
        normalized: String,
        existing: Set<String>,
        checkDuplicateAgainstEmpty: Boolean
    ) {
        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Category name must not be blank.")
        }
        if (!VALID_CUSTOM_CATEGORY_REGEX.matches(normalized)) {
            throw IllegalArgumentException(
                "Invalid category name '$normalized'. Must match regex ^[a-z0-9 _-]{2,30}$ (2-30 chars, only lowercase letters, digits, spaces, underscores, or hyphens)."
            )
        }
        if (existing.size >= MAX_CUSTOM_CATEGORIES) {
            throw IllegalArgumentException(
                "Maximum of $MAX_CUSTOM_CATEGORIES custom categories allowed. Remove one before adding '$normalized'."
            )
        }
        if (checkDuplicateAgainstEmpty || existing.isNotEmpty()) {
            val duplicate = existing.any { it.equals(normalized, ignoreCase = true) }
            if (duplicate) {
                throw IllegalArgumentException("Category '$normalized' already exists (duplicate check is case-insensitive).")
            }
        }
    }

    /** Theme mode flow (separate from SafetySettings to avoid breaking existing consumers). */
    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> ThemeMode.fromValue(preferences[Keys.themeMode]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[Keys.themeMode] = mode.value }
    }

    // Cloud Text Analysis consent — OFF by default. No network on read/write. Stored only.
    val cloudTextAnalysisEnabled: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[Keys.cloudTextAnalysisEnabled] ?: false }

    suspend fun setCloudTextAnalysisEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.cloudTextAnalysisEnabled] = enabled }
    }

    // Phase 3: Safety incident notification preferences — OFF by default
    val notifyHighRisk: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[Keys.notifyHighRisk] ?: false }

    val notifyWarning: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[Keys.notifyWarning] ?: false }

    suspend fun setNotifyHighRisk(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.notifyHighRisk] = enabled }
    }

    suspend fun setNotifyWarning(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.notifyWarning] = enabled }
    }

    val notifiedIncidentIds: Flow<Set<String>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[Keys.notifiedIds] ?: emptySet() }

    suspend fun addNotifiedIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[Keys.notifiedIds] ?: emptySet()
            preferences[Keys.notifiedIds] = current + ids
        }
    }

    suspend fun clearNotifiedIds() {
        dataStore.edit { preferences -> preferences.remove(Keys.notifiedIds) }
    }

    suspend fun pruneNotifiedIdsToCurrent(currentIds: Set<String>) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.notifiedIds] ?: emptySet()
            val pruned = current.intersect(currentIds)
            if (pruned.size != current.size) {
                if (pruned.isEmpty()) preferences.remove(Keys.notifiedIds)
                else preferences[Keys.notifiedIds] = pruned
            }
        }
    }

    private object Keys {
        val mode = stringPreferencesKey("safety_mode")
        val speechLanguage = stringPreferencesKey("speech_language")
        val customCategoriesJson = stringPreferencesKey("custom_categories_json")
        val themeMode = stringPreferencesKey("theme_mode")
        val notifyHighRisk = booleanPreferencesKey("notify_high_risk")
        val notifyWarning = booleanPreferencesKey("notify_warning")
        val notifiedIds = stringSetPreferencesKey("notified_incident_ids")
        val cloudTextAnalysisEnabled = booleanPreferencesKey("cloud_text_analysis_enabled")
    }

    companion object {
        const val MAX_CUSTOM_CATEGORIES = 5
        val VALID_CUSTOM_CATEGORY_REGEX: Regex = Regex("^[a-z0-9 _-]{2,30}$")
    }
}

data class SafetySettings(
    val mode: SafetyMode = SafetyMode.BALANCED,
    val speechLanguage: SpeechLanguage = SpeechLanguage.ENGLISH,
    val enabledCategories: Set<SafetyCategory> = SafetyCategory.entries
        .filter(SafetyCategory::enabledByDefault)
        .toSet(),
    val customCategories: Set<String> = emptySet()
)

enum class SafetyMode(val value: String, val label: String) {
    BALANCED("balanced", "Balanced"),
    STRICT("strict", "Strict"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromValue(value: String?): SafetyMode = entries.firstOrNull {
            it.value == value
        } ?: BALANCED
    }
}

enum class SpeechLanguage(val value: String, val label: String) {
    ENGLISH("en", "English audio"),
    TELUGU("te", "Telugu audio (experimental)");

    companion object {
        fun fromValue(value: String?): SpeechLanguage = entries.firstOrNull {
            it.value == value
        } ?: ENGLISH
    }
}

enum class SafetyCategory(
    val key: Preferences.Key<Boolean>,
    val label: String,
    val description: String,
    val enabledByDefault: Boolean
) {
    HARASSMENT(
        booleanPreferencesKey("category_harassment"),
        "Harassment and bullying",
        "Insults, threats, or targeted abuse",
        true
    ),
    VULGAR_LANGUAGE(
        booleanPreferencesKey("category_vulgar_language"),
        "Vulgar language",
        "Profanity and crude language in visible text",
        true
    ),
    SCAMS(
        booleanPreferencesKey("category_scams"),
        "Scams and manipulation",
        "Deceptive offers or pressure tactics",
        true
    ),
    DISTRESSING_CONTENT(
        booleanPreferencesKey("category_distressing_content"),
        "Distressing content",
        "Potentially upsetting language or imagery",
        true
    ),
    ADULT_CONTENT(
        booleanPreferencesKey("category_adult_content"),
        "Adult content",
        "Sexual or suggestive material",
        true
    ),
    SOLICITATION_GROOMING(
        booleanPreferencesKey("category_solicitation_grooming"),
        "Solicitation & grooming concerns",
        "Inappropriate adult-minor contact indicators",
        true
    ),
    SELF_HARM(
        booleanPreferencesKey("category_self_harm"),
        "Self-harm references",
        "Provides supportive resources and guidance",
        true
    )
}
