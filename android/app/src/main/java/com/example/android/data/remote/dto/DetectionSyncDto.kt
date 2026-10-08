package com.example.android.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Objects for Phase 8 Backend Communication.
 * Strictly adheres to data minimization: NO raw SMS content is included.
 */
data class DetectionSyncRequest(
    @SerializedName("client_event_id")
    val clientEventId: String,

    @SerializedName("event_type")
    val eventType: String, // "SMS" or "QR"

    @SerializedName("risk_score")
    val riskScore: Int, // 0 - 100

    @SerializedName("threat_category")
    val threatCategory: String, // "SAFE", "LOW", "MEDIUM", "HIGH"

    @SerializedName("recommended_action")
    val recommendedAction: String,

    @SerializedName("timestamp")
    val timestamp: String, // ISO-8601 formatted string

    @SerializedName("device_id")
    val deviceId: String? = null,

    @SerializedName("model_versions")
    val modelVersions: Map<String, String>? = null,

    @SerializedName("model_results")
    val modelResults: Map<String, Any>? = null,

    @SerializedName("rule_results")
    val ruleResults: List<Map<String, Any>>? = null,

    @SerializedName("context_results")
    val contextResults: Map<String, Any>? = null,

    @SerializedName("reasons")
    val reasons: List<String>? = null,

    @SerializedName("url_metadata")
    val urlMetadata: Map<String, Any>? = null,

    @SerializedName("qr_metadata")
    val qrMetadata: Map<String, Any>? = null
)

data class DetectionSyncResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("client_event_id")
    val clientEventId: String?,

    @SerializedName("user_id")
    val userId: String?,

    @SerializedName("event_type")
    val eventType: String,

    @SerializedName("risk_score")
    val riskScore: Int,

    @SerializedName("threat_category")
    val threatCategory: String,

    @SerializedName("recommended_action")
    val recommendedAction: String,

    @SerializedName("status")
    val status: String,

    @SerializedName("created_at")
    val createdAt: String
)

data class UserProfileDto(
    @SerializedName("id")
    val id: String,

    @SerializedName("firebase_uid")
    val firebaseUid: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("display_name")
    val displayName: String?,

    @SerializedName("role")
    val role: String, // "USER" or "ADMIN"

    @SerializedName("is_active")
    val isActive: Boolean
)

data class FeedbackSyncRequest(
    @SerializedName("threat_event_id")
    val threatEventId: String,

    @SerializedName("classification")
    val classification: String, // "TRUE_POSITIVE", "FALSE_POSITIVE", "FALSE_NEGATIVE"

    @SerializedName("comment")
    val comment: String? = null
)

data class FeedbackSyncResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("threat_event_id")
    val threatEventId: String,

    @SerializedName("feedback_type")
    val feedbackType: String,

    @SerializedName("status")
    val status: String
)
