package com.example.android.domain.model

/**
 * Domain model representing structural URL features extracted during preliminary analysis.
 * Used as input for future Phase 5 XGBoost classification.
 */
data class UrlFeatures(
    val url: String,
    val scheme: String,
    val domain: String,
    val hostname: String,
    val path: String,
    val query: String?,
    val isHttps: Boolean,
    val urlLength: Int,
    val hostnameLength: Int,
    val pathLength: Int,
    val hasQuery: Boolean,
    val isIpHostname: Boolean,
    val subdomainCount: Int,
    val hasSuspiciousSymbols: Boolean,
    val hasUnusualPort: Boolean,
    val isPunycode: Boolean,
    val suspiciousCharacteristics: List<String>
)
