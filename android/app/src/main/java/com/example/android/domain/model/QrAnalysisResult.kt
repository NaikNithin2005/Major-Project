package com.example.android.domain.model

import com.example.android.domain.riskengine.RiskAssessment

/**
 * Domain model representing the outcome of QR code analysis.
 * Enriched with Phase 6 RiskAssessment, XAI evidence, model outputs, and URL features.
 */
data class QrAnalysisResult(
    val id: String,
    val timestamp: Long,
    val rawContent: String,
    val extractedUrl: String,
    val domain: String,
    val riskScore: Int,
    val isQuishing: Boolean,
    val confidence: Float,
    val processedAt: Long,
    val assessment: RiskAssessment? = null,
    val urlFeatures: UrlFeatures? = null,
    val isModelAvailable: Boolean = true,
    val urlModelProb: Float? = null,
    val anomalyScore: Float? = null,
    val targetUrl: String = extractedUrl,
    val source: String = "CAMERA"
) {
    val rawPayload: String get() = rawContent
}
