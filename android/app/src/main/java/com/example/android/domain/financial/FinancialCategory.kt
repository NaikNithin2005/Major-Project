package com.example.android.domain.financial

/**
 * Detailed categories for financial and transactional communications.
 * Used to distinguish legitimate banking receipts, UPI confirmations,
 * and OTPs from malicious financial impersonation scams.
 */
enum class FinancialCategory(val displayName: String) {
    TRANSACTION_COMPLETED("Transaction Completed"),
    MONEY_RECEIVED("Money Received"),
    MONEY_TRANSFERRED("Money Transferred"),
    PAYMENT_FAILED("Payment Failed"),
    ACCOUNT_BALANCE_UPDATED("Account Balance Updated"),
    OTP_RECEIVED("One-Time Password (OTP)"),
    BANK_SERVICE_NOTIFICATION("Bank Service Notification"),
    POTENTIAL_BANKING_SCAM("Potential Banking Scam"),
    NON_FINANCIAL("Standard Text Message")
}

data class FinancialClassification(
    val category: FinancialCategory,
    val isLegitimateFinancial: Boolean,
    val isPotentialScam: Boolean,
    val confidence: Float,
    val reasons: List<String>,
    val extractedAmount: String? = null,
    val extractedAccountSnippet: String? = null,
    val extractedRefId: String? = null,
    val isTrustedSenderMatch: Boolean = false,
    val hasCredentialHarvestingDemand: Boolean = false,
    val hasSuspiciousLink: Boolean = false
)
