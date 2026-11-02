package com.example.android.presentation

import com.example.android.domain.model.AuthState
import com.example.android.domain.model.SmsAnalysisResult
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import com.example.android.domain.repository.SmsAnalysisRepository
import com.example.android.domain.repository.ThreatHistoryRepository
import com.example.android.domain.usecase.GetAuthStateUseCase
import com.example.android.domain.usecase.LogoutUseCase
import com.example.android.presentation.dashboard.DashboardViewModel
import com.example.android.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
class IncomingMessageAndSyncTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testRequiredPermissionsArray_containsSmsAndCamera() {
        val permissions = PermissionUtils.getRequiredPermissions()
        assertTrue(permissions.contains(android.Manifest.permission.RECEIVE_SMS))
        assertTrue(permissions.contains(android.Manifest.permission.READ_SMS))
        assertTrue(permissions.contains(android.Manifest.permission.CAMERA))
    }

    @Test
    fun testDashboardHistory_includesBothThreatsAndSafeIncomingMessages() = runTest {
        val fakeThreatRepo = FakeThreatHistoryRepo()
        val fakeSmsRepo = FakeSmsRepo()
        val fakeAuthRepo = FakeAuthRepo()

        // Threat SMS logged
        fakeThreatRepo.threats.value = listOf(
            ThreatRecord(
                id = "threat-01",
                timestamp = 1000L,
                source = "SMS",
                sender = "BadActor",
                riskScore = 85,
                category = "HIGH",
                actionTaken = "QUARANTINED",
                details = "Suspicious link detected"
            )
        )

        // Safe SMS logged in SmsAnalysisRepository
        fakeSmsRepo.analyses.value = listOf(
            SmsAnalysisResult(
                id = "safe-02",
                messageId = "msg_safe",
                timestamp = 2000L,
                sender = "Mom",
                riskScore = 5,
                isSmishing = false,
                confidence = 0.99f,
                extractedUrlsCount = 0,
                processedAt = 2000L,
                rawBody = "Hi sweetie, see you soon!"
            )
        )

        val viewModel = DashboardViewModel(
            getAuthStateUseCase = GetAuthStateUseCase(fakeAuthRepo),
            logoutUseCase = LogoutUseCase(fakeAuthRepo),
            threatHistoryRepository = fakeThreatRepo,
            smsAnalysisRepository = fakeSmsRepo
        )

        advanceUntilIdle()

        val history = viewModel.uiState.value.threatHistory
        assertEquals(2, history.size)
        // Ordered by timestamp descending: safe-02 (2000L) then threat-01 (1000L)
        assertEquals("safe-02", history[0].id)
        assertEquals("SAFE", history[0].category)
        assertEquals("Mom", history[0].sender)

        assertEquals("threat-01", history[1].id)
        assertEquals("HIGH", history[1].category)
        assertEquals("BadActor", history[1].sender)
    }

    // --- Fake doubles for testing ---

    private class FakeAuthRepo : com.example.android.domain.repository.AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(
            AuthState.Authenticated(
                User(uid = "test-user", email = "test@example.com", displayName = "Test", isGuest = false)
            )
        )
        override val authState: StateFlow<AuthState> = _authState.asStateFlow()
        override fun getCurrentUser(): User? = (_authState.value as? AuthState.Authenticated)?.user
        override suspend fun getIdToken(forceRefresh: Boolean): String? = "mock-token"
        override suspend fun login(email: String, pass: String): Result<User> = Result.failure(Exception())
        override suspend fun register(name: String, email: String, pass: String): Result<User> = Result.failure(Exception())
        override suspend fun loginAsGuest(): Result<User> = Result.failure(Exception())
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override fun updateUserRole(role: String) {}
    }

    private class FakeThreatHistoryRepo : ThreatHistoryRepository {
        val threats = MutableStateFlow<List<ThreatRecord>>(emptyList())
        override fun observeAllThreats(): Flow<List<ThreatRecord>> = threats
        override fun observeQuarantinedThreats(): Flow<List<ThreatRecord>> = threats.map { list -> list.filter { it.isQuarantined } }
        override suspend fun getAllThreats(): Result<List<ThreatRecord>> = Result.success(threats.value)
        override suspend fun getThreatById(id: String): Result<ThreatRecord?> = Result.success(threats.value.find { it.id == id })
        override suspend fun addThreat(threat: ThreatRecord): Result<Unit> {
            threats.value = threats.value + threat
            return Result.success(Unit)
        }
        override suspend fun updateThreat(threat: ThreatRecord): Result<Unit> = Result.success(Unit)
        override suspend fun updateThreatAction(id: String, action: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteThreat(id: String): Result<Unit> = Result.success(Unit)
            override suspend fun addThreats(threats: List<com.example.android.domain.model.ThreatRecord>): Result<Unit> = Result.success(Unit)
    override fun observeThreatsByUserId(userId: String?): kotlinx.coroutines.flow.Flow<List<com.example.android.domain.model.ThreatRecord>> = observeAllThreats()
    override suspend fun deleteThreatsByUserId(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun clearAllThreats(): Result<Unit> = Result.success(Unit)
        override suspend fun updateQuarantineStatus(id: String, isQuarantined: Boolean): Result<Unit> = Result.success(Unit)
        override suspend fun updateReviewStatus(id: String, reviewStatus: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeSmsRepo : SmsAnalysisRepository {
        val analyses = MutableStateFlow<List<SmsAnalysisResult>>(emptyList())
        override fun observeAllAnalyses(): Flow<List<SmsAnalysisResult>> = analyses
        override suspend fun saveAnalysis(result: SmsAnalysisResult): Result<Unit> {
            analyses.value = analyses.value + result
            return Result.success(Unit)
        }
        override suspend fun getAnalysisById(id: String): Result<SmsAnalysisResult?> = Result.success(analyses.value.find { it.id == id })
        override suspend fun deleteAnalysis(id: String): Result<Unit> = Result.success(Unit)
    }
}
