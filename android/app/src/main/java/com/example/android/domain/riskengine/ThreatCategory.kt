package com.example.android.domain.riskengine

/**
 * Authoritative PRD threat categories based on normalized 0-100 risk score.
 */
enum class ThreatCategory(val displayName: String, val minScore: Int, val maxScore: Int) {
    SAFE("SAFE", 0, 25),
    LOW("LOW", 26, 50),
    MEDIUM("MEDIUM", 51, 75),
    HIGH("HIGH", 76, 100);

    companion object {
        fun fromScore(score: Int): ThreatCategory {
            val clamped = score.coerceIn(0, 100)
            return when {
                clamped <= 25 -> SAFE
                clamped <= 50 -> LOW
                clamped <= 75 -> MEDIUM
                else -> HIGH
            }
        }
    }
}
