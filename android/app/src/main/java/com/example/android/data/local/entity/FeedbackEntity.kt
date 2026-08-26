package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val threatId: String,
    val timestamp: Long,
    val userCategory: String, // "False Positive", "False Negative", "Confirmed Threat"
    val comment: String,
    val isSubmitted: Boolean = false
)
