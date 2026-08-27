package com.example.android.domain.model

/**
 * Data transfer object encapsulating all extracted information required for URL classification.
 * Connects the Phase 4 scanner layer to the future Phase 5 XGBoost engine.
 */
data class UrlAnalysisInput(
    val rawPayload: String,
    val extractedUrl: String,
    val features: UrlFeatures,
    val source: String,
    val timestamp: Long = System.currentTimeMillis()
)
