package com.example.android.data.remote.api

import com.example.android.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface BackendApiService {

    @GET("api/v1/users/me")
    suspend fun getCurrentUserProfile(): Response<UserProfileDto>

    @POST("api/v1/detections")
    suspend fun syncDetection(
        @Body request: DetectionSyncRequest
    ): Response<DetectionSyncResponse>

    @POST("api/v1/feedback")
    suspend fun submitFeedback(
        @Body request: FeedbackSyncRequest
    ): Response<FeedbackSyncResponse>

    @GET("health")
    suspend fun checkHealth(): Response<Map<String, Any>>

    // Security Center API Endpoints
    @GET("api/v1/security/trusted-senders")
    suspend fun getTrustedSenders(): Response<List<SenderSecurityDto>>

    @POST("api/v1/security/trusted-senders")
    suspend fun trustSender(
        @Body request: TrustSenderRequestDto
    ): Response<SenderSecurityDto>

    @DELETE("api/v1/security/trusted-senders/{sender_id}")
    suspend fun removeTrustedSender(
        @Path("sender_id") senderId: String
    ): Response<SenderSecurityDto>

    @GET("api/v1/security/untrusted-senders")
    suspend fun getUntrustedSenders(): Response<List<SenderSecurityDto>>

    @GET("api/v1/security/blocked-senders")
    suspend fun getBlockedSenders(): Response<List<SenderSecurityDto>>

    @POST("api/v1/security/blocked-senders")
    suspend fun blockSender(
        @Body request: BlockSenderRequestDto
    ): Response<SenderSecurityDto>

    @DELETE("api/v1/security/blocked-senders/{sender_id}")
    suspend fun unblockSender(
        @Path("sender_id") senderId: String
    ): Response<SenderSecurityDto>

    @GET("api/v1/security/quarantined-sms")
    suspend fun getQuarantinedSms(): Response<List<QuarantinedSmsDto>>

    @POST("api/v1/security/quarantined-sms/{threat_id}/action")
    suspend fun quarantineAction(
        @Path("threat_id") threatId: String,
        @Body request: QuarantineActionDto
    ): Response<QuarantinedSmsDto>

    @GET("api/v1/security/blocked-sms")
    suspend fun getBlockedSms(): Response<List<BlockedSmsDto>>

    @POST("api/v1/security/blocked-sms/{threat_id}/action")
    suspend fun blockedSmsAction(
        @Path("threat_id") threatId: String,
        @Body request: SmsBlockActionDto
    ): Response<BlockedSmsDto>
}
