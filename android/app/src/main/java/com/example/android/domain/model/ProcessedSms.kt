package com.example.android.domain.model

/**
 * Preprocessed SMS data model ready for TinyBERT transformer tokenizer / Phase 5 inference engine.
 */
data class ProcessedSms(
    val raw: RawSms,
    val normalizedText: String,
    val tokens: List<String>,
    val evidence: SmsFeatureEvidence
)
