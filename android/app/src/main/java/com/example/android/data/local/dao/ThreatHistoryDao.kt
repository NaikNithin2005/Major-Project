package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.ThreatHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreatHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreat(threat: ThreatHistoryEntity)

    @Query("SELECT * FROM threat_history WHERE id = :id LIMIT 1")
    suspend fun getThreatById(id: String): ThreatHistoryEntity?

    @Query("SELECT * FROM threat_history ORDER BY timestamp DESC")
    fun observeAllThreats(): Flow<List<ThreatHistoryEntity>>

    @Query("SELECT * FROM threat_history ORDER BY timestamp DESC")
    suspend fun getAllThreats(): List<ThreatHistoryEntity>

    @Query("SELECT * FROM threat_history WHERE isQuarantined = 1 ORDER BY timestamp DESC")
    fun observeQuarantinedThreats(): Flow<List<ThreatHistoryEntity>>

    @Update
    suspend fun updateThreat(threat: ThreatHistoryEntity)

    @Query("UPDATE threat_history SET isQuarantined = :isQuarantined WHERE id = :id")
    suspend fun updateQuarantineStatus(id: String, isQuarantined: Boolean)

    @Query("UPDATE threat_history SET reviewStatus = :reviewStatus WHERE id = :id")
    suspend fun updateReviewStatus(id: String, reviewStatus: String)

    @Query("UPDATE threat_history SET actionTaken = :action WHERE id = :id")
    suspend fun updateThreatAction(id: String, action: String)

    @Query("DELETE FROM threat_history WHERE id = :id")
    suspend fun deleteThreatById(id: String)

    @Query("DELETE FROM threat_history")
    suspend fun clearAllThreats()
}
