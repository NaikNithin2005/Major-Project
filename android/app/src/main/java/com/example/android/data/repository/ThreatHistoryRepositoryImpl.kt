package com.example.android.data.repository

import com.example.android.data.local.dao.ThreatHistoryDao
import com.example.android.data.local.entity.ThreatHistoryEntity
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.ThreatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThreatHistoryRepositoryImpl(
    private val dao: ThreatHistoryDao
) : ThreatHistoryRepository {

    override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
        return try {
            dao.insertThreat(threat.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThreatById(id: String): Result<ThreatRecord?> {
        return try {
            val entity = dao.getThreatById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeAllThreats(): Flow<List<ThreatRecord>> {
        return dao.observeAllThreats().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllThreats(): Result<List<ThreatRecord>> {
        return try {
            val list = dao.getAllThreats().map { it.toDomain() }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateThreat(threat: ThreatRecord): Result<Unit> {
        return try {
            dao.updateThreat(threat.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteThreat(id: String): Result<Unit> {
        return try {
            dao.deleteThreatById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearAllThreats(): Result<Unit> {
        return try {
            dao.clearAllThreats()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ThreatRecord.toEntity() = ThreatHistoryEntity(
        id = id,
        timestamp = timestamp,
        source = source,
        sender = sender,
        riskScore = riskScore,
        category = category,
        actionTaken = actionTaken,
        details = details
    )

    private fun ThreatHistoryEntity.toDomain() = ThreatRecord(
        id = id,
        timestamp = timestamp,
        source = source,
        sender = sender,
        riskScore = riskScore,
        category = category,
        actionTaken = actionTaken,
        details = details
    )
}
