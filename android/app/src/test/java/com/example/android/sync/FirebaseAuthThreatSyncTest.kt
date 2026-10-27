package com.example.android.sync

import com.example.android.data.local.dao.SyncQueueDao
import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.data.local.entity.ThreatHistoryEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.data.remote.dto.DetectionSyncResponse
import com.example.android.data.repository.SyncRepositoryImpl
import com.example.android.data.repository.ThreatHistoryRepositoryImpl
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Validates Requirement 11:
 * - User A detection & per-user cloud storage (users/{uid}/threats/{threatId})
 * - Stable UUID idempotency & duplicate prevention
 * - Session isolation on logout
 * - Cross-user account switching (User A vs User B data isolation)
 * - Unauthenticated offline-only policy
 */
class FirebaseAuthThreatSyncTest {

    private lateinit var fakeSyncDao: FakeSyncQueueDao
    private lateinit var fakeThreatDao: FakeThreatHistoryDao
    private lateinit var fakeBackend: FakeBackendApiService
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeFirestore: FakeFirestoreDataSource
    private lateinit var syncRepository: SyncRepositoryImpl
    private lateinit var threatRepository: ThreatHistoryRepositoryImpl
    private lateinit var logoutUseCase: LogoutUseCase

    private val userA = User("uid_user_A", "userA@squishshield.local", "User Alpha", false, "USER")
    private val userB = User("uid_user_B", "userB@squishshield.local", "User Beta", false, "USER")

    @Before
    fun setUp() {
        fakeSyncDao = FakeSyncQueueDao()
        fakeThreatDao = FakeThreatHistoryDao()
        fakeBackend = FakeBackendApiService()
        fakeAuth = FakeAuthRepository()
        fakeFirestore = FakeFirestoreDataSource()

        syncRepository = SyncRepositoryImpl(
            syncQueueDao = fakeSyncDao,
            backendApiService = fakeBackend,
            authRepository = fakeAuth,
            firestoreDataSource = fakeFirestore
        )

        threatRepository = ThreatHistoryRepositoryImpl(fakeThreatDao)
        logoutUseCase = LogoutUseCase(fakeAuth, threatRepository)
    }

    @Test
    fun testUserA_threatDetected_savedUnderUserACollection() = runBlocking {
        // 1. User A logs in
        fakeAuth.setUser(userA)
        assertEquals("uid_user_A", fakeAuth.getCurrentUser()?.uid)

        // 2. User A detects a threat
        val clientEventId = "TR-20261027-ALPHA-01"
        val syncReq = DetectionSyncRequest(
            clientEventId = clientEventId,
            eventType = "SMS",
            riskScore = 88,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK",
            timestamp = "2026-10-27T12:00:00Z",
            reasons = listOf("Suspicious urgent bank verification link")
        )

        val enqueueResult = syncRepository.enqueueDetection(syncReq)
        assertTrue(enqueueResult.isSuccess)

        // Verify stored in sync queue with User A's UID
        val queuedItem = fakeSyncDao.getByClientEventId(clientEventId)
        assertNotNull(queuedItem)
        assertEquals("uid_user_A", queuedItem?.userId)

        // 3. Process sync queue (simulating network synchronization)
        fakeBackend.nextResponse = Response.success(
            DetectionSyncResponse(
                id = clientEventId,
                clientEventId = clientEventId,
                userId = "uid_user_A",
                eventType = "SMS",
                riskScore = 88,
                threatCategory = "HIGH",
                recommendedAction = "BLOCK",
                status = "ANALYZED",
                createdAt = "2026-10-27T12:00:01Z"
            )
        )

        val summary = syncRepository.processSyncQueue()
        assertEquals(1, summary.syncedCount)

        // 4. Assert saved in Firestore directly under users/uid_user_A/threats/TR-20261027-ALPHA-01
        assertTrue(fakeFirestore.savedThreats.containsKey("uid_user_A"))
        val userAThreats = fakeFirestore.savedThreats["uid_user_A"]!!
        assertTrue(userAThreats.containsKey(clientEventId))

        val savedDoc = userAThreats[clientEventId]!!
        assertEquals(clientEventId, savedDoc["id"])
        assertEquals("SMS", savedDoc["eventType"])
        assertEquals("SMISHING", savedDoc["threatType"])
        assertEquals("HIGH", savedDoc["severity"])
        assertEquals(88, savedDoc["riskScore"])
        assertEquals("BLOCK", savedDoc["actionTaken"])
        assertEquals("uid_user_A", savedDoc["userId"])
    }

