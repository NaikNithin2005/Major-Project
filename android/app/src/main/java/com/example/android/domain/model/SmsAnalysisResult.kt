package com.example.android.domain.model

data class SmsAnalysisResult(
    val id: String,
    val messageId: String,
    val timestamp: Long,
    val sender: String,
    val riskScore: Int,
    val isSmishing: Boolean,
    val confidence: Float,
    val extractedUrlsCount: Int,
    val processedAt: Long
)
