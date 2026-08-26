package com.example.android.settings

import com.example.android.domain.model.SystemSettings
import com.example.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase2SettingsTest {

    private inner class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SystemSettings())

        override fun observeSettings(): Flow<SystemSettings> = state

        override suspend fun setSmsProtection(enabled: Boolean): Result<Unit> {
            state.value = state.value.copy(realtimeSmsProtection = enabled)
            return Result.success(Unit)
        }

        override suspend fun setQrScanner(enabled: Boolean): Result<Unit> {
            state.value = state.value.copy(realtimeQrScanner = enabled)
            return Result.success(Unit)
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> {
            state.value = state.value.copy(notificationsEnabled = enabled)
            return Result.success(Unit)
        }

        override suspend fun setBiometricLock(enabled: Boolean): Result<Unit> {
            state.value = state.value.copy(biometricLock = enabled)
            return Result.success(Unit)
        }
    }

    @Test
    fun testDefaultSettingsValues() = runBlocking {
        val repo = FakeSettingsRepository()
        val defaultSettings = repo.observeSettings().first()

        assertTrue(defaultSettings.realtimeSmsProtection)
        assertTrue(defaultSettings.realtimeQrScanner)
        assertTrue(defaultSettings.notificationsEnabled)
        assertFalse(defaultSettings.biometricLock)
    }

    @Test
    fun testUpdatingSettingsPersistsState() = runBlocking {
        val repo = FakeSettingsRepository()

        repo.setSmsProtection(false)
        repo.setBiometricLock(true)

        val updatedSettings = repo.observeSettings().first()

        assertFalse(updatedSettings.realtimeSmsProtection)
        assertTrue(updatedSettings.biometricLock)
    }
}
