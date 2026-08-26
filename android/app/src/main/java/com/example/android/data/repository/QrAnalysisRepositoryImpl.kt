package com.example.android.data.repository

import com.example.android.data.local.dao.QRAnalysisDao
import com.example.android.data.local.entity.QRAnalysisEntity
import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.repository.QrAnalysisRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QrAnalysisRepositoryImpl(
    private val dao: QRAnalysisDao
) : QrAnalysisRepository {

    override suspend fun saveAnalysis(result: QrAnalysisResult): Result<Unit> {
        return try {
            dao.insertQrAnalysis(result.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAnalysisById(id: String): Result<QrAnalysisResult?> {
        return try {
            val entity = dao.getQrAnalysisById(id)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeAllAnalyses(): Flow<List<QrAnalysisResult>> {
        return dao.observeQrAnalyses().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun deleteAnalysis(id: String): Result<Unit> {
        return try {
            dao.deleteQrAnalysis(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun QrAnalysisResult.toEntity() = QRAnalysisEntity(
        id = id,
        timestamp = timestamp,
        rawContent = rawContent,
        extractedUrl = extractedUrl,
        domain = domain,
        riskScore = riskScore,
        isQuishing = isQuishing,
        confidence = confidence,
        processedAt = processedAt
    )

    private fun QRAnalysisEntity.toDomain() = QrAnalysisResult(
        id = id,
        timestamp = timestamp,
        rawContent = rawContent,
        extractedUrl = extractedUrl,
        domain = domain,
        riskScore = riskScore,
        isQuishing = isQuishing,
        confidence = confidence,
        processedAt = processedAt
    )
}
