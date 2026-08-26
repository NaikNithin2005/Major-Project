package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_analysis")
data class SMSAnalysisEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val timestamp: Long,
    val sender: String,
    val riskScore: Int,
    val isSmishing: Boolean,
    val confidence: Float,
    val extractedUrlsCount: Int,
    val processedAt: Long = System.currentTimeMillis()
)
