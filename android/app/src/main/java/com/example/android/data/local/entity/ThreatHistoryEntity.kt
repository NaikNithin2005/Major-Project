package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "threat_history")
data class ThreatHistoryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val source: String,       // "SMS", "QR", "URL"
    val sender: String,       // Phone number, Domain, or Identifier
    val riskScore: Int,       // 0 - 100
    val category: String,     // "Smishing", "Quishing", "Malicious URL", "Safe"
    val actionTaken: String,  // "BLOCKED", "WARNED", "ALLOWED"
    val details: String       // Context summary without raw message text
)
