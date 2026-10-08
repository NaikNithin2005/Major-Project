package com.example.android.domain.report

import com.example.android.domain.context.ContextFinding
import com.example.android.domain.riskengine.RecommendedAction
import com.example.android.domain.riskengine.RiskAssessment
import com.example.android.domain.riskengine.ScoreBreakdown
import com.example.android.domain.riskengine.ThreatCategory
import com.example.android.domain.rules.RuleResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Structured Threat Report data structure conforming to Phase 6 specifications.
 * Enforces privacy: raw SMS content is sanitized and never stored.
 * Every analyzed event produces:
 * - Risk Score
 * - Threat Category
 * - Model Results
 * - Reasons
 * - Recommended Action
 * - Timestamp
 */
data class ThreatReport(
    val reportId: String = "TR-" + UUID.randomUUID().toString().take(12).uppercase(),
    val timestamp: Long = System.currentTimeMillis(),
    val detectionType: String, // SMS, QR, URL, COMBINED
    val riskScore: Int,
    val category: ThreatCategory,
    val scoreBreakdown: ScoreBreakdown,
    val ruleResults: List<RuleResult>,
    val contextFindings: List<ContextFinding>,
    val reasons: List<String>,
    val recommendedAction: RecommendedAction,
    val destinationUrl: String? = null,
    val privacyNotice: String = "Raw SMS content is sanitized and kept strictly local-first."
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
            "\n  - No anomalous indicators detected."
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

    companion object {
        fun fromAssessment(
            assessment: RiskAssessment,
            detectionType: String,
            destinationUrl: String? = null
        ): ThreatReport {
            return ThreatReport(
                timestamp = assessment.timestamp,
                detectionType = detectionType,
                riskScore = assessment.riskScore,
                category = assessment.category,
                scoreBreakdown = assessment.scoreBreakdown,
                ruleResults = assessment.ruleResults,
                contextFindings = assessment.contextFindings,
                reasons = assessment.reasons,
                recommendedAction = assessment.recommendedAction,
                destinationUrl = destinationUrl
            )
        }
    }
}
