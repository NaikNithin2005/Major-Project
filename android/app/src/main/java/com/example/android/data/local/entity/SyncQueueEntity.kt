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

    val syncedAt: Long? = null,

    val userId: String? = null,

    val pendingOperation: String = OP_TELEMETRY_SYNC,

    val errorCategory: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"

        const val OP_TELEMETRY_SYNC = "TELEMETRY_SYNC"
        const val OP_REMOTE_URL_CHECK = "REMOTE_URL_CHECK"

        const val ERROR_NETWORK_OFFLINE = "NETWORK_OFFLINE"
        const val ERROR_AUTH_REQUIRED = "AUTH_REQUIRED"
        const val ERROR_SERVER = "SERVER_ERROR"
        const val ERROR_PERMANENT = "PERMANENT_REJECT"
    }
}
