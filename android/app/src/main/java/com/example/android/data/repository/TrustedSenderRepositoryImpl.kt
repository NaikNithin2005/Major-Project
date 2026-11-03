package com.example.android.data.repository

import com.example.android.data.local.dao.TrustedSenderDao
import com.example.android.data.local.entity.TrustedSenderEntity
import com.example.android.data.remote.api.BackendApiService
import com.example.android.data.remote.dto.TrustSenderRequestDto
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.model.TrustedSender
import com.example.android.domain.repository.AuthRepository
import com.example.android.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrustedSenderRepositoryImpl(
    private val dao: TrustedSenderDao,
    private val backendApiService: BackendApiService? = null,
    private val firestoreDataSource: FirestoreDataSource? = null,
    private val authRepository: AuthRepository? = null
) : TrustedSenderRepository {

    override fun observeTrustedSenders(): Flow<List<TrustedSender>> {
        return dao.observeAllTrustedSenders().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getTrustedSenders(): List<TrustedSender> {
        return dao.getAllTrustedSenders().map { it.toDomain() }
    }

    override suspend fun isTrusted(sender: String): Boolean {
        if (sender.isBlank()) return false
        return dao.isTrusted(sender.trim().uppercase())
    }

    override suspend fun addTrustedSender(sender: String, userId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = sender.trim().uppercase()
        val currentUid = userId ?: authRepository?.getCurrentUser()?.uid
        try {
            val entity = TrustedSenderEntity(
                sender = normalized,
                addedTimestamp = System.currentTimeMillis(),
                trustLevel = "VERIFIED",
                userId = currentUid
            )
            dao.insertTrustedSender(entity)

            // Dual-sync: Remote backend API
            if (backendApiService != null) {
                try {
                    backendApiService.trustSender(TrustSenderRequestDto(sender_id = normalized, display_name = normalized, reason = "Trusted via Android"))
                } catch (ignored: Exception) {}
            }

            // Dual-sync: Cloud Firestore
            if (firestoreDataSource != null && currentUid != null && currentUid.isNotBlank()) {
                try {
                    firestoreDataSource.syncTrustedSender(currentUid, normalized, true, "Trusted via Android")
                } catch (ignored: Exception) {}
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTrustedSender(sender: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalized = sender.trim().uppercase()
        val currentUid = authRepository?.getCurrentUser()?.uid
        try {
            dao.removeTrustedSender(normalized)

            // Dual-sync: Remote backend API
            if (backendApiService != null) {
                try {
                    backendApiService.removeTrustedSender(normalized)
                } catch (ignored: Exception) {}
            }

            // Dual-sync: Cloud Firestore
            if (firestoreDataSource != null && currentUid != null && currentUid.isNotBlank()) {
                try {
                    firestoreDataSource.syncTrustedSender(currentUid, normalized, false)
                } catch (ignored: Exception) {}
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearAll(): Result<Unit> {
        return try {
            dao.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncTrustedSenders(userId: String?): Result<Int> = withContext(Dispatchers.IO) {
        val currentUid = userId ?: authRepository?.getCurrentUser()?.uid
        var syncCount = 0

        // 1. Sync from Backend API
        if (backendApiService != null) {
            try {
                val res = backendApiService.getTrustedSenders()
                if (res.isSuccessful && res.body() != null) {
                    val serverSenders = res.body()!!
                    for (s in serverSenders) {
                        val normalized = s.senderId.trim().uppercase()
                        dao.insertTrustedSender(
                            TrustedSenderEntity(
                                sender = normalized,
                                addedTimestamp = System.currentTimeMillis(),
                                trustLevel = "VERIFIED",
                                userId = currentUid
                            )
                        )
                        syncCount++
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Sync from Firestore
        if (firestoreDataSource != null && currentUid != null && currentUid.isNotBlank()) {
            try {
                val fsRes = firestoreDataSource.fetchUserTrustedSenders(currentUid)
                if (fsRes.isSuccess) {
                    val list = fsRes.getOrNull() ?: emptyList()
                    for (item in list) {
                        val senderId = (item["senderId"] as? String) ?: continue
                        val normalized = senderId.trim().uppercase()
                        dao.insertTrustedSender(
                            TrustedSenderEntity(
                                sender = normalized,
                                addedTimestamp = System.currentTimeMillis(),
                                trustLevel = "VERIFIED",
                                userId = currentUid
                            )
                        )
                        syncCount++
                    }
                }
            } catch (ignored: Exception) {}
        }

        Result.success(syncCount)
    }

    private fun TrustedSenderEntity.toDomain() = TrustedSender(
        sender = sender,
        addedTimestamp = addedTimestamp,
        trustLevel = trustLevel,
        userId = userId
    )
}
