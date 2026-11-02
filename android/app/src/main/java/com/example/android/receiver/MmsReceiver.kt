package com.example.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Required MmsReceiver for Android Default SMS App compliance.
 * Receives incoming MMS messages via WAP_PUSH_DELIVER_ACTION.
 */
class MmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) {
            Log.d(TAG, "MMS WAP Push message received by Squish Default SMS handler")
        }
    }
}

