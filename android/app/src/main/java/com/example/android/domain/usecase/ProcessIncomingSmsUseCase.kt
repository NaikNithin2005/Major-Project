package com.example.android.domain.usecase

import com.example.android.data.parser.SmsParser
import com.example.android.data.preprocessor.SmsPreprocessor
import com.example.android.domain.classifier.SmishingClassifier
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import java.util.UUID

/**
 * Use case coordinating incoming SMS acquisition, parsing, feature extraction, classification,
 * and persistent local storage through repository layers.
 */
class ProcessIncomingSmsUseCase(
    private val smsParser: SmsParser,
    private val smsPreprocessor: SmsPreprocessor,
    private val classifier: SmishingClassifier,
    private val smsAnalysisRepository: SmsAnalysisRepository,
    private val threatHistoryRepository: ThreatHistoryRepository
) {

    suspend operator fun invoke(rawSms: RawSms): Result<SmsAnalysisResult> {
        return try {
            // 1. Parse SMS
            val parsedSms = smsParser.parse(rawSms)

            // 2. Preprocess SMS
            val processedSms = smsPreprocessor.preprocess(parsedSms)

            // 3. Classify with SmishingClassifier interface (TinyBERT contract)
            val classification = classifier.classify(processedSms)

            val analysisResult = SmsAnalysisResult(
                id = UUID.randomUUID().toString(),
                messageId = rawSms.messageId,
                timestamp = rawSms.timestamp,
                sender = parsedSms.safeSender,
                riskScore = classification.riskScore,
                isSmishing = classification.isSmishing,
                confidence = classification.confidence,
                extractedUrlsCount = parsedSms.extractedUrls.size,
                processedAt = System.currentTimeMillis()
            )

            // 4. Save analysis summary to local Room database via SmsAnalysisRepository
            smsAnalysisRepository.saveAnalysis(analysisResult)

            // 5. If threat detected, record in ThreatHistoryRepository
            if (classification.isSmishing || classification.riskScore >= 50) {
                val threatRecord = ThreatRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = rawSms.timestamp,
                    source = "SMS",
                    sender = parsedSms.safeSender,
                    riskScore = classification.riskScore,
                    category = classification.threatCategory,
                    actionTaken = "FLAGGED",
                    details = "Detected ${parsedSms.extractedUrls.size} URLs, keywords: ${processedSms.evidence.detectedKeywords.joinToString()}"
                )
                threatHistoryRepository.addThreat(threatRecord)
            }

            Result.success(analysisResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
