package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_analysis")
data class QRAnalysisEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val rawContent: String,
    val extractedUrl: String,
    val domain: String,
    val riskScore: Int,
    val isQuishing: Boolean,
    val confidence: Float,
    val processedAt: Long = System.currentTimeMillis()
)
