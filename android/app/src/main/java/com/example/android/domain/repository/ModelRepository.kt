package com.example.android.domain.repository

import com.example.android.domain.model.ModelMetadata
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    suspend fun registerModelVersion(model: ModelMetadata): Result<Unit>
    suspend fun getActiveModelVersion(modelType: String): Result<ModelMetadata?>
    fun observeAllModels(): Flow<List<ModelMetadata>>
}
