package com.example.android.data.repository

import com.example.android.data.local.dao.FeedbackDao
import com.example.android.data.local.entity.FeedbackEntity
import com.example.android.domain.model.UserFeedback
import com.example.android.domain.repository.FeedbackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FeedbackRepositoryImpl(
    private val dao: FeedbackDao
) : FeedbackRepository {

    override suspend fun submitFeedback(feedback: UserFeedback): Result<Unit> {
        return try {
            dao.insertFeedback(feedback.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUnsubmittedFeedback(): Result<List<UserFeedback>> {
        return try {
            val list = dao.getUnsubmittedFeedback().map { it.toDomain() }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeAllFeedback(): Flow<List<UserFeedback>> {
        return dao.observeAllFeedback().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun markAsSubmitted(id: String): Result<Unit> {
        return try {
            dao.markSubmitted(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun UserFeedback.toEntity() = FeedbackEntity(
        id = id,
        threatId = threatId,
        timestamp = timestamp,
        userCategory = userCategory,
        comment = comment,
        isSubmitted = isSubmitted
    )

    private fun FeedbackEntity.toDomain() = UserFeedback(
        id = id,
        threatId = threatId,
        timestamp = timestamp,
        userCategory = userCategory,
        comment = comment,
        isSubmitted = isSubmitted
    )
}
