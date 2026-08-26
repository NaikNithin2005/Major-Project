package com.example.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val settingKey: String,
    val settingValue: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
