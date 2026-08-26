package com.example.android.domain.usecase

import com.example.android.domain.repository.ThreatHistoryRepository

class DeleteThreatRecordUseCase(
    private val repository: ThreatHistoryRepository
) {
    suspend operator fun invoke(id: String): Result<Unit> {
        return repository.deleteThreat(id)
    }
}
