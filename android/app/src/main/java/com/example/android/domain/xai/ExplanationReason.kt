package com.example.android.domain.xai

/**
 * Structured, human-readable reason with mapped evidence source.
 */
data class ExplanationReason(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val source: String,         // MODEL, RULE, URL_FEATURE, QR_FEATURE, SMS_FEATURE, CONTEXT, ANOMALY
    val confidence: Float,
    val debugDetail: String? = null
)
