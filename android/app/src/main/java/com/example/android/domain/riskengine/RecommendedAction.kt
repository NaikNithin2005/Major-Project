package com.example.android.domain.riskengine

/**
 * Standardized recommended actions based on threat assessment.
 * Follows Rules.md: never performs automated destructive actions.
 */
enum class RecommendedAction(val actionName: String, val userGuidance: String) {
    ALLOW(
        actionName = "Allow",
        userGuidance = "No threat indicators detected. Safe to proceed."
    ),
    ALLOW_WITH_CAUTION(
        actionName = "Allow with caution",
        userGuidance = "Minor anomalies observed. Exercise caution and verify sender legitimacy."
    ),
    WARN_USER(
        actionName = "Warn user and require explicit confirmation",
        userGuidance = "Suspicious signals detected. Do not enter credentials or authorize payments."
    ),
    BLOCK_OR_STRONGLY_WARN(
        actionName = "Block or strongly warn",
        userGuidance = "High-confidence threat detected. Interaction blocked; do not visit or trust this content."
    );

    companion object {
        fun fromCategory(category: ThreatCategory): RecommendedAction {
            return when (category) {
                ThreatCategory.SAFE -> ALLOW
                ThreatCategory.LOW -> ALLOW_WITH_CAUTION
                ThreatCategory.MEDIUM -> WARN_USER
                ThreatCategory.HIGH -> BLOCK_OR_STRONGLY_WARN
            }
        }
    }

    val description: String get() = userGuidance
}
