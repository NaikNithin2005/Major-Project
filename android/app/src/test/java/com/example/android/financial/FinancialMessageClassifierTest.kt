package com.example.android.financial

import com.example.android.domain.financial.FinancialCategory
import com.example.android.domain.financial.FinancialMessageClassifier
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FinancialMessageClassifierTest {

    private lateinit var classifier: FinancialMessageClassifier

    @Before
    fun setUp() {
        classifier = FinancialMessageClassifier()
    }

    @Test
    fun testLegitimateDebitAlert_classifiedAsLegitimate() {
        val body = "Dear Customer, your ICICI Bank A/c XX3910 has been debited by INR 1,200.00 on 05-Feb-24 at AMAZON. UPI Ref: 730855031651. Avl Bal: INR 80,525.70. Call 18002586161 if not done by you."
        val result = classifier.classify(body, "AD-ICICI")

        assertTrue(result.isLegitimateFinancial)
        assertFalse(result.isPotentialScam)
        assertEquals(FinancialCategory.MONEY_TRANSFERRED, result.category)
        assertEquals("1,200.00", result.extractedAmount)
        assertEquals("3910", result.extractedAccountSnippet)
        assertEquals("730855031651", result.extractedRefId)
    }

    @Test
    fun testLegitimateCreditAlert_classifiedAsLegitimate() {
        val body = "Dear Customer, INR 25,000.00 credited to your A/c XX9921 on 01-Nov-26 by SALARY. Avl Bal: INR 35,420.00."
        val result = classifier.classify(body, "VK-HDFCBK")

        assertTrue(result.isLegitimateFinancial)
        assertFalse(result.isPotentialScam)
        assertEquals(FinancialCategory.MONEY_RECEIVED, result.category)
        assertEquals("25,000.00", result.extractedAmount)
        assertEquals("9921", result.extractedAccountSnippet)
    }

    @Test
    fun testLegitimateOtpMessage_classifiedAsLegitimate() {
        val body = "810526 is your One Time Password (OTP) for transaction of INR 450.00 at Amazon with card ending 4912. Valid for 10 mins. Do not share OTP with anyone."
        val result = classifier.classify(body, "BW-SBIINB")

        assertTrue(result.isLegitimateFinancial)
        assertFalse(result.isPotentialScam)
        assertEquals(FinancialCategory.OTP_RECEIVED, result.category)
    }

    @Test
    fun testBalanceAlert_classifiedAsLegitimate() {
        val body = "Dear Customer, your available balance for A/c ending 1234 is INR 42,100.50. Statement for A/c generated."
        val result = classifier.classify(body, "AX-AXISBK")

        assertTrue(result.isLegitimateFinancial)
        assertFalse(result.isPotentialScam)
        assertEquals(FinancialCategory.ACCOUNT_BALANCE_UPDATED, result.category)
    }

    @Test
    fun testBankServiceNotification_classifiedAsLegitimate() {
        val body = "Dear Customer, your Credit Card statement payment of INR 3,500 is due on 15-Nov. Avoid late charges."
        val result = classifier.classify(body, "AD-KOTAKB")

        assertTrue(result.isLegitimateFinancial)
        assertFalse(result.isPotentialScam)
        assertEquals(FinancialCategory.BANK_SERVICE_NOTIFICATION, result.category)
    }

    @Test
    fun testFakeBankingImpersonationScam_flaggedAsScam() {
        val body = "Your Bank of Baroda YONO account is locked. Complete biometric verification within 24 hours at http://hdfc-netbanking-verify.top/verify or all transactions will be restricted."
        val urls = listOf("http://hdfc-netbanking-verify.top/verify")
        val result = classifier.classify(body, "+919876543210", urls)

        assertFalse(result.isLegitimateFinancial)
        assertTrue(result.isPotentialScam)
        assertEquals(FinancialCategory.POTENTIAL_BANKING_SCAM, result.category)
        assertTrue(result.hasSuspiciousLink)
    }

    @Test
    fun testCredentialHarvestingDemand_flaggedAsScam() {
        val body = "Dear Customer, unauthorized transaction detected. Share OTP with our agent to cancel the payment immediately."
        val result = classifier.classify(body, "+919876543210")

        assertFalse(result.isLegitimateFinancial)
        assertTrue(result.isPotentialScam)
        assertTrue(result.hasCredentialHarvestingDemand)
        assertEquals(FinancialCategory.POTENTIAL_BANKING_SCAM, result.category)
    }

    @Test
    fun testTrustedSenderWithPhishingLink_preservesPhishingWarning() {
        val body = "Your account is suspended. Verify KYC at http://onlinesbi-kyc-update.xyz/login"
        val urls = listOf("http://onlinesbi-kyc-update.xyz/login")
        val result = classifier.classify(body, "AD-HDFCBK", urls, isSenderTrusted = true)

        assertTrue(result.isPotentialScam)
        assertTrue(result.isTrustedSenderMatch)
        assertTrue(result.hasSuspiciousLink)
    }
}

