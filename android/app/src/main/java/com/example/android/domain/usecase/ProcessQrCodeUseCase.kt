package com.example.android.domain.usecase

import com.example.android.data.classifier.OnnxUrlClassifier
import com.example.android.data.parser.UrlExtractor
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.domain.classifier.UrlClassifier
import com.example.android.domain.model.*
import com.example.android.domain.repository.QrAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.riskengine.RiskAssessment
import com.example.android.domain.riskengine.RiskEngine
import com.example.android.domain.riskengine.ThreatCategory
import android.content.Context
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.domain.repository.SyncRepository
import com.example.android.sync.SyncScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Use Case processing scanned or imported QR code payloads.
 * Pipeline: QR Raw Payload -> URL Extraction -> Feature Extraction -> UrlClassifier (ONNX) -> Risk Engine -> Local Storage.
 */
class ProcessQrCodeUseCase(
    private val urlClassifier: UrlClassifier,
    private val qrAnalysisRepository: QrAnalysisRepository,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val riskEngine: RiskEngine = RiskEngine(),
    private val syncRepository: SyncRepository? = null,
    private val context: Context? = null
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
            val finalResult = if (targetUrl.isNotBlank()) {
                val features = UrlFeatureExtractor.extractFeatures(targetUrl)
                val analysisInput = UrlAnalysisInput(
                    rawPayload = trimmedPayload,
                    extractedUrl = targetUrl,
                    features = features,
                    source = source
                )

                var urlProb: Float? = null
                var anomalyVal: Float? = null
                var isOk = true

                val preliminary = if (urlClassifier is OnnxUrlClassifier) {
                    val eval = urlClassifier.evaluateUrl(targetUrl, features)
                    urlProb = eval.phishingProbability
                    anomalyVal = eval.anomalyScore
                    isOk = eval.isXgboostAvailable
                    QrAnalysisResult(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        rawContent = trimmedPayload,
                        extractedUrl = targetUrl,
                        domain = features.domain.ifBlank { features.hostname },
                        riskScore = eval.riskScore,
                        isQuishing = eval.isQuishing,
                        confidence = eval.confidence,
                        processedAt = System.currentTimeMillis()
                    )
                } else {
                    urlClassifier.classify(analysisInput)
                }

                val evalUrlScore = urlProb?.let { it * 100f } ?: preliminary.riskScore.toFloat()

                // Evaluate through Phase 6 RiskEngine
                val riskAssessment = riskEngine.assess(
                    urlScore = evalUrlScore,
                    anomalyScore = anomalyVal,
                    urlFeatures = features,
                    qrPayload = trimmedPayload,
                    unavailableModels = if (!isOk) listOf("xgboost-url-v2") else emptyList()
                )

                QrAnalysisResult(
                    id = preliminary.id,
                    timestamp = preliminary.timestamp,
                    rawContent = preliminary.rawContent,
                    extractedUrl = preliminary.extractedUrl,
                    domain = preliminary.domain,
                    riskScore = riskAssessment.riskScore,
                    isQuishing = riskAssessment.riskScore >= 50,
                    confidence = preliminary.confidence,
                    processedAt = System.currentTimeMillis(),
                    assessment = riskAssessment,
                    urlFeatures = features,
                    isModelAvailable = isOk,
                    urlModelProb = urlProb,
                    anomalyScore = anomalyVal,
                    targetUrl = targetUrl,
                    source = source
                )
            } else {
                // Non-URL QR Payload (Plain text / special scheme)
                val riskAssessment = riskEngine.assess(
                    qrPayload = trimmedPayload
                )

                QrAnalysisResult(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    rawContent = trimmedPayload,
                    extractedUrl = "",
                    domain = "TEXT_PAYLOAD",
                    riskScore = riskAssessment.riskScore,
                    isQuishing = riskAssessment.riskScore >= 50,
                    confidence = 0.95f,
                    processedAt = System.currentTimeMillis(),
                    assessment = riskAssessment,
                    urlFeatures = null,
                    isModelAvailable = true,
                    urlModelProb = null,
                    anomalyScore = null,
                    targetUrl = "",
                    source = source
                )
            }

            // Persist scan result to Room database
            qrAnalysisRepository.saveAnalysis(finalResult)

            // Persist structured scan record with raw payload and QR details to Threat History
            val parsedQr = com.example.android.data.parser.QrPayloadParser.parse(trimmedPayload)
            val assessment = finalResult.assessment
            val finalScore = assessment?.riskScore ?: finalResult.riskScore
            val isQuarantined = finalResult.isQuishing || finalScore >= 70
            val severityStr = assessment?.category?.name ?: if (finalScore >= 70) "HIGH" else if (finalScore >= 50) "MEDIUM" else "SAFE"
            val actionStr = if (isQuarantined) "QUARANTINED" else (assessment?.recommendedAction?.actionName ?: "PROCEED_SAFELY")

            val threatRecord = ThreatRecord(
                id = finalResult.id,
                timestamp = finalResult.timestamp,
                source = "QR Code ($source)",
                sender = if (finalResult.extractedUrl.isNotBlank()) finalResult.domain else parsedQr.displayTitle,
                riskScore = finalScore,
                category = severityStr,
                actionTaken = actionStr,
                details = assessment?.reasons?.joinToString("; ") ?: "QR scan evaluated",
                rawContent = trimmedPayload,
                extractedUrl = if (finalResult.extractedUrl.isNotBlank()) finalResult.extractedUrl else parsedQr.extractedUrl,
                payloadType = parsedQr.payloadType,
                severity = severityStr,
                modelVersion = "xgboost-url-v2",
                isQuarantined = isQuarantined,
                reviewStatus = "NEW",
                contentRetentionMode = "LOCAL_SAVED"
            )
            if (assessment != null && (finalResult.isQuishing || assessment.category != ThreatCategory.SAFE)) {
                threatHistoryRepository.addThreat(threatRecord)
            }

            // Phase 8: Data-minimized sync queue enqueue (does NOT block local detection)
            try {
                val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val syncReq = DetectionSyncRequest(
                    clientEventId = finalResult.id,
                    eventType = "QR",
                    riskScore = finalResult.riskScore,
                    threatCategory = assessment?.category?.name ?: if (finalResult.riskScore >= 70) "HIGH" else if (finalResult.riskScore >= 50) "MEDIUM" else "SAFE",
                    recommendedAction = assessment?.recommendedAction?.actionName ?: "PROCEED_SAFELY",
                    timestamp = isoDateFormat.format(Date(finalResult.timestamp)),
                    modelVersions = mapOf(
                        "xgboost" to "xgboost-url-v2",
                        "isolation_forest" to "iforest-anomaly-v1",
                        "risk_engine" to "rule-risk-engine-v1"
                    ),
                    modelResults = mapOf(
                        "url_model_prob" to (finalResult.urlModelProb ?: 0f),
                        "anomaly_score" to (finalResult.anomalyScore ?: 0f),
                        "is_model_available" to finalResult.isModelAvailable
                    ),
                    ruleResults = assessment?.ruleResults?.map {
                        mapOf(
                            "id" to it.id,
                            "category" to it.category,
                            "severity" to it.severity,
                            "description" to it.description,
                            "score_contribution" to it.scoreContribution
                        )
                    },
                    contextResults = mapOf("sender" to (if (finalResult.extractedUrl.isNotBlank()) finalResult.domain else "QR Payload")),
                    reasons = assessment?.reasons ?: emptyList(),
                    urlMetadata = finalResult.urlFeatures?.let {
                        mapOf(
                            "domain" to it.domain,
                            "hostname" to it.hostname,
                            "is_https" to it.isHttps,
                            "is_ip_hostname" to it.isIpHostname,
                            "subdomain_count" to it.subdomainCount,
                            "has_suspicious_symbols" to it.hasSuspiciousSymbols
                        )
                    },
                    qrMetadata = mapOf(
                        "target_url" to finalResult.targetUrl,
                        "source" to finalResult.source
                    ),
                    sanitizedPreview = "QR (${parsedQr.payloadType}): ${parsedQr.displayTitle}",
                    contentRetentionMode = "LOCAL_SAVED",
                    decodedUrl = finalResult.extractedUrl.ifBlank { null },
                    reviewStatus = "NEW",
                    explanation = assessment?.reasons?.joinToString("; ") ?: "Decoded QR scan"
                )
                syncRepository?.enqueueDetection(syncReq)
                context?.let { ctx -> SyncScheduler.scheduleImmediateSync(ctx) }
            } catch (ignored: Exception) {
                // Critical Rule: Offline-first detection must NEVER fail because of sync queue operations
            }

            Result.success(finalResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
