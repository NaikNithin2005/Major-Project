package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val threatId: String,
    val timestamp: Long,
    val userCategory: String, // e.g. "Legitimate Bank/Service", "Trusted Sender", "Personal Message", "Confirmed Scam"
    val comment: String,
    val isSubmitted: Boolean = false,
    val sender: String = "",
    val originalPrediction: String = "",
    val correctedClassification: String = "",
    val userId: String? = null
)
