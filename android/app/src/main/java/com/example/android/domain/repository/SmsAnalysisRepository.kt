package com.example.android.domain.repository

import com.example.android.domain.model.SmsAnalysisResult
import kotlinx.coroutines.flow.Flow

interface SmsAnalysisRepository {
    suspend fun saveAnalysis(result: SmsAnalysisResult): Result<Unit>
    suspend fun getAnalysisById(id: String): Result<SmsAnalysisResult?>
    fun observeAllAnalyses(): Flow<List<SmsAnalysisResult>>
    suspend fun deleteAnalysis(id: String): Result<Unit>
}
