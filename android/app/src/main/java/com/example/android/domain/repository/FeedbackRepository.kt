package com.example.android.domain.repository

import com.example.android.domain.model.UserFeedback
import kotlinx.coroutines.flow.Flow

interface FeedbackRepository {
    suspend fun submitFeedback(feedback: UserFeedback): Result<Unit>
    suspend fun getUnsubmittedFeedback(): Result<List<UserFeedback>>
    fun observeAllFeedback(): Flow<List<UserFeedback>>
    suspend fun markAsSubmitted(id: String): Result<Unit>
}
