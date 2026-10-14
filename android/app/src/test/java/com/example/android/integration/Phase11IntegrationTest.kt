package com.example.android.integration

import com.example.android.data.local.dao.SyncQueueDao
import com.example.android.data.local.dao.ThreatHistoryDao
import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.data.local.entity.ThreatHistoryEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.*
import com.example.android.data.repository.SyncRepositoryImpl
import com.example.android.data.repository.ThreatHistoryRepositoryImpl
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.ThreatRecord
import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.riskengine.RiskAssessment
import com.example.android.domain.riskengine.RiskEngine
import com.example.android.domain.riskengine.ThreatCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * Phase 11 - Android End-to-End Integration, Offline-First, and Account-Switching Tests.
 */
class Phase11IntegrationTest {

    private lateinit var fakeSyncDao: FakeSyncQueueDao
    private lateinit var fakeThreatDao: FakeThreatHistoryDao
    private lateinit var fakeBackend: FakeBackendApiService
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var syncRepository: SyncRepositoryImpl
    private lateinit var threatRepository: ThreatHistoryRepositoryImpl

    private val userA = User(
        uid = "firebase_user_A_101",
        email = "alice@example.com",
        displayName = "Alice",
        isGuest = false,
        role = "USER"
    )

    private val userB = User(
        uid = "firebase_user_B_202",
        email = "bob@example.com",
        displayName = "Bob",
        isGuest = false,
        role = "USER"
    )

    @Before
    fun setUp() {
        fakeSyncDao = FakeSyncQueueDao()
        fakeThreatDao = FakeThreatHistoryDao()
        fakeBackend = FakeBackendApiService()
        fakeAuth = FakeAuthRepository(userA)
        syncRepository = SyncRepositoryImpl(fakeSyncDao, fakeBackend, fakeAuth)
        threatRepository = ThreatHistoryRepositoryImpl(fakeThreatDao)
    }

    @Test
    fun testScenarioD_AccountSwitching_preservesDataOwnership_neverSyncsUnderOtherUser() = runBlocking {
        // 1. Authenticate as User A
        fakeAuth.setUser(userA)

        // 2. User A detects a threat while offline
        val detectionA = DetectionSyncRequest(
            clientEventId = "threat-event-alice-001",
            eventType = "SMS",
            riskScore = 88,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK_AND_DELETE",
            timestamp = "2026-10-13T10:00:00Z",
            modelVersions = mapOf("tinybert" to "v2"),
            modelResults = mapOf("nlp_prob" to 0.94),
            reasons = listOf("Urgency trigger detected", "Suspicious sender origin")
        )

        val enqueueResult = syncRepository.enqueueDetection(detectionA)
        assertTrue(enqueueResult.isSuccess)
        val enqueuedId = enqueueResult.getOrNull()!!

        // Verify it was enqueued with User A's UID
        val entityA = fakeSyncDao.items.find { it.id == enqueuedId }
        assertNotNull(entityA)
        assertEquals(userA.uid, entityA?.userId)
        assertEquals(SyncQueueEntity.STATUS_PENDING, entityA?.status)

        // 3. User A signs out
        fakeAuth.logout()
        assertNull(fakeAuth.getCurrentUser())

        // 4. User B signs in
        fakeAuth.setUser(userB)
        assertEquals(userB.uid, fakeAuth.getCurrentUser()?.uid)

        // 5. User B triggers sync processing
        val syncSummaryB = syncRepository.processSyncQueue()

        // User A's pending events MUST NOT be synced under User B's identity
        assertEquals(0, syncSummaryB.totalProcessed)
        assertEquals(0, syncSummaryB.syncedCount)
        assertEquals(0, fakeBackend.sentDetections.size)

        // Verify entityA remains in PENDING status for User A
        val entityAAfterBobSync = fakeSyncDao.items.find { it.id == enqueuedId }
        assertEquals(SyncQueueEntity.STATUS_PENDING, entityAAfterBobSync?.status)

        // 6. User B logs out, User A signs back in
        fakeAuth.logout()
        fakeAuth.setUser(userA)
        assertEquals(userA.uid, fakeAuth.getCurrentUser()?.uid)

        // 7. User A processes sync queue
        val syncSummaryA = syncRepository.processSyncQueue()
        assertEquals(1, syncSummaryA.totalProcessed)
        assertEquals(1, syncSummaryA.syncedCount)
        assertEquals(1, fakeBackend.sentDetections.size)
        assertEquals("threat-event-alice-001", fakeBackend.sentDetections[0].clientEventId)

        // Verify item is now marked SYNCED
        val entityAAfterAliceSync = fakeSyncDao.items.find { it.id == enqueuedId }
        assertEquals(SyncQueueEntity.STATUS_SYNCED, entityAAfterAliceSync?.status)
    }

