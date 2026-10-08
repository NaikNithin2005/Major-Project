package com.example.android.domain.model

import com.example.android.domain.riskengine.RiskAssessment

/**
 * Domain model representing the outcome of incoming SMS smishing evaluation.
 * Enriched with Phase 6 RiskAssessment, TinyBERT model probability, and XAI evidence.
 */
data class SmsAnalysisResult(
    val id: String,
    val messageId: String,
    val timestamp: Long,
    val sender: String,
    val riskScore: Int,
    val isSmishing: Boolean,
    val confidence: Float,
    val extractedUrlsCount: Int,
    val processedAt: Long,
    val assessment: RiskAssessment? = null,
    val rawBody: String = "",
    val isModelAvailable: Boolean = true,
    val smishingModelProb: Float? = null
)
