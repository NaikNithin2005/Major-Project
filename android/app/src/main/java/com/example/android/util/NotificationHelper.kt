package com.example.android.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.android.MainActivity

/**
 * Modernized, consumer-friendly notification manager for Squish.
 * Replaces cybersecurity jargon with clear, understandable, actionable messages.
 */
object NotificationHelper {

    const val CHANNEL_DANGEROUS = "squish_threat_alerts_high"
    const val CHANNEL_NEEDS_ATTENTION = "squish_threat_alerts_medium"
    const val CHANNEL_SAFE_CHECKS = "squish_safe_checks"
    const val CHANNEL_TIPS = "squish_security_tips"

    // Backwards-compatibility aliases
    const val CHANNEL_THREAT_HIGH = CHANNEL_DANGEROUS
    const val CHANNEL_THREAT_MEDIUM = CHANNEL_NEEDS_ATTENTION
    const val CHANNEL_SECURITY_TIPS = CHANNEL_TIPS

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val dangerousChannel = NotificationChannel(
                CHANNEL_DANGEROUS,
                "Dangerous Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for messages and QR codes that appear to be dangerous scams."
                enableLights(true)
                enableVibration(true)
            }

            val attentionChannel = NotificationChannel(
                CHANNEL_NEEDS_ATTENTION,
                "Messages Needing Attention",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Helpful warnings when a message or link looks unusual or asks for sensitive actions."
            }

            val safeChannel = NotificationChannel(
                CHANNEL_SAFE_CHECKS,
                "Safe Message Confirmations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quiet confirmations when incoming messages pass standard scam checks."
            }

            val tipsChannel = NotificationChannel(
                CHANNEL_TIPS,
                "Helpful Safety Tips",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Everyday tips to help keep your personal messages and accounts safe."
            }

            notificationManager.createNotificationChannels(listOf(dangerousChannel, attentionChannel, safeChannel, tipsChannel))
        }
    }

    private fun canSendNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Alert for Dangerous SMS scam messages.
     */
    fun showDangerousMessageAlert(
        context: Context,
        sender: String,
        explanation: String,
        eventId: String,
        showLockScreenPreview: Boolean = false
    ) {
        if (!canSendNotifications(context)) return
        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 2) // Opens Safety Checks / History tab
            putExtra("EVENT_ID", eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (explanation.isNotBlank()) {
            "From $sender: $explanation Avoid opening links or sharing codes."
        } else {
            "This message contains signs of a scam. Avoid opening its links or sharing passwords, banking details, or verification codes."
        }

        // Lock screen notification: masked if preview disabled, detailed if preview enabled
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_DANGEROUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (showLockScreenPreview) "Possible Scam: $sender" else "Possible Scam Detected")
            .setContentText(if (showLockScreenPreview) bodyText else "A dangerous message was caught. Unlock your phone to review safely.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_DANGEROUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Possible Scam Message Detected")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "View Message", pendingIntent)
            .addAction(android.R.drawable.ic_dialog_info, "See Why Dangerous", pendingIntent)
            .setVisibility(if (showLockScreenPreview) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Alert for Suspicious SMS messages that Need Attention.
     */
    fun showSuspiciousMessageAlert(
        context: Context,
        sender: String,
        explanation: String,
        eventId: String,
        showLockScreenPreview: Boolean = false
    ) {
        if (!canSendNotifications(context)) return
        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 2)
            putExtra("EVENT_ID", eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (explanation.isNotBlank()) {
            "From $sender: $explanation"
        } else {
            "The message asks you to act quickly or contains an unfamiliar link. It could be a scam."
        }

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_NEEDS_ATTENTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (showLockScreenPreview) "Unusual Message: $sender" else "Message Needs Attention")
            .setContentText(if (showLockScreenPreview) bodyText else "A new message requires your review. Unlock to see details.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_NEEDS_ATTENTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Be Careful: This Message Looks Unusual")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "View Message", pendingIntent)
            .addAction(android.R.drawable.ic_dialog_info, "Check Details", pendingIntent)
            .setVisibility(if (showLockScreenPreview) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Gentle confirmation when a safe message is scanned.
     */
    fun showSafeMessageNotification(
        context: Context,
        sender: String,
        eventId: String
    ) {
        if (!canSendNotifications(context)) return
        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 2)
            putExtra("EVENT_ID", eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = "Message from $sender checked. No obvious scam signs were found."

        val notification = NotificationCompat.Builder(context, CHANNEL_SAFE_CHECKS)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Message Checked")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Alert for Dangerous QR Code scans.
     */
    fun showDangerousQrAlert(
        context: Context,
        destination: String,
        explanation: String,
        eventId: String,
        showLockScreenPreview: Boolean = false
    ) {
        if (!canSendNotifications(context)) return
        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 2)
            putExtra("EVENT_ID", eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bodyText = if (explanation.isNotBlank()) {
            "$destination: $explanation Avoid entering personal details."
        } else {
            "The website stored in this QR code may put your personal information at risk."
        }

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_DANGEROUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (showLockScreenPreview) "Warning: Unsafe QR Code" else "Unsafe QR Code Detected")
            .setContentText(if (showLockScreenPreview) bodyText else "A dangerous QR code was scanned. Unlock phone to review.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_DANGEROUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Warning: This QR Code May Be Unsafe")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "View Scan Details", pendingIntent)
            .addAction(android.R.drawable.ic_dialog_info, "See Safety Information", pendingIntent)
            .setVisibility(if (showLockScreenPreview) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        } catch (_: SecurityException) {}
    }

    /**
     * Legacy adapter for backward compatibility.
     */
    fun showThreatAlert(
        context: Context,
        title: String,
        description: String,
        severity: String,
        eventId: String,
        source: String = "SMS"
    ) {
        if (severity.equals("CRITICAL", ignoreCase = true) || severity.equals("HIGH", ignoreCase = true)) {
            if (source.contains("QR", ignoreCase = true)) {
                showDangerousQrAlert(context, source, description, eventId)
            } else {
                showDangerousMessageAlert(context, "Sender", description, eventId)
            }
        } else {
            showSuspiciousMessageAlert(context, "Sender", description, eventId)
        }
    }

    fun showDailyTip(
        context: Context,
        tipTitle: String,
        tipBody: String
    ) {
        if (!canSendNotifications(context)) return
        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 0)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TIPS)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Safety Tip: $tipTitle")
            .setContentText(tipBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tipBody))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9999, notification)
        } catch (_: SecurityException) {}
    }
}
