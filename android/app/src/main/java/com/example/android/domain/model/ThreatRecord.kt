package com.example.android.domain.model

data class ThreatRecord(
    val id: String,
    val timestamp: Long,
    val source: String,
    val sender: String,
    val riskScore: Int,
    val category: String,
    val actionTaken: String,
    val details: String,
    val rawContent: String? = null,
    val extractedUrl: String? = null,
    val payloadType: String? = null,
    val severity: String? = null,
    val modelVersion: String? = null,
    val isQuarantined: Boolean = false,
    val reviewStatus: String = "NEW",
    val contentRetentionMode: String = "LOCAL_SAVED"
)
