package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.ModelVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelVersionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelVersion(modelVersion: ModelVersionEntity)

    @Query("SELECT * FROM model_versions WHERE modelType = :modelType AND isActive = 1 LIMIT 1")
    suspend fun getActiveModelVersion(modelType: String): ModelVersionEntity?

    @Query("SELECT * FROM model_versions ORDER BY installedAt DESC")
    fun observeAllModelVersions(): Flow<List<ModelVersionEntity>>

    @Update
    suspend fun updateModelVersion(modelVersion: ModelVersionEntity)
}
