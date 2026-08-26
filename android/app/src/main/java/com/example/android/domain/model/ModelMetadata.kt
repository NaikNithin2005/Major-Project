package com.example.android.domain.model

data class ModelMetadata(
    val modelType: String,
    val version: String,
    val sha256Checksum: String,
    val isActive: Boolean,
    val installedAt: Long
)
