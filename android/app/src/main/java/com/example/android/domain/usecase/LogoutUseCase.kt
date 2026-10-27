package com.example.android.domain.usecase

import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val threatHistoryRepository: ThreatHistoryRepository? = null
) {
    suspend operator fun invoke(): Result<Unit> {
        val result = repository.logout()
        try {
            threatHistoryRepository?.clearAllThreats()
        } catch (ignored: Exception) {
            // Best effort cache clearance
        }
        return result
    }
}
