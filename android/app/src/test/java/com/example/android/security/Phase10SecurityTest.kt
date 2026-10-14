package com.example.android.security

import com.example.android.data.parser.SmsParser
import com.example.android.data.parser.UrlFeatureExtractor
import com.example.android.data.preprocessor.SmsPreprocessor
import com.example.android.domain.model.RawSms
import com.example.android.domain.riskengine.RiskEngine
import com.example.android.domain.riskengine.ThreatCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Phase 10 — Security Hardening & Robustness Unit Tests for Android.
 * Validates resilience against malformed inputs, dangerous QR payloads,
 * extreme boundary conditions, and ensures data minimization and score boundaries.
 */
class Phase10SecurityTest {

    private lateinit var smsParser: SmsParser
    private lateinit var smsPreprocessor: SmsPreprocessor
    private lateinit var riskEngine: RiskEngine

    @Before
    fun setUp() {
        smsParser = SmsParser()
        smsPreprocessor = SmsPreprocessor()
        riskEngine = RiskEngine()
    }

    @Test
    fun testMalformedUrlInputsDoNotCrashExtractor() {
        val malformedInputs = listOf(
            "",
            "   ",
            "://missing-scheme.com",
            "http://",
            "https://",
            "http://[::1]:999999999/path", // Invalid IPv6 & port out of range
            "javascript:alert('xss')",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "file:///data/user/0/com.example.android/databases/app.db",
            "content://telephony/siminfo",
            "http://evil.com:invalid_port/path",
            "https://\uD83D\uDCA9.com/test", // Emoji domain
            "https://attacker.com/path?query=" + "A".repeat(5000) // Oversized query
        )

        for (input in malformedInputs) {
            val features = UrlFeatureExtractor.extractFeatures(input)
            assertNotNull("Extractor must return non-null features for: $input", features)
            assertNotNull(features.hostname)
            assertNotNull(features.domain)
        }
    }

    @Test
    fun testDangerousSchemesAreClassifiedSuspiciousOrNonWeb() {
        val dangerousSchemes = listOf(
            "javascript:void(0)",
            "file:///system/bin/sh",
            "intent:#Intent;action=android.intent.action.VIEW;end"
        )

        for (schemeUrl in dangerousSchemes) {
            val features = UrlFeatureExtractor.extractFeatures(schemeUrl)
            // Should not falsely claim HTTPS
            assertFalse(features.isHttps)
        }
    }

    @Test
    fun testExtremeSmsInputsDoNotCrashPipeline() {
        val extremeMessages = listOf(
            RawSms(messageId = "1", sender = "", body = "", timestamp = 0L),
            RawSms(messageId = "2", sender = "UNKNOWN", body = "   \n\t   ", timestamp = System.currentTimeMillis()),
            RawSms(messageId = "3", sender = "SPOOFED_ALPHANUMERIC_LONG_SENDER_NAME_123456789", body = "Normal text", timestamp = 1L),
            RawSms(messageId = "4", sender = "+1234567890", body = "X".repeat(20000), timestamp = System.currentTimeMillis()),
            RawSms(messageId = "5", sender = "BANK", body = "Check: https://bit.ly/3xYZ and http://suspicious.site", timestamp = 100L)
        )

        for (rawSms in extremeMessages) {
            val parsed = smsParser.parse(rawSms)
            assertNotNull(parsed)
            assertTrue("Safe sender must never be blank", parsed.safeSender.isNotBlank())

            val normalized = smsPreprocessor.normalize(parsed.safeBody)
            assertNotNull(normalized)
        }
    }

    @Test
    fun testRiskScoresStrictlyBoundedBetweenZeroAndOneHundred() {
        val testCases = listOf(
            Triple(0f, 0f, 0f),       // All zero
            Triple(100f, 100f, 100f), // All 100
            Triple(9999f, 9999f, 9999f), // Out of range positive
            Triple(-500f, -100f, -50f),  // Negative values
            Triple(75f, null, null),
            Triple(null, 85f, null)
        )

        for ((smsScore, urlScore, anomalyScore) in testCases) {
            val assessment = riskEngine.assess(
                smsScore = smsScore,
                urlScore = urlScore,
                anomalyScore = anomalyScore
            )
            assertTrue("Risk score ${assessment.riskScore} must be >= 0", assessment.riskScore >= 0)
            assertTrue("Risk score ${assessment.riskScore} must be <= 100", assessment.riskScore <= 100)
            assertNotNull(assessment.category)
            assertNotNull(assessment.recommendedAction)
        }
    }

    @Test
    fun testCategoryBoundaryMapping() {
        // Direct score-to-category invariant checks
        assertEquals(ThreatCategory.SAFE, ThreatCategory.fromScore(0))
        assertEquals(ThreatCategory.SAFE, ThreatCategory.fromScore(25))
        assertEquals(ThreatCategory.LOW, ThreatCategory.fromScore(26))
        assertEquals(ThreatCategory.LOW, ThreatCategory.fromScore(50))
        assertEquals(ThreatCategory.MEDIUM, ThreatCategory.fromScore(51))
        assertEquals(ThreatCategory.MEDIUM, ThreatCategory.fromScore(75))
        assertEquals(ThreatCategory.HIGH, ThreatCategory.fromScore(76))
        assertEquals(ThreatCategory.HIGH, ThreatCategory.fromScore(100))
    }

    @Test
    fun testModelFailureFallbackDoesNotFalselyClassifyAsSafe() {
        val testUrl = "http://192.168.1.1/admin/auth"
        val features = UrlFeatureExtractor.extractFeatures(testUrl)
        val assessment = riskEngine.assess(
            urlScore = null,
            anomalyScore = null,
            urlFeatures = features,
            qrPayload = testUrl,
            unavailableModels = listOf("xgboost-url-v2")
        )

        // Rule heuristics must detect unencrypted HTTP IP host threat even if ML model is unavailable
        assertTrue("Fallback must detect IP address threat: ${assessment.riskScore}", assessment.riskScore >= 25)
        assertNotEquals(ThreatCategory.SAFE, assessment.category)
        assertTrue(assessment.ruleResults.any { it.id == "RULE_IP_HOSTNAME" })
    }
}
