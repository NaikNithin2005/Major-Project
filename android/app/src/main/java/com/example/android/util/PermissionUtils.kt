package com.example.android.util

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Exact status labels as specified by the Squish security readiness policy.
 */
enum class ProtectionStatusLabel(val label: String) {
    ENABLED("Enabled"),
    NOT_GRANTED("Not granted"),
    ACTION_REQUIRED("Action required"),
    NOTIFICATIONS_DISABLED("Notifications disabled"),
    DEFAULT_SMS_REQUIRED("Default SMS app required"),
    MODEL_UNAVAILABLE("Model unavailable"),
    LIMITED_PROTECTION("Limited protection")
}

data class ProtectionReadinessSummary(
    val smsStatus: ProtectionStatusLabel,
    val defaultSmsStatus: ProtectionStatusLabel,
    val notificationStatus: ProtectionStatusLabel,
    val modelStatus: ProtectionStatusLabel,
    val cameraStatus: ProtectionStatusLabel,
    val overallStatus: ProtectionStatusLabel,
    val isOperational: Boolean,
    val statusDescription: String
)

object PermissionUtils {

    fun hasSmsReceivePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsReadPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsPermission(context: Context): Boolean {
        return hasSmsReceivePermission(context) && hasSmsReadPermission(context)
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun areNotificationChannelsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return true
            val highChannel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_THREAT_HIGH)
            if (highChannel != null && highChannel.importance == NotificationManager.IMPORTANCE_NONE) {
                return false
            }
        }
        return true
    }

    /**
     * Determines whether Squish holds the system Default SMS Handler role.
     * On Android 10+ (Q+), uses official RoleManager.
     * On earlier versions, inspects Telephony.Sms.getDefaultSmsPackage.
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
            } else {
                Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates the official platform Intent to request the Default SMS Handler role.
     */
    fun createDefaultSmsRoleIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                } else null
            } else {
                Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if the on-device TinyBERT ONNX model asset is present and accessible.
     */
    fun isTinyBertModelAvailable(context: Context): Boolean {
        return try {
            context.assets.open("models/tinybert_smishing.onnx").use { true }
        } catch (_: Exception) {
            false
        }
    }

    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasSmsPermission(context) && hasCameraPermission(context) && hasNotificationPermission(context)
    }

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    /**
     * Computes the complete readiness status with authentic status labels.
     */
    fun getProtectionReadiness(context: Context): ProtectionReadinessSummary {
        val hasSms = hasSmsPermission(context)
        val isDefault = isDefaultSmsApp(context)
        val notifPerm = hasNotificationPermission(context)
        val notifChannels = areNotificationChannelsEnabled(context)
        val modelOk = isTinyBertModelAvailable(context)
        val cameraOk = hasCameraPermission(context)

        val smsStatus = when {
            hasSms -> ProtectionStatusLabel.ENABLED
            hasSmsReceivePermission(context) || hasSmsReadPermission(context) -> ProtectionStatusLabel.ACTION_REQUIRED
            else -> ProtectionStatusLabel.NOT_GRANTED
        }

        val defaultSmsStatus = when {
            isDefault -> ProtectionStatusLabel.ENABLED
            else -> ProtectionStatusLabel.DEFAULT_SMS_REQUIRED
        }

        val notifStatus = when {
            !notifPerm -> ProtectionStatusLabel.NOT_GRANTED
            !notifChannels -> ProtectionStatusLabel.NOTIFICATIONS_DISABLED
            else -> ProtectionStatusLabel.ENABLED
        }

        val modelStatus = if (modelOk) ProtectionStatusLabel.ENABLED else ProtectionStatusLabel.MODEL_UNAVAILABLE
        val cameraStatus = if (cameraOk) ProtectionStatusLabel.ENABLED else ProtectionStatusLabel.NOT_GRANTED

        val overallStatus = when {
            smsStatus == ProtectionStatusLabel.ENABLED && notifStatus == ProtectionStatusLabel.ENABLED && modelStatus == ProtectionStatusLabel.ENABLED ->
                ProtectionStatusLabel.ENABLED
            hasSms || cameraOk ->
                ProtectionStatusLabel.LIMITED_PROTECTION
            else ->
                ProtectionStatusLabel.ACTION_REQUIRED
        }

        val isOperational = hasSms || cameraOk

        val desc = when (overallStatus) {
            ProtectionStatusLabel.ENABLED -> "All critical protection modules are active and operational."
            ProtectionStatusLabel.LIMITED_PROTECTION -> "Operating in limited protection mode. Some automated real-time capabilities are unavailable."
            else -> "Action required to activate real-time threat monitoring."
        }

        return ProtectionReadinessSummary(
            smsStatus = smsStatus,
            defaultSmsStatus = defaultSmsStatus,
            notificationStatus = notifStatus,
            modelStatus = modelStatus,
            cameraStatus = cameraStatus,
            overallStatus = overallStatus,
            isOperational = isOperational,
            statusDescription = desc
        )
    }
}

