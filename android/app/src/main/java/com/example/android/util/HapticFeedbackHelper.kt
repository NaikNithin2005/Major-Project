package com.example.android.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Modern, accessible haptic feedback manager for Squish Shield.
 *
 * Implements configurable tactile feedback:
 * - Subtle short confirmation for safe scans.
 * - Distinct, urgent warning pattern for high-confidence malicious threats.
 * - No vibration for low-risk or inconclusive results.
 * - Respects system capabilities and user settings toggle.
 */
object HapticFeedbackHelper {

    private const val PREFS_NAME = "squish_shield_preferences"
    private const val KEY_HAPTICS_ENABLED = "pref_haptic_feedback_enabled"

    // Guard against rapid duplicate vibration triggers
    private var lastVibrationTime = 0L
    private const val MIN_INTERVAL_MS = 500L

    // Guard against repeated vibrations for identical events (deduplication)
    private val vibratedEventIds = mutableSetOf<String>()

    fun recordEventVibration(eventId: String): Boolean {
        if (vibratedEventIds.contains(eventId)) return false
        vibratedEventIds.add(eventId)
        if (vibratedEventIds.size > 100) {
            val oldest = vibratedEventIds.first()
            vibratedEventIds.remove(oldest)
        }
        return true
    }

    fun clearVibratedEvents() {
        vibratedEventIds.clear()
        lastVibrationTime = 0L
    }

    fun hasVibratedEvent(eventId: String): Boolean {
        return vibratedEventIds.contains(eventId)
    }

    /**
     * Checks if haptics are enabled in user settings (defaults to true).
     */
    fun isHapticsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
    }

    /**
     * Updates the user preference for haptic feedback.
     */
    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    /**
     * Obtains the platform Vibrator instance safely across Android API versions.
     */
    @Suppress("DEPRECATION")
    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Subtle, short confirmation pulse for an ordinary safe scan verdict.
     */
    fun performSafeScanHaptic(context: Context) {
        if (!isHapticsEnabled(context)) return

        val now = System.currentTimeMillis()
        if (now - lastVibrationTime < MIN_INTERVAL_MS) return
        lastVibrationTime = now

        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(25L)
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Distinct, double-buzz warning pattern for high-confidence malicious threats (Quishing / Smishing).
     */
    fun performMaliciousThreatHaptic(context: Context) {
        if (!isHapticsEnabled(context)) return

        val now = System.currentTimeMillis()
        if (now - lastVibrationTime < MIN_INTERVAL_MS) return
        lastVibrationTime = now

        val vibrator = getVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 80, 50, 140)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 80, 50, 140), -1)
            }
        } catch (ignored: Exception) {}
    }

    /**
     * Convenience method to dispatch the appropriate haptic based on scan score and verdict.
     * Deduplicates by eventId to ensure no repeated vibrations occur for duplicate events.
     */
    fun performScanResultHaptic(context: Context, riskScore: Int, isMalicious: Boolean, eventId: String? = null) {
        if (!eventId.isNullOrBlank()) {
            if (!recordEventVibration(eventId)) return
        }

        if (isMalicious || riskScore >= 70) {
            performMaliciousThreatHaptic(context)
        } else if (riskScore < 40) {
            performSafeScanHaptic(context)
        }
        // Inconclusive or moderate risk (40..69) -> No disruptive haptic
    }
}
