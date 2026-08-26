package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_versions")
data class ModelVersionEntity(
    @PrimaryKey val modelType: String, // "TINYBERT", "XGBOOST", "ISOLATION_FOREST"
    val version: String,
    val sha256Checksum: String,
    val isActive: Boolean,
    val installedAt: Long = System.currentTimeMillis()
)
