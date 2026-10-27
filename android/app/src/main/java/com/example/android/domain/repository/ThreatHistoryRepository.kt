package com.example.android.domain.repository

import com.example.android.domain.model.ThreatRecord
import kotlinx.coroutines.flow.Flow

interface ThreatHistoryRepository {
    suspend fun addThreat(threat: ThreatRecord): Result<Unit>
    suspend fun getThreatById(id: String): Result<ThreatRecord?>
    fun observeAllThreats(): Flow<List<ThreatRecord>>
    fun observeQuarantinedThreats(): Flow<List<ThreatRecord>>
    suspend fun getAllThreats(): Result<List<ThreatRecord>>
    suspend fun updateThreat(threat: ThreatRecord): Result<Unit>
    suspend fun updateQuarantineStatus(id: String, isQuarantined: Boolean): Result<Unit>
    suspend fun updateReviewStatus(id: String, reviewStatus: String): Result<Unit>
    suspend fun updateThreatAction(id: String, action: String): Result<Unit>
    suspend fun deleteThreat(id: String): Result<Unit>
    suspend fun clearAllThreats(): Result<Unit>
}
