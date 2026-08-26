package com.example.android.domain.model

data class ThreatRecord(
    val id: String,
    val timestamp: Long,
    val source: String,
    val sender: String,
    val riskScore: Int,
    val category: String,
    val actionTaken: String,
    val details: String
)
