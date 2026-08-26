package com.example.android.domain.repository

import com.example.android.domain.model.QrAnalysisResult
import kotlinx.coroutines.flow.Flow

interface QrAnalysisRepository {
    suspend fun saveAnalysis(result: QrAnalysisResult): Result<Unit>
    suspend fun getAnalysisById(id: String): Result<QrAnalysisResult?>
    fun observeAllAnalyses(): Flow<List<QrAnalysisResult>>
    suspend fun deleteAnalysis(id: String): Result<Unit>
}
