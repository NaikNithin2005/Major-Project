package com.example.android.domain.classifier

import com.example.android.domain.model.QrAnalysisResult
import com.example.android.domain.model.UrlAnalysisInput
import java.util.UUID

/**
 * Baseline heuristic implementation of [UrlClassifier] for Phase 4 testing.
 * Provides functional evaluation of extracted URL features until XGBoost model is integrated in Phase 5.
 */
class DefaultUrlClassifier : UrlClassifier {

    override suspend fun classify(input: UrlAnalysisInput): QrAnalysisResult {
        val features = input.features
        val suspiciousList = features.suspiciousCharacteristics

        // Baseline heuristic scoring for Phase 4 validation
        var riskScore = 0
        if (!features.isHttps) riskScore += 20
        if (features.isIpHostname) riskScore += 30
        if (features.hasSuspiciousSymbols) riskScore += 15
        if (features.subdomainCount > 2) riskScore += 15
        if (features.hasUnusualPort) riskScore += 10
        if (features.isPunycode) riskScore += 10

        val isQuishing = riskScore >= 50
        val confidence = if (suspiciousList.isEmpty()) 0.95f else (0.50f + (riskScore / 200f)).coerceAtMost(0.99f)

        return QrAnalysisResult(
            id = UUID.randomUUID().toString(),
            timestamp = input.timestamp,
            rawContent = input.rawPayload,
            extractedUrl = input.extractedUrl,
            domain = features.domain.ifBlank { features.hostname },
            riskScore = riskScore.coerceIn(0, 100),
            isQuishing = isQuishing,
            confidence = confidence,
            processedAt = System.currentTimeMillis()
        )
    }
}
