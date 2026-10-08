package com.example.android.domain.context

import com.example.android.domain.model.ProcessedSms
import com.example.android.domain.model.UrlFeatures

/**
 * Context Correlation Engine performing cross-verification between SMS, Sender, URL, and QR.
 * Produces deterministic, explainable context scores (0 - 100).
 */
class ContextCorrelationEngine {

    companion object {
        val TRUSTED_BRAND_DOMAINS: Map<String, List<String>> = mapOf(
            "sbi" to listOf("sbi.co.in", "onlinesbi.sbi", "onlinesbi.com", "statebankofindia.com"),
            "hdfc" to listOf("hdfcbank.com", "hdfc.com"),
            "icici" to listOf("icicibank.com"),
            "axis" to listOf("axisbank.com"),
            "pnb" to listOf("pnbindia.in"),
            "paypal" to listOf("paypal.com", "paypal.me"),
            "amazon" to listOf("amazon.in", "amazon.com", "amzn.to"),
            "netflix" to listOf("netflix.com"),
            "apple" to listOf("apple.com", "icloud.com"),
            "google" to listOf("google.com", "google.co.in", "gmail.com"),
            "paytm" to listOf("paytm.com"),
            "phonepe" to listOf("phonepe.com")
        )

        private val FINANCIAL_TERMS = setOf(
            "bank", "kyc", "account", "otp", "debit", "credit", "card", "pan", "cvv",
            "pin", "netbanking", "beneficiary", "funds", "transaction", "suspended", "blocked"
        )
    }

    fun correlate(
        processedSms: ProcessedSms? = null,
        urlFeatures: UrlFeatures? = null,
        qrPayload: String? = null
    ): Pair<Float, List<ContextFinding>> {
        val findings = mutableListOf<ContextFinding>()
        var inconsistencyPoints = 0f

        val domain = urlFeatures?.domain?.lowercase() ?: urlFeatures?.hostname?.lowercase() ?: ""
        val hostname = urlFeatures?.hostname?.lowercase() ?: ""

        // 1. Check Brand vs Domain Mismatch
        val brands = mutableListOf<String>()
        if (processedSms != null) {
            brands.addAll(processedSms.evidence.brandEntities.map { it.lowercase() })
            // Scan raw sender or normalized text for trusted brand keys
            val combined = "${processedSms.raw.sender} ${processedSms.normalizedText}".lowercase()
            for (key in TRUSTED_BRAND_DOMAINS.keys) {
                if (combined.contains(key) && !brands.contains(key)) {
                    brands.add(key)
                }
            }
        }

        if (brands.isNotEmpty() && hostname.isNotBlank()) {
            for (brand in brands) {
                val allowedDomains = TRUSTED_BRAND_DOMAINS[brand] ?: emptyList()
                val isMatch = allowedDomains.any { allowed ->
                    domain == allowed || domain.endsWith(".$allowed") ||
                    hostname == allowed || hostname.endsWith(".$allowed")
                }

                if (!isMatch) {
                    findings.add(
                        ContextFinding(
                            checkName = "BRAND_DOMAIN_MISMATCH",
                            matched = true,
                            severity = "CRITICAL",
                            description = "Message references brand '${brand.uppercase()}', but target domain '$domain' is unauthorized.",
                            evidence = "Claimed: ${brand.uppercase()} | Target: $domain",
                            source = "CONTEXT"
                        )
                    )
                    inconsistencyPoints += 75f
                    break
                } else {
                    findings.add(
                        ContextFinding(
                            checkName = "BRAND_DOMAIN_MATCH",
                            matched = true,
                            severity = "INFO",
                            description = "Target domain corresponds to verified infrastructure for '${brand.uppercase()}'.",
                            evidence = "Verified official domain: $hostname",
                            source = "CONTEXT"
                        )
                    )
                }
            }
        }

        // 2. Financial Context vs Generic/Unverified Host
        if (processedSms != null && urlFeatures != null && brands.isEmpty()) {
            val hasFinancialContext = processedSms.normalizedText.split(" ", "\n").any { it in FINANCIAL_TERMS }
            if (hasFinancialContext) {
                findings.add(
                    ContextFinding(
                        checkName = "BANKING_CONTEXT_MISMATCH",
                        matched = true,
                        severity = "HIGH",
                        description = "Message references sensitive banking operations, pointing to an unverified domain.",
                        evidence = "Financial terms directed to $hostname",
                        source = "CONTEXT"
                    )
                )
                inconsistencyPoints += 50f
            }
        }

        // 3. SMS + QR Cross-Channel Correlation
        if (processedSms != null && !qrPayload.isNullOrBlank()) {
            val instructsScan = listOf("scan qr", "scan code", "qr code", "attached qr").any {
                processedSms.normalizedText.contains(it)
            }
            if (instructsScan && (inconsistencyPoints > 0 || (urlFeatures != null && !urlFeatures.isHttps))) {
                findings.add(
                    ContextFinding(
                        checkName = "SMS_QR_CROSS_CHANNEL_ANOMALY",
                        matched = true,
                        severity = "HIGH",
                        description = "SMS prompt instructing QR scan leads to an unverified external destination.",
                        evidence = "Cross-channel link with unverified payload",
                        source = "CONTEXT"
                    )
                )
                inconsistencyPoints += 25f
            }
        }

        // 4. Personal Mobile Number claiming Institutional Authority
        if (processedSms != null && (brands.isNotEmpty() || inconsistencyPoints > 0)) {
            val sender = processedSms.raw.sender.trim()
            if (sender.startsWith("+") && sender.length >= 10) {
                findings.add(
                    ContextFinding(
                        checkName = "PRIVATE_NUMBER_ORGANIZATIONAL_SENDER",
                        matched = true,
                        severity = "MEDIUM",
                        description = "Personal telephone number used to transmit official alerts.",
                        evidence = "Sender format: $sender",
                        source = "CONTEXT"
                    )
                )
                inconsistencyPoints += 25f
            }
        }

        // Normalize context score: 0, 25, 50, 75, 100
        val contextScore = when {
            inconsistencyPoints >= 100f -> 100f
            inconsistencyPoints >= 75f -> 75f
            inconsistencyPoints >= 50f -> 50f
            inconsistencyPoints >= 25f -> 25f
            else -> 0f
        }

        return Pair(contextScore, findings)
    }
}
