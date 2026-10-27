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
 * Production Notification Manager for Squish Security App.
 * Handles high-severity threat alerts, suspicious activity notifications,
 * and daily security tips with dedicated Android notification channels.
 */
object NotificationHelper {

    const val CHANNEL_THREAT_HIGH = "squish_threat_alerts_high"
    const val CHANNEL_THREAT_MEDIUM = "squish_threat_alerts_medium"
    const val CHANNEL_SECURITY_TIPS = "squish_security_tips"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val highChannel = NotificationChannel(
                CHANNEL_THREAT_HIGH,
                "High-Risk Threat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for confirmed smishing, quishing, and phishing attacks."
                enableLights(true)
                enableVibration(true)
            }

            val mediumChannel = NotificationChannel(
                CHANNEL_THREAT_MEDIUM,
                "Suspicious Activity Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Warnings for suspicious messages, links, and unverified senders."
            }

            val tipChannel = NotificationChannel(
                CHANNEL_SECURITY_TIPS,
                "Daily Security Tips",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Educational cybersecurity guidance and anti-phishing tips."
            }

            notificationManager.createNotificationChannels(listOf(highChannel, mediumChannel, tipChannel))
        }
    }

    fun showThreatAlert(
        context: Context,
        title: String,
        description: String,
        severity: String,
        eventId: String,
        source: String = "SMS"
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        initNotificationChannels(context)

        val channelId = if (severity.equals("CRITICAL", ignoreCase = true) || severity.equals("HIGH", ignoreCase = true)) {
            CHANNEL_THREAT_HIGH
        } else {
            CHANNEL_THREAT_MEDIUM
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 3) // Opens Alerts tab
            putExtra("EVENT_ID", eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(
                if (channelId == CHANNEL_THREAT_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        } catch (_: SecurityException) {
        }
    }

    fun showDailyTip(
        context: Context,
        tipTitle: String,
        tipBody: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        initNotificationChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_TAB", 0) // Opens Home tab
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            9999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SECURITY_TIPS)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Security Tip: $tipTitle")
            .setContentText(tipBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tipBody))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(9999, notification)
        } catch (_: SecurityException) {
        }
    }
}