    @Test
    fun testScenarioA_B_C_OfflineDetectionNetworkRecoveryAndRetry() = runBlocking {
        fakeAuth.setUser(userA)

        val detection = DetectionSyncRequest(
            clientEventId = "offline-qr-event-999",
            eventType = "QR",
            riskScore = 72,
            threatCategory = "MEDIUM",
            recommendedAction = "WARN_USER",
            timestamp = "2026-10-13T10:15:00Z"
        )

        // Scenario A: Offline detection enqueued locally
        val enqueueRes = syncRepository.enqueueDetection(detection)
        assertTrue(enqueueRes.isSuccess)

        // Simulate backend down (503 Service Unavailable)
        fakeBackend.simulateServerError = true
        val retrySummary = syncRepository.processSyncQueue()
        assertEquals(1, retrySummary.totalProcessed)
        assertEquals(1, retrySummary.retryCount)
        assertEquals(0, retrySummary.syncedCount)

        val item = fakeSyncDao.items[0]
        assertEquals(SyncQueueEntity.STATUS_PENDING, item.status)
        assertEquals(1, item.attemptCount)
        assertTrue(item.nextAttemptAt > System.currentTimeMillis())

        // Scenario B & C: Network / backend restored, retry succeeds
        fakeBackend.simulateServerError = false
        // Reset nextAttemptAt to allow immediate retry
        fakeSyncDao.items[0] = fakeSyncDao.items[0].copy(nextAttemptAt = 0L)

        val successSummary = syncRepository.processSyncQueue()
        assertEquals(1, successSummary.totalProcessed)
        assertEquals(1, successSummary.syncedCount)
        assertEquals(SyncQueueEntity.STATUS_SYNCED, fakeSyncDao.items[0].status)
        assertEquals("srv-id-offline-qr-event-999", fakeSyncDao.items[0].serverEventId)
    }

