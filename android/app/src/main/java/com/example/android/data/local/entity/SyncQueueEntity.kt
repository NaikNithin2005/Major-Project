package com.example.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Local Room entity representing pending, retrying, and synced detection events.
 * Guarantees that threat reports survive process death, reboots, and network offline conditions.
 * Enforces privacy: NO raw SMS content is stored in this queue.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(index = true)
    val clientEventId: String,

    val eventType: String, // "SMS" or "QR"

    val payloadJson: String, // Minimized telemetry JSON

    val createdAt: Long = System.currentTimeMillis(),

    val attemptCount: Int = 0,

    val lastAttemptAt: Long = 0L,

    val nextAttemptAt: Long = 0L,

    @ColumnInfo(index = true)
    val status: String = STATUS_PENDING, // "PENDING", "SYNCING", "SYNCED", "FAILED"

    val lastError: String? = null,

    val serverEventId: String? = null,

    val syncedAt: Long? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}
