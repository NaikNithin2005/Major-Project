package com.example.android.domain.model

import com.example.android.domain.riskengine.RiskAssessment

/**
 * Rich domain result for SMS processing containing the complete Phase 6 Risk Assessment,
 * Smishing model probabilities, XAI explanations, recommended action, and original SMS metadata.
 */
data class SmsProcessResult(
    val analysisResult: SmsAnalysisResult,
    val assessment: RiskAssessment,
    val rawSms: RawSms,
    val processedSms: ProcessedSms,
    val smishingModelProb: Float? = null,
    val isModelAvailable: Boolean = true
)
