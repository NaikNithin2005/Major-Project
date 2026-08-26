package com.example.android.domain.model

data class QrAnalysisResult(
    val id: String,
    val timestamp: Long,
    val rawContent: String,
    val extractedUrl: String,
    val domain: String,
    val riskScore: Int,
    val isQuishing: Boolean,
    val confidence: Float,
    val processedAt: Long
)
