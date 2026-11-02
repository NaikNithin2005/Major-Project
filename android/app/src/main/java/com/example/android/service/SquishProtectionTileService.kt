package com.example.android.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.android.MainActivity
import com.example.android.util.PermissionUtils

/**
 * Android Quick Settings Tile allowing users to quickly view and toggle
 * Squish Shield SMS & Quishing real-time protection status.
 *
 * Platform aware:
 * - If runtime SMS or notification permissions are missing, prompts setup flow rather than activating a non-functional state.
 * - Distinguishes between active monitoring, paused monitoring, and setup required.
 */
class SquishProtectionTileService : TileService() {

    companion object {
        const val PREFS_NAME = "squish_shield_preferences"
        const val KEY_PROTECTION_ENABLED = "pref_sms_protection_enabled"

        fun isProtectionEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_PROTECTION_ENABLED, true)
        }

        fun setProtectionEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_PROTECTION_ENABLED, enabled).apply()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        val hasSms = PermissionUtils.hasSmsPermission(this)

        // If required runtime capabilities are missing, launch Squish Setup
        if (!hasSms) {
            launchSetupScreen()
            return
        }

        // Toggle user protection preference
        val currentlyEnabled = isProtectionEnabled(this)
        val newTarget = !currentlyEnabled
        setProtectionEnabled(this, newTarget)

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val hasSms = PermissionUtils.hasSmsPermission(this)
        val isDefaultSms = PermissionUtils.isDefaultSmsApp(this)
        val userEnabled = isProtectionEnabled(this)

        if (!hasSms) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Squish Shield"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Setup Required"
            }
        } else if (userEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Squish Shield"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isDefaultSms) "Active (Default App)" else "Active (Passive Shield)"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Squish Shield"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Protection Paused"
            }
        }

        tile.updateTile()
    }

    private fun launchSetupScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_setup", true)
        }

        if (Build.VERSION.SDK_INT >= 34) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
