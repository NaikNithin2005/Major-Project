package com.example.android.data.repository

import com.example.android.data.local.datastore.PreferencesDataStore
import com.example.android.domain.model.SystemSettings
import com.example.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SettingsRepositoryImpl(
    private val preferencesDataStore: PreferencesDataStore
) : SettingsRepository {

    override fun observeSettings(): Flow<SystemSettings> {
        return combine(
            preferencesDataStore.isSmsProtectionEnabled,
            preferencesDataStore.isQrScannerEnabled,
            preferencesDataStore.areNotificationsEnabled,
            preferencesDataStore.isBiometricLockEnabled
        ) { sms, qr, notif, bio ->
            SystemSettings(
                realtimeSmsProtection = sms,
                realtimeQrScanner = qr,
                notificationsEnabled = notif,
                biometricLock = bio
            )
        }
    }

    override suspend fun setSmsProtection(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setSmsProtection(enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setQrScanner(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setQrScanner(enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setNotificationsEnabled(enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setBiometricLock(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setBiometricLock(enabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
