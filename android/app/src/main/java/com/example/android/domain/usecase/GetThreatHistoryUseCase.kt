package com.example.android.domain.usecase

import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.ThreatHistoryRepository
import kotlinx.coroutines.flow.Flow

class GetThreatHistoryUseCase(
    private val repository: ThreatHistoryRepository
) {
    operator fun invoke(): Flow<List<ThreatRecord>> {
        return repository.observeAllThreats()
    }
}
