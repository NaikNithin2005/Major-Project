package com.example.android.domain.repository

import com.example.android.domain.model.SystemSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<SystemSettings>
    suspend fun getSettings(): SystemSettings
    suspend fun setSmsProtection(enabled: Boolean): Result<Unit>
    suspend fun setQrScanner(enabled: Boolean): Result<Unit>
    suspend fun setUrlAnalysis(enabled: Boolean): Result<Unit>
    suspend fun setHaptics(enabled: Boolean): Result<Unit>
    suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit>
    suspend fun setNotifyHighSeverity(enabled: Boolean): Result<Unit>
    suspend fun setNotifyMediumSeverity(enabled: Boolean): Result<Unit>
    suspend fun setNotifyDailyTips(enabled: Boolean): Result<Unit>
    suspend fun setShowNotificationPreviews(enabled: Boolean): Result<Unit>
    suspend fun setAiModelEnabled(enabled: Boolean): Result<Unit>
    suspend fun setOptInAiLearning(enabled: Boolean): Result<Unit>
    suspend fun setCloudSyncEnabled(enabled: Boolean): Result<Unit>
    suspend fun setBiometricLock(enabled: Boolean): Result<Unit>
    suspend fun setDarkTheme(enabled: Boolean): Result<Unit>
    suspend fun setThreatSharingEnabled(enabled: Boolean): Result<Unit>
    suspend fun syncSettingsWithCloud(userId: String): Result<Unit>
}
