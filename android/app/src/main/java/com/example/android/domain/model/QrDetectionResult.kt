package com.example.android.domain.model

import com.example.android.domain.riskengine.RiskAssessment

/**
 * Rich, unified domain result for QR scanning containing the complete Phase 6 Risk Assessment,
 * individual model probabilities, XAI explanations, recommended action, and extracted URL features.
 */
data class QrDetectionResult(
    val analysisResult: QrAnalysisResult,
    val assessment: RiskAssessment,
    val urlFeatures: UrlFeatures?,
    val rawPayload: String,
    val targetUrl: String,
    val source: String = "CAMERA",
    val urlModelProb: Float? = null,
    val anomalyScore: Float? = null,
    val isModelAvailable: Boolean = true
)
