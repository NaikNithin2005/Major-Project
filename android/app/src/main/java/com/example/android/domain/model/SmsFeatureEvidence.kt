package com.example.android.domain.model

/**
 * Detailed feature evidence extracted during SMS preprocessing.
 * Used for future Explainable AI (XAI) and multi-factor threat correlation.
 */
data class SmsFeatureEvidence(
    val detectedKeywords: List<String> = emptyList(),
    val urgencyIndicators: List<String> = emptyList(),
    val brandEntities: List<String> = emptyList(),
    val senderPattern: String = "UNKNOWN", // e.g. ALPHANUMERIC, PHONE_NUMBER, SHORTCODE, UNKNOWN
    val extractedUrls: List<String> = emptyList(),
    val containsOtp: Boolean = false,
    val containsPhoneNumber: Boolean = false,
    val specialCharRatio: Float = 0.0f
)
