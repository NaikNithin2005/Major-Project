package com.example.android.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aegis_preferences")

class PreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_REALTIME_SMS_PROTECTION = booleanPreferencesKey("realtime_sms_protection")
        val KEY_REALTIME_QR_SCANNER = booleanPreferencesKey("realtime_qr_scanner")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme")
        val KEY_SYNC_FREQUENCY_HOURS = intPreferencesKey("sync_frequency_hours")
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

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIOMETRIC_LOCK] = enabled
        }
    }
}
