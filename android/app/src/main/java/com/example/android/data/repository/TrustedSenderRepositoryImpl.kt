package com.example.android.data.repository

import com.example.android.data.local.dao.TrustedSenderDao
import com.example.android.data.local.entity.TrustedSenderEntity
import com.example.android.domain.model.TrustedSender
import com.example.android.domain.repository.TrustedSenderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrustedSenderRepositoryImpl(
    private val dao: TrustedSenderDao
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
        return dao.isTrusted(sender.trim())
    }

    override suspend fun addTrustedSender(sender: String, userId: String?): Result<Unit> {
        return try {
            val entity = TrustedSenderEntity(
                sender = sender.trim().uppercase(),
                addedTimestamp = System.currentTimeMillis(),
                trustLevel = "VERIFIED",
                userId = userId
            )
            dao.insertTrustedSender(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTrustedSender(sender: String): Result<Unit> {
        return try {
            dao.removeTrustedSender(sender.trim())
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

    private fun TrustedSenderEntity.toDomain() = TrustedSender(
        sender = sender,
        addedTimestamp = addedTimestamp,
        trustLevel = trustLevel,
        userId = userId
    )
}
