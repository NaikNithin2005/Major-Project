package com.example.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.android.domain.model.SystemSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aegis_preferences")

class PreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_REALTIME_SMS_PROTECTION = booleanPreferencesKey("realtime_sms_protection")
        val KEY_REALTIME_QR_SCANNER = booleanPreferencesKey("realtime_qr_scanner")
        val KEY_URL_ANALYSIS_ENABLED = booleanPreferencesKey("url_analysis_enabled")
        val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFY_HIGH_SEVERITY = booleanPreferencesKey("notify_high_severity")
        val KEY_NOTIFY_MEDIUM_SEVERITY = booleanPreferencesKey("notify_medium_severity")
        val KEY_NOTIFY_DAILY_TIPS = booleanPreferencesKey("notify_daily_tips")
        val KEY_SHOW_NOTIFICATION_PREVIEWS = booleanPreferencesKey("show_notification_previews")
        val KEY_AI_MODEL_ENABLED = booleanPreferencesKey("ai_model_enabled")
        val KEY_OPT_IN_AI_LEARNING = booleanPreferencesKey("opt_in_ai_learning")
        val KEY_CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_SYNC_FREQUENCY_HOURS = intPreferencesKey("sync_frequency_hours")
        val KEY_THREAT_INTEL_SHARING = booleanPreferencesKey("threat_intel_sharing_enabled")
    }

    val systemSettings: Flow<SystemSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            SystemSettings(
                realtimeSmsProtection = preferences[KEY_REALTIME_SMS_PROTECTION] ?: true,
                realtimeQrScanner = preferences[KEY_REALTIME_QR_SCANNER] ?: true,
                urlAnalysisEnabled = preferences[KEY_URL_ANALYSIS_ENABLED] ?: true,
                hapticsEnabled = preferences[KEY_HAPTICS_ENABLED] ?: true,
                notificationsEnabled = preferences[KEY_NOTIFICATIONS_ENABLED] ?: true,
                notifyHighSeverity = preferences[KEY_NOTIFY_HIGH_SEVERITY] ?: true,
                notifyMediumSeverity = preferences[KEY_NOTIFY_MEDIUM_SEVERITY] ?: true,
                notifyDailyTips = preferences[KEY_NOTIFY_DAILY_TIPS] ?: true,
                showNotificationPreviews = preferences[KEY_SHOW_NOTIFICATION_PREVIEWS] ?: false,
                aiModelEnabled = preferences[KEY_AI_MODEL_ENABLED] ?: true,
                optInAiLearning = preferences[KEY_OPT_IN_AI_LEARNING] ?: false,
                cloudSyncEnabled = preferences[KEY_CLOUD_SYNC_ENABLED] ?: true,
                biometricLock = preferences[KEY_BIOMETRIC_LOCK] ?: false,
                darkTheme = preferences[KEY_DARK_THEME] ?: true,
                threatIntelligenceSharingEnabled = preferences[KEY_THREAT_INTEL_SHARING] ?: false
            )
        }

    val isSmsProtectionEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_REALTIME_SMS_PROTECTION] ?: true }

    val isQrScannerEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_REALTIME_QR_SCANNER] ?: true }

    val areNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_NOTIFICATIONS_ENABLED] ?: true }

    val isBiometricLockEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_BIOMETRIC_LOCK] ?: false }

    val isThreatSharingEnabled: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_THREAT_INTEL_SHARING] ?: false }

    val showNotificationPreviews: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[KEY_SHOW_NOTIFICATION_PREVIEWS] ?: false }

    suspend fun getSystemSettings(): SystemSettings {
        return try {
            systemSettings.first()
        } catch (e: Exception) {
            SystemSettings()
        }
    }

    suspend fun setSmsProtection(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REALTIME_SMS_PROTECTION] = enabled
        }
    }

    suspend fun setQrScanner(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REALTIME_QR_SCANNER] = enabled
        }
    }

    suspend fun setUrlAnalysis(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_URL_ANALYSIS_ENABLED] = enabled
        }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setNotifyHighSeverity(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFY_HIGH_SEVERITY] = enabled
        }
    }

    suspend fun setNotifyMediumSeverity(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFY_MEDIUM_SEVERITY] = enabled
        }
    }

    suspend fun setNotifyDailyTips(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFY_DAILY_TIPS] = enabled
        }
    }

    suspend fun setShowNotificationPreviews(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_NOTIFICATION_PREVIEWS] = enabled
        }
    }

    suspend fun setAiModelEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AI_MODEL_ENABLED] = enabled
        }
    }

    suspend fun setOptInAiLearning(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_OPT_IN_AI_LEARNING] = enabled
        }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CLOUD_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIOMETRIC_LOCK] = enabled
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DARK_THEME] = enabled
        }
    }

    suspend fun setThreatSharing(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THREAT_INTEL_SHARING] = enabled
        }
    }
}
