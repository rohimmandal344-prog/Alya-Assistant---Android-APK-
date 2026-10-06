package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alya_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        private val KEY_VOICE_PITCH = floatPreferencesKey("voice_pitch")
        private val KEY_VOICE_SPEED = floatPreferencesKey("voice_speed")
        private val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        private val KEY_THINKING_LEVEL = stringPreferencesKey("thinking_level")
        private val KEY_OFFLINE_MODE = booleanPreferencesKey("force_offline_mode")
        private val KEY_ACCESSIBILITY_CONFIRMATION = booleanPreferencesKey("require_gesture_confirmation")
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_CUSTOM_API_KEY = stringPreferencesKey("custom_gemini_api_key")
        private val KEY_BACKGROUND_TALKING = booleanPreferencesKey("background_talking_enabled")
        private val KEY_CUSTOM_WAKE_WORD = stringPreferencesKey("custom_wake_word")
        private val KEY_WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        private val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAPTIC_FEEDBACK] ?: true
    }

    val customWakeWord: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_WAKE_WORD] ?: "Alya"
    }

    val wakeSensitivity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_WAKE_SENSITIVITY] ?: 0.7f
    }

    val selectedLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: "en-US"
    }

    val wakeWordEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_WAKE_WORD] ?: true
    }

    val voicePitch: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_VOICE_PITCH] ?: 1.05f // Natural female tone
    }

    val voiceSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_VOICE_SPEED] ?: 1.0f
    }

    val aiModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_MODEL] ?: "gemini-2.5-flash"
    }

    val thinkingLevel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THINKING_LEVEL] ?: "low"
    }

    val forceOfflineMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_OFFLINE_MODE] ?: false
    }

    val requireGestureConfirmation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCESSIBILITY_CONFIRMATION] ?: false
    }

    val customApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_API_KEY] ?: ""
    }

    val backgroundTalkingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_TALKING] ?: true
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WAKE_WORD] = enabled }
    }

    suspend fun setVoicePitch(pitch: Float) {
        context.dataStore.edit { it[KEY_VOICE_PITCH] = pitch }
    }

    suspend fun setVoiceSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_VOICE_SPEED] = speed }
    }

    suspend fun setAiModel(model: String) {
        context.dataStore.edit { it[KEY_AI_MODEL] = model }
    }

    suspend fun setThinkingLevel(level: String) {
        context.dataStore.edit { it[KEY_THINKING_LEVEL] = level }
    }

    suspend fun setForceOfflineMode(offline: Boolean) {
        context.dataStore.edit { it[KEY_OFFLINE_MODE] = offline }
    }

    suspend fun setRequireGestureConfirmation(require: Boolean) {
        context.dataStore.edit { it[KEY_ACCESSIBILITY_CONFIRMATION] = require }
    }

    suspend fun setSelectedLanguage(lang: String) {
        context.dataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    suspend fun setCustomApiKey(key: String) {
        context.dataStore.edit { it[KEY_CUSTOM_API_KEY] = key.trim() }
    }

    suspend fun setBackgroundTalkingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BACKGROUND_TALKING] = enabled }
    }

    suspend fun setCustomWakeWord(wakeWord: String) {
        context.dataStore.edit { it[KEY_CUSTOM_WAKE_WORD] = wakeWord.trim() }
    }

    suspend fun setWakeSensitivity(sensitivity: Float) {
        context.dataStore.edit { it[KEY_WAKE_SENSITIVITY] = sensitivity }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }
}