    @Test
    fun testIdempotency_DuplicateClientEventIdNeverEnqueuesDuplicate() = runBlocking {
        fakeAuth.setUser(userA)

        val req1 = DetectionSyncRequest(
            clientEventId = "idempotent-event-42",
            eventType = "SMS",
            riskScore = 90,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK_AND_DELETE",
            timestamp = "2026-10-13T10:20:00Z"
        )

        val req2 = req1.copy(riskScore = 95) // Same clientEventId

        val res1 = syncRepository.enqueueDetection(req1)
        val res2 = syncRepository.enqueueDetection(req2)

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)
        // Should return the same queue entity id without inserting a second row
        assertEquals(res1.getOrNull(), res2.getOrNull())
        assertEquals(1, fakeSyncDao.items.size)
    }

    @Test
    fun testThreatActions_PersistedInRoomDatabase() = runBlocking {
        val initialThreat = ThreatRecord(
            id = "threat-act-001",
            timestamp = System.currentTimeMillis(),
            source = "SMS",
            sender = "+19876543210",
            riskScore = 85,
            category = "HIGH",
            actionTaken = "WARN_USER",
            details = "Urgency trigger detected; Unverified banking domain"
        )

        // 1. Insert initial threat
        threatRepository.addThreat(initialThreat)
        val retrieved1 = threatRepository.getThreatById(initialThreat.id).getOrNull()
        assertNotNull(retrieved1)
        assertEquals("WARN_USER", retrieved1?.actionTaken)

        // 2. User executes defensive BLOCK action
        val blockedThreat = initialThreat.copy(actionTaken = "BLOCKED")
        threatRepository.updateThreat(blockedThreat)

        val retrieved2 = threatRepository.getThreatById(initialThreat.id).getOrNull()
        assertEquals("BLOCKED", retrieved2?.actionTaken)

        // 3. User executes MARK_SAFE action
        val safeThreat = initialThreat.copy(actionTaken = "MARK_SAFE")
        threatRepository.updateThreat(safeThreat)

        val retrieved3 = threatRepository.getThreatById(initialThreat.id).getOrNull()
        assertEquals("MARK_SAFE", retrieved3?.actionTaken)

        // 4. Delete threat
        threatRepository.deleteThreat(initialThreat.id)
        val retrieved4 = threatRepository.getThreatById(initialThreat.id).getOrNull()
        assertNull(retrieved4)
    }

    @Test
    fun testRiskEngine_BoundaryInvariantsAndClassificationCategories() {
        val engine = RiskEngine()

        // Category boundaries according to PRD & Phases.md:
        // 0-25: SAFE
        // 26-50: LOW
        // 51-75: MEDIUM
        // 76-100: HIGH
        assertEquals(ThreatCategory.SAFE, ThreatCategory.fromScore(0))
        assertEquals(ThreatCategory.SAFE, ThreatCategory.fromScore(25))
        assertEquals(ThreatCategory.LOW, ThreatCategory.fromScore(26))
        assertEquals(ThreatCategory.LOW, ThreatCategory.fromScore(50))
        assertEquals(ThreatCategory.MEDIUM, ThreatCategory.fromScore(51))
        assertEquals(ThreatCategory.MEDIUM, ThreatCategory.fromScore(75))
        assertEquals(ThreatCategory.HIGH, ThreatCategory.fromScore(76))
        assertEquals(ThreatCategory.HIGH, ThreatCategory.fromScore(100))

        // Clamping check on extreme inputs
        val safeAssessment = engine.assess(smsScore = 0f, urlScore = 0f, qrScore = 0f, contextScore = 0f, anomalyScore = 0f)
        assertTrue(safeAssessment.riskScore in 0..25)
        assertEquals(ThreatCategory.SAFE, safeAssessment.category)

        val maxAssessment = engine.assess(smsScore = 100f, urlScore = 100f, qrScore = 100f, contextScore = 100f, anomalyScore = 100f)
        assertEquals(100, maxAssessment.riskScore)
        assertEquals(ThreatCategory.HIGH, maxAssessment.category)
    }

    // --- Test Doubles ---

    private class FakeSyncQueueDao : SyncQueueDao {
        val items = mutableListOf<SyncQueueEntity>()

        override suspend fun insert(entity: SyncQueueEntity): Long {
            items.removeAll { it.id == entity.id }
            items.add(entity)
            return 1L
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
                it.status == SyncQueueEntity.STATUS_PENDING &&
                it.nextAttemptAt <= now &&
                (userId == null || it.userId == null || it.userId == userId)
            }.take(limit)
        }

        override suspend fun markSyncing(id: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(status = SyncQueueEntity.STATUS_SYNCING, lastAttemptAt = timestamp)
            }
        }

        override suspend fun markSynced(id: String, serverEventId: String, syncedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(
                    status = SyncQueueEntity.STATUS_SYNCED,
                    serverEventId = serverEventId,
                    syncedAt = syncedAt,
                    lastError = null
                )
            }
        }

        override suspend fun markRetry(id: String, attemptCount: Int, nextAttemptAt: Long, error: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(
                    status = SyncQueueEntity.STATUS_PENDING,
                    attemptCount = attemptCount,
                    nextAttemptAt = nextAttemptAt,
                    lastError = error,
                    lastAttemptAt = timestamp
                )
            }
        }

        override suspend fun markFailed(id: String, error: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(status = SyncQueueEntity.STATUS_FAILED, lastError = error, lastAttemptAt = timestamp)
            }
        }

        override fun observePendingCount(): Flow<Int> = flowOf(items.count { it.status == SyncQueueEntity.STATUS_PENDING })
        override fun observeAll(): Flow<List<SyncQueueEntity>> = flowOf(items)
        override suspend fun getPendingCount(): Int = items.count { it.status == SyncQueueEntity.STATUS_PENDING }

        override suspend fun resetFailedItems(): Int {
            var count = 0
            items.forEachIndexed { idx, entity ->
                if (entity.status == SyncQueueEntity.STATUS_FAILED) {
                    items[idx] = entity.copy(status = SyncQueueEntity.STATUS_PENDING, nextAttemptAt = 0L)
                    count++
                }
            }
            return count
        }

        override suspend fun purgeOldSynced(cutoffTimestamp: Long): Int {
            val before = items.size
            items.removeAll { it.status == SyncQueueEntity.STATUS_SYNCED && (it.syncedAt ?: 0L) < cutoffTimestamp }
            return before - items.size
        }
    }

    private class FakeThreatHistoryDao : ThreatHistoryDao {
        val records = mutableListOf<ThreatHistoryEntity>()

        override suspend fun insertThreat(threat: ThreatHistoryEntity) {
            records.removeAll { it.id == threat.id }
            records.add(threat)
        }

        override suspend fun getThreatById(id: String): ThreatHistoryEntity? {
            return records.find { it.id == id }
        }

        override fun observeAllThreats(): Flow<List<ThreatHistoryEntity>> = flowOf(records)
        override suspend fun getAllThreats(): List<ThreatHistoryEntity> = records.toList()

        override suspend fun updateThreat(threat: ThreatHistoryEntity) {
            val idx = records.indexOfFirst { it.id == threat.id }
            if (idx >= 0) records[idx] = threat
        }

        override suspend fun deleteThreatById(id: String) {
            records.removeAll { it.id == id }
        }

        override suspend fun clearAllThreats() {
            records.clear()
        }
    }

    private class FakeBackendApiService : BackendApiService {
        val sentDetections = mutableListOf<DetectionSyncRequest>()
        var simulateServerError: Boolean = false

        override suspend fun getCurrentUserProfile(): Response<UserProfileDto> {
            return Response.success(UserProfileDto("1", "uid", "test@shield.ai", "User", "USER", true))
        }

        override suspend fun syncDetection(request: DetectionSyncRequest): Response<DetectionSyncResponse> {
            if (simulateServerError) {
                return Response.error(503, okhttp3.ResponseBody.create(null, "Service Unavailable"))
            }
            sentDetections.add(request)
            return Response.success(
                DetectionSyncResponse(
                    id = "srv-id-${request.clientEventId}",
                    clientEventId = request.clientEventId,
                    userId = "active-user",
                    eventType = request.eventType,
                    riskScore = request.riskScore,
                    threatCategory = request.threatCategory,
                    recommendedAction = request.recommendedAction,
                    status = "SYNCED",
                    createdAt = "2026-10-13T10:00:00Z"
                )
            )
        }

        override suspend fun submitFeedback(request: FeedbackSyncRequest): Response<FeedbackSyncResponse> {
            return Response.success(FeedbackSyncResponse("fb-1", request.threatEventId, request.classification, "RECEIVED"))
        }

        override suspend fun checkHealth(): Response<Map<String, Any>> = Response.success(mapOf("status" to "ok"))
    }

    private class FakeAuthRepository(private var currentUser: User?) : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(
            if (currentUser != null) AuthState.Authenticated(currentUser!!) else AuthState.Unauthenticated
        )
        override val authState: StateFlow<AuthState> = _authState

        fun setUser(user: User?) {
            currentUser = user
            _authState.value = if (user != null) AuthState.Authenticated(user) else AuthState.Unauthenticated
        }

        override fun getCurrentUser(): User? = currentUser

        override suspend fun getIdToken(forceRefresh: Boolean): String? {
            return currentUser?.let { "mock-token-${it.uid}" }
        }

        override suspend fun login(email: String, pass: String): Result<User> = Result.success(currentUser!!)
        override suspend fun register(name: String, email: String, pass: String): Result<User> = Result.success(currentUser!!)
        override suspend fun loginAsGuest(): Result<User> = Result.success(currentUser!!)
        override suspend fun logout(): Result<Unit> {
            currentUser = null
            _authState.value = AuthState.Unauthenticated
            return Result.success(Unit)
        }

        override fun updateUserRole(role: String) {
            currentUser = currentUser?.copy(role = role)
        }
    }
}
