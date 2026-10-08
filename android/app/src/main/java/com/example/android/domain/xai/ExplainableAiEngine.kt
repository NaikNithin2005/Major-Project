package com.example.android.domain.xai

import com.example.android.domain.context.ContextFinding
import com.example.android.domain.rules.RuleResult

/**
 * Explainable AI (XAI) Engine for Android.
 * Converts structured model outputs, rule evaluations, and context findings into
 * human-readable, prioritized evidence explanations.
 */
class ExplainableAiEngine {

    companion object {
        private val SEVERITY_ORDER = mapOf(
            "CRITICAL" to 5,
            "HIGH" to 4,
            "MEDIUM" to 3,
            "LOW" to 2,
            "INFO" to 1
        )
    }

    fun generateExplanations(
        smsProbability: Float? = null,
        urlProbability: Float? = null,
        anomalyScore: Float? = null,
        ruleResults: List<RuleResult> = emptyList(),
        contextFindings: List<ContextFinding> = emptyList(),
        maxReasons: Int = 5
    ): List<ExplanationReason> {
        val reasons = mutableListOf<ExplanationReason>()

        // 1. Model Evidence
        if (urlProbability != null && urlProbability >= 0.80f) {
            reasons.add(
                ExplanationReason(
                    id = "EXP_MODEL_URL_HIGH",
                    title = "High Malicious Probability by URL Classifier",
                    description = "Machine learning URL model identified strong phishing patterns (${(urlProbability * 100).toInt()}% confidence).",
                    severity = "CRITICAL",
                    source = "MODEL",
                    confidence = urlProbability,
                    debugDetail = "URL ML model probability: $urlProbability"
                )
            )
        } else if (urlProbability != null && urlProbability >= 0.50f) {
            reasons.add(
                ExplanationReason(
                    id = "EXP_MODEL_URL_SUSPICIOUS",
                    title = "Suspicious URL Patterns Detected",
                    description = "URL classifier detected suspicious structural patterns (${(urlProbability * 100).toInt()}% risk).",
                    severity = "MEDIUM",
                    source = "MODEL",
                    confidence = urlProbability,
                    debugDetail = "URL ML model probability: $urlProbability"
                )
            )
        }

        if (smsProbability != null && smsProbability >= 0.80f) {
            reasons.add(
                ExplanationReason(
                    id = "EXP_MODEL_SMS_HIGH",
                    title = "Smishing Language Patterns Identified",
                    description = "Natural language model detected deceitful social engineering content (${(smsProbability * 100).toInt()}% smishing risk).",
                    severity = "CRITICAL",
                    source = "MODEL",
                    confidence = smsProbability,
                    debugDetail = "SMS model probability: $smsProbability"
                )
            )
        } else if (smsProbability != null && smsProbability >= 0.50f) {
            reasons.add(
                ExplanationReason(
                    id = "EXP_MODEL_SMS_SUSPICIOUS",
                    title = "Potentially Deceptive Message Text",
                    description = "Message formulation exhibits characteristics common in unsolicited smishing (${(smsProbability * 100).toInt()}% risk).",
                    severity = "MEDIUM",
                    source = "MODEL",
                    confidence = smsProbability,
                    debugDetail = "SMS model probability: $smsProbability"
                )
            )
        }

        if (anomalyScore != null && anomalyScore >= 0.75f) {
            reasons.add(
                ExplanationReason(
                    id = "EXP_MODEL_ANOMALY",
                    title = "Zero-Day Structural Anomaly Detected",
                    description = "Unsupervised detector flagged abnormal structural deviations from standard legitimate patterns.",
                    severity = "HIGH",
                    source = "ANOMALY",
                    confidence = anomalyScore,
                    debugDetail = "Anomaly score: $anomalyScore"
                )
            )
        }

        // 2. Context Findings
        for (finding in contextFindings) {
            if (finding.matched && finding.severity in listOf("CRITICAL", "HIGH", "MEDIUM")) {
                reasons.add(
                    ExplanationReason(
                        id = "EXP_CTX_${finding.checkName}",
                        title = finding.description.split(".").firstOrNull() ?: finding.checkName,
                        description = finding.description,
                        severity = finding.severity,
                        source = finding.source,
                        confidence = 0.90f,
                        debugDetail = finding.evidence
                    )
                )
            }
        }

        // 3. Rule Triggers
        for (rule in ruleResults) {
            if (rule.severity in listOf("CRITICAL", "HIGH", "MEDIUM")) {
                reasons.add(
                    ExplanationReason(
                        id = "EXP_${rule.id}",
                        title = rule.description.split(";").firstOrNull()?.split(".")?.firstOrNull() ?: rule.id,
                        description = rule.description,
                        severity = rule.severity,
                        source = rule.source,
                        confidence = 0.95f,
                        debugDetail = rule.evidence
                    )
                )
            }
        }

        // Deduplicate by title
        val distinctReasons = reasons.distinctBy { it.title }

        // Sort by severity weight descending
        val sorted = distinctReasons.sortedByDescending { SEVERITY_ORDER[it.severity] ?: 0 }

        return sorted.take(maxReasons.coerceAtLeast(3))
    }
}
