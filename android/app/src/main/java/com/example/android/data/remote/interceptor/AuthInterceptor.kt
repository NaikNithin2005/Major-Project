package com.example.android.data.remote.interceptor

import com.example.android.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Authentication Interceptor that injects Bearer Firebase ID Token into backend requests.
 * Never logs raw tokens, passwords, or JWTs.
 * Handles 401 Unauthorized by attempting a single token refresh and request retry.
 */
class AuthInterceptor(
    private val authRepository: AuthRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 1. Fetch current token
        val token = runBlocking {
            try {
                authRepository.getIdToken(forceRefresh = false)
            } catch (e: Exception) {
                null
            }
        }

        val authenticatedRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
        } else {
            originalRequest.newBuilder()
                .header("Accept", "application/json")
                .build()
        }

        val response = chain.proceed(authenticatedRequest)

        // 2. Controlled 401 Token Refresh & Single Retry (no infinite loops)
        if (response.code == 401 && !token.isNullOrBlank()) {
            response.close()

            val freshToken = runBlocking {
                try {
                    authRepository.getIdToken(forceRefresh = true)
                } catch (e: Exception) {
                    null
                }
            }

            if (!freshToken.isNullOrBlank() && freshToken != token) {
                val retriedRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $freshToken")
                    .header("Accept", "application/json")
                    .build()
                return chain.proceed(retriedRequest)
            }
        }

        return response
    }
}
