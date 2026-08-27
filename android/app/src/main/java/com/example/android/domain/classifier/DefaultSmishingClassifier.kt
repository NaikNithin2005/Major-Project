package com.example.android.domain.classifier

import com.example.android.domain.model.ProcessedSms

/**
 * Baseline Phase 3 implementation of [SmishingClassifier].
 * Evaluates extracted feature evidence (urls, urgency, keywords, sender pattern)
 * to provide baseline analysis before Phase 5 TinyBERT model integration.
 */
class DefaultSmishingClassifier : SmishingClassifier {

    override suspend fun classify(processedSms: ProcessedSms): SmsClassificationResult {
        val evidence = processedSms.evidence
        var score = 0
        
        if (evidence.extractedUrls.isNotEmpty()) {
            score += 40
        }
        if (evidence.urgencyIndicators.isNotEmpty()) {
            score += 25 * evidence.urgencyIndicators.size
        }
        if (evidence.detectedKeywords.isNotEmpty()) {
            score += 15 * evidence.detectedKeywords.size
        }
        if (evidence.brandEntities.isNotEmpty() && evidence.extractedUrls.isNotEmpty()) {
            score += 20
        }

        score = score.coerceIn(0, 100)

        val isSmishing = score >= 50
        val category = when {
            score >= 75 -> "HIGH_RISK"
            score >= 50 -> "SMISHING"
            score >= 25 -> "SPAM"
            else -> "SAFE"
        }

        val confidence = when {
            score >= 80 || score <= 10 -> 0.95f
            score >= 50 -> 0.85f
            else -> 0.75f
        }

        return SmsClassificationResult(
            riskScore = score,
            isSmishing = isSmishing,
            confidence = confidence,
            threatCategory = category,
            evidence = evidence
        )
    }
}
