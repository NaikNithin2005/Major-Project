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
}
