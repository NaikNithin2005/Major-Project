package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncQueueEntity): Long

    @Update
    suspend fun update(entity: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE clientEventId = :clientEventId LIMIT 1")
    suspend fun getByClientEventId(clientEventId: String): SyncQueueEntity?

    @Query("""
        SELECT * FROM sync_queue 
        WHERE status = 'PENDING' AND nextAttemptAt <= :now 
        AND (:userId IS NULL OR userId IS NULL OR userId = :userId)
        ORDER BY createdAt ASC 
        LIMIT :limit
    """)
    suspend fun getPendingItems(now: Long, limit: Int = 25, userId: String? = null): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'SYNCING', lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun markSyncing(id: String, timestamp: Long)

    @Query("""
        UPDATE sync_queue 
        SET status = 'SYNCED', serverEventId = :serverEventId, syncedAt = :syncedAt, lastError = NULL 
        WHERE id = :id
    """)
    suspend fun markSynced(id: String, serverEventId: String, syncedAt: Long)

    @Query("""
        UPDATE sync_queue 
        SET status = 'PENDING', attemptCount = :attemptCount, nextAttemptAt = :nextAttemptAt, lastError = :error, lastAttemptAt = :timestamp 
        WHERE id = :id
    """)
    suspend fun markRetry(id: String, attemptCount: Int, nextAttemptAt: Long, error: String, timestamp: Long)

    @Query("""
        UPDATE sync_queue 
        SET status = 'FAILED', lastError = :error, lastAttemptAt = :timestamp 
        WHERE id = :id
    """)
    suspend fun markFailed(id: String, error: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    suspend fun getPendingCount(): Int

    @Query("UPDATE sync_queue SET status = 'PENDING', nextAttemptAt = 0 WHERE status = 'FAILED'")
    suspend fun resetFailedItems(): Int

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND syncedAt < :cutoffTimestamp")
    suspend fun purgeOldSynced(cutoffTimestamp: Long): Int
}
