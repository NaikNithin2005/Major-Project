package com.example.android.domain.repository

import com.example.android.domain.model.SystemSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<SystemSettings>
    suspend fun setSmsProtection(enabled: Boolean): Result<Unit>
    suspend fun setQrScanner(enabled: Boolean): Result<Unit>
    suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit>
    suspend fun setBiometricLock(enabled: Boolean): Result<Unit>
}
