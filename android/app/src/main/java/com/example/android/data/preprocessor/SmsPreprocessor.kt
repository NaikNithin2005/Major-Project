package com.example.android.data.preprocessor

import com.example.android.data.parser.ParsedSmsData
import com.example.android.domain.model.ProcessedSms
import com.example.android.domain.model.SmsFeatureEvidence

/**
 * Text Preprocessing pipeline preparing SMS data for evidence extraction and future TinyBERT transformer tokenization.
 */
class SmsPreprocessor {

    private val SUSPICIOUS_KEYWORDS = setOf(
        "blocked", "suspended", "verify", "verification", "claim", "reward", "prize",
        "update", "expired", "expire", "penalty", "action required", "click here",
        "lottery", "unauthorized", "kyc", "compromised", "deactivated", "urgent action",
        "refund", "winner", "cashback", "alert"
    )

    private val URGENCY_INDICATORS = setOf(
        "immediately", "urgent", "24 hours", "24hrs", "today", "now", "soon",
        "expires today", "suspended immediately", "final notice", "within 10 mins"
    )

    fun preprocess(parsedSms: ParsedSmsData): ProcessedSms {
        val normalizedText = normalize(parsedSms.safeBody)
        val tokens = tokenize(normalizedText)

        val detectedKeywords = tokens.filter { SUSPICIOUS_KEYWORDS.contains(it) }.distinct()
        
        val urgencyIndicators = URGENCY_INDICATORS.filter { indicator ->
            normalizedText.contains(indicator, ignoreCase = true)
        }.distinct()

        val senderPattern = classifySenderPattern(parsedSms.safeSender)

        val evidence = SmsFeatureEvidence(
            detectedKeywords = detectedKeywords,
            urgencyIndicators = urgencyIndicators,
            brandEntities = parsedSms.detectedBrands,
            senderPattern = senderPattern,
            extractedUrls = parsedSms.extractedUrls,
            containsOtp = parsedSms.containsOtp,
            containsPhoneNumber = parsedSms.containsPhoneNumbers,
            specialCharRatio = parsedSms.specialCharRatio
        )

        return ProcessedSms(
            raw = parsedSms.rawSms,
            normalizedText = normalizedText,
            tokens = tokens,
            evidence = evidence
        )
    }

    /**
     * Normalizes text by removing invisible zero-width formatting characters,
     * resolving visual homoglyphs to Latin equivalents, converting to lowercase,
     * flattening whitespace, and trimming.
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""
        // Strip zero-width format chars: \u200B-\u200F, \uFEFF, \u00AD, \u2060, \u202A-\u202E
        val stripped = text.replace(Regex("[\\u200B-\\u200F\\uFEFF\\u00AD\\u2060\\u202A-\\u202E]"), "")
        // Map common visual homoglyphs (Cyrillic / Greek lookalikes)
        val homoglyphs = mapOf(
            'а' to 'a', 'е' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c', 'у' to 'y', 'х' to 'x', 'і' to 'i',
            'А' to 'A', 'В' to 'B', 'Е' to 'E', 'К' to 'K', 'М' to 'M', 'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X',
            'α' to 'a', 'ο' to 'o', 'ν' to 'v', 'ρ' to 'p', 'τ' to 't', 'κ' to 'k'
        )
        val dehomoglyphed = buildString {
            for (ch in stripped) {
                append(homoglyphs[ch] ?: ch)
            }
        }
        return dehomoglyphed.lowercase()
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Basic tokenization producing cleaned word tokens.
     */
    fun tokenize(normalizedText: String): List<String> {
        if (normalizedText.isBlank()) return emptyList()
        return normalizedText.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() }
    }

    /**
     * Classifies the sender string into structural patterns.
     */
    fun classifySenderPattern(sender: String): String {
        val trimmed = sender.trim()
        return when {
            trimmed.isBlank() -> "UNKNOWN"
            trimmed.matches(Regex("^[A-Za-z0-9]{2,3}-[A-Za-z0-9]{3,8}$")) -> "ALPHANUMERIC_SHORTCODE"
            trimmed.startsWith("+") && trimmed.substring(1).all { it.isDigit() } -> "INTERNATIONAL_PHONE"
            trimmed.all { it.isDigit() } && trimmed.length in 5..6 -> "NUMERIC_SHORTCODE"
            trimmed.all { it.isDigit() } && trimmed.length in 10..12 -> "LOCAL_PHONE"
            else -> "ALPHANUMERIC_CUSTOM"
        }
    }
}
