package com.example.android.data.repository

import com.example.android.data.local.dao.SyncQueueDao
import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.repository.SyncRepository
import com.example.android.domain.repository.SyncSummary
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.math.min

class SyncRepositoryImpl(
    private val syncQueueDao: SyncQueueDao,
    private val backendApiService: BackendApiService,
    private val authRepository: AuthRepository,
    private val gson: Gson = Gson()
) : SyncRepository {

    override suspend fun enqueueDetection(request: DetectionSyncRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if already enqueued with same clientEventId
            val existing = syncQueueDao.getByClientEventId(request.clientEventId)
            if (existing != null) {
                return@withContext Result.success(existing.id)
            }

            // Serialize data-minimized payload (NO raw SMS)
            val payloadJson = gson.toJson(request)
            val currentUserId = authRepository.getCurrentUser()?.uid
            val entity = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                clientEventId = request.clientEventId,
                eventType = request.eventType,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
                status = SyncQueueEntity.STATUS_PENDING,
                userId = currentUserId
            )

            syncQueueDao.insert(entity)
            Result.success(entity.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun processSyncQueue(): SyncSummary = withContext(Dispatchers.IO) {
        val token = authRepository.getIdToken()
        if (token.isNullOrBlank()) {
            // No auth token available; telemetry remains in offline queue
            return@withContext SyncSummary(0, 0, 0, 0)
        }

        try {
            syncQueueDao.resetFailedItems()
        } catch (ignored: Exception) {}

        val now = System.currentTimeMillis()
        val currentUserId = authRepository.getCurrentUser()?.uid
        val pendingItems = syncQueueDao.getPendingItems(now = now, limit = 25, userId = currentUserId)
        if (pendingItems.isEmpty()) {
            return@withContext SyncSummary(0, 0, 0, 0)
        }

        var synced = 0
        var failed = 0
        var retried = 0

        for (item in pendingItems) {
            syncQueueDao.markSyncing(item.id, now)

            try {
                val request = gson.fromJson(item.payloadJson, DetectionSyncRequest::class.java)
                val response = backendApiService.syncDetection(request)

                if (response.isSuccessful && response.body() != null) {
                    val serverEvent = response.body()!!
                    syncQueueDao.markSynced(
                        id = item.id,
                        serverEventId = serverEvent.id,
                        syncedAt = System.currentTimeMillis()
                    )
                    synced++
                } else {
                    val code = response.code()
                    val errorBody = response.errorBody()?.string() ?: "HTTP error $code"

                    if (code == 400 || code == 422) {
                        // Permanent non-retryable validation error
                        syncQueueDao.markFailed(item.id, "Non-retryable: $errorBody", System.currentTimeMillis())
                        failed++
                    } else if (code == 401) {
                        // Auth token expired - schedule retry with backoff and attempt refresh
                        val attempts = item.attemptCount + 1
                        val backoffMs = calculateBackoff(attempts)
                        syncQueueDao.markRetry(item.id, attempts, System.currentTimeMillis() + backoffMs, "Unauthorized (401)", System.currentTimeMillis())
                        authRepository.getIdToken(forceRefresh = true)
                        retried++
                    } else {
                        // 5xx / 429 / Server temporary failure - retry with exponential backoff
                        val attempts = item.attemptCount + 1
                        val backoffMs = calculateBackoff(attempts)
                        syncQueueDao.markRetry(item.id, attempts, System.currentTimeMillis() + backoffMs, "HTTP $code: $errorBody", System.currentTimeMillis())
                        retried++
                    }
                }
            } catch (e: IOException) {
                // Network unavailable, timeout, DNS failure - retry with exponential backoff
                val attempts = item.attemptCount + 1
                val backoffMs = calculateBackoff(attempts)
                syncQueueDao.markRetry(
                    id = item.id,
                    attemptCount = attempts,
                    nextAttemptAt = System.currentTimeMillis() + backoffMs,
                    error = e.message ?: "Network unreachable",
                    timestamp = System.currentTimeMillis()
                )
                retried++
            } catch (e: Exception) {
                // Corrupt payload or serialization failure - mark failed
                syncQueueDao.markFailed(item.id, "Payload error: ${e.message}", System.currentTimeMillis())
                failed++
            }
        }

        SyncSummary(
            totalProcessed = pendingItems.size,
            syncedCount = synced,
            failedCount = failed,
            retryCount = retried
        )
    }

    override fun observePendingCount(): Flow<Int> {
        return syncQueueDao.observePendingCount()
    }

    override fun observeSyncQueue(): Flow<List<SyncQueueEntity>> {
        return syncQueueDao.observeAll()
    }

    override suspend fun fetchAndApplyServerProfile(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = backendApiService.getCurrentUserProfile()
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                authRepository.updateUserRole(profile.role)

                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    Result.success(currentUser.copy(role = profile.role))
                } else {
                    Result.success(
                        User(
                            uid = profile.firebaseUid,
                            email = profile.email,
                            displayName = profile.displayName ?: "User",
                            isGuest = false,
                            role = profile.role
                        )
                    )
                }
            } else {
                Result.failure(IOException("Server returned HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        // Exponential backoff: 2s, 4s, 8s, 16s, 32s... capped at 5 minutes
        val exponent = attempt.coerceIn(1, 8)
        val delay = 2000L * (1 shl exponent)
        return min(300_000L, delay)
    }
}
