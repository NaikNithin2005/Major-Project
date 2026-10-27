package com.example.android.domain.rules

import com.example.android.domain.model.ProcessedSms
import com.example.android.domain.model.UrlFeatures

/**
 * Deterministic Rule Engine for observable security indicators.
 * Implements testable rules across SMS, URL, and QR payloads.
 */
class RuleEngine {

    companion object {
        private val SUSPICIOUS_SCHEMES = setOf("javascript:", "data:", "intent:", "smsto:", "market:", "tel:")
        private val KNOWN_SHORTENERS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "is.gd", "ow.ly", "buff.ly", "cutt.ly"
        )
        private val SUSPICIOUS_TLDS = setOf(
            ".xyz", ".top", ".site", ".online", ".cc", ".tk", ".zip", ".club", ".link", ".buzz"
        )
    }

    fun evaluate(
        urlFeatures: UrlFeatures? = null,
        processedSms: ProcessedSms? = null,
        qrPayload: String? = null
    ): List<RuleResult> {
        val results = mutableListOf<RuleResult>()

        // 1. URL Feature Rules
        if (urlFeatures != null) {
            evaluateUrlRules(urlFeatures, results)
        }

        // 2. SMS Feature Rules
        if (processedSms != null) {
            evaluateSmsRules(processedSms, results)
        }

        // 3. QR Payload Rules
        if (!qrPayload.isNullOrBlank()) {
            evaluateQrRules(qrPayload, results)
        }

        return results
    }

    private fun evaluateUrlRules(features: UrlFeatures, results: MutableList<RuleResult>) {
        // Missing HTTPS
        if (!features.isHttps) {
            results.add(
                RuleResult(
                    id = "RULE_MISSING_HTTPS",
                    category = "NETWORK_SECURITY",
                    severity = "MEDIUM",
                    description = "Insecure communication protocol (Missing HTTPS).",
                    scoreContribution = 15f,
                    evidence = "Scheme is unencrypted: ${features.scheme}",
                    source = "URL_FEATURE"
                )
            )
        }

        // IP Hostname
        if (features.isIpHostname) {
            results.add(
                RuleResult(
                    id = "RULE_IP_HOSTNAME",
                    category = "DOMAIN_REPUTATION",
                    severity = "HIGH",
                    description = "Host is directly addressed via numeric IP address instead of registered domain.",
                    scoreContribution = 25f,
                    evidence = "IP hostname detected: ${features.hostname}",
                    source = "URL_FEATURE"
                )
            )
        }

        // Punycode Homograph
        if (features.isPunycode) {
            results.add(
                RuleResult(
                    id = "RULE_PUNYCODE_HOMOGRAPH",
                    category = "DOMAIN_REPUTATION",
                    severity = "HIGH",
                    description = "Punycode/IDN homograph indicator detected; possible visual domain spoofing.",
                    scoreContribution = 25f,
                    evidence = "Punycode domain: ${features.hostname}",
                    source = "URL_FEATURE"
                )
            )
        }

        // Excessive Subdomains
        if (features.subdomainCount > 2) {
            results.add(
                RuleResult(
                    id = "RULE_EXCESSIVE_SUBDOMAINS",
                    category = "DOMAIN_STRUCTURE",
                    severity = "MEDIUM",
                    description = "Excessive subdomain depth (${features.subdomainCount} levels) detected.",
                    scoreContribution = 15f,
                    evidence = "Subdomain count: ${features.subdomainCount}",
                    source = "URL_FEATURE"
                )
            )
        }

        // Unusual Port
        if (features.hasUnusualPort) {
            results.add(
                RuleResult(
                    id = "RULE_UNUSUAL_PORT",
                    category = "NETWORK_SECURITY",
                    severity = "MEDIUM",
                    description = "Non-standard network port specified in address.",
                    scoreContribution = 15f,
                    evidence = "URL features indicate non-standard port",
                    source = "URL_FEATURE"
                )
            )
        }

        // Suspicious Symbols
        if (features.hasSuspiciousSymbols) {
            results.add(
                RuleResult(
                    id = "RULE_SUSPICIOUS_SYMBOLS",
                    category = "URL_STRUCTURE",
                    severity = "MEDIUM",
                    description = "URL contains obfuscated characters, @, or excess hyphens.",
                    scoreContribution = 15f,
                    evidence = "Obfuscation symbols present in URL string",
                    source = "URL_FEATURE"
                )
            )
        }

        // Shortened URL
        if (KNOWN_SHORTENERS.any { features.hostname.contains(it) }) {
            results.add(
                RuleResult(
                    id = "RULE_SHORTENED_URL",
                    category = "DOMAIN_REPUTATION",
                    severity = "LOW",
                    description = "URL shortening service conceals true destination address.",
                    scoreContribution = 10f,
                    evidence = "Known URL shortener: ${features.hostname}",
                    source = "URL_FEATURE"
                )
            )
        }

        // Suspicious TLD
        if (SUSPICIOUS_TLDS.any { features.hostname.endsWith(it) }) {
            results.add(
                RuleResult(
                    id = "RULE_SUSPICIOUS_TLD",
                    category = "DOMAIN_REPUTATION",
                    severity = "MEDIUM",
                    description = "Domain registered under high-abuse Top-Level Domain.",
                    scoreContribution = 15f,
                    evidence = "High-abuse TLD in ${features.hostname}",
                    source = "URL_FEATURE"
                )
            )
        }

        // URL Length
        if (features.urlLength > 75) {
            results.add(
                RuleResult(
                    id = "RULE_LONG_URL",
                    category = "URL_STRUCTURE",
                    severity = "LOW",
                    description = "Abnormally long URL length (${features.urlLength} characters).",
                    scoreContribution = 10f,
                    evidence = "URL length: ${features.urlLength} chars",
                    source = "URL_FEATURE"
                )
            )
        }
    }

    private fun evaluateSmsRules(processedSms: ProcessedSms, results: MutableList<RuleResult>) {
        val evidence = processedSms.evidence
        val cleanText = processedSms.cleanedBody.lowercase()

        // Check for safe operational urgency (e.g. OTP validity or parcel delivery without threats or links)
        val isSafeOperationalUrgency = (cleanText.contains("valid for") || cleanText.contains("expires in") || cleanText.contains("out for delivery") || cleanText.contains("arriving today")) &&
                !cleanText.contains("suspended") && !cleanText.contains("blocked") && !cleanText.contains("immediate action") && !cleanText.contains("final notice") &&
                evidence.extractedUrls.isEmpty()

        // Urgency Language
        if (evidence.urgencyIndicators.isNotEmpty() && !isSafeOperationalUrgency) {
            results.add(
                RuleResult(
                    id = "RULE_URGENCY_LANGUAGE",
                    category = "SOCIAL_ENGINEERING",
                    severity = "HIGH",
                    description = "Message employs psychological urgency language forcing rapid action.",
                    scoreContribution = 25f,
                    evidence = "Urgency indicators: ${evidence.urgencyIndicators.joinToString()}",
                    source = "SMS_FEATURE"
                )
            )
        }

        // Credential Harvesting Demand
        val hasCredDemand = cleanText.contains("share otp") || cleanText.contains("reply with otp") || cleanText.contains("send otp") ||
                cleanText.contains("enter pin") || cleanText.contains("cvv") || cleanText.contains("share password")
        if (hasCredDemand) {
            results.add(
                RuleResult(
                    id = "RULE_CREDENTIAL_HARVESTING",
                    category = "FINANCIAL_RISK",
                    severity = "CRITICAL",
                    description = "Message explicitly solicits sensitive authorization credentials (OTP/PIN/CVV/Password).",
                    scoreContribution = 35f,
                    evidence = "Credential solicitation pattern detected in SMS body.",
                    source = "SMS_FEATURE"
                )
            )
        }

        // Financial / Banking Keywords
        val financialKeywords = evidence.detectedKeywords.filter { kw ->
            kw in listOf("kyc", "bank", "account", "otp", "debit", "credit", "card", "refund", "upi")
        }
        if (financialKeywords.isNotEmpty()) {
            val isTransactionalReceipt = (cleanText.contains("debited") || cleanText.contains("credited") || cleanText.contains("avl bal") || cleanText.contains("statement for") || cleanText.contains("withdrawn from atm")) &&
                    !cleanText.contains("suspended") && !cleanText.contains("blocked") && !cleanText.contains("kyc") && evidence.extractedUrls.isEmpty()
            if (isTransactionalReceipt) {
                results.add(
                    RuleResult(
                        id = "RULE_BANKING_CONTEXT",
                        category = "FINANCIAL_RISK",
                        severity = "INFO",
                        description = "Message is an informational banking transactional receipt or balance alert.",
                        scoreContribution = 0f,
                        evidence = "Financial receipt keywords: ${financialKeywords.joinToString()}",
                        source = "SMS_FEATURE"
                    )
                )
            } else {
                results.add(
                    RuleResult(
                        id = "RULE_BANKING_CONTEXT",
                        category = "FINANCIAL_RISK",
                        severity = "HIGH",
                        description = "Message references financial operations or credential verification.",
                        scoreContribution = 20f,
                        evidence = "Financial keywords: ${financialKeywords.joinToString()}",
                        source = "SMS_FEATURE"
                    )
                )
            }
        }

        // Unknown / Alphanumeric Sender Pattern or Personal Phone sending Banking claims
        if (evidence.senderPattern == "LOCAL_PHONE" || evidence.senderPattern == "INTERNATIONAL_PHONE") {
            if (financialKeywords.isNotEmpty() || cleanText.contains("kyc") || cleanText.contains("pan")) {
                results.add(
                    RuleResult(
                        id = "RULE_SENDER_CONTENT_MISMATCH",
                        category = "SENDER_REPUTATION",
                        severity = "HIGH",
                        description = "Personal phone number used to transmit official banking or KYC demands.",
                        scoreContribution = 25f,
                        evidence = "Personal phone sender pattern: ${evidence.senderPattern}",
                        source = "SMS_FEATURE"
                    )
                )
            }
        } else if (evidence.senderPattern == "ALPHANUMERIC_CUSTOM" || evidence.senderPattern == "UNKNOWN") {
            results.add(
                RuleResult(
                    id = "RULE_UNKNOWN_SENDER",
                    category = "SENDER_REPUTATION",
                    severity = "MEDIUM",
                    description = "Sender ID exhibits unverified or spoofed alphanumeric gateway patterns.",
                    scoreContribution = 15f,
                    evidence = "Sender pattern: ${evidence.senderPattern}",
                    source = "SMS_FEATURE"
                )
            )
        }
    }


    private fun evaluateQrRules(qrPayload: String, results: MutableList<RuleResult>) {
        val trimmed = qrPayload.trim().lowercase()
        for (scheme in SUSPICIOUS_SCHEMES) {
            if (trimmed.startsWith(scheme)) {
                results.add(
                    RuleResult(
                        id = "RULE_SUSPICIOUS_QR_SCHEME",
                        category = "PAYLOAD_SAFETY",
                        severity = "CRITICAL",
                        description = "QR payload uses dangerous protocol scheme capable of immediate execution.",
                        scoreContribution = 50f,
                        evidence = "Scheme: $scheme",
                        source = "QR_FEATURE"
                    )
                )
                break
            }
        }
    }
}
