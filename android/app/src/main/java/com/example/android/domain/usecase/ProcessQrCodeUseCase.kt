package com.example.android.domain.usecase

import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.*
import com.example.android.domain.repository.QrAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import java.util.UUID

/**
 * Use Case processing scanned or imported QR code payloads.
 * Pipeline: QR Raw Payload -> URL Extraction -> Feature Extraction -> UrlClassifier -> Local Storage.
 */
class ProcessQrCodeUseCase(
    private val urlClassifier: UrlClassifier,
    private val qrAnalysisRepository: QrAnalysisRepository,
    private val threatHistoryRepository: ThreatHistoryRepository
) {

    suspend operator fun invoke(
        rawPayload: String,
        source: String = "CAMERA"
    ): Result<QrAnalysisResult> {
        val trimmedPayload = rawPayload.trim()
        if (trimmedPayload.isBlank()) {
            return Result.failure(IllegalArgumentException("QR payload is empty or blank"))
        }

        val isExplicitUrlScheme = trimmedPayload.startsWith("http://", ignoreCase = true) ||
                                  trimmedPayload.startsWith("https://", ignoreCase = true) ||
                                  trimmedPayload.startsWith("www.", ignoreCase = true)

        val extractedUrls = UrlExtractor.extractUrls(trimmedPayload)

        val targetUrl = when {
            isExplicitUrlScheme -> UrlExtractor.normalizeUrl(trimmedPayload)
            extractedUrls.isNotEmpty() && (!trimmedPayload.contains(" ") || extractedUrls.first().startsWith("http", ignoreCase = true)) -> {
                UrlExtractor.normalizeUrl(extractedUrls.first())
            }
            else -> ""
        }

        return try {
            val result = if (targetUrl.isNotBlank()) {
                val urlFeatures = UrlFeatureExtractor.extractFeatures(targetUrl)
                val analysisInput = UrlAnalysisInput(
                    rawPayload = trimmedPayload,
                    extractedUrl = targetUrl,
                    features = urlFeatures,
                    source = source
                )
                urlClassifier.classify(analysisInput)
            } else {
                // Non-URL QR Payload (Plain text / text data)
                QrAnalysisResult(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    rawContent = trimmedPayload,
                    extractedUrl = "",
                    domain = "TEXT_PAYLOAD",
                    riskScore = 0,
                    isQuishing = false,
                    confidence = 1.0f,
                    processedAt = System.currentTimeMillis()
                )
            }

            // Persist scan result to Room database
            qrAnalysisRepository.saveAnalysis(result)

            // If detected as suspicious / quishing threat, record in Threat History
            if (result.isQuishing || result.riskScore >= 50) {
                threatHistoryRepository.addThreat(
                    ThreatRecord(
                        id = UUID.randomUUID().toString(),
                        timestamp = result.timestamp,
                        source = "QR Code ($source)",
                        sender = result.domain.ifBlank { "QR Code" },
                        riskScore = result.riskScore,
                        category = "QUISHING",
                        actionTaken = "FLAGGED",
                        details = result.extractedUrl.ifBlank { result.rawContent.take(50) }
                    )
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
