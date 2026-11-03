package com.example.android.domain.repository

import com.example.android.domain.model.TrustedSender
import kotlinx.coroutines.flow.Flow

interface TrustedSenderRepository {
    fun observeTrustedSenders(): Flow<List<TrustedSender>>
    suspend fun getTrustedSenders(): List<TrustedSender>
    suspend fun isTrusted(sender: String): Boolean
    suspend fun addTrustedSender(sender: String, userId: String? = null): Result<Unit>
    suspend fun removeTrustedSender(sender: String): Result<Unit>
    suspend fun clearAll(): Result<Unit>
    suspend fun syncTrustedSenders(userId: String? = null): Result<Int>
}
