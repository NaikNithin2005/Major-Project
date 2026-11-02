package com.example.android.data.repository

import com.example.android.data.local.datastore.PreferencesDataStore
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.model.SystemSettings
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SettingsRepositoryImpl(
    private val preferencesDataStore: PreferencesDataStore,
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource(),
    private val authRepository: AuthRepository? = null
) : SettingsRepository {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun observeSettings(): Flow<SystemSettings> {
        return preferencesDataStore.systemSettings
    }

    override suspend fun getSettings(): SystemSettings {
        return preferencesDataStore.getSystemSettings()
    }

    private fun maybeSyncToCloud() {
        val currentUser = authRepository?.getCurrentUser()
        if (currentUser != null && !currentUser.isGuest) {
            scope.launch {
                try {
                    val settings = preferencesDataStore.getSystemSettings()
                    firestoreDataSource.saveUserSettings(currentUser.uid, settings.toMap())
                } catch (ignored: Exception) {}
            }
        }
    }

    override suspend fun setSmsProtection(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setSmsProtection(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setQrScanner(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setQrScanner(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setUrlAnalysis(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setUrlAnalysis(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setHaptics(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setHaptics(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setNotificationsEnabled(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setNotifyHighSeverity(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setNotifyHighSeverity(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setNotifyMediumSeverity(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setNotifyMediumSeverity(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setNotifyDailyTips(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setNotifyDailyTips(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setShowNotificationPreviews(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setShowNotificationPreviews(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setAiModelEnabled(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setAiModelEnabled(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setOptInAiLearning(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setOptInAiLearning(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setCloudSyncEnabled(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setCloudSyncEnabled(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setBiometricLock(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setBiometricLock(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDarkTheme(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setDarkTheme(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setThreatSharingEnabled(enabled: Boolean): Result<Unit> {
        return try {
            preferencesDataStore.setThreatSharing(enabled)
            maybeSyncToCloud()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncSettingsWithCloud(userId: String): Result<Unit> {
        if (userId.isBlank()) return Result.failure(IllegalArgumentException("User ID is required"))
        return try {
            val cloudRes = firestoreDataSource.fetchUserSettings(userId)
            val cloudData = cloudRes.getOrNull()
            if (cloudData != null && cloudData.isNotEmpty()) {
                // Apply cloud settings to local storage
                cloudData["realtimeSmsProtection"]?.let { preferencesDataStore.setSmsProtection(it as Boolean) }
                cloudData["realtimeQrScanner"]?.let { preferencesDataStore.setQrScanner(it as Boolean) }
                cloudData["urlAnalysisEnabled"]?.let { preferencesDataStore.setUrlAnalysis(it as Boolean) }
                cloudData["hapticsEnabled"]?.let { preferencesDataStore.setHaptics(it as Boolean) }
                cloudData["notificationsEnabled"]?.let { preferencesDataStore.setNotificationsEnabled(it as Boolean) }
                cloudData["notifyHighSeverity"]?.let { preferencesDataStore.setNotifyHighSeverity(it as Boolean) }
                cloudData["notifyMediumSeverity"]?.let { preferencesDataStore.setNotifyMediumSeverity(it as Boolean) }
                cloudData["notifyDailyTips"]?.let { preferencesDataStore.setNotifyDailyTips(it as Boolean) }
                cloudData["showNotificationPreviews"]?.let { preferencesDataStore.setShowNotificationPreviews(it as Boolean) }
                cloudData["aiModelEnabled"]?.let { preferencesDataStore.setAiModelEnabled(it as Boolean) }
                cloudData["optInAiLearning"]?.let { preferencesDataStore.setOptInAiLearning(it as Boolean) }
                cloudData["cloudSyncEnabled"]?.let { preferencesDataStore.setCloudSyncEnabled(it as Boolean) }
                cloudData["biometricLock"]?.let { preferencesDataStore.setBiometricLock(it as Boolean) }
                cloudData["darkTheme"]?.let { preferencesDataStore.setDarkTheme(it as Boolean) }
                cloudData["threatIntelligenceSharingEnabled"]?.let { preferencesDataStore.setThreatSharing(it as Boolean) }
            } else {
                // Seed cloud with current local settings
                val current = preferencesDataStore.getSystemSettings()
                firestoreDataSource.saveUserSettings(userId, current.toMap())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SystemSettings.toMap(): Map<String, Any> {
        return mapOf(
            "realtimeSmsProtection" to realtimeSmsProtection,
            "realtimeQrScanner" to realtimeQrScanner,
            "urlAnalysisEnabled" to urlAnalysisEnabled,
            "hapticsEnabled" to hapticsEnabled,
            "notificationsEnabled" to notificationsEnabled,
            "notifyHighSeverity" to notifyHighSeverity,
            "notifyMediumSeverity" to notifyMediumSeverity,
            "notifyDailyTips" to notifyDailyTips,
            "showNotificationPreviews" to showNotificationPreviews,
            "aiModelEnabled" to aiModelEnabled,
            "optInAiLearning" to optInAiLearning,
            "cloudSyncEnabled" to cloudSyncEnabled,
            "biometricLock" to biometricLock,
            "darkTheme" to darkTheme,
            "threatIntelligenceSharingEnabled" to threatIntelligenceSharingEnabled
        )
    }
}
