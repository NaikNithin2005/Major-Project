package com.example.android.sync

import com.example.android.data.local.dao.SyncQueueDao
import com.example.android.data.local.dao.ThreatHistoryDao
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
import java.io.IOException

class OfflineScanningAndSyncTest {

    private class FakeThreatHistoryDao : ThreatHistoryDao {
        val threats = mutableListOf<ThreatHistoryEntity>()

        override suspend fun insertThreat(threat: ThreatHistoryEntity) {
            threats.add(threat)
        }

        override suspend fun getThreatById(id: String): ThreatHistoryEntity? = threats.find { it.id == id }
        override fun observeAllThreats(): Flow<List<ThreatHistoryEntity>> = flowOf(threats)
        override fun observeQuarantinedThreats(): Flow<List<ThreatHistoryEntity>> =
            flowOf(threats.filter { it.isQuarantined })
        override suspend fun getAllThreats(): List<ThreatHistoryEntity> = threats

        override suspend fun updateThreat(threat: ThreatHistoryEntity) {
            val idx = threats.indexOfFirst { it.id == threat.id }
            if (idx >= 0) threats[idx] = threat
        }

        override suspend fun deleteThreatById(id: String) {
            threats.removeAll { it.id == id }
        }

            override suspend fun insertThreats(threats: List<com.example.android.data.local.entity.ThreatHistoryEntity>) { threats.forEach { insertThreat(it) } }
    override fun observeThreatsByUserId(userId: String?): kotlinx.coroutines.flow.Flow<List<com.example.android.data.local.entity.ThreatHistoryEntity>> = observeAllThreats()
    override suspend fun deleteThreatsByUserId(userId: String) {}

    override suspend fun clearAllThreats() {
            threats.clear()
        }

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

        override suspend fun getPendingThreats(userId: String?): List<ThreatHistoryEntity> {
            return threats.filter { it.syncStatus == "PENDING" && (userId == null || it.userId == null || it.userId == userId) }
        }

        override suspend fun updateSyncStatus(id: String, syncStatus: String) {
            val idx = threats.indexOfFirst { it.id == id }
            if (idx >= 0) threats[idx] = threats[idx].copy(syncStatus = syncStatus, retryCount = threats[idx].retryCount + 1)
        }

        override suspend fun updatePendingOperation(id: String, pendingOperation: String) {
            val idx = threats.indexOfFirst { it.id == id }
            if (idx >= 0) threats[idx] = threats[idx].copy(pendingOperation = pendingOperation)
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
        override suspend fun resetAllPendingForImmediateSync(): Int {
            var count = 0
            items.forEachIndexed { idx, entity ->
                if (entity.status == "FAILED" || entity.status == "PENDING") {
                    items[idx] = entity.copy(status = "PENDING", nextAttemptAt = 0L)
                    count++
                }
            }
            return count
        }
        override suspend fun purgeOldSynced(cutoffTimestamp: Long): Int = 0
    }

    private class FakeAuthRepository(private var user: User? = null) : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(
            if (user != null) AuthState.Authenticated(user!!) else AuthState.Unauthenticated
        )
        override val authState: StateFlow<AuthState> = _authState.asStateFlow()

        fun setUser(u: User?) {
            user = u
            _authState.value = if (u != null) AuthState.Authenticated(u) else AuthState.Unauthenticated
        }

        override suspend fun login(email: String, pass: String): Result<User> = Result.failure(Exception())
        override suspend fun register(email: String, pass: String, name: String): Result<User> = Result.failure(Exception())
        override suspend fun logout(): Result<Unit> {
            setUser(null)
            return Result.success(Unit)
        }
        override suspend fun loginAsGuest(): Result<User> = Result.failure(Exception())
        override fun getCurrentUser(): User? = user
        override suspend fun getIdToken(forceRefresh: Boolean): String? = if (user != null) "fake-token-${user!!.uid}" else null
        override fun updateUserRole(role: String) {
            user = user?.copy(role = role)
        }
    }

    private class FakeBackendApiService : BackendApiService {
        val syncedRequests = mutableListOf<DetectionSyncRequest>()
        var simulateOffline: Boolean = false

        override suspend fun syncDetection(request: DetectionSyncRequest): Response<DetectionSyncResponse> {
            if (simulateOffline) {
                throw IOException("Network unreachable (offline)")
            }
            syncedRequests.add(request)
            return Response.success(
                DetectionSyncResponse(
                    id = "srv-${request.clientEventId}",
                    clientEventId = request.clientEventId,
                    userId = "srv-user",
                    eventType = request.eventType,
                    riskScore = request.riskScore,
                    threatCategory = request.threatCategory,
                    recommendedAction = request.recommendedAction,
                    status = "SYNCED",
                    createdAt = "2026-10-28T00:00:00Z"
                )
            )
        }

