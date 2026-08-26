package com.example.android.domain.usecase

import com.example.android.domain.repository.SettingsRepository

class UpdateSettingUseCase(
    private val repository: SettingsRepository
) {
    suspend fun setSmsProtection(enabled: Boolean): Result<Unit> {
        return repository.setSmsProtection(enabled)
    }

    suspend fun setQrScanner(enabled: Boolean): Result<Unit> {
        return repository.setQrScanner(enabled)
    }

    suspend fun setNotificationsEnabled(enabled: Boolean): Result<Unit> {
        return repository.setNotificationsEnabled(enabled)
    }

    suspend fun setBiometricLock(enabled: Boolean): Result<Unit> {
        return repository.setBiometricLock(enabled)
    }
}
