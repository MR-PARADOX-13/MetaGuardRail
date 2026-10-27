package com.example.metaguardrail.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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
                }.toSet()
            )
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

    private object Keys {
        val mode = stringPreferencesKey("safety_mode")
        val speechLanguage = stringPreferencesKey("speech_language")
    }
}

data class SafetySettings(
    val mode: SafetyMode = SafetyMode.BALANCED,
    val speechLanguage: SpeechLanguage = SpeechLanguage.ENGLISH,
    val enabledCategories: Set<SafetyCategory> = SafetyCategory.entries
        .filter(SafetyCategory::enabledByDefault)
        .toSet()
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
