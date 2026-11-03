package com.example.android.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SenderSecurityDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("trust_status") val trustStatus: String,
    @SerializedName("verification_status") val verificationStatus: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("risk_score") val riskScore: Int = 0,
    @SerializedName("total_messages_count") val totalMessagesCount: Int = 0,
    @SerializedName("is_blocked") val isBlocked: Boolean = false,
    @SerializedName("block_reason") val blockReason: String? = null
)

data class TrustSenderRequestDto(
    @SerializedName("sender_id") val sender_id: String,
    @SerializedName("display_name") val display_name: String? = null,
    @SerializedName("reason") val reason: String? = null
)

data class BlockSenderRequestDto(
    @SerializedName("sender_id") val sender_id: String,
    @SerializedName("reason") val reason: String? = null
)

data class QuarantinedSmsDto(
    @SerializedName("id") val id: String,
    @SerializedName("sender") val sender: String? = null,
    @SerializedName("raw_content") val rawContent: String? = null,
    @SerializedName("risk_score") val riskScore: Int = 0,
    @SerializedName("threat_category") val threatCategory: String = "HIGH",
    @SerializedName("recommended_action") val recommendedAction: String = "QUARANTINE",
    @SerializedName("is_quarantined") val isQuarantined: Boolean = true,
    @SerializedName("quarantine_reason") val quarantineReason: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)

data class QuarantineActionDto(
    @SerializedName("action") val action: String,
    @SerializedName("reason") val reason: String? = null
)

data class BlockedSmsDto(
    @SerializedName("id") val id: String,
    @SerializedName("sender") val sender: String? = null,
    @SerializedName("raw_content") val rawContent: String? = null,
    @SerializedName("risk_score") val riskScore: Int = 0,
    @SerializedName("threat_category") val threatCategory: String = "HIGH",
    @SerializedName("is_blocked") val isBlocked: Boolean = true,
    @SerializedName("block_reason") val blockReason: String? = null,
    @SerializedName("blocked_at") val blockedAt: String? = null
)

data class SmsBlockActionDto(
    @SerializedName("action") val action: String,
    @SerializedName("reason") val reason: String? = null
)

