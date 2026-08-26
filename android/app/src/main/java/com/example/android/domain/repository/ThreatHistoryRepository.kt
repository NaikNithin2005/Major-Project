package com.example.android.domain.repository

import com.example.android.domain.model.ThreatRecord
import kotlinx.coroutines.flow.Flow

interface ThreatHistoryRepository {
    suspend fun addThreat(threat: ThreatRecord): Result<Unit>
    suspend fun getThreatById(id: String): Result<ThreatRecord?>
    fun observeAllThreats(): Flow<List<ThreatRecord>>
    suspend fun getAllThreats(): Result<List<ThreatRecord>>
    suspend fun updateThreat(threat: ThreatRecord): Result<Unit>
    suspend fun deleteThreat(id: String): Result<Unit>
    suspend fun clearAllThreats(): Result<Unit>
}
