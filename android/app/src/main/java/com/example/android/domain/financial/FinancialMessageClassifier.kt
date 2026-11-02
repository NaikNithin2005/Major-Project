package com.example.android.domain.financial

import java.util.Locale

/**
 * Dedicated financial message intelligence classifier.
 * Evaluates multi-signal financial indicators (amount, account snippets, UPI refs,
 * sender patterns, credential solicitation, and link safety) to accurately separate
 * legitimate transactions and OTPs from fraudulent banking scams.
 */
class FinancialMessageClassifier {

    companion object {
        private val AMOUNT_REGEX = Regex("""(?i)(?:inr|rs\.?|₹|\$|usd)\s*([0-9,]+(?:\.[0-9]{1,2})?)""")
        private val ACCOUNT_SNIPPET_REGEX = Regex("""(?i)(?:a/c|acct|card|account)\s*(?:no\.?)?\s*(?:ending\s*)?(?:in\s*)?(?:xx|x+|\*+)?([0-9]{3,6})""")
        private val UPI_REF_REGEX = Regex("""(?i)(?:upi\s*ref|ref\s*(?:id)?|reference|rrn|txn\s*(?:id)?)[:\s#]*([a-z0-9]{6,16})""")
        private val BALANCE_REGEX = Regex("""(?i)(?:avl\s*bal|available\s*balance|bal)[:\s]*(?:inr|rs\.?|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""")

        private val KNOWN_BANK_PREFIXES = setOf(
            "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "PNB", "BOB", "CANARA", "UNIONB",
            "YESBK", "INDUS", "PAYTM", "GPAY", "PHNPE", "IDFC", "FEDBNK", "RBLBNK", "AADHAR", "UIDAI"
        )

        private val SUSPICIOUS_DOMAINS = setOf(
            ".xyz", ".top", ".cc", ".online", ".site", ".club", ".link", ".info", ".biz", ".tk", ".zip"
        )

        private val CREDENTIAL_DEMANDS = listOf(
            "share otp", "send otp", "give otp", "reply with otp", "enter pin",
            "share pin", "cvv", "share password", "send password", "provide your credentials"
        )

        private val THREAT_ULTIMATUMS = listOf(
            "suspended today", "account is locked", "account will be blocked",
            "blocked within 24 hours", "permanent block", "complete biometric verification",
            "deactivated today", "face penalty of"
        )
    }

