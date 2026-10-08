package com.example.android.domain.repository

import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.domain.model.User
import kotlinx.coroutines.flow.Flow

data class SyncSummary(
    val totalProcessed: Int,
    val syncedCount: Int,
    val failedCount: Int,
    val retryCount: Int
)

interface SyncRepository {
    suspend fun enqueueDetection(request: DetectionSyncRequest): Result<String>
    suspend fun processSyncQueue(): SyncSummary
    fun observePendingCount(): Flow<Int>
    fun observeSyncQueue(): Flow<List<SyncQueueEntity>>
    suspend fun fetchAndApplyServerProfile(): Result<User>
}
