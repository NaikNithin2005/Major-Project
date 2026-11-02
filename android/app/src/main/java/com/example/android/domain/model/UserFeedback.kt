package com.example.android.domain.model

data class UserFeedback(
    val id: String,
    val threatId: String,
    val timestamp: Long,
    val userCategory: String,
    val comment: String,
    val isSubmitted: Boolean,
    val sender: String = "",
    val originalPrediction: String = "",
    val correctedClassification: String = "",
    val userId: String? = null
)
