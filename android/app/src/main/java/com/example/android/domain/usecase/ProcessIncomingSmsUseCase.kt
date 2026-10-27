package com.example.android.domain.usecase

import com.example.android.data.classifier.OnnxTinyBertSmishingClassifier
import com.example.android.data.parser.SmsParser
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.data.preprocessor.SmsPreprocessor
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
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
 * Use case coordinating incoming SMS acquisition, parsing, feature extraction,
 * TinyBERT ONNX classification, multi-modal Risk Engine evaluation, and persistent local storage.
 */
class ProcessIncomingSmsUseCase(
    private val smsParser: SmsParser,
    private val smsPreprocessor: SmsPreprocessor,
    private val classifier: SmishingClassifier,
    private val smsAnalysisRepository: SmsAnalysisRepository,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val riskEngine: RiskEngine = RiskEngine(),
    private val syncRepository: SyncRepository? = null,
    private val context: Context? = null
) {

    suspend operator fun invoke(rawSms: RawSms): Result<SmsAnalysisResult> {
        return try {
            // 1. Parse SMS
            val parsedSms = smsParser.parse(rawSms)

            // 2. Preprocess SMS
            val processedSms = smsPreprocessor.preprocess(parsedSms)

            // 3. Classify with TinyBERT ONNX or fallback
            var smishingProb: Float? = null
            var isModelOk = true

            val classification = if (classifier is OnnxTinyBertSmishingClassifier) {
                val eval = classifier.evaluateSms(processedSms.normalizedText)
                smishingProb = eval.smishingProbability
                isModelOk = eval.isModelAvailable
                classifier.classify(processedSms)
            } else {
                classifier.classify(processedSms)
            }

            // 4. Extract URL features if URLs are present in message
            val urlFeatures = if (parsedSms.extractedUrls.isNotEmpty()) {
                UrlFeatureExtractor.extractFeatures(parsedSms.extractedUrls.first())
            } else {
                null
            }

            val smsScore = smishingProb?.let { it * 100f } ?: classification.riskScore.toFloat()

            // 5. Evaluate Multi-Modal Risk Engine (Phase 6 decision layer)
            val assessment = riskEngine.assess(
                smsScore = smsScore,
                processedSms = processedSms,
                urlFeatures = urlFeatures,
                unavailableModels = if (!isModelOk) listOf("tinybert-smishing-v2") else emptyList()
            )

            val isSmishing = assessment.riskScore >= 50

            val analysisResult = SmsAnalysisResult(
                id = UUID.randomUUID().toString(),
                messageId = rawSms.messageId,
                timestamp = rawSms.timestamp,
                sender = parsedSms.safeSender,
                riskScore = assessment.riskScore,
                isSmishing = isSmishing,
                confidence = classification.confidence,
                extractedUrlsCount = parsedSms.extractedUrls.size,
                processedAt = System.currentTimeMillis(),
                assessment = assessment,
                rawBody = rawSms.body,
                isModelAvailable = isModelOk,
                smishingModelProb = smishingProb
            )

            // 6. Save analysis summary to local Room database via SmsAnalysisRepository
            smsAnalysisRepository.saveAnalysis(analysisResult)

            // 7. Save structured scan record in ThreatHistoryRepository
            val isThreat = isSmishing || assessment.category != ThreatCategory.SAFE
            val isQuarantined = isSmishing || assessment.riskScore >= 70

            val threatRecord = ThreatRecord(
                id = analysisResult.id,
                timestamp = rawSms.timestamp,
                source = "SMS",
                sender = parsedSms.safeSender,
                riskScore = assessment.riskScore,
                category = assessment.category.name,
                actionTaken = if (isQuarantined) "QUARANTINED" else assessment.recommendedAction.actionName,
                details = assessment.reasons.joinToString("; "),
                rawContent = rawSms.body,
                extractedUrl = parsedSms.extractedUrls.firstOrNull(),
                payloadType = "SMS",
                severity = assessment.category.name,
                modelVersion = "tinybert-smishing-v2",
                isQuarantined = isQuarantined,
                reviewStatus = "NEW",
                contentRetentionMode = "LOCAL_SAVED"
            )
            if (isThreat) {
                threatHistoryRepository.addThreat(threatRecord)
            }

            // 8. Phase 8: Data-minimized sync queue enqueue (does NOT block local detection)
            try {
                val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val syncReq = DetectionSyncRequest(
                    clientEventId = analysisResult.id,
                    eventType = "SMS",
                    riskScore = assessment.riskScore,
                    threatCategory = assessment.category.name,
                    recommendedAction = assessment.recommendedAction.actionName,
                    timestamp = isoDateFormat.format(Date(analysisResult.timestamp)),
                    modelVersions = mapOf(
                        "tinybert" to "tinybert-smishing-v2",
                        "risk_engine" to "rule-risk-engine-v1"
                    ),
                    modelResults = mapOf(
                        "smishing_probability" to (smishingProb ?: 0f),
                        "is_model_available" to isModelOk
                    ),
                    ruleResults = assessment.ruleResults.map {
                        mapOf(
                            "id" to it.id,
                            "category" to it.category,
                            "severity" to it.severity,
                            "description" to it.description,
                            "score_contribution" to it.scoreContribution
                        )
                    },
                    contextResults = mapOf("sender" to parsedSms.safeSender),
                    reasons = assessment.reasons,
                    urlMetadata = urlFeatures?.let {
                        mapOf(
                            "domain" to it.domain,
                            "hostname" to it.hostname,
                            "is_https" to it.isHttps,
                            "is_ip_hostname" to it.isIpHostname,
                            "subdomain_count" to it.subdomainCount,
                            "has_suspicious_symbols" to it.hasSuspiciousSymbols
                        )
                    },
                    sanitizedPreview = "SMS from ${parsedSms.safeSender}: ${assessment.reasons.firstOrNull() ?: "Scanned incoming SMS"}",
                    contentRetentionMode = "LOCAL_SAVED",
                    decodedUrl = parsedSms.extractedUrls.firstOrNull(),
                    reviewStatus = "NEW",
                    explanation = assessment.reasons.joinToString("; ")
                )
                syncRepository?.enqueueDetection(syncReq)
                context?.let { ctx -> SyncScheduler.scheduleImmediateSync(ctx) }
            } catch (ignored: Exception) {
                // Critical Rule: Offline-first detection must NEVER fail because of sync queue operations
            }

            Result.success(analysisResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
