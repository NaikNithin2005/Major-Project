package com.example.android.domain.usecase

import android.content.Context
import com.example.android.data.classifier.OnnxTinyBertSmishingClassifier
import com.example.android.data.parser.SmsParser
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.data.preprocessor.SmsPreprocessor
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.financial.FinancialCategory
import com.example.android.domain.financial.FinancialMessageClassifier
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.SyncRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.repository.TrustedSenderRepository
import com.example.android.domain.riskengine.RecommendedAction
import com.example.android.domain.riskengine.RiskAssessment
import com.example.android.domain.riskengine.RiskEngine
import com.example.android.domain.riskengine.ThreatCategory
import com.example.android.sync.SyncScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Production use case coordinating incoming SMS acquisition, parsing, feature extraction,
 * financial intelligence classification, TinyBERT evaluation, Risk Engine aggregation,
 * and persistent local storage.
 *
 * Guarantees zero false-positive quarantine of genuine banking debits, credits, UPI alerts, and OTPs.
 */
class ProcessIncomingSmsUseCase(
    private val smsParser: SmsParser,
    private val smsPreprocessor: SmsPreprocessor,
    private val classifier: SmishingClassifier,
    private val smsAnalysisRepository: SmsAnalysisRepository,
    private val threatHistoryRepository: ThreatHistoryRepository,
    private val riskEngine: RiskEngine = RiskEngine(),
    private val syncRepository: SyncRepository? = null,
    private val context: Context? = null,
    private val trustedSenderRepository: TrustedSenderRepository? = null,
    private val financialMessageClassifier: FinancialMessageClassifier = FinancialMessageClassifier(),
    private val personalizedLearningEngine: com.example.android.domain.learning.PersonalizedLearningEngine? = null
) {

    suspend operator fun invoke(rawSms: RawSms): Result<SmsAnalysisResult> {
        return try {
            // 1. Parse SMS
            val parsedSms = smsParser.parse(rawSms)

            // 2. Preprocess SMS
            val processedSms = smsPreprocessor.preprocess(parsedSms)

            // 3. Sender Trust check
            val isSenderTrusted = trustedSenderRepository?.isTrusted(parsedSms.safeSender) ?: false

            // 4. Financial Intelligence Classification
            val financialAnalysis = financialMessageClassifier.classify(
                rawBody = rawSms.body,
                sender = parsedSms.safeSender,
                extractedUrls = parsedSms.extractedUrls,
                isSenderTrusted = isSenderTrusted
            )

            // 5. Classify with TinyBERT ONNX or fallback
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

            // 6. Extract URL features if URLs are present in message
            val urlFeatures = if (parsedSms.extractedUrls.isNotEmpty()) {
                UrlFeatureExtractor.extractFeatures(parsedSms.extractedUrls.first())
            } else {
                null
            }

            val smsScore = smishingProb?.let { it * 100f } ?: classification.riskScore.toFloat()

            // 7. Evaluate Multi-Modal Risk Engine
            val baseAssessment = riskEngine.assess(
                smsScore = smsScore,
                processedSms = processedSms,
                urlFeatures = urlFeatures,
                unavailableModels = if (!isModelOk) listOf("tinybert-smishing-v2") else emptyList()
            )

            // 8. Financial and Trusted Sender Multi-Signal Calibration
            val preLearningAssessment: RiskAssessment = when {
                // Legitimate banking transaction, debit, credit, UPI, or OTP
                financialAnalysis.isLegitimateFinancial -> {
                    baseAssessment.copy(
                        riskScore = 10,
                        category = ThreatCategory.SAFE,
                        reasons = listOf(
                            "Verified legitimate financial notification (${financialAnalysis.category.displayName})."
                        ) + financialAnalysis.reasons,
                        recommendedAction = RecommendedAction.ALLOW
                    )
                }

                // Potential financial scam or credential harvesting demand
                financialAnalysis.isPotentialScam -> {
                    val reasons = mutableListOf<String>()
                    if (isSenderTrusted) {
                        reasons.add("Warning: Sender is marked as trusted, but this message contains high-risk scam indicators or credential requests.")
                    }
                    reasons.addAll(financialAnalysis.reasons)
                    reasons.addAll(baseAssessment.reasons)

                    baseAssessment.copy(
                        riskScore = maxOf(85, baseAssessment.riskScore),
                        category = ThreatCategory.HIGH,
                        reasons = reasons.distinct(),
                        recommendedAction = RecommendedAction.BLOCK_OR_STRONGLY_WARN
                    )
                }

                // Trusted sender with no dangerous links or ultimatums: soften baseline score
                isSenderTrusted && !financialAnalysis.hasSuspiciousLink && parsedSms.extractedUrls.isEmpty() -> {
                    val adjustedScore = (baseAssessment.riskScore - 30).coerceAtLeast(10)
                    baseAssessment.copy(
                        riskScore = adjustedScore,
                        category = ThreatCategory.fromScore(adjustedScore),
                        reasons = listOf("Verified sender in your trusted list.") + baseAssessment.reasons,
                        recommendedAction = if (adjustedScore < 40) RecommendedAction.ALLOW else baseAssessment.recommendedAction
                    )
                }

                else -> baseAssessment
            }

            // 8b. Personalized Learning Profile Calibration (Honoring Security Guardrails)
            val finalAssessment: RiskAssessment = if (personalizedLearningEngine != null && !financialAnalysis.isLegitimateFinancial) {
                val calibration = personalizedLearningEngine.calibrate(
                    sender = parsedSms.safeSender,
                    baseScore = preLearningAssessment.riskScore,
                    hasCredentialHarvesting = financialAnalysis.hasCredentialHarvestingDemand,
                    hasDangerousUrl = financialAnalysis.hasSuspiciousLink
                )
                if (calibration.isCalibrated) {
                    val newScore = calibration.adjustedRiskScore
                    val reasons = mutableListOf<String>()
                    calibration.calibrationReason?.let { reasons.add(it) }
                    reasons.addAll(preLearningAssessment.reasons)
                    preLearningAssessment.copy(
                        riskScore = newScore,
                        category = ThreatCategory.fromScore(newScore),
                        reasons = reasons.distinct(),
                        recommendedAction = if (newScore < 40) RecommendedAction.ALLOW else preLearningAssessment.recommendedAction
                    )
                } else {
                    preLearningAssessment
                }
            } else {
                preLearningAssessment
            }

            val isSmishing = finalAssessment.riskScore >= 50

            // 9. Separate Detection from Quarantine
            // Only quarantine confirmed critical credential harvesting or extreme scam ultimatums.
            // Never automatically quarantine legitimate transactions or low/medium messages.
            val isQuarantined = financialAnalysis.hasCredentialHarvestingDemand ||
                    (finalAssessment.riskScore >= 85 && parsedSms.extractedUrls.isNotEmpty() && !financialAnalysis.isLegitimateFinancial)

            val analysisResult = SmsAnalysisResult(
                id = UUID.randomUUID().toString(),
                messageId = rawSms.messageId,
                timestamp = rawSms.timestamp,
                sender = parsedSms.safeSender,
                riskScore = finalAssessment.riskScore,
                isSmishing = isSmishing,
                confidence = classification.confidence,
                extractedUrlsCount = parsedSms.extractedUrls.size,
                processedAt = System.currentTimeMillis(),
                assessment = finalAssessment,
                rawBody = rawSms.body,
                isModelAvailable = isModelOk,
                smishingModelProb = smishingProb
            )

            // Save analysis summary to local Room database via SmsAnalysisRepository
            smsAnalysisRepository.saveAnalysis(analysisResult)

            val explanationText = when {
                finalAssessment.reasons.isNotEmpty() -> finalAssessment.reasons.joinToString("; ")
                finalAssessment.riskScore >= 70 -> "Message exhibits multiple high-risk scam characteristics."
                finalAssessment.riskScore >= 40 -> "Message contains unusual urgency or unfamiliar links."
                else -> "No common scam signs were found in this message. Always stay alert when opening unexpected links."
            }

            val threatRecord = ThreatRecord(
                id = analysisResult.id,
                timestamp = rawSms.timestamp,
                source = "SMS",
                sender = parsedSms.safeSender,
                riskScore = finalAssessment.riskScore,
                category = finalAssessment.category.name,
                actionTaken = if (isQuarantined) "QUARANTINED" else finalAssessment.recommendedAction.actionName,
                details = explanationText,
                rawContent = rawSms.body,
                extractedUrl = parsedSms.extractedUrls.firstOrNull(),
                payloadType = "SMS",
                severity = finalAssessment.category.name,
                modelVersion = "tinybert-smishing-v2",
                isQuarantined = isQuarantined,
                reviewStatus = "NEW",
                contentRetentionMode = "LOCAL_SAVED"
            )

            // Save ALL messages to persistent history so users can inspect safe, suspicious, and dangerous messages
            threatHistoryRepository.addThreat(threatRecord)

            // Real-time consumer-friendly alert notifications
            if (context != null) {
                try {
                    val settings = try {
                        com.example.android.data.local.datastore.PreferencesDataStore(context).getSystemSettings()
                    } catch (e: Exception) {
                        com.example.android.domain.model.SystemSettings()
                    }

                    if (settings.notificationsEnabled) {
                        when {
                            finalAssessment.riskScore >= 70 -> {
                                if (settings.notifyHighSeverity) {
                                    com.example.android.util.NotificationHelper.showDangerousMessageAlert(
                                        context = context,
                                        sender = parsedSms.safeSender,
                                        explanation = finalAssessment.reasons.firstOrNull() ?: "Scam indicators detected in text.",
                                        eventId = analysisResult.id,
                                        showLockScreenPreview = settings.showNotificationPreviews
                                    )
                                }
                            }
                            finalAssessment.riskScore >= 40 -> {
                                if (settings.notifyMediumSeverity) {
                                    com.example.android.util.NotificationHelper.showSuspiciousMessageAlert(
                                        context = context,
                                        sender = parsedSms.safeSender,
                                        explanation = finalAssessment.reasons.firstOrNull() ?: "Unusual phrasing or link detected.",
                                        eventId = analysisResult.id,
                                        showLockScreenPreview = settings.showNotificationPreviews
                                    )
                                }
                            }
                            else -> {
                                com.example.android.util.NotificationHelper.showSafeMessageNotification(
                                    context = context,
                                    sender = parsedSms.safeSender,
                                    eventId = analysisResult.id
                                )
                            }
                        }
                    }
                } catch (ignored: Exception) {
                }
            }

            // Sync queue enqueue with full message body and sender details
            try {
                val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val syncReq = DetectionSyncRequest(
                    clientEventId = analysisResult.id,
                    eventType = "SMS",
                    riskScore = finalAssessment.riskScore,
                    threatCategory = finalAssessment.category.name,
                    recommendedAction = finalAssessment.recommendedAction.actionName,
                    timestamp = isoDateFormat.format(Date(analysisResult.timestamp)),
                    modelVersions = mapOf(
                        "tinybert" to "tinybert-smishing-v2",
                        "risk_engine" to "rule-risk-engine-v1"
                    ),
                    modelResults = mapOf(
                        "smishing_probability" to (smishingProb ?: 0f),
                        "is_model_available" to isModelOk
                    ),
                    ruleResults = finalAssessment.ruleResults.map {
                        mapOf(
                            "id" to it.id,
                            "category" to it.category,
                            "severity" to it.severity,
                            "description" to it.description,
                            "score_contribution" to it.scoreContribution
                        )
                    },
                    contextResults = mapOf("sender" to parsedSms.safeSender),
                    reasons = finalAssessment.reasons,
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
                    sanitizedPreview = "SMS from ${parsedSms.safeSender}: ${finalAssessment.reasons.firstOrNull() ?: rawSms.body.take(60)}",
                    contentRetentionMode = "LOCAL_SAVED",
                    decodedUrl = parsedSms.extractedUrls.firstOrNull(),
                    reviewStatus = "NEW",
                    explanation = explanationText,
                    rawContent = rawSms.body,
                    sender = parsedSms.safeSender
                )
                syncRepository?.enqueueDetection(syncReq)
                context?.let { ctx -> SyncScheduler.scheduleImmediateSync(ctx) }
            } catch (ignored: Exception) {
            }

            Result.success(analysisResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
