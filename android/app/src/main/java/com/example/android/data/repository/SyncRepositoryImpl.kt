package com.example.android.data.repository

import com.example.android.data.local.dao.SyncQueueDao
import com.example.android.data.local.entity.SyncQueueEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.DetectionSyncRequest
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.repository.SyncRepository
import com.example.android.domain.repository.SyncSummary
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.android.data.local.dao.ThreatHistoryDao
import com.example.android.data.local.entity.ThreatHistoryEntity
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.min

class SyncRepositoryImpl(
    private val syncQueueDao: SyncQueueDao,
    private val backendApiService: BackendApiService,
    private val authRepository: AuthRepository,
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource(),
    private val gson: Gson = Gson(),
    private val threatHistoryDao: ThreatHistoryDao? = null
) : SyncRepository {

    override suspend fun enqueueDetection(request: DetectionSyncRequest): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Check if already enqueued with same clientEventId
            val existing = syncQueueDao.getByClientEventId(request.clientEventId)
            if (existing != null) {
                return@withContext Result.success(existing.id)
            }

            // Serialize data-minimized payload (NO raw SMS or personal credentials)
            val payloadJson = gson.toJson(request)
            val currentUserId = authRepository.getCurrentUser()?.uid

            val entity = SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                clientEventId = request.clientEventId,
                eventType = request.eventType,
                payloadJson = payloadJson,
                createdAt = System.currentTimeMillis(),
                status = SyncQueueEntity.STATUS_PENDING,
                userId = currentUserId
            )

            syncQueueDao.insert(entity)

            // If user is authenticated, attempt immediate non-blocking sync to Cloud Firestore
            if (!currentUserId.isNullOrBlank()) {
                try {
                    val threatMap = buildFirestoreThreatMap(request, currentUserId)
                    val fsResult = firestoreDataSource.saveThreatEvent(currentUserId, threatMap)
                    if (fsResult.isSuccess) {
                        threatHistoryDao?.updateSyncStatus(request.clientEventId, ThreatHistoryEntity.STATUS_SYNCED)
                        threatHistoryDao?.updatePendingOperation(request.clientEventId, ThreatHistoryEntity.OP_NONE)
                    }
                } catch (ignored: Exception) {
                    // Non-blocking fallback; WorkManager processSyncQueue will retry
                }
            }

            Result.success(entity.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun processSyncQueue(): SyncSummary = withContext(Dispatchers.IO) {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser == null || currentUser.isGuest) {
            return@withContext SyncSummary(0, 0, 0, 0)
        }
        val currentUserId = currentUser.uid

        try {
            syncQueueDao.resetAllPendingForImmediateSync()
        } catch (ignored: Exception) {}

        var totalSynced = 0
        var totalFailed = 0
        var totalRetried = 0
        var totalProcessed = 0

        var hasMore = true
        var loopIteration = 0

        while (hasMore && loopIteration < 10) {
            loopIteration++
            val now = System.currentTimeMillis()
            val pendingItems = syncQueueDao.getPendingItems(now = now, limit = 50, userId = currentUserId)
            if (pendingItems.isEmpty()) {
                hasMore = false
                break
            }

            totalProcessed += pendingItems.size

            for (item in pendingItems) {
                if (item.userId != null && item.userId != currentUserId) {
                    continue
                }

                syncQueueDao.markSyncing(item.id, now)

                try {
                    val request = gson.fromJson(item.payloadJson, DetectionSyncRequest::class.java)

                    // 1. Direct Cloud Firestore sync: users/{uid}/threats/{threatId}
                    var firestoreSuccess = false
                    try {
                        val threatMap = buildFirestoreThreatMap(request, currentUserId)
                        val fsRes = firestoreDataSource.saveThreatEvent(currentUserId, threatMap)
                        firestoreSuccess = fsRes.isSuccess
                    } catch (ignored: Exception) {
                    }

                    // 2. Dual-sync with Backend API Service (non-blocking with strict 2s timeout)
                    var backendSuccess = false
                    var serverEventId = item.clientEventId
                    var isPermanentError = false
                    var errorMsg = ""

                    try {
                        withTimeoutOrNull(2000L) {
                            val response = backendApiService.syncDetection(request)
                            if (response.isSuccessful && response.body() != null) {
                                backendSuccess = true
                                serverEventId = response.body()!!.id
                            } else {
                                val code = response.code()
                                val errorBody = response.errorBody()?.string() ?: "HTTP error $code"
                                if (code == 400 || code == 422) {
                                    isPermanentError = true
                                    errorMsg = "Non-retryable: $errorBody"
                                } else if (code == 401) {
                                    authRepository.getIdToken(forceRefresh = true)
                                    errorMsg = "Unauthorized (401)"
                                } else {
                                    errorMsg = "HTTP $code: $errorBody"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Backend server unreachable"
                    }

                    if (firestoreSuccess || backendSuccess) {
                        syncQueueDao.markSynced(
                            id = item.id,
                            serverEventId = serverEventId,
                            syncedAt = System.currentTimeMillis()
                        )
                        threatHistoryDao?.updateSyncStatus(item.clientEventId, ThreatHistoryEntity.STATUS_SYNCED)
                        threatHistoryDao?.updatePendingOperation(item.clientEventId, ThreatHistoryEntity.OP_NONE)
                        totalSynced++
                    } else if (isPermanentError) {
                        syncQueueDao.markFailed(item.id, errorMsg, System.currentTimeMillis())
                        threatHistoryDao?.updateSyncStatus(item.clientEventId, ThreatHistoryEntity.STATUS_FAILED)
                        totalFailed++
                    } else {
                        val attempts = item.attemptCount + 1
                        val backoffMs = calculateBackoff(attempts)
                        syncQueueDao.markRetry(
                            id = item.id,
                            attemptCount = attempts,
                            nextAttemptAt = System.currentTimeMillis() + backoffMs,
                            error = errorMsg.ifBlank { "Network or server unavailable" },
                            timestamp = System.currentTimeMillis()
                        )
                        threatHistoryDao?.updateSyncStatus(item.clientEventId, ThreatHistoryEntity.STATUS_PENDING)
                        totalRetried++
                    }
                } catch (e: Exception) {
                    syncQueueDao.markFailed(item.id, "Payload error: ${e.message}", System.currentTimeMillis())
                    totalFailed++
                }
            }
        }

        SyncSummary(
            totalProcessed = totalProcessed,
            syncedCount = totalSynced,
            failedCount = totalFailed,
            retryCount = totalRetried
        )
    }

    override fun observePendingCount(): Flow<Int> {
        return syncQueueDao.observePendingCount()
    }

    override fun observeSyncQueue(): Flow<List<SyncQueueEntity>> {
        return syncQueueDao.observeAll()
    }

    override suspend fun fetchAndApplyServerProfile(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = backendApiService.getCurrentUserProfile()
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                authRepository.updateUserRole(profile.role)

                val currentUser = authRepository.getCurrentUser()
                if (currentUser != null) {
                    Result.success(currentUser.copy(role = profile.role))
                } else {
                    Result.success(
                        User(
                            uid = profile.firebaseUid,
                            email = profile.email,
                            displayName = profile.displayName ?: "User",
                            isGuest = false,
                            role = profile.role
                        )
                    )
                }
            } else {
                Result.failure(IOException("Server returned HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreUserCloudHistory(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }

        try {
            val cloudResult = firestoreDataSource.fetchUserThreats(userId)
            val cloudDocs = cloudResult.getOrThrow()
            if (cloudDocs.isEmpty()) {
                return@withContext Result.success(0)
            }

            val entities = cloudDocs.mapNotNull { doc ->
                try {
                    val id = (doc["id"] as? String)
                        ?: (doc["scanId"] as? String)
                        ?: return@mapNotNull null

                    val timestamp = when (val dt = doc["detectedAt"]) {
                        is Timestamp -> dt.toDate().time
                        is Long -> dt
                        is Double -> dt.toLong()
                        is Map<*, *> -> {
                            val seconds = (dt["_seconds"] as? Number)?.toLong() ?: 0L
                            seconds * 1000L
                        }
                        else -> System.currentTimeMillis()
                    }

                    val source = (doc["sourceType"] as? String)
                        ?: (doc["eventType"] as? String)
                        ?: "SMS"

                    val sender = (doc["sender"] as? String)
                        ?: (if (source.contains("QR", ignoreCase = true)) "QR Code Scan" else "Unknown Sender")

                    val riskScore = when (val rs = doc["riskScore"]) {
                        is Number -> rs.toInt()
                        is String -> rs.toIntOrNull() ?: 0
                        else -> 0
                    }.coerceIn(0, 100)

                    val category = (doc["classification"] as? String)
                        ?: (doc["threatType"] as? String)
                        ?: (doc["severity"] as? String)
                        ?: "Safe"

                    val actionTaken = (doc["actionTaken"] as? String) ?: "ALLOWED"
                    val details = (doc["explanation"] as? String)
                        ?: (doc["sanitizedDescription"] as? String)
                        ?: ""

                    val rawContent = (doc["rawContent"] as? String)
                        ?: (doc["sanitizedPreview"] as? String)

                    val extractedUrl = (doc["decodedUrl"] as? String)
                        ?: (doc["extractedUrl"] as? String)

                    val severity = (doc["severity"] as? String) ?: "LOW"
                    val reviewStatus = (doc["reviewStatus"] as? String) ?: "REVIEWED"
                    val retentionMode = (doc["contentRetentionMode"] as? String) ?: "LOCAL_SAVED"

                    val isQuarantined = (doc["isQuarantined"] as? Boolean)
                        ?: actionTaken.equals("QUARANTINED", ignoreCase = true)
                        ?: actionTaken.equals("BLOCKED", ignoreCase = true)

                    ThreatHistoryEntity(
                        id = id,
                        timestamp = timestamp,
                        source = source,
                        sender = sender,
                        riskScore = riskScore,
                        category = category,
                        actionTaken = actionTaken,
                        details = details,
                        rawContent = rawContent,
                        extractedUrl = extractedUrl,
                        payloadType = source,
                        severity = severity,
                        modelVersion = (doc["modelVersion"] as? Map<*, *>)?.get("detector") as? String ?: "squish-detector-v1",
                        isQuarantined = isQuarantined,
                        reviewStatus = reviewStatus,
                        contentRetentionMode = retentionMode,
                        pendingOperation = ThreatHistoryEntity.OP_NONE,
                        syncStatus = ThreatHistoryEntity.STATUS_SYNCED,
                        retryCount = 0,
                        userId = userId
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (entities.isNotEmpty() && threatHistoryDao != null) {
                threatHistoryDao.insertThreats(entities)
            }

            Result.success(entities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFirestoreThreatMap(request: DetectionSyncRequest, userId: String): Map<String, Any> {
        val date = try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            isoFormat.parse(request.timestamp) ?: Date()
        } catch (e: Exception) {
            Date()
        }

        val threatType = if (request.threatCategory.equals("SAFE", ignoreCase = true)) {
            "SAFE"
        } else if (request.eventType.equals("SMS", ignoreCase = true)) {
            "SMISHING"
        } else {
            "QUISHING"
        }

        val classification = if (request.threatCategory.equals("SAFE", ignoreCase = true)) {
            "SAFE"
        } else if (request.eventType.equals("SMS", ignoreCase = true)) {
            "SMISHING"
        } else {
            "QUISHING"
        }

        val sanitizedDesc = request.reasons?.firstOrNull() ?: "${request.eventType} threat detection event"
        val preview = request.sanitizedPreview ?: sanitizedDesc

        val map = mutableMapOf<String, Any>(
            "id" to request.clientEventId,
            "scanId" to request.clientEventId,
            "eventType" to request.eventType,
            "threatType" to threatType,
            "threatCategory" to request.threatCategory,
            "threat_category" to request.threatCategory,
            "severity" to request.threatCategory,
            "classification" to classification,
            "riskScore" to request.riskScore,
            "confidence" to 0.95,
            "sourceType" to request.eventType,
            "sanitizedPreview" to preview,
            "sanitizedDescription" to sanitizedDesc,
            "contentRetentionMode" to (request.contentRetentionMode ?: "LOCAL_SAVED"),
            "detectedAt" to Timestamp(date),
            "modelVersion" to (request.modelVersions ?: mapOf("detector" to "squish-detector-v1")),
            "detectionIndicators" to (request.reasons ?: emptyList<String>()),
            "explanation" to (request.explanation ?: request.reasons?.joinToString("; ") ?: sanitizedDesc),
            "actionTaken" to request.recommendedAction,
            "reviewStatus" to (request.reviewStatus ?: "NEW"),
            "createdAt" to FieldValue.serverTimestamp(),
            "userId" to userId,
            "rawContent" to (request.rawContent ?: preview),
            "sender" to (request.sender ?: (if (request.eventType.equals("SMS", ignoreCase = true)) "Unknown Sender" else "QR Scan"))
        )

        request.decodedUrl?.let { map["decodedUrl"] = it }
        request.urlMetadata?.let { map["urlMetadata"] = it }
        request.qrMetadata?.let { map["qrMetadata"] = it }

        return map
    }

    override suspend fun syncAllLocalThreatsToFirestore(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }

        try {
            val localThreats = threatHistoryDao?.getAllThreats() ?: emptyList()
            if (localThreats.isEmpty()) {
                return@withContext Result.success(0)
            }

            val threatMaps = localThreats.map { threat ->
                val date = Date(threat.timestamp)
                val isSms = threat.source.contains("SMS", ignoreCase = true)
                val eventType = if (isSms) "SMS" else "QR"

                val isHigh = threat.riskScore >= 70 ||
                        threat.category.contains("HIGH", ignoreCase = true) ||
                        threat.category.contains("MALICIOUS", ignoreCase = true) ||
                        threat.category.contains("SMISHING", ignoreCase = true) ||
                        threat.category.contains("QUISHING", ignoreCase = true)

                val isAttention = !isHigh && (
                        threat.riskScore >= 40 ||
                        threat.category.contains("MEDIUM", ignoreCase = true) ||
                        threat.category.contains("SUSPICIOUS", ignoreCase = true) ||
                        threat.category.contains("LOW", ignoreCase = true) ||
                        threat.category.contains("ATTENTION", ignoreCase = true)
                )

                val normalizedCategory = when {
                    isHigh -> "HIGH"
                    isAttention -> "MEDIUM"
                    else -> "SAFE"
                }

                val threatType = if (normalizedCategory == "SAFE") "SAFE" else if (isSms) "SMISHING" else "QUISHING"
                val classification = if (normalizedCategory == "SAFE") "SAFE" else if (isSms) "SMISHING" else "QUISHING"
                val sanitizedDesc = threat.details?.ifBlank { "$eventType scan event" } ?: "$eventType scan event"
                val displaySender = threat.sender ?: (if (isSms) "Unknown Sender" else "QR Scan")
                val preview = "${if (isSms) "SMS from " else "QR Scan: "}$displaySender"

                val map = mutableMapOf<String, Any>(
                    "id" to threat.id,
                    "scanId" to threat.id,
                    "eventType" to eventType,
                    "threatType" to threatType,
                    "threatCategory" to normalizedCategory,
                    "threat_category" to normalizedCategory,
                    "severity" to normalizedCategory,
                    "classification" to classification,
                    "riskScore" to threat.riskScore,
                    "confidence" to 0.95,
                    "sourceType" to eventType,
                    "sanitizedPreview" to preview,
                    "sanitizedDescription" to sanitizedDesc,
                    "contentRetentionMode" to (threat.contentRetentionMode?.ifBlank { "LOCAL_SAVED" } ?: "LOCAL_SAVED"),
                    "detectedAt" to Timestamp(date),
                    "modelVersion" to mapOf("detector" to (threat.modelVersion?.ifBlank { "squish-detector-v1" } ?: "squish-detector-v1")),
                    "detectionIndicators" to (if (!threat.details.isNullOrBlank()) threat.details.split(";").map { it.trim() } else emptyList<String>()),
                    "explanation" to sanitizedDesc,
                    "actionTaken" to (threat.actionTaken?.ifBlank { "PROCEED_SAFELY" } ?: "PROCEED_SAFELY"),
                    "reviewStatus" to (threat.reviewStatus?.ifBlank { "NEW" } ?: "NEW"),
                    "createdAt" to Timestamp(date),
                    "userId" to userId,
                    "rawContent" to (threat.rawContent ?: preview),
                    "sender" to displaySender
                )

                threat.extractedUrl?.let {
                    if (it.isNotBlank()) {
                        map["decodedUrl"] = it
                        map["extractedUrl"] = it
                    }
                }

                map
            }

            val result = firestoreDataSource.saveThreatEventsBatch(userId, threatMaps)

            if (result.isSuccess) {
                for (threat in localThreats) {
                    threatHistoryDao?.updateSyncStatus(threat.id, ThreatHistoryEntity.STATUS_SYNCED)
                    threatHistoryDao?.updatePendingOperation(threat.id, ThreatHistoryEntity.OP_NONE)
                    syncQueueDao.getByClientEventId(threat.id)?.let { queueItem ->
                        syncQueueDao.markSynced(queueItem.id, threat.id, System.currentTimeMillis())
                    }
                }
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        // Exponential backoff: 2s, 4s, 8s, 16s, 32s... capped at 5 minutes
        val exponent = attempt.coerceIn(1, 8)
        val delay = 2000L * (1 shl exponent)
        return min(300_000L, delay)
    }
}