        override suspend fun getCurrentUserProfile() = Response.success(
            com.example.android.data.remote.dto.UserProfileDto("1", "uid", "test@shield.ai", "User", "USER", true)
        )
        override suspend fun submitFeedback(request: com.example.android.data.remote.dto.FeedbackSyncRequest) = Response.success(
            com.example.android.data.remote.dto.FeedbackSyncResponse("1", request.threatEventId, request.classification, "OK")
        )
        override suspend fun checkHealth(): Response<Map<String, Any>> = Response.success(mapOf("status" to "ok"))
    }

    private class FakeFirestoreDataSource : FirestoreDataSource() {
        val storedMaps = mutableMapOf<String, MutableMap<String, Any>>()
        var simulateOffline: Boolean = false

        override suspend fun saveThreatEvent(userId: String, threatData: Map<String, Any>): Result<Unit> {
            if (simulateOffline) {
                return Result.failure(IOException("Firestore unavailable (offline)"))
            }
            val threatId = threatData["id"] as? String ?: "threat-1"
            storedMaps.getOrPut(userId) { mutableMapOf() }[threatId] = threatData
            return Result.success(Unit)
        }
    }

    private lateinit var fakeDao: FakeThreatHistoryDao
    private lateinit var fakeSyncDao: FakeSyncQueueDao
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeBackend: FakeBackendApiService
    private lateinit var fakeFirestore: FakeFirestoreDataSource
    private lateinit var threatRepository: ThreatHistoryRepositoryImpl
    private lateinit var syncRepository: SyncRepositoryImpl

    private val userA = User("uid_alpha", "alpha@shield.ai", "Alpha", false, "USER")
    private val userB = User("uid_beta", "beta@shield.ai", "Beta", false, "USER")

    @Before
    fun setup() {
        fakeDao = FakeThreatHistoryDao()
        fakeSyncDao = FakeSyncQueueDao()
        fakeAuth = FakeAuthRepository(userA)
        fakeBackend = FakeBackendApiService()
        fakeFirestore = FakeFirestoreDataSource()
        threatRepository = ThreatHistoryRepositoryImpl(fakeDao)
        syncRepository = SyncRepositoryImpl(
            syncQueueDao = fakeSyncDao,
            backendApiService = fakeBackend,
            authRepository = fakeAuth,
            firestoreDataSource = fakeFirestore,
            threatHistoryDao = fakeDao
        )
    }

    @Test
    fun testScanPersistence_storesAllPhase3Metadata() = runBlocking {
        val eventId = "scan-offline-001"
        val record = ThreatRecord(
            id = eventId,
            timestamp = 1730000000000L,
            source = "QR Code (CAMERA)",
            sender = "evil-phish.xyz",
            riskScore = 88,
            category = "HIGH",
            actionTaken = "QUARANTINED",
            details = "Suspicious login target with deceptive credential harvesting pattern",
            rawContent = "https://evil-phish.xyz/login",
            extractedUrl = "https://evil-phish.xyz/login",
            payloadType = "URL",
            severity = "HIGH",
            modelVersion = "xgboost-url-v2",
            isQuarantined = true,
            reviewStatus = "NEW",
            contentRetentionMode = "LOCAL_SAVED",
            pendingOperation = ThreatHistoryEntity.OP_REMOTE_REPUTATION_CHECK,
            syncStatus = ThreatHistoryEntity.STATUS_PENDING,
            retryCount = 0,
            userId = "uid_alpha"
        )

        // Save local scan to Room
        threatRepository.addThreat(record)

        val retrievedResult = threatRepository.getThreatById(eventId)
        assertTrue(retrievedResult.isSuccess)
        val retrieved = retrievedResult.getOrNull()
        assertNotNull(retrieved)
        assertEquals(eventId, retrieved?.id)
        assertEquals("QR Code (CAMERA)", retrieved?.source)
        assertEquals("evil-phish.xyz", retrieved?.sender)
        assertEquals(88, retrieved?.riskScore)
        assertEquals("HIGH", retrieved?.category)
        assertEquals("QUARANTINED", retrieved?.actionTaken)
        assertEquals("xgboost-url-v2", retrieved?.modelVersion)
        assertEquals(ThreatHistoryEntity.OP_REMOTE_REPUTATION_CHECK, retrieved?.pendingOperation)
        assertEquals(ThreatHistoryEntity.STATUS_PENDING, retrieved?.syncStatus)
        assertEquals(0, retrieved?.retryCount)
        assertEquals("uid_alpha", retrieved?.userId)
    }

