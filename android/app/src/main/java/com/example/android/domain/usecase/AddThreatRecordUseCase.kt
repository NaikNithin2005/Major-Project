package com.example.android.domain.usecase

import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.ThreatHistoryRepository

class AddThreatRecordUseCase(
    private val repository: ThreatHistoryRepository
) {
    suspend operator fun invoke(threat: ThreatRecord): Result<Unit> {
        return repository.addThreat(threat)
    }
}
