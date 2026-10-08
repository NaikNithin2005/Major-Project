package com.example.android.domain.riskengine

/**
 * Detailed component-wise scoring breakdown supporting full scoring transparency.
 */
data class ScoreBreakdown(
    val smsScore: Float = 0f,
    val urlScore: Float = 0f,
    val qrScore: Float = 0f,
    val contextScore: Float = 0f,
    val anomalyScore: Float = 0f,
    val smsContribution: Float = 0f,
    val urlContribution: Float = 0f,
    val qrContribution: Float = 0f,
    val contextContribution: Float = 0f,
    val anomalyContribution: Float = 0f,
    val finalScore: Float = 0f,
    val category: ThreatCategory = ThreatCategory.SAFE
)
