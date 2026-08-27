package com.example.android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.android.AegisApplication
import com.example.android.domain.model.RawSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver listening for incoming SMS messages.
 * Serves as an acquisition component; delegates analysis to domain use cases asynchronously.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val pendingResult = goAsync()
        val application = context.applicationContext as? AegisApplication
        val container = application?.container

        if (container == null) {
            Log.e(TAG, "AppContainer not initialized in AegisApplication")
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if user has enabled SMS protection in settings
                val settings = container.settingsRepository.observeSettings().first()
                if (!settings.realtimeSmsProtection) {
                    Log.d(TAG, "SMS Protection disabled in settings. Skipping analysis.")
                    return@launch
                }

                val messages = extractSmsMessages(intent)
                if (messages.isEmpty()) {
                    Log.w(TAG, "No valid SMS messages parsed from broadcast intent.")
                    return@launch
                }

                // Group by sender for multipart SMS handling
                val groupedBySender = messages.groupBy { it.originatingAddress ?: "UNKNOWN" }

                for ((sender, senderMessages) in groupedBySender) {
                    val fullBody = senderMessages.joinToString(separator = "") { it.messageBody ?: "" }
                    val timestamp = senderMessages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

                    if (fullBody.isNotBlank()) {
                        val rawSms = RawSms(
                            messageId = System.currentTimeMillis().toString(),
                            sender = sender,
                            body = fullBody,
                            timestamp = timestamp
                        )

                        container.processIncomingSmsUseCase(rawSms)
                            .onSuccess { result ->
                                Log.i(TAG, "Processed SMS from $sender. Risk Score: ${result.riskScore}")
                            }
                            .onFailure { error ->
                                Log.e(TAG, "Failed to process SMS from $sender: ${error.message}")
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SmsReceiver processing: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun extractSmsMessages(intent: Intent): List<SmsMessage> {
        return try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract SmsMessages from intent: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
