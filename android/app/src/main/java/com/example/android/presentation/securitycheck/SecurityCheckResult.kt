package com.example.android.presentation.securitycheck

enum class UrlClassification {
    MALICIOUS_INDICATORS_DETECTED,
    SUSPICIOUS,
    SAFE_INDICATORS_FOUND,
    UNKNOWN
}

enum class ThreatCategoryType {
    PHISHING_URL,
    QUISHING_ATTACK,
    SMISHING_SOCIAL_ENGINEERING,
    MALWARE_WEB_THREAT,
    VERIFIED_SAFE,
    UNKNOWN_PROFILE
}

data class SecurityCheckResult(
    val targetUrl: String,
    val destinationDomain: String,
    val source: String, // "DEEP_LINK", "APP_LINK", "SHARESHEET", "QR_CODE", "MANUAL"
    val classification: UrlClassification,
    val threatCategory: ThreatCategoryType,
    val riskScore: Int, // 0 to 100
    val confidence: Float, // 0.0 to 1.0
    val riskIndicators: List<String>,
    val recommendedActions: List<String>,
    val modelVersion: String = "XGBoost URL Classifier v2 + Isolation Forest v2",
    val rulesVersion: String = "RuleEngine v2 (Observable Heuristics)",
    val timestamp: Long = System.currentTimeMillis(),
    val isSsrfRisk: Boolean = false,
    val ssrfReason: String? = null,
    val isOfflineMode: Boolean = true
)
