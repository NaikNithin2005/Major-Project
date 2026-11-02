package com.example.android.domain.model

data class SystemSettings(
    val realtimeSmsProtection: Boolean = true,
    val realtimeQrScanner: Boolean = true,
    val urlAnalysisEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val notifyHighSeverity: Boolean = true,
    val notifyMediumSeverity: Boolean = true,
    val notifyDailyTips: Boolean = true,
    val showNotificationPreviews: Boolean = false,
    val aiModelEnabled: Boolean = true,
    val optInAiLearning: Boolean = false,
    val cloudSyncEnabled: Boolean = true,
    val biometricLock: Boolean = false,
    val darkTheme: Boolean = true,
    val threatIntelligenceSharingEnabled: Boolean = false
)
