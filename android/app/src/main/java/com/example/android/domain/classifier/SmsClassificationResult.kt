package com.example.android.domain.classifier

import com.example.android.domain.model.SmsFeatureEvidence

/**
 * Output representation produced by the Smishing Classifier.
 */
data class SmsClassificationResult(
    val riskScore: Int, // 0 - 100
    val isSmishing: Boolean,
    val confidence: Float, // 0.0 - 1.0
    val threatCategory: String, // SAFE, SPAM, SMISHING, HIGH_RISK, LEGITIMATE_BANKING, SUSPICIOUS
    val evidence: SmsFeatureEvidence,
    val classId: Int = 1, // 0: LEGITIMATE_BANKING, 1: LEGITIMATE_GENERAL, 2: SMISHING, 3: SUSPICIOUS
    val classificationLabel: String = "LEGITIMATE_GENERAL",
    val probabilities: Map<String, Float> = emptyMap()
)
