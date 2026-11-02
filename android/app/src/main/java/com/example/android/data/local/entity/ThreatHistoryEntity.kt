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
    val contentRetentionMode: String = "LOCAL_SAVED", // "LOCAL_SAVED", "SESSION_ONLY", "NOT_RETAINED"
    val pendingOperation: String = OP_NONE,      // "NONE", "REMOTE_REPUTATION_CHECK", "TELEMETRY_SYNC"
    val syncStatus: String = STATUS_SYNCED,      // "PENDING", "SYNCING", "SYNCED", "FAILED"
    val retryCount: Int = 0,                     // Retry counter for background sync
    val userId: String? = null                   // Authenticated account scope for cloud synchronization
) {
    companion object {
        const val OP_NONE = "NONE"
        const val OP_REMOTE_REPUTATION_CHECK = "REMOTE_REPUTATION_CHECK"
        const val OP_TELEMETRY_SYNC = "TELEMETRY_SYNC"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}

