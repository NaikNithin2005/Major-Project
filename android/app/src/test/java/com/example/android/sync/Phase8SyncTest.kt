package com.example.android.sync

import com.example.android.data.local.dao.SyncQueueDao
import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.*
import com.example.android.data.repository.SyncRepositoryImpl
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class Phase8SyncTest {

    private lateinit var fakeDao: FakeSyncQueueDao
    private lateinit var fakeBackend: FakeBackendApiService
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var syncRepository: SyncRepositoryImpl

    private val testUser = User(
        uid = "firebase-user-123",
        email = "analyst@shield.ai",
        displayName = "Security Analyst",
        isGuest = false,
        role = "USER"
    )

    private val guestUser = User(
        uid = "guest_temp_123",
        email = "",
        displayName = "Guest User",
        isGuest = true,
        role = "USER"
    )

    @Before
    fun setUp() {
        fakeDao = FakeSyncQueueDao()
        fakeBackend = FakeBackendApiService()
        fakeAuth = FakeAuthRepository(testUser)
        syncRepository = SyncRepositoryImpl(fakeDao, fakeBackend, fakeAuth)
    }

    @Test
    fun testDataMinimization_noRawSmsInPayload() = runBlocking {
        val syncRequest = DetectionSyncRequest(
            clientEventId = "sms-event-456",
            eventType = "SMS",
            riskScore = 85,
            threatCategory = "HIGH",
            recommendedAction = "BLOCK_AND_REPORT",
            timestamp = "2026-10-07T16:00:00Z",
            modelVersions = mapOf("tinybert" to "v2"),
            reasons = listOf("Urgent smishing detected", "Unknown sender")
        )

        val result = syncRepository.enqueueDetection(syncRequest)

        assertTrue(result.isSuccess)
        assertEquals(1, fakeDao.items.size)
        val entity = fakeDao.items.first()
        val payload = entity.payloadJson

        assertFalse("Payload must NOT contain raw SMS text", payload.contains("Hey mom I lost my phone"))
        assertTrue("Payload must contain risk_score", payload.contains("\"risk_score\":85"))
        assertTrue("Payload must contain threat_category", payload.contains("\"threat_category\":\"HIGH\""))
        assertEquals("SMS", entity.eventType)
        assertEquals("PENDING", entity.status)
    }

    @Test
    fun testGuestMode_doesNotUploadTelemetry() = runBlocking {
        fakeAuth.setUser(guestUser)

        val result = syncRepository.processSyncQueue()

        assertEquals(0, result.totalProcessed)
        assertEquals(0, result.syncedCount)
        assertEquals(0, fakeBackend.sentDetections.size)
    }

    @Test
    fun testOnlineSync_uploadsTelemetryAndMarksSynced() = runBlocking {
        fakeDao.items.add(
            SyncQueueEntity(
                id = "queue-001",
                clientEventId = "event-001",
                eventType = "SMS",
                payloadJson = """{"client_event_id":"event-001","event_type":"SMS","risk_score":75,"threat_category":"HIGH","recommended_action":"BLOCK","timestamp":"2026-10-07T12:00:00Z"}""",
                status = "PENDING"
            )
        )

        fakeBackend.nextResponse = Response.success(
            DetectionSyncResponse(
                id = "server-event-999",
                clientEventId = "event-001",
                userId = "user-1",
                eventType = "SMS",
                riskScore = 75,
                threatCategory = "HIGH",
                recommendedAction = "BLOCK",
                status = "ANALYZED",
                createdAt = "2026-10-07T12:00:01Z"
            )
        )

        val result = syncRepository.processSyncQueue()

        assertEquals(1, result.totalProcessed)
        assertEquals(1, result.syncedCount)
        assertEquals(0, result.failedCount)
        assertEquals("SYNCED", fakeDao.items.first().status)
        assertEquals("server-event-999", fakeDao.items.first().serverEventId)
    }

    @Test
    fun testUnauthorized401_triggersTokenRefresh() = runBlocking {
        fakeDao.items.add(
            SyncQueueEntity(
                id = "queue-002",
                clientEventId = "event-002",
                eventType = "QR",
                payloadJson = """{"client_event_id":"event-002","event_type":"QR","risk_score":90,"threat_category":"HIGH","recommended_action":"WARN","timestamp":"2026-10-07T12:00:00Z"}""",
                status = "PENDING"
            )
        )

        fakeBackend.nextResponse = Response.error(
            401,
            "{\"detail\":\"Token expired\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = syncRepository.processSyncQueue()

        assertEquals(1, result.totalProcessed)
        assertEquals(1, result.retryCount)
        assertTrue(fakeAuth.tokenRefreshed)
        assertEquals("PENDING", fakeDao.items.first().status)
    }

    @Test
    fun testExponentialBackoff_onServer500Error() = runBlocking {
        fakeDao.items.add(
            SyncQueueEntity(
                id = "queue-003",
                clientEventId = "event-003",
                eventType = "SMS",
                payloadJson = """{"client_event_id":"event-003","event_type":"SMS","risk_score":80,"threat_category":"HIGH","recommended_action":"BLOCK","timestamp":"2026-10-07T12:00:00Z"}""",
                status = "PENDING",
                attemptCount = 1
            )
        )

        fakeBackend.nextResponse = Response.error(
            500,
            "{\"detail\":\"Internal server error\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = syncRepository.processSyncQueue()

        assertEquals(1, result.totalProcessed)
        assertEquals(0, result.syncedCount)
        assertEquals(1, result.retryCount)
        val item = fakeDao.items.first()
        assertEquals(2, item.attemptCount)
        assertTrue(item.nextAttemptAt > System.currentTimeMillis())
        assertTrue(item.lastError?.contains("HTTP 500") == true)
    }

    @Test
    fun testPermanentFailure_onClient400Error() = runBlocking {
        fakeDao.items.add(
            SyncQueueEntity(
                id = "queue-004",
                clientEventId = "event-004",
                eventType = "SMS",
                payloadJson = """{"client_event_id":"event-004","event_type":"SMS","risk_score":80,"threat_category":"HIGH","recommended_action":"BLOCK","timestamp":"2026-10-07T12:00:00Z"}""",
                status = "PENDING"
            )
        )

        fakeBackend.nextResponse = Response.error(
            400,
            "{\"detail\":\"Validation error: invalid format\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )

        val result = syncRepository.processSyncQueue()

        assertEquals(1, result.totalProcessed)
        assertEquals(0, result.syncedCount)
        assertEquals(1, result.failedCount)
        val item = fakeDao.items.first()
        assertEquals("FAILED", item.status)
        assertTrue(item.lastError?.contains("Non-retryable") == true)
    }

    @Test
    fun testServerProfileSync_authoritativeRoleUpdated() = runBlocking {
        fakeBackend.nextProfileResponse = Response.success(
            UserProfileDto(
                id = "srv-123",
                firebaseUid = "firebase-user-123",
                email = "analyst@shield.ai",
                displayName = "Security Analyst",
                role = "ADMIN",
                isActive = true
            )
        )

        val result = syncRepository.fetchAndApplyServerProfile()

        assertTrue(result.isSuccess)
        assertEquals("ADMIN", result.getOrNull()?.role)
        assertEquals("ADMIN", fakeAuth.getCurrentUser()?.role)
    }

    // --- Lightweight Test Doubles ---

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

        override suspend fun getPendingItems(now: Long, limit: Int): List<SyncQueueEntity> {
            return items.filter { it.status == "PENDING" && it.nextAttemptAt <= now }.take(limit)
        }

        override suspend fun markSyncing(id: String, timestamp: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx >= 0) {
                items[idx] = items[idx].copy(status = "SYNCING", lastAttemptAt = timestamp)
            }
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

        override fun observePendingCount(): Flow<Int> {
            return flowOf(items.count { it.status == "PENDING" })
        }

        override fun observeAll(): Flow<List<SyncQueueEntity>> {
            return flowOf(items)
        }

        override suspend fun getPendingCount(): Int {
            return items.count { it.status == "PENDING" }
        }

        override suspend fun purgeOldSynced(cutoffTimestamp: Long): Int {
            val before = items.size
            items.removeAll { it.status == "SYNCED" && (it.syncedAt ?: 0L) < cutoffTimestamp }
            return before - items.size
        }
    }

    private class FakeBackendApiService : BackendApiService {
        val sentDetections = mutableListOf<DetectionSyncRequest>()
        var nextResponse: Response<DetectionSyncResponse>? = null
        var nextProfileResponse: Response<UserProfileDto>? = null

        override suspend fun getCurrentUserProfile(): Response<UserProfileDto> {
            return nextProfileResponse ?: Response.success(
                UserProfileDto("1", "uid", "email", "name", "USER", true)
            )
        }

        override suspend fun syncDetection(request: DetectionSyncRequest): Response<DetectionSyncResponse> {
            sentDetections.add(request)
            return nextResponse ?: Response.success(
                DetectionSyncResponse("srv-1", request.clientEventId, "uid", request.eventType, request.riskScore, request.threatCategory, request.recommendedAction, "SYNCED", "2026-10-07T12:00:00Z")
            )
        }

        override suspend fun submitFeedback(request: FeedbackSyncRequest): Response<FeedbackSyncResponse> {
            return Response.success(FeedbackSyncResponse("fb-1", request.threatEventId, request.classification, "RECEIVED"))
        }

        override suspend fun checkHealth(): Response<Map<String, Any>> {
            return Response.success(mapOf("status" to "ok"))
        }
    }

    private class FakeAuthRepository(initialUser: User?) : AuthRepository {
        private var userState: User? = initialUser
        var tokenRefreshed = false

        private val _authState = MutableStateFlow<AuthState>(
            if (initialUser != null) AuthState.Authenticated(initialUser) else AuthState.Unauthenticated
        )
        override val authState: StateFlow<AuthState> = _authState

        fun setUser(user: User?) {
            userState = user
            _authState.value = if (user != null) AuthState.Authenticated(user) else AuthState.Unauthenticated
        }

        override fun getCurrentUser(): User? = userState

        override suspend fun getIdToken(forceRefresh: Boolean): String? {
            if (forceRefresh) tokenRefreshed = true
            return if (userState?.isGuest == true) null else "fake-bearer-id-token"
        }

        override suspend fun login(email: String, pass: String): Result<User> = Result.success(userState!!)
        override suspend fun register(name: String, email: String, pass: String): Result<User> = Result.success(userState!!)
        override suspend fun loginAsGuest(): Result<User> = Result.success(userState!!)
        override suspend fun logout(): Result<Unit> {
            userState = null
            _authState.value = AuthState.Unauthenticated
            return Result.success(Unit)
        }

        override fun updateUserRole(role: String) {
            userState = userState?.copy(role = role)
        }
    }
}
