package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_history")
data class ThreatHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val source: String,                          // "SMS", "QR", "URL", "MANUAL_TEXT"
    val sender: String,                          // Phone number, Domain, or Identifier
    val riskScore: Int,                          // 0 - 100
    val category: String,                        // "Smishing", "Quishing", "Malicious URL", "Safe"
    val actionTaken: String,                     // "BLOCKED", "WARNED", "ALLOWED", "RESOLVED"
    val details: String,                         // Context summary & explanation reasons
    val rawContent: String? = null,              // Original scanned message body or decoded QR payload
    val extractedUrl: String? = null,            // Extracted / target URL if present
    val payloadType: String? = null,             // "SMS", "URL", "WIFI", "CONTACT", "EMAIL", "TEXT"
    val severity: String? = null,                // "CRITICAL", "HIGH", "MEDIUM", "LOW", "SAFE"
    val modelVersion: String? = null,            // e.g. "tinybert-smishing-v2", "xgboost-url-v2"
    val isQuarantined: Boolean = false,          // Quarantine status
    val reviewStatus: String = "NEW",            // "NEW", "REVIEWED", "RESOLVED", "BLOCKED"
    val contentRetentionMode: String = "LOCAL_SAVED" // "LOCAL_SAVED", "SESSION_ONLY", "NOT_RETAINED"
)
