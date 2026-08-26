package com.example.android.domain.usecase

import com.example.android.domain.model.SystemSettings
import com.example.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class GetSettingsUseCase(
    private val repository: SettingsRepository
) {
    operator fun invoke(): Flow<SystemSettings> {
        return repository.observeSettings()
    }
}
