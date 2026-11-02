package com.example.android.data.local.dao

import androidx.room.*
import com.example.android.data.local.entity.FeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackEntity)

    @Query("SELECT * FROM feedback WHERE isSubmitted = 0 ORDER BY timestamp ASC")
    suspend fun getUnsubmittedFeedback(): List<FeedbackEntity>

    @Query("SELECT * FROM feedback ORDER BY timestamp DESC")
    fun observeAllFeedback(): Flow<List<FeedbackEntity>>

    @Query("SELECT * FROM feedback WHERE sender = :sender ORDER BY timestamp DESC")
    suspend fun getFeedbackForSender(sender: String): List<FeedbackEntity>

    @Query("SELECT * FROM feedback WHERE threatId = :threatId LIMIT 1")
    suspend fun getFeedbackForThreat(threatId: String): FeedbackEntity?

    @Query("UPDATE feedback SET isSubmitted = 1 WHERE id = :id")
    suspend fun markSubmitted(id: String)

    @Query("DELETE FROM feedback WHERE id = :id")
    suspend fun deleteFeedback(id: String)

    @Query("DELETE FROM feedback WHERE threatId = :threatId")
    suspend fun deleteFeedbackForThreat(threatId: String)
}
