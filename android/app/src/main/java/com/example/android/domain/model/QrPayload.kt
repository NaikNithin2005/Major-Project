package com.example.android.domain.model

data class QrPayload(
    val rawPayload: String,
    val payloadType: QrPayloadType,
    val source: String, // "CAMERA" or "GALLERY"
    val timestamp: Long = System.currentTimeMillis()
)
