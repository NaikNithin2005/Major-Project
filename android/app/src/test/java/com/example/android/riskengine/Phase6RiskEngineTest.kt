package com.example.android.riskengine

import com.example.android.domain.context.ContextCorrelationEngine
import com.example.android.domain.model.ProcessedSms
import com.example.android.domain.model.RawSms
import com.example.android.domain.model.SmsFeatureEvidence
import com.example.android.domain.model.UrlFeatures
import com.example.android.domain.report.ThreatReport
import com.example.android.domain.riskengine.RecommendedAction
import com.example.android.domain.riskengine.RiskEngine
import com.example.android.domain.riskengine.ThreatCategory
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.xai.ExplainableAiEngine
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class Phase6RiskEngineTest {

    private lateinit var riskEngine: RiskEngine
    private lateinit var ruleEngine: RuleEngine
    private lateinit var contextEngine: ContextCorrelationEngine
    private lateinit var xaiEngine: ExplainableAiEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
        contextEngine = ContextCorrelationEngine()
        xaiEngine = ExplainableAiEngine()
        riskEngine = RiskEngine(ruleEngine, contextEngine, xaiEngine)
    }

    // =========================================================================
    // 1. RISK SCORING & CATEGORY TESTS
    // =========================================================================

    @Test
    fun testRiskEngineSafeCategory() {
        val assessment = riskEngine.assess(
            smsScore = 5f,
            urlScore = 2f,
            qrScore = 0f,
            contextScore = 0f,
            anomalyScore = 0f
        )
        assertEquals(ThreatCategory.SAFE, assessment.category)
        assertTrue(assessment.riskScore in 0..25)
        assertEquals(RecommendedAction.ALLOW, assessment.recommendedAction)
    }

    @Test
    fun testRiskEngineLowCategory() {
        val assessment = riskEngine.assess(
            smsScore = 35f,
            urlScore = 30f,
            qrScore = 20f,
            contextScore = 25f,
            anomalyScore = 20f
        )
        assertEquals(ThreatCategory.LOW, assessment.category)
        assertTrue(assessment.riskScore in 26..50)
        assertEquals(RecommendedAction.ALLOW_WITH_CAUTION, assessment.recommendedAction)
    }

    @Test
    fun testRiskEngineMediumCategory() {
        val assessment = riskEngine.assess(
            smsScore = 65f,
            urlScore = 60f,
            qrScore = 40f,
            contextScore = 50f,
            anomalyScore = 50f
        )
        assertEquals(ThreatCategory.MEDIUM, assessment.category)
        assertTrue(assessment.riskScore in 51..75)
        assertEquals(RecommendedAction.WARN_USER, assessment.recommendedAction)
    }

    @Test
    fun testRiskEngineHighCategory() {
        val assessment = riskEngine.assess(
            smsScore = 95f,
            urlScore = 98f,
            qrScore = 70f,
            contextScore = 75f,
            anomalyScore = 80f
        )
        assertEquals(ThreatCategory.HIGH, assessment.category)
        assertTrue(assessment.riskScore in 76..100)
        assertEquals(RecommendedAction.BLOCK_OR_STRONGLY_WARN, assessment.recommendedAction)
    }

    @Test
    fun testExactCategoryBoundaries() {
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
    fun testScoreClamping() {
        val minAssessment = riskEngine.assess(smsScore = -20f, urlScore = -10f)
        assertEquals(0, minAssessment.riskScore)

        val maxAssessment = riskEngine.assess(smsScore = 150f, urlScore = 120f, qrScore = 100f, contextScore = 100f, anomalyScore = 100f)
        assertEquals(100, maxAssessment.riskScore)
    }

    @Test
    fun testWeightedScoreBreakdownCalculation() {
        // sms=80 (contrib 28), url=90 (contrib 27), qr=60 (contrib 9), ctx=70 (contrib 7), anom=50 (contrib 5)
        // Total expected = 28 + 27 + 9 + 7 + 5 = 76
        val assessment = riskEngine.assess(
            smsScore = 80f,
            urlScore = 90f,
            qrScore = 60f,
            contextScore = 70f,
            anomalyScore = 50f
        )
        val bd = assessment.scoreBreakdown
        assertEquals(28.0f, bd.smsContribution, 0.5f)
        assertEquals(27.0f, bd.urlContribution, 0.5f)
        assertEquals(9.0f, bd.qrContribution, 0.5f)
        assertEquals(7.0f, bd.contextContribution, 0.5f)
        assertEquals(5.0f, bd.anomalyContribution, 0.5f)
        assertEquals(76, assessment.riskScore)
    }

    // =========================================================================
    // 2. CONTEXT ENGINE TESTS
    // =========================================================================

    @Test
    fun testContextCorrelationBrandDomainMismatch() {
        val dummySms = ProcessedSms(
            raw = RawSms("1", "SBI-ALERT", "Dear Customer, update your SBI KYC immediately.", System.currentTimeMillis()),
            normalizedText = "dear customer, update your sbi kyc immediately.",
            tokens = listOf("sbi", "kyc"),
            evidence = SmsFeatureEvidence(brandEntities = listOf("SBI"), detectedKeywords = listOf("kyc"), urgencyIndicators = listOf("immediately"))
        )
        val dummyUrl = createDummyUrlFeatures(url = "http://sbi-verification.xyz/login", domain = "sbi-verification.xyz", hostname = "sbi-verification.xyz", isHttps = false)

        val (score, findings) = contextEngine.correlate(
            processedSms = dummySms,
            urlFeatures = dummyUrl
        )

        assertTrue(score >= 75f)
        assertTrue(findings.any { it.checkName == "BRAND_DOMAIN_MISMATCH" })
    }

    @Test
    fun testContextCorrelationBrandDomainMatch() {
        val dummySms = ProcessedSms(
            raw = RawSms("1", "SBI-ALERT", "Your OTP is 123456.", System.currentTimeMillis()),
            normalizedText = "your otp is 123456.",
            tokens = listOf("otp"),
            evidence = SmsFeatureEvidence(brandEntities = listOf("SBI"))
        )
        val dummyUrl = createDummyUrlFeatures(url = "https://onlinesbi.sbi/portal", domain = "onlinesbi.sbi", hostname = "onlinesbi.sbi", isHttps = true)

        val (score, findings) = contextEngine.correlate(
            processedSms = dummySms,
            urlFeatures = dummyUrl
        )

        assertEquals(0f, score, 0.01f)
        assertTrue(findings.any { it.checkName == "BRAND_DOMAIN_MATCH" })
    }

    // =========================================================================
    // 3. RULE ENGINE TESTS
    // =========================================================================

    @Test
    fun testRuleEngineDetectsMissingHttpsAndIpHostname() {
        val features = createDummyUrlFeatures(
            url = "http://192.168.1.1:8080/admin",
            domain = "192.168.1.1",
            hostname = "192.168.1.1",
            isHttps = false,
            isIpHostname = true,
            hasUnusualPort = true
        )
        val results = ruleEngine.evaluate(urlFeatures = features)
        val ids = results.map { it.id }

        assertTrue(ids.contains("RULE_MISSING_HTTPS"))
        assertTrue(ids.contains("RULE_IP_HOSTNAME"))
        assertTrue(ids.contains("RULE_UNUSUAL_PORT"))
    }

    @Test
    fun testRuleEngineDetectsPunycodeAndSuspiciousTld() {
        val features = createDummyUrlFeatures(
            url = "https://xn--pple-43d.xyz/auth",
            domain = "xn--pple-43d.xyz",
            hostname = "xn--pple-43d.xyz",
            isHttps = true,
            isPunycode = true
        )
        val results = ruleEngine.evaluate(urlFeatures = features)
        val ids = results.map { it.id }

        assertTrue(ids.contains("RULE_PUNYCODE_HOMOGRAPH"))
        assertTrue(ids.contains("RULE_SUSPICIOUS_TLD"))
    }

    // =========================================================================
    // 4. EXPLAINABLE AI (XAI) TESTS
    // =========================================================================

    @Test
    fun testXaiExplanationsMapToActualEvidence() {
        val reasons = xaiEngine.generateExplanations(
            urlProbability = 0.95f,
            smsProbability = 0.88f,
            anomalyScore = 0.82f
        )
        assertTrue(reasons.isNotEmpty())
        for (r in reasons) {
            assertTrue(r.source in listOf("MODEL", "ANOMALY", "RULE", "CONTEXT", "URL_FEATURE", "SMS_FEATURE", "QR_FEATURE"))
            assertTrue(r.title.isNotBlank())
        }
    }

    // =========================================================================
    // 5. PRIVACY & THREAT REPORT TESTS
    // =========================================================================

    @Test
    fun testThreatReportPreservesPrivacy() {
        val assessment = riskEngine.assess(
            smsScore = 90f,
            urlScore = 95f
        )
        val report = ThreatReport.fromAssessment(assessment, detectionType = "SMS", destinationUrl = "http://fake.xyz")
        assertNotNull(report.privacyNotice)
        assertEquals(assessment.riskScore, report.riskScore)
        assertEquals(assessment.category, report.category)
    }

    private fun createDummyUrlFeatures(
        url: String,
        domain: String,
        hostname: String,
        isHttps: Boolean,
        isIpHostname: Boolean = false,
        hasUnusualPort: Boolean = false,
        isPunycode: Boolean = false
    ): UrlFeatures {
        return UrlFeatures(
            url = url,
            scheme = if (isHttps) "https" else "http",
            domain = domain,
            hostname = hostname,
            path = "/",
            query = null,
            isHttps = isHttps,
            urlLength = url.length,
            hostnameLength = hostname.length,
            pathLength = 1,
            hasQuery = false,
            isIpHostname = isIpHostname,
            subdomainCount = 0,
            hasSuspiciousSymbols = false,
            hasUnusualPort = hasUnusualPort,
            isPunycode = isPunycode,
            suspiciousCharacteristics = emptyList()
        )
    }
}