    @Test
    fun testOfflineProcessing_enqueuesWithoutFailing() = runBlocking {
        // Simulate network offline
        fakeBackend.simulateOffline = true
        fakeFirestore.simulateOffline = true

        val eventId = "scan-offline-002"
        val record = ThreatRecord(
            id = eventId,
            timestamp = System.currentTimeMillis(),
            source = "QR",
            sender = "malicious.test",
            riskScore = 95,
            category = "HIGH",
            actionTaken = "BLOCKED",
            details = "Malicious URL detected locally",
            pendingOperation = ThreatHistoryEntity.OP_REMOTE_REPUTATION_CHECK,
            syncStatus = ThreatHistoryEntity.STATUS_PENDING,
            retryCount = 0,
            userId = "uid_alpha"
        )

        // 1. Local Room save must succeed offline
        threatRepository.addThreat(record)
        val stored = threatRepository.getThreatById(eventId).getOrNull()
        assertNotNull(stored)

        // 2. Sync queue enqueue must succeed offline
        val syncReq = DetectionSyncRequest(
            clientEventId = eventId,
            eventType = "QR",
            riskScore = 95,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK",
            timestamp = "2026-10-28T12:00:00Z"
        )
        val enqueueRes = syncRepository.enqueueDetection(syncReq)
        assertTrue(enqueueRes.isSuccess)

        // 3. Process sync while offline: reports retry without failing
        val summary = syncRepository.processSyncQueue()
        assertEquals(1, summary.retryCount)
        assertEquals(0, summary.syncedCount)

        // 4. Local entity still intact with PENDING status and incremented retry count
        val local = threatRepository.getThreatById(eventId).getOrNull()
        assertEquals(ThreatHistoryEntity.STATUS_PENDING, local?.syncStatus)
        assertEquals(1, local?.retryCount)
    }

    @Test
    fun testAccountSafeSync_doesNotUploadUserAQueuedItemsUnderUserB() = runBlocking {
        // User A enqueues an item
        fakeAuth.setUser(userA)
        val eventIdA = "scan-user-a-001"
        val reqA = DetectionSyncRequest(
            clientEventId = eventIdA,
            eventType = "SMS",
            riskScore = 90,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK",
            timestamp = "2026-10-28T12:00:00Z"
        )
        syncRepository.enqueueDetection(reqA)

        // Now switch account to User B
        fakeAuth.setUser(userB)

        // Process queue while User B is logged in
        val summary = syncRepository.processSyncQueue()

        // User A's item must NOT have been synced under User B
        assertEquals(0, summary.syncedCount)
        assertEquals(0, fakeBackend.syncedRequests.size)
        assertNull(fakeFirestore.storedMaps["uid_beta"]?.get(eventIdA))
    }

    @Test
    fun testSyncSuccess_updatesRoomRecordStatusAndPendingOp() = runBlocking {
        val eventId = "scan-sync-success-001"
        val record = ThreatRecord(
            id = eventId,
            timestamp = System.currentTimeMillis(),
            source = "QR",
            sender = "example.com",
            riskScore = 80,
            category = "HIGH",
            actionTaken = "BLOCKED",
            details = "Detected",
            pendingOperation = ThreatHistoryEntity.OP_REMOTE_REPUTATION_CHECK,
            syncStatus = ThreatHistoryEntity.STATUS_PENDING,
            retryCount = 0,
            userId = "uid_alpha"
        )
        threatRepository.addThreat(record)

        val syncReq = DetectionSyncRequest(
            clientEventId = eventId,
            eventType = "QR",
            riskScore = 80,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK",
            timestamp = "2026-10-28T12:00:00Z"
        )
        syncRepository.enqueueDetection(syncReq)

        // When network is online, processSyncQueue succeeds
        val summary = syncRepository.processSyncQueue()
        assertEquals(1, summary.syncedCount)

        // Verify Room record was updated to SYNCED and OP_NONE (no duplicate created)
        val updated = threatRepository.getThreatById(eventId).getOrNull()
        assertEquals(ThreatHistoryEntity.STATUS_SYNCED, updated?.syncStatus)
        assertEquals(ThreatHistoryEntity.OP_NONE, updated?.pendingOperation)
        assertEquals(1, fakeDao.threats.size)
    }

    @Test
    fun testIdempotentWrites_noDuplicateOnReEnqueue() = runBlocking {
        val eventId = "scan-idempotent-001"
        val syncReq = DetectionSyncRequest(
            clientEventId = eventId,
            eventType = "QR",
            riskScore = 80,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK",
            timestamp = "2026-10-28T12:00:00Z"
        )

        val id1 = syncRepository.enqueueDetection(syncReq).getOrThrow()
        val id2 = syncRepository.enqueueDetection(syncReq).getOrThrow()

        // Same queue item id returned, not duplicated
        assertEquals(id1, id2)
        assertEquals(1, fakeSyncDao.items.size)
    }
}
