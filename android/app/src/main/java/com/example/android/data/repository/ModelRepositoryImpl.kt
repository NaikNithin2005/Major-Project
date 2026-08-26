package com.example.android.data.repository

import com.example.android.data.local.dao.ModelVersionDao
import com.example.android.data.local.entity.ModelVersionEntity
import com.example.android.domain.model.ModelMetadata
import com.example.android.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelRepositoryImpl(
    private val dao: ModelVersionDao
) : ModelRepository {

    override suspend fun registerModelVersion(model: ModelMetadata): Result<Unit> {
        return try {
            dao.insertModelVersion(model.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getActiveModelVersion(modelType: String): Result<ModelMetadata?> {
        return try {
            val entity = dao.getActiveModelVersion(modelType)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeAllModels(): Flow<List<ModelMetadata>> {
        return dao.observeAllModelVersions().map { list -> list.map { it.toDomain() } }
    }

    private fun ModelMetadata.toEntity() = ModelVersionEntity(
        modelType = modelType,
        version = version,
        sha256Checksum = sha256Checksum,
        isActive = isActive,
        installedAt = installedAt
    )

    private fun ModelVersionEntity.toDomain() = ModelMetadata(
        modelType = modelType,
        version = version,
        sha256Checksum = sha256Checksum,
        isActive = isActive,
        installedAt = installedAt
    )
}
