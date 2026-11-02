package com.example.android.domain.repository

import com.example.android.domain.model.UserFeedback
import kotlinx.coroutines.flow.Flow

interface FeedbackRepository {
    suspend fun submitFeedback(feedback: UserFeedback): Result<Unit>
    suspend fun getUnsubmittedFeedback(): Result<List<UserFeedback>>
    fun observeAllFeedback(): Flow<List<UserFeedback>>
    suspend fun getFeedbackForSender(sender: String): List<UserFeedback>
    suspend fun getFeedbackForThreat(threatId: String): UserFeedback?
    suspend fun markAsSubmitted(id: String): Result<Unit>
    suspend fun deleteFeedback(id: String): Result<Unit>
    suspend fun deleteFeedbackForThreat(threatId: String): Result<Unit>
}
