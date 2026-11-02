package com.example.android.domain.learning

import com.example.android.domain.model.UserFeedback
import com.example.android.domain.repository.FeedbackRepository
import com.example.android.domain.repository.TrustedSenderRepository

/**
 * Phase 4: Personalized Learning Engine.
 * Dynamically calibrates risk assessments based on explicit user corrections and trusted senders.
 *
 * Implements strict security guardrails:
 * - Never overrides confirmed credential harvesting (asking for OTP, password, PIN)
 * - Never overrides confirmed dangerous or malicious URLs
 */
class PersonalizedLearningEngine(
    private val feedbackRepository: FeedbackRepository,
    private val trustedSenderRepository: TrustedSenderRepository
) {

    data class LearningCalibration(
        val adjustedRiskScore: Int,
        val isCalibrated: Boolean,
        val calibrationReason: String? = null,
        val isMarkedSafeByUser: Boolean = false,
        val isMarkedDangerousByUser: Boolean = false
    )

    suspend fun calibrate(
        sender: String,
        baseScore: Int,
        hasCredentialHarvesting: Boolean,
        hasDangerousUrl: Boolean
    ): LearningCalibration {
        // Critical Security Guardrails: Never override credential theft or malicious malware links
        if (hasCredentialHarvesting || hasDangerousUrl) {
            return LearningCalibration(
                adjustedRiskScore = maxOf(baseScore, 85),
                isCalibrated = false,
                calibrationReason = "Guardrail Active: Critical credential harvesting or dangerous links cannot be overridden by user preferences."
            )
        }

        // 1. Check if sender is in user's Trusted Senders list
        val isTrusted = trustedSenderRepository.isTrusted(sender)
        if (isTrusted) {
            val calibratedScore = (baseScore - 40).coerceAtLeast(10)
            return LearningCalibration(
                adjustedRiskScore = calibratedScore,
                isCalibrated = true,
                calibrationReason = "Personalized learning applied: Sender '$sender' is in your Trusted Senders list.",
                isMarkedSafeByUser = true
            )
        }

        // 2. Check historical feedback for this sender
        val feedbackList = feedbackRepository.getFeedbackForSender(sender)
        if (feedbackList.isNotEmpty()) {
            val mostRecent = feedbackList.first()
            if (mostRecent.correctedClassification == "SAFE" || 
                mostRecent.userCategory.contains("Safe", ignoreCase = true) ||
                mostRecent.userCategory.contains("Legitimate", ignoreCase = true) ||
                mostRecent.userCategory.contains("Trusted", ignoreCase = true)
            ) {
                val calibratedScore = (baseScore - 35).coerceAtLeast(10)
                return LearningCalibration(
                    adjustedRiskScore = calibratedScore,
                    isCalibrated = true,
                    calibrationReason = "Personalized learning applied: You previously confirmed messages from '$sender' are legitimate.",
                    isMarkedSafeByUser = true
                )
            } else if (mostRecent.correctedClassification == "DANGEROUS" ||
                mostRecent.userCategory.contains("Scam", ignoreCase = true) ||
                mostRecent.userCategory.contains("Dangerous", ignoreCase = true) ||
                mostRecent.userCategory.contains("Spam", ignoreCase = true)
            ) {
                val calibratedScore = (baseScore + 30).coerceAtMost(95)
                return LearningCalibration(
                    adjustedRiskScore = calibratedScore,
                    isCalibrated = true,
                    calibrationReason = "Personalized learning applied: You previously reported messages from '$sender' as dangerous.",
                    isMarkedDangerousByUser = true
                )
            }
        }

        return LearningCalibration(
            adjustedRiskScore = baseScore,
            isCalibrated = false
        )
    }
}

