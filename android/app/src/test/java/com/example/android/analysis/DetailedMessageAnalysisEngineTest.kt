package com.example.android.analysis

import com.example.android.domain.analysis.DetailedMessageAnalysisEngine
import com.example.android.domain.analysis.DetailedMessageAnalysisEngine.DetectionVerdict
import com.example.android.domain.model.ThreatRecord
import org.junit.Assert.*
import org.junit.Test

class DetailedMessageAnalysisEngineTest {

    @Test
    fun testScamBankMessage_correctlyAnalyzed() {
        val scamThreat = ThreatRecord(
            id = "test-1",
            timestamp = 1730000000000L,
            source = "SMS",
            sender = "+919876543210",
            riskScore = 88,
            category = "HIGH",
            actionTaken = "WARN_USER",
            details = "Urgency detected; Credential harvesting attempt",
            rawContent = "Dear customer, your bank account is suspended immediately. Update your KYC now at http://bank-update.xyz or account blocked today.",
            extractedUrl = "http://bank-update.xyz",
            payloadType = "SMS"
        )

        val analysis = DetailedMessageAnalysisEngine.analyze(scamThreat, isSenderTrusted = false)

        assertEquals(DetectionVerdict.DANGEROUS, analysis.verdict)
        assertEquals("Bank Transaction / Notification", analysis.detectedCategory)
        assertFalse("Language urgency should be detected", analysis.languageUrgency.isFavorable)
        assertFalse("Link safety should be flagged", analysis.linkSafety.isFavorable)
        assertFalse("Personal info request should be flagged", analysis.personalInfoRequest.isFavorable)
        assertTrue("Why flagged explanation should mention urgency", analysis.whyFlaggedExplanation.contains("urgency", ignoreCase = true))
        assertTrue("Recommendations should contain bank-specific advice", analysis.contextualRecommendations.any { it.contains("OTP", ignoreCase = true) })
    }

    @Test
    fun testLegitimateDebitMessage_correctlyAnalyzed() {
        val safeThreat = ThreatRecord(
            id = "test-2",
            timestamp = 1730000000000L,
            source = "SMS",
            sender = "VM-HDFCBK",
            riskScore = 10,
            category = "SAFE",
            actionTaken = "ALLOW",
            details = "Verified legitimate financial notification",
            rawContent = "Your A/C XX1234 is debited for INR 450.00 on 01-Nov-26 via UPI. Ref: 4301294819. Avail Bal: INR 12,500.50. Call 18002026161 if not done by you.",
            extractedUrl = null,
            payloadType = "SMS"
        )

        val analysis = DetailedMessageAnalysisEngine.analyze(safeThreat, isSenderTrusted = false)

        assertEquals(DetectionVerdict.LOOKS_SAFE, analysis.verdict)
        assertEquals("Bank Transaction / Notification", analysis.detectedCategory)
        assertTrue("Sender format should be favorable", analysis.senderVerification.isFavorable)
        assertTrue("Language urgency should be favorable", analysis.languageUrgency.isFavorable)
        assertTrue("Link safety should be favorable", analysis.linkSafety.isFavorable)
        assertTrue("Personal info request should be favorable", analysis.personalInfoRequest.isFavorable)
        assertTrue("Financial action should be favorable", analysis.financialAction.isFavorable)
    }

    @Test
    fun testDeliveryPhishingMessage_correctlyAnalyzed() {
        val deliveryThreat = ThreatRecord(
            id = "test-3",
            timestamp = 1730000000000L,
            source = "SMS",
            sender = "+12025550199",
            riskScore = 75,
            category = "HIGH",
            actionTaken = "WARN_USER",
            details = "Delivery fee scam",
            rawContent = "Your DHL package is pending delivery. Pay redelivery fee immediately at bit.ly/dhl-track-99 to avoid return.",
            extractedUrl = "bit.ly/dhl-track-99",
            payloadType = "SMS"
        )

        val analysis = DetailedMessageAnalysisEngine.analyze(deliveryThreat, isSenderTrusted = false)

        assertEquals(DetectionVerdict.DANGEROUS, analysis.verdict)
        assertEquals("Delivery & Shipping Notice", analysis.detectedCategory)
        assertFalse("Link should be suspicious", analysis.linkSafety.isFavorable)
        assertFalse("Financial action should be flagged", analysis.financialAction.isFavorable)
        assertTrue("Recommendations should contain courier advice", analysis.contextualRecommendations.any { it.contains("package", ignoreCase = true) || it.contains("courier", ignoreCase = true) })
    }

    @Test
    fun testJobScamMessage_correctlyAnalyzed() {
        val jobThreat = ThreatRecord(
            id = "test-4",
            timestamp = 1730000000000L,
            source = "SMS",
            sender = "+919123456789",
            riskScore = 80,
            category = "HIGH",
            actionTaken = "WARN_USER",
            details = "Advance fee job offer",
            rawContent = "Exciting part time job offer! Work from home and earn daily income Rs. 3000-5000. Deposit registration fee at http://work-jobs.xyz to start immediately.",
            extractedUrl = "http://work-jobs.xyz",
            payloadType = "SMS"
        )

        val analysis = DetailedMessageAnalysisEngine.analyze(jobThreat, isSenderTrusted = false)

        assertEquals(DetectionVerdict.DANGEROUS, analysis.verdict)
        assertEquals("Job Offer / Recruitment", analysis.detectedCategory)
        assertTrue("Recommendations should contain job advice", analysis.contextualRecommendations.any { it.contains("employer", ignoreCase = true) || it.contains("registration fee", ignoreCase = true) })
    }
}