    @Test
    fun testNetworkRetry_withStableEventId_doesNotCreateDuplicateRecord() = runBlocking {
        fakeAuth.setUser(userA)
        val stableEventId = "STABLE-THREAT-UUID-123"
        val syncReq = DetectionSyncRequest(
            clientEventId = stableEventId,
            eventType = "QR",
            riskScore = 95,
            threatCategory = "HIGH",
            recommendedAction = "WARN",
            timestamp = "2026-10-27T12:05:00Z"
        )

        // Enqueue twice with same stable clientEventId
        val res1 = syncRepository.enqueueDetection(syncReq)
        val res2 = syncRepository.enqueueDetection(syncReq)

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)
        assertEquals(res1.getOrNull(), res2.getOrNull())

        // Ensure only 1 record exists in queue
        assertEquals(1, fakeSyncDao.items.size)
        assertEquals(stableEventId, fakeSyncDao.items.first().clientEventId)
    }

    @Test
    fun testLogoutAndAccountSwitching_isolatesUserAAndUserBData() = runBlocking {
        // Step 1: User A logs in and records local threat
        fakeAuth.setUser(userA)
        threatRepository.addThreat(
            ThreatRecord(
                id = "threat-local-A",
                timestamp = System.currentTimeMillis(),
                source = "SMS",
                sender = "BankAlert",
                riskScore = 80,
                category = "HIGH",
                actionTaken = "BLOCK",
                details = "User A Threat"
            )
        )

        val userAThreatsBefore = threatRepository.getAllThreats().getOrNull()
        assertEquals(1, userAThreatsBefore?.size)

        // Step 2: User A logs out
        val logoutRes = logoutUseCase()
        assertTrue(logoutRes.isSuccess)
        assertTrue(fakeAuth.authState.value is AuthState.Unauthenticated)

        // Local cache must be cleared so User A's threats are not exposed
        val threatsAfterLogout = threatRepository.getAllThreats().getOrNull()
        assertTrue(threatsAfterLogout.isNullOrEmpty())

        // Step 3: User B logs in
        fakeAuth.setUser(userB)
        assertEquals("uid_user_B", fakeAuth.getCurrentUser()?.uid)

        // User B starts with isolated empty state (cannot see User A's records)
        val userBInitial = threatRepository.getAllThreats().getOrNull()
        assertTrue(userBInitial.isNullOrEmpty())

        // User B detects their own threat
        threatRepository.addThreat(
            ThreatRecord(
                id = "threat-local-B",
                timestamp = System.currentTimeMillis(),
                source = "QR",
                sender = "mall-promo.qr",
                riskScore = 65,
                category = "MEDIUM",
                actionTaken = "WARN",
                details = "User B Threat"
            )
        )

        val userBThreats = threatRepository.getAllThreats().getOrNull()
        assertEquals(1, userBThreats?.size)
        assertEquals("threat-local-B", userBThreats?.first()?.id)

        // Firestore documents of User A must not be accessible or merged into User B's scope
        assertFalse(fakeFirestore.savedThreats.getOrDefault("uid_user_B", emptyMap()).containsKey("threat-local-A"))
    }

    @Test
    fun testUnauthenticatedUser_neverUploadsToCloud() = runBlocking {
        // Given an unauthenticated session
        fakeAuth.clearUser()
        assertNull(fakeAuth.getCurrentUser())

        val unauthReq = DetectionSyncRequest(
            clientEventId = "unauth-event-01",
            eventType = "SMS",
            riskScore = 70,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK",
            timestamp = "2026-10-27T12:00:00Z"
        )

        syncRepository.enqueueDetection(unauthReq)

        // Queue item was stored with userId = null
        val item = fakeSyncDao.getByClientEventId("unauth-event-01")
        assertNotNull(item)
        assertNull(item?.userId)

        // processSyncQueue does NOT upload unauthenticated items to any cloud account
        val summary = syncRepository.processSyncQueue()
        assertEquals(0, summary.totalProcessed)
        assertEquals(0, summary.syncedCount)
        assertTrue(fakeFirestore.savedThreats.isEmpty())
    }

    // --- In-Memory Test Doubles ---

    private class FakeFirestoreDataSource : FirestoreDataSource() {
        val savedThreats = mutableMapOf<String, MutableMap<String, Map<String, Any>>>()

        override suspend fun saveThreatEvent(userId: String, threatData: Map<String, Any>): Result<Unit> {
            val userMap = savedThreats.getOrPut(userId) { mutableMapOf() }
            val id = threatData["id"] as String
            userMap[id] = threatData
            return Result.success(Unit)
        }

        override suspend fun deleteThreatEvent(userId: String, threatId: String): Result<Unit> {
            savedThreats[userId]?.remove(threatId)
            return Result.success(Unit)
        }
    }

    private class FakeAuthRepository : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState.asStateFlow()
        private var currentUser: User? = null

        fun setUser(user: User) {
            currentUser = user
            _authState.value = AuthState.Authenticated(user)
        }

        fun clearUser() {
            currentUser = null
            _authState.value = AuthState.Unauthenticated
        }

        override fun getCurrentUser(): User? = currentUser

        override suspend fun getIdToken(forceRefresh: Boolean): String? {
            return currentUser?.let { "mock-token-${it.uid}" }
        }

        override suspend fun login(email: String, pass: String): Result<User> {
            val user = User("mock-uid", email, "User", false)
            setUser(user)
            return Result.success(user)
        }

        override suspend fun register(name: String, email: String, pass: String): Result<User> {
            val user = User("mock-uid", email, name, false)
            return Result.success(user)
        }

        override suspend fun loginAsGuest(): Result<User> {
            val guest = User("guest-uid", "guest@squishshield.local", "Guest", true)
            setUser(guest)
            return Result.success(guest)
        }

        override suspend fun logout(): Result<Unit> {
            clearUser()
            return Result.success(Unit)
        }

        override fun updateUserRole(role: String) {
            currentUser = currentUser?.copy(role = role)
        }
    }

    private class FakeSyncQueueDao : SyncQueueDao {
        val items = mutableListOf<SyncQueueEntity>()

        override suspend fun insert(entity: SyncQueueEntity): Long {
            items.add(entity)
            return items.size.toLong()
        }

        override suspend fun update(entity: SyncQueueEntity) {
            val idx = items.indexOfFirst { it.id == entity.id }
            if (idx >= 0) items[idx] = entity
        }

        override suspend fun getByClientEventId(clientEventId: String): SyncQueueEntity? {
            return items.find { it.clientEventId == clientEventId }
        }

        override suspend fun getPendingItems(now: Long, limit: Int, userId: String?): List<SyncQueueEntity> {
            return items.filter {
                it.status == "PENDING" && it.nextAttemptAt <= now && (userId == null || it.userId == null || it.userId == userId)
            }.take(limit)
        }

        override suspend fun markSyncing(id: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) items[idx] = items[idx].copy(status = "SYNCING", lastAttemptAt = timestamp)
        }

        override suspend fun markSynced(id: String, serverEventId: String, syncedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(status = "SYNCED", serverEventId = serverEventId, syncedAt = syncedAt, lastError = null)
            }
        }

        override suspend fun markRetry(id: String, attemptCount: Int, nextAttemptAt: Long, error: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(status = "PENDING", attemptCount = attemptCount, nextAttemptAt = nextAttemptAt, lastError = error, lastAttemptAt = timestamp)
            }
        }

        override suspend fun markFailed(id: String, error: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(status = "FAILED", lastError = error, lastAttemptAt = timestamp)
            }
        }

        override fun observePendingCount(): Flow<Int> = flowOf(items.count { it.status == "PENDING" })
        override fun observeAll(): Flow<List<SyncQueueEntity>> = flowOf(items)
        override suspend fun getPendingCount(): Int = items.count { it.status == "PENDING" }
        override suspend fun resetFailedItems(): Int = 0
        override suspend fun purgeOldSynced(cutoffTimestamp: Long): Int = 0
    }

    private class FakeThreatHistoryDao : com.example.android.data.local.dao.ThreatHistoryDao {
        val threats = mutableListOf<ThreatHistoryEntity>()

        override suspend fun insertThreat(entity: ThreatHistoryEntity) {
            threats.add(entity)
        }

        override suspend fun getThreatById(id: String): ThreatHistoryEntity? = threats.find { it.id == id }
        override fun observeAllThreats(): Flow<List<ThreatHistoryEntity>> = flowOf(threats)
        override suspend fun getAllThreats(): List<ThreatHistoryEntity> = threats
        override suspend fun updateThreat(entity: ThreatHistoryEntity) {
            val idx = threats.indexOfFirst { it.id == entity.id }
            if (idx >= 0) threats[idx] = entity
        }
        override suspend fun deleteThreatById(id: String) {
            threats.removeAll { it.id == id }
        }
        override suspend fun clearAllThreats() {
            threats.clear()
        }
        override fun observeQuarantinedThreats(): Flow<List<ThreatHistoryEntity>> = flowOf(threats.filter { it.isQuarantined })
        override suspend fun updateQuarantineStatus(id: String, isQuarantined: Boolean) {
            val idx = threats.indexOfFirst { it.id == id }
            if (idx >= 0) threats[idx] = threats[idx].copy(isQuarantined = isQuarantined)
        }
        override suspend fun updateReviewStatus(id: String, reviewStatus: String) {
            val idx = threats.indexOfFirst { it.id == id }
            if (idx >= 0) threats[idx] = threats[idx].copy(reviewStatus = reviewStatus)
        }
        override suspend fun updateThreatAction(id: String, action: String) {
            val idx = threats.indexOfFirst { it.id == id }
            if (idx >= 0) threats[idx] = threats[idx].copy(actionTaken = action)
        }
    }

    private class FakeBackendApiService : BackendApiService {
        var nextResponse: Response<DetectionSyncResponse>? = null

        override suspend fun syncDetection(request: DetectionSyncRequest): Response<DetectionSyncResponse> {
            return nextResponse ?: Response.success(
                DetectionSyncResponse(
                    id = request.clientEventId,
                    clientEventId = request.clientEventId,
                    userId = "uid_user_A",
                    eventType = request.eventType,
                    riskScore = request.riskScore,
                    threatCategory = request.threatCategory,
                    recommendedAction = request.recommendedAction,
                    status = "ANALYZED",
                    createdAt = "2026-10-27T12:00:00Z"
                )
            )
        }

        override suspend fun getCurrentUserProfile(): Response<com.example.android.data.remote.dto.UserProfileDto> {
            return Response.success(
                com.example.android.data.remote.dto.UserProfileDto(
                    id = "u1",
                    firebaseUid = "uid_user_A",
                    email = "userA@squishshield.local",
                    displayName = "User Alpha",
                    role = "USER",
                    isActive = true
                )
            )
        }

        override suspend fun submitFeedback(request: com.example.android.data.remote.dto.FeedbackSyncRequest): Response<com.example.android.data.remote.dto.FeedbackSyncResponse> {
            return Response.success(
                com.example.android.data.remote.dto.FeedbackSyncResponse(
                    id = "fb-1",
                    threatEventId = request.threatEventId,
                    feedbackType = request.classification,
                    status = "RECEIVED"
                )
            )
        }

        override suspend fun checkHealth(): Response<Map<String, Any>> {
            return Response.success(mapOf("status" to "ok"))
        }
    }
}
