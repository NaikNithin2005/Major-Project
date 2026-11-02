package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_senders")
data class TrustedSenderEntity(
    @PrimaryKey
    val sender: String,
    val addedTimestamp: Long,
    val trustLevel: String = "VERIFIED",
    val userId: String? = null
)
