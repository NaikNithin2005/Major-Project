package com.example.android.domain.riskengine

import com.example.android.domain.context.ContextCorrelationEngine
import com.example.android.domain.model.ProcessedSms
import com.example.android.domain.model.UrlFeatures
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.xai.ExplainableAiEngine
import kotlin.math.roundToInt

/**
 * Dedicated Risk Engine combining multi-modal AI model outputs, deterministic rules,
 * cross-channel context correlation, and explainable AI into an authoritative assessment.
 * Adheres to PRD Section 4.16 and Rules.md.
 */
class RiskEngine(
    private val ruleEngine: RuleEngine = RuleEngine(),
    private val contextEngine: ContextCorrelationEngine = ContextCorrelationEngine(),
    private val xaiEngine: ExplainableAiEngine = ExplainableAiEngine()
) {

    companion object {
        const val WEIGHT_SMS = 0.35f
        const val WEIGHT_URL = 0.30f
        const val WEIGHT_QR = 0.15f
        const val WEIGHT_CONTEXT = 0.10f
        const val WEIGHT_ANOMALY = 0.10f
    }

    /**
     * Assesses risk across all available inputs.
     * All component scores must be normalized to 0 - 100 before aggregation.
     */
    fun assess(
        smsScore: Float? = null,
        urlScore: Float? = null,
        qrScore: Float? = null,
        contextScore: Float? = null,
        anomalyScore: Float? = null,
        processedSms: ProcessedSms? = null,
        urlFeatures: UrlFeatures? = null,
        qrPayload: String? = null,
        unavailableModels: List<String> = emptyList()
    ): RiskAssessment {
        val timestamp = System.currentTimeMillis()

        // 1. Evaluate Rule Engine
        val ruleResults = ruleEngine.evaluate(
            urlFeatures = urlFeatures,
            processedSms = processedSms,
            qrPayload = qrPayload
        )

        // 2. Evaluate Context Correlation Engine
        val (computedContextScore, contextFindings) = contextEngine.correlate(
            processedSms = processedSms,
            urlFeatures = urlFeatures,
            qrPayload = qrPayload
        )
        val finalContextScore = contextScore ?: computedContextScore

        // 3. Compute QR Feature Score if not explicitly provided
        val finalQrScore = qrScore ?: computeQrFeatureScore(qrPayload, urlFeatures)

        // 4. Weighted Aggregation with Dynamic Reweighting for Missing/Unavailable Components
        val activeWeights = mutableMapOf<String, Pair<Float, Float>>()

        if (smsScore != null) activeWeights["sms"] = Pair(WEIGHT_SMS, smsScore.coerceIn(0f, 100f))
        if (urlScore != null) activeWeights["url"] = Pair(WEIGHT_URL, urlScore.coerceIn(0f, 100f))
        activeWeights["qr"] = Pair(WEIGHT_QR, finalQrScore.coerceIn(0f, 100f))
        activeWeights["context"] = Pair(WEIGHT_CONTEXT, finalContextScore.coerceIn(0f, 100f))
        if (anomalyScore != null) activeWeights["anomaly"] = Pair(WEIGHT_ANOMALY, anomalyScore.coerceIn(0f, 100f))

        val totalActiveWeight = activeWeights.values.sumOf { it.first.toDouble() }.toFloat()
        val scale = if (totalActiveWeight > 0f) 1.0f / totalActiveWeight else 1.0f

        val smsEff = activeWeights["sms"]?.second ?: 0f
        val urlEff = activeWeights["url"]?.second ?: 0f
        val qrEff = activeWeights["qr"]?.second ?: 0f
        val ctxEff = activeWeights["context"]?.second ?: 0f
        val anomEff = activeWeights["anomaly"]?.second ?: 0f

        val smsContrib = if (activeWeights.containsKey("sms")) (WEIGHT_SMS * scale) * smsEff else 0f
        val urlContrib = if (activeWeights.containsKey("url")) (WEIGHT_URL * scale) * urlEff else 0f
        val qrContrib = (WEIGHT_QR * scale) * qrEff
        val ctxContrib = (WEIGHT_CONTEXT * scale) * ctxEff
        val anomContrib = if (activeWeights.containsKey("anomaly")) (WEIGHT_ANOMALY * scale) * anomEff else 0f

        val rawFinalScore = (smsContrib + urlContrib + qrContrib + ctxContrib + anomContrib).coerceIn(0f, 100f)
        val intFinalScore = rawFinalScore.roundToInt()

        // 5. Categorize and Recommended Action
        val category = ThreatCategory.fromScore(intFinalScore)
        val recommendedAction = RecommendedAction.fromCategory(category)

        val breakdown = ScoreBreakdown(
            smsScore = smsEff,
            urlScore = urlEff,
            qrScore = qrEff,
            contextScore = ctxEff,
            anomalyScore = anomEff,
            smsContribution = smsContrib,
            urlContribution = urlContrib,
            qrContribution = qrContrib,
            contextContribution = ctxContrib,
            anomalyContribution = anomContrib,
            finalScore = rawFinalScore,
            category = category
        )

        // 6. Generate Explainable AI (XAI) Reasons
        val structuredReasons = xaiEngine.generateExplanations(
            smsProbability = smsScore?.let { it / 100f },
            urlProbability = urlScore?.let { it / 100f },
            anomalyScore = anomalyScore?.let { it / 100f },
            ruleResults = ruleResults,
            contextFindings = contextFindings,
            maxReasons = 5
        )

        val reasonsText = structuredReasons.map { it.title }.ifEmpty {
            if (category == ThreatCategory.SAFE) {
                listOf("All security checks passed; no anomalous indicators detected.")
            } else {
                listOf("Suspicious activity detected across analyzed features.")
            }
        }

        return RiskAssessment(
            riskScore = intFinalScore,
            category = category,
            scoreBreakdown = breakdown,
            ruleResults = ruleResults,
            contextFindings = contextFindings,
            reasons = reasonsText,
            structuredReasons = structuredReasons,
            recommendedAction = recommendedAction,
            timestamp = timestamp,
            unavailableModels = unavailableModels
        )
    }

    private fun computeQrFeatureScore(qrPayload: String?, urlFeatures: UrlFeatures?): Float {
        if (qrPayload.isNullOrBlank()) return 0f
        val trimmed = qrPayload.trim().lowercase()

        // Dangerous execution schemes
        if (listOf("javascript:", "data:", "intent:", "smsto:").any { trimmed.startsWith(it) }) {
            return 80f
        }

        // Plain text non-URL payload
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.contains("://") && !trimmed.contains(".")) {
            return 0f
        }

        var score = 0f
        if (urlFeatures != null) {
            if (!urlFeatures.isHttps) score += 20f
            if (urlFeatures.isIpHostname) score += 30f
            if (urlFeatures.isPunycode) score += 20f
            if (urlFeatures.hasUnusualPort) score += 15f
            if (urlFeatures.subdomainCount > 2) score += 15f
            if (urlFeatures.hasSuspiciousSymbols) score += 15f
        }

        return score.coerceIn(0f, 100f)
    }
}
