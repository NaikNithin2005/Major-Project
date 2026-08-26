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

    @Update
    suspend fun updateThreat(threat: ThreatHistoryEntity)

    @Query("DELETE FROM threat_history WHERE id = :id")
    suspend fun deleteThreatById(id: String)

    @Query("DELETE FROM threat_history")
    suspend fun clearAllThreats()
}
