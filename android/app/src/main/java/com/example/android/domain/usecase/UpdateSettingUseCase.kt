package com.example.android.domain.usecase

import com.example.android.domain.repository.SettingsRepository

class UpdateSettingUseCase(
    private val repository: SettingsRepository
) {
    suspend fun setSmsProtection(enabled: Boolean): Result<Unit> = repository.setSmsProtection(enabled)
    suspend fun setQrScanner(enabled: Boolean): Result<Unit> = repository.setQrScanner(enabled)
    suspend fun setUrlAnalysis(enabled: Boolean): Result<Unit> = repository.setUrlAnalysis(enabled)
    suspend fun setHaptics(enabled: Boolean): Result<Unit> = repository.setHaptics(enabled)
    suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> = repository.setNotificationsEnabled(enabled)
    suspend fun setNotifyHighSeverity(enabled: Boolean): Result<Unit> = repository.setNotifyHighSeverity(enabled)
    suspend fun setNotifyMediumSeverity(enabled: Boolean): Result<Unit> = repository.setNotifyMediumSeverity(enabled)
    suspend fun setNotifyDailyTips(enabled: Boolean): Result<Unit> = repository.setNotifyDailyTips(enabled)
    suspend fun setShowNotificationPreviews(enabled: Boolean): Result<Unit> = repository.setShowNotificationPreviews(enabled)
    suspend fun setAiModelEnabled(enabled: Boolean): Result<Unit> = repository.setAiModelEnabled(enabled)
    suspend fun setOptInAiLearning(enabled: Boolean): Result<Unit> = repository.setOptInAiLearning(enabled)
    suspend fun setCloudSyncEnabled(enabled: Boolean): Result<Unit> = repository.setCloudSyncEnabled(enabled)
    suspend fun setBiometricLock(enabled: Boolean): Result<Unit> = repository.setBiometricLock(enabled)
    suspend fun setDarkTheme(enabled: Boolean): Result<Unit> = repository.setDarkTheme(enabled)
    suspend fun setThreatSharingEnabled(enabled: Boolean): Result<Unit> = repository.setThreatSharingEnabled(enabled)
    suspend fun syncSettingsWithCloud(userId: String): Result<Unit> = repository.syncSettingsWithCloud(userId)
}
