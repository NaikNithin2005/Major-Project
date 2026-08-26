package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.QRAnalysisEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QRAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrAnalysis(analysis: QRAnalysisEntity)

    @Query("SELECT * FROM qr_analysis WHERE id = :id LIMIT 1")
    suspend fun getQrAnalysisById(id: String): QRAnalysisEntity?

    @Query("SELECT * FROM qr_analysis ORDER BY timestamp DESC")
    fun observeQrAnalyses(): Flow<List<QRAnalysisEntity>>

    @Query("DELETE FROM qr_analysis WHERE id = :id")
    suspend fun deleteQrAnalysis(id: String)

    @Query("DELETE FROM qr_analysis")
    suspend fun clearAllQrAnalyses()
}
