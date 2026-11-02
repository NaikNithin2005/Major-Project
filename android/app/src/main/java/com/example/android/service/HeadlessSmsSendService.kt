package com.example.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Required HeadlessSmsSendService for Android Default SMS App compliance.
 * Handles RESPOND_VIA_MESSAGE when a user responds to an incoming call with a quick text.
 */
class HeadlessSmsSendService : Service() {

    companion object {
        private const val TAG = "HeadlessSmsSendService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            Log.d(TAG, "Quick response via message handled by Squish")
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

