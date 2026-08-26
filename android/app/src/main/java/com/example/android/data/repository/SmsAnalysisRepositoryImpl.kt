package com.example.android.data.repository

import com.example.android.data.local.dao.SMSAnalysisDao
import com.example.android.data.local.entity.SMSAnalysisEntity
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.repository.SmsAnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SmsAnalysisRepositoryImpl(
    private val dao: SMSAnalysisDao
) : SmsAnalysisRepository {

    override suspend fun saveAnalysis(result: SmsAnalysisResult): Result<Unit> {
        return try {
            dao.insertSmsAnalysis(result.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAnalysisById(id: String): Result<SmsAnalysisResult?> {
        return try {
            val entity = dao.getSmsAnalysisById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeAllAnalyses(): Flow<List<SmsAnalysisResult>> {
        return dao.observeSmsAnalyses().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun deleteAnalysis(id: String): Result<Unit> {
        return try {
            dao.deleteSmsAnalysis(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun SmsAnalysisResult.toEntity() = SMSAnalysisEntity(
        id = id,
        messageId = messageId,
        timestamp = timestamp,
        sender = sender,
        riskScore = riskScore,
        isSmishing = isSmishing,
        confidence = confidence,
        extractedUrlsCount = extractedUrlsCount,
        processedAt = processedAt
    )

    private fun SMSAnalysisEntity.toDomain() = SmsAnalysisResult(
        id = id,
        messageId = messageId,
        timestamp = timestamp,
        sender = sender,
        riskScore = riskScore,
        isSmishing = isSmishing,
        confidence = confidence,
        extractedUrlsCount = extractedUrlsCount,
        processedAt = processedAt
    )
}
