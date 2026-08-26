package com.example.android.domain.model

data class SystemSettings(
    val realtimeSmsProtection: Boolean = true,
    val realtimeQrScanner: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val biometricLock: Boolean = false,
    val darkTheme: Boolean = true
)