    fun classify(
        rawBody: String,
        sender: String,
        extractedUrls: List<String> = emptyList(),
        isSenderTrusted: Boolean = false
    ): FinancialClassification {
        val lower = rawBody.lowercase(Locale.ROOT)
        val cleanSender = sender.trim().uppercase(Locale.ROOT)

        val extractedAmount = AMOUNT_REGEX.find(rawBody)?.groupValues?.getOrNull(1)
        val extractedAccount = ACCOUNT_SNIPPET_REGEX.find(rawBody)?.groupValues?.getOrNull(1)
        val extractedRefId = UPI_REF_REGEX.find(rawBody)?.groupValues?.getOrNull(1)

        val isProtectiveWarning = lower.contains("do not share") || lower.contains("don't share") ||
                lower.contains("never share") || lower.contains("never ask") || lower.contains("do not disclose")

        val hasCredentialDemand = !isProtectiveWarning && CREDENTIAL_DEMANDS.any { lower.contains(it) }
        val hasThreatUltimatum = THREAT_ULTIMATUMS.any { lower.contains(it) }

        val hasSuspiciousLink = extractedUrls.any { url ->
            val uLower = url.lowercase(Locale.ROOT)
            SUSPICIOUS_DOMAINS.any { uLower.contains(it) } || uLower.contains("bit.ly") || uLower.contains("tinyurl")
        }

        val isSenderAlphanumericShortcode = cleanSender.matches(Regex("^[A-Z0-9]{2,3}-[A-Z0-9]{4,8}$")) ||
                cleanSender.matches(Regex("^[A-Z0-9]{4,8}$"))
        val isSenderPersonalPhone = cleanSender.matches(Regex("""^\+?[0-9]{10,14}$"""))
        val matchesKnownBankSender = KNOWN_BANK_PREFIXES.any { cleanSender.contains(it) }

        val reasons = mutableListOf<String>()

        // 1. Detect Explicit Malicious Banking Impersonation Scams
        if (hasCredentialDemand) {
            reasons.add("Message explicitly demands sensitive security credentials (OTP/PIN/CVV/Password).")
            return FinancialClassification(
                category = FinancialCategory.POTENTIAL_BANKING_SCAM,
                isLegitimateFinancial = false,
                isPotentialScam = true,
                confidence = 0.98f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted,
                hasCredentialHarvestingDemand = true,
                hasSuspiciousLink = hasSuspiciousLink
            )
        }

        if (hasThreatUltimatum && extractedUrls.isNotEmpty()) {
            reasons.add("Message creates false urgency with account suspension threats and includes an unverified external link.")
            if (isSenderPersonalPhone) {
                reasons.add("Sent from a personal mobile telephone number claiming to represent a banking institution.")
            }
            return FinancialClassification(
                category = FinancialCategory.POTENTIAL_BANKING_SCAM,
                isLegitimateFinancial = false,
                isPotentialScam = true,
                confidence = 0.95f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = hasSuspiciousLink
            )
        }

        if (hasSuspiciousLink && (lower.contains("bank") || lower.contains("kyc") || lower.contains("pan") || lower.contains("refund"))) {
            reasons.add("Message references financial operations with high-risk suspicious website domain link.")
            return FinancialClassification(
                category = FinancialCategory.POTENTIAL_BANKING_SCAM,
                isLegitimateFinancial = false,
                isPotentialScam = true,
                confidence = 0.92f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = true
            )
        }

        // 2. Legitimate OTP & Two-Factor Verification Messages
        val isOtpPattern = (lower.contains("otp") || lower.contains("one time password") || lower.contains("verification code")) &&
                (lower.contains("valid for") || lower.contains("do not share") || lower.contains("do not disclose") || lower.contains("secret code"))
        if (isOtpPattern) {
            reasons.add("Standard one-time password (OTP) authorization code.")
            if (lower.contains("do not share") || lower.contains("never calls asking")) {
                reasons.add("Includes standard official security advisory cautioning against sharing codes.")
            }
            return FinancialClassification(
                category = FinancialCategory.OTP_RECEIVED,
                isLegitimateFinancial = true,
                isPotentialScam = false,
                confidence = 0.95f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted || matchesKnownBankSender,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = false
            )
        }

        // 3. Legitimate Banking Debit Alerts
        val isDebitPattern = (lower.contains("debited") || lower.contains("withdrawn") || lower.contains("sent rs") || lower.contains("paid rs")) &&
                (extractedAmount != null || extractedAccount != null || extractedRefId != null || lower.contains("avl bal"))
        if (isDebitPattern && extractedUrls.isEmpty()) {
            reasons.add("Standard informational banking debit notification.")
            if (extractedAmount != null) reasons.add("Transacted amount: INR $extractedAmount")
            if (extractedRefId != null) reasons.add("Transaction reference: $extractedRefId")
            return FinancialClassification(
                category = FinancialCategory.MONEY_TRANSFERRED,
                isLegitimateFinancial = true,
                isPotentialScam = false,
                confidence = 0.94f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted || matchesKnownBankSender,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = false
            )
        }

        // 4. Legitimate Banking Credit Alerts / Money Received
        val isCreditPattern = (lower.contains("credited") || lower.contains("received rs") || lower.contains("deposited")) &&
                (extractedAmount != null || extractedAccount != null || lower.contains("avl bal"))
        if (isCreditPattern && extractedUrls.isEmpty()) {
            reasons.add("Standard informational banking credit receipt.")
            if (extractedAmount != null) reasons.add("Credited amount: INR $extractedAmount")
            return FinancialClassification(
                category = FinancialCategory.MONEY_RECEIVED,
                isLegitimateFinancial = true,
                isPotentialScam = false,
                confidence = 0.94f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted || matchesKnownBankSender,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = false
            )
        }

        // 5. Account Balance Updates
        val isBalancePattern = lower.contains("avl bal") || lower.contains("available balance") || lower.contains("statement for a/c")
        if (isBalancePattern && extractedUrls.isEmpty()) {
            reasons.add("Periodic banking balance statement notification.")
            return FinancialClassification(
                category = FinancialCategory.ACCOUNT_BALANCE_UPDATED,
                isLegitimateFinancial = true,
                isPotentialScam = false,
                confidence = 0.90f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted || matchesKnownBankSender,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = false
            )
        }

        // 6. Generic Bank Service Notifications
        if ((lower.contains("bank") || lower.contains("credit card") || lower.contains("loan payment") || lower.contains("due date")) &&
            (matchesKnownBankSender || isSenderAlphanumericShortcode) && extractedUrls.isEmpty()) {
            reasons.add("Informational service update from verified sender header.")
            return FinancialClassification(
                category = FinancialCategory.BANK_SERVICE_NOTIFICATION,
                isLegitimateFinancial = true,
                isPotentialScam = false,
                confidence = 0.88f,
                reasons = reasons,
                extractedAmount = extractedAmount,
                extractedAccountSnippet = extractedAccount,
                extractedRefId = extractedRefId,
                isTrustedSenderMatch = isSenderTrusted || matchesKnownBankSender,
                hasCredentialHarvestingDemand = false,
                hasSuspiciousLink = false
            )
        }

        // 7. Non-Financial Messages
        return FinancialClassification(
            category = FinancialCategory.NON_FINANCIAL,
            isLegitimateFinancial = false,
            isPotentialScam = false,
            confidence = 0.80f,
            reasons = listOf("Non-transactional communication."),
            extractedAmount = extractedAmount,
            extractedAccountSnippet = extractedAccount,
            extractedRefId = extractedRefId,
            isTrustedSenderMatch = isSenderTrusted,
            hasCredentialHarvestingDemand = false,
            hasSuspiciousLink = hasSuspiciousLink
        )
    }
}
