package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.SMSAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SMSAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsAnalysis(analysis: SMSAnalysisEntity)

    @Query("SELECT * FROM sms_analysis WHERE id = :id LIMIT 1")
    suspend fun getSmsAnalysisById(id: String): SMSAnalysisEntity?

    @Query("SELECT * FROM sms_analysis ORDER BY timestamp DESC")
    fun observeSmsAnalyses(): Flow<List<SMSAnalysisEntity>>

    @Query("DELETE FROM sms_analysis WHERE id = :id")
    suspend fun deleteSmsAnalysis(id: String)

    @Query("DELETE FROM sms_analysis")
    suspend fun clearAllSmsAnalyses()
}
