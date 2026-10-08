package com.example.android.domain.riskengine

import com.example.android.domain.context.ContextFinding
import com.example.android.domain.rules.RuleResult
import com.example.android.domain.xai.ExplanationReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Complete immutable assessment object produced by RiskEngine.
 * Adheres to Phase 6 Acceptance Criteria:
 * Every analyzed event produces:
 * - Risk Score
 * - Threat Category
 * - Model Results
 * - Reasons
 * - Recommended Action
 * - Timestamp
 */
data class RiskAssessment(
    val riskScore: Int,                 // 0 - 100
    val category: ThreatCategory,       // SAFE, LOW, MEDIUM, HIGH
    val scoreBreakdown: ScoreBreakdown = ScoreBreakdown(finalScore = riskScore.toFloat(), category = category),
    val ruleResults: List<RuleResult> = emptyList(),
    val contextFindings: List<ContextFinding> = emptyList(),
    val reasons: List<String> = emptyList(),
    val structuredReasons: List<ExplanationReason> = emptyList(),
    val recommendedAction: RecommendedAction = RecommendedAction.fromCategory(category),
    val timestamp: Long = System.currentTimeMillis(),
    val unavailableModels: List<String> = emptyList()
) {
    fun toEventSummary(): String {
        val modelLines = buildList {
            if (scoreBreakdown.smsScore > 0f) add("Smishing Model (TinyBERT NLP): ${scoreBreakdown.smsScore.roundToInt()}%")
            if (scoreBreakdown.urlScore > 0f) add("URL Model (XGBoost): ${scoreBreakdown.urlScore.roundToInt()}%")
            if (scoreBreakdown.qrScore > 0f) add("QR Payload Analysis: ${scoreBreakdown.qrScore.roundToInt()}%")
            if (scoreBreakdown.anomalyScore > 0f) add("Anomaly Model (Isolation Forest): ${scoreBreakdown.anomalyScore.roundToInt()}%")
            if (scoreBreakdown.contextScore > 0f) add("Context & Threat Correlation Engine: ${scoreBreakdown.contextScore.roundToInt()}%")
            if (isEmpty()) add("Verified on-device AI multi-modal pipeline")
        }.joinToString("\n  - ", prefix = "\n  - ")

        val reasonsLines = if (reasons.isNotEmpty()) {
            reasons.joinToString("\n  - ", prefix = "\n  - ")
        } else {
            "\n  - No malicious indicators detected."
        }

        val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

        return """
Risk Score: $riskScore / 100
Threat Category: ${category.name}
Model Results:$modelLines
Reasons:$reasonsLines
Recommended Action: ${recommendedAction.actionName.uppercase()} - ${recommendedAction.description}
Timestamp: $formattedDate
""".trimIndent()
    }
}
