package com.example.android.securitycheck

import com.example.android.domain.classifier.DefaultUrlClassifier
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.UserFeedback
import com.example.android.domain.repository.FeedbackRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.rules.RuleEngine
import com.example.android.domain.xai.ExplainableAiEngine
import com.example.android.presentation.securitycheck.SecurityCheckUiState
import com.example.android.presentation.securitycheck.SecurityCheckViewModel
import com.example.android.presentation.securitycheck.ThreatCategoryType
import com.example.android.presentation.securitycheck.UrlClassification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityCheckPipelineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeThreatRepository: FakeThreatHistoryRepository
    private lateinit var fakeFeedbackRepository: FakeFeedbackRepository
    private lateinit var viewModel: SecurityCheckViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeThreatRepository = FakeThreatHistoryRepository()
        fakeFeedbackRepository = FakeFeedbackRepository()
        viewModel = SecurityCheckViewModel(
            urlClassifier = DefaultUrlClassifier(),
            ruleEngine = RuleEngine(),
            explainableAiEngine = ExplainableAiEngine(),
            threatHistoryRepository = fakeThreatRepository,
            feedbackRepository = fakeFeedbackRepository,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSafeBenignUrlEvaluation() = runTest {
        viewModel.analyzeUrl("https://www.google.com/search?q=cybersecurity", source = "INCOMING_URL")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SecurityCheckUiState.Success)
        val success = state as SecurityCheckUiState.Success
        val result = success.result

        assertEquals("google.com", result.destinationDomain)
        assertEquals(UrlClassification.SAFE_INDICATORS_FOUND, result.classification)
        assertTrue(result.riskScore < 35)
        assertFalse(result.isSsrfRisk)
        assertTrue(result.recommendedActions.isNotEmpty())
        assertEquals(1, fakeThreatRepository.savedThreats.size)
    }

    @Test
    fun testPhishingLookalikeIpUrlEvaluation() = runTest {
        viewModel.analyzeUrl("http://192.168.1.100/chase-bank-login?auth=token", source = "DEEP_LINK")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SecurityCheckUiState.Success)
        val success = state as SecurityCheckUiState.Success
        val result = success.result

        assertTrue("Expected score >= 70 but was ${result.riskScore}", result.riskScore >= 70)
        assertEquals(UrlClassification.MALICIOUS_INDICATORS_DETECTED, result.classification)
        assertTrue(result.isSsrfRisk) // 192.168.x.x is SSRF / private IP
        assertTrue(result.recommendedActions.any { it.contains("Do NOT open") || it.contains("passwords") })
        assertEquals(1, fakeThreatRepository.savedThreats.size)
        assertEquals("QUARANTINED", fakeThreatRepository.savedThreats[0].actionTaken)
    }

    @Test
    fun testSsrfLoopbackRejectionAndQuarantine() = runTest {
        viewModel.analyzeUrl("http://127.0.0.1:8080/admin/sensitive-data", source = "APP_LINK")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SecurityCheckUiState.Success)
        val success = state as SecurityCheckUiState.Success
        val result = success.result

        assertTrue(result.isSsrfRisk)
        assertEquals(95, result.riskScore)
        assertEquals(UrlClassification.MALICIOUS_INDICATORS_DETECTED, result.classification)
        assertNotNull(result.ssrfReason)
        assertTrue(result.ssrfReason!!.contains("loopback"))
    }

    @Test
    fun testDangerousProtocolSchemeBlocked() = runTest {
        viewModel.analyzeUrl("javascript:alert(document.domain)", source = "INCOMING_URL")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SecurityCheckUiState.Success)
        val success = state as SecurityCheckUiState.Success
        val result = success.result

        // Dangerous protocol should be flagged as unknown/malicious profile and warned
        assertFalse(result.classification == UrlClassification.SAFE_INDICATORS_FOUND)
        assertTrue(result.riskIndicators.any { it.contains("Dangerous or unsupported protocol scheme") })
    }

    @Test
    fun testQuishingAttackCategorization() = runTest {
        viewModel.analyzeUrl("https://verify-account-update.top/login", source = "QR_CODE")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SecurityCheckUiState.Success)
        val success = state as SecurityCheckUiState.Success
        val result = success.result

        assertEquals(ThreatCategoryType.QUISHING_ATTACK, result.threatCategory)
    }

    @Test
    fun testBlankInputReturnsError() = runTest {
        viewModel.analyzeUrl("   ", source = "INCOMING_URL")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SecurityCheckUiState.Error)
    }

    @Test
    fun testUserFeedbackSubmission() = runTest {
        viewModel.analyzeUrl("https://www.google.com", source = "INCOMING_URL")
        advanceUntilIdle()

        val state = viewModel.uiState.value as SecurityCheckUiState.Success
        viewModel.submitFeedback(state.threatId, "FALSE_POSITIVE", "This is an official site.")
        advanceUntilIdle()

        assertEquals(1, fakeFeedbackRepository.savedFeedback.size)
        assertEquals("FALSE_POSITIVE", fakeFeedbackRepository.savedFeedback[0].userCategory)
        assertEquals("This is an official site.", fakeFeedbackRepository.savedFeedback[0].comment)
    }

    // Fake Repositories
    class FakeThreatHistoryRepository : ThreatHistoryRepository {
        val savedThreats = mutableListOf<ThreatRecord>()
        private val _flow = MutableStateFlow<List<ThreatRecord>>(emptyList())

        override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
            savedThreats.add(threat)
            _flow.value = savedThreats.toList()
            return Result.success(Unit)
        }

        override suspend fun getThreatById(id: String): Result<ThreatRecord?> {
            return Result.success(savedThreats.find { it.id == id })
        }

        override fun observeAllThreats(): Flow<List<ThreatRecord>> = _flow

        override suspend fun getAllThreats(): Result<List<ThreatRecord>> {
            return Result.success(savedThreats.toList())
        }

        override suspend fun updateThreat(threat: ThreatRecord): Result<Unit> {
            val idx = savedThreats.indexOfFirst { it.id == threat.id }
            if (idx != -1) savedThreats[idx] = threat
            return Result.success(Unit)
        }

        override suspend fun deleteThreat(id: String): Result<Unit> {
            savedThreats.removeAll { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun clearAllThreats(): Result<Unit> {
            savedThreats.clear()
            return Result.success(Unit)
        }
    }

    class FakeFeedbackRepository : FeedbackRepository {
        val savedFeedback = mutableListOf<UserFeedback>()

        override suspend fun submitFeedback(feedback: UserFeedback): Result<Unit> {
            savedFeedback.add(feedback)
            return Result.success(Unit)
        }

        override suspend fun getUnsubmittedFeedback(): Result<List<UserFeedback>> {
            return Result.success(savedFeedback.filter { !it.isSubmitted })
        }

        override fun observeAllFeedback(): Flow<List<UserFeedback>> = MutableStateFlow(savedFeedback)

        override suspend fun markAsSubmitted(id: String): Result<Unit> {
            return Result.success(Unit)
        }
    }
}
