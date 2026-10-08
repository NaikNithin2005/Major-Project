package com.example.android.domain.repository

import com.example.android.domain.model.AuthState
import com.example.android.domain.model.User
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    fun getCurrentUser(): User?
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
    suspend fun login(email: String, pass: String): Result<User>
    suspend fun register(name: String, email: String, pass: String): Result<User>
    suspend fun loginAsGuest(): Result<User>
    suspend fun logout(): Result<Unit>
    fun updateUserRole(role: String)
}
