package com.example.android.data.repository

import com.example.android.data.local.dao.ThreatHistoryDao
import com.example.android.data.local.entity.ThreatHistoryEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.QuarantineActionDto
import com.example.android.data.remote.dto.SmsBlockActionDto
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ThreatHistoryRepositoryImpl(
    private val dao: ThreatHistoryDao,
    private val backendApiService: BackendApiService? = null,
    private val firestoreDataSource: FirestoreDataSource? = null,
    private val authRepository: AuthRepository? = null
) : ThreatHistoryRepository {

    override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
        return try {
            dao.insertThreat(threat.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addThreats(threats: List<ThreatRecord>): Result<Unit> {
        return try {
            dao.insertThreats(threats.map { it.toEntity() })
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

    override fun observeThreatsByUserId(userId: String?): Flow<List<ThreatRecord>> {
        return dao.observeThreatsByUserId(userId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeQuarantinedThreats(): Flow<List<ThreatRecord>> {
        return dao.observeQuarantinedThreats().map { list ->
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

    override suspend fun updateQuarantineStatus(id: String, isQuarantined: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.updateQuarantineStatus(id, isQuarantined)
            dao.updateThreatAction(id, if (isQuarantined) "QUARANTINED" else "ALLOWED")

            val currentUid = authRepository?.getCurrentUser()?.uid
            if (firestoreDataSource != null && currentUid != null && currentUid.isNotBlank()) {
                firestoreDataSource.updateQuarantineStatus(currentUid, id, isQuarantined)
            }
            if (backendApiService != null) {
                try {
                    backendApiService.quarantineAction(
                        id,
                        QuarantineActionDto(
                            action = if (isQuarantined) "QUARANTINE" else "RELEASE",
                            reason = if (isQuarantined) "Quarantined via Android" else "Released via Android"
                        )
                    )
                } catch (ignored: Exception) {}
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateReviewStatus(id: String, reviewStatus: String): Result<Unit> {
        return try {
            dao.updateReviewStatus(id, reviewStatus)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateThreatAction(id: String, action: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.updateThreatAction(id, action)
            if (action == "RESOLVED" || action == "MARK_SAFE") {
                dao.updateReviewStatus(id, "RESOLVED")
            } else if (action == "BLOCK") {
                dao.updateReviewStatus(id, "BLOCKED")
            }

            val currentUid = authRepository?.getCurrentUser()?.uid
            if (firestoreDataSource != null && currentUid != null && currentUid.isNotBlank()) {
                firestoreDataSource.updateThreatAction(currentUid, id, action)
            }
            if (backendApiService != null) {
                try {
                    if (action == "BLOCK") {
                        backendApiService.blockedSmsAction(id, SmsBlockActionDto(action = "BLOCK", reason = "Blocked via Android"))
                    } else if (action == "RESOLVED" || action == "MARK_SAFE") {
                        backendApiService.quarantineAction(id, QuarantineActionDto(action = "RELEASE", reason = "Resolved via Android"))
                    }
                } catch (ignored: Exception) {}
            }
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

    override suspend fun deleteThreatsByUserId(userId: String): Result<Unit> {
        return try {
            dao.deleteThreatsByUserId(userId)
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
        details = details,
        rawContent = rawContent,
        extractedUrl = extractedUrl,
        payloadType = source,
        severity = if (riskScore >= 70) "HIGH" else if (riskScore >= 40) "MEDIUM" else "LOW",
        modelVersion = "tinybert-smishing-v2",
        isQuarantined = isQuarantined,
        reviewStatus = if (actionTaken == "RESOLVED") "RESOLVED" else "REVIEWED",
        contentRetentionMode = "LOCAL_SAVED",
        pendingOperation = ThreatHistoryEntity.OP_NONE,
        syncStatus = ThreatHistoryEntity.STATUS_PENDING,
        retryCount = 0,
        userId = userId
    )

    private fun ThreatHistoryEntity.toDomain() = ThreatRecord(
        id = id,
        timestamp = timestamp,
        source = source,
        sender = sender,
        riskScore = riskScore,
        category = category,
        actionTaken = actionTaken,
        details = details,
        rawContent = rawContent,
        extractedUrl = extractedUrl,
        userId = userId,
        isQuarantined = isQuarantined
    )
}
