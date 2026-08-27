package com.example.android.domain.model

/**
 * Domain representation of raw SMS metadata extracted from Android SMS broadcast.
 * Adheres to data minimization: only used in-memory during processing pipeline.
 */
data class RawSms(
    val messageId: String = System.currentTimeMillis().toString(),
    val sender: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis()
)
