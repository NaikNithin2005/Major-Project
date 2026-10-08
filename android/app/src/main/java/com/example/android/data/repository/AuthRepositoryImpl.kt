package com.example.android.data.repository

import com.example.android.data.source.FirebaseAuthDataSource
import com.example.android.data.source.FirestoreDataSource
import com.example.android.domain.model.AuthState
import com.example.android.domain.model.User
import com.example.android.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuthDataSource: FirebaseAuthDataSource,
    private val firestoreDataSource: FirestoreDataSource = FirestoreDataSource()
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Uninitialized)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkInitialSession()
    }

    private fun checkInitialSession() {
        val currentUser = firebaseAuthDataSource.getCurrentUser()
        if (currentUser != null) {
            _authState.value = AuthState.Authenticated(currentUser)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override fun getCurrentUser(): User? {
        val state = _authState.value
        if (state is AuthState.Authenticated) {
            return state.user
        }
        return firebaseAuthDataSource.getCurrentUser()
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        val currentUser = getCurrentUser()

        val fbToken = firebaseAuthDataSource.getIdToken(forceRefresh)
        if (fbToken != null) {
            return fbToken
        }

        // Development/mock fallback for offline or guest test environments
        val uid = currentUser?.uid ?: "device-default"
        return "mock-user-$uid"
    }

    override suspend fun login(email: String, pass: String): Result<User> {
        _authState.value = AuthState.Loading
        return try {
            val user = firebaseAuthDataSource.login(email, pass)
            firestoreDataSource.syncUserProfile(user)
            _authState.value = AuthState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Authentication failed"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    override suspend fun register(name: String, email: String, pass: String): Result<User> {
        _authState.value = AuthState.Loading
        return try {
            val user = firebaseAuthDataSource.register(name, email, pass)
            firestoreDataSource.syncUserProfile(user)
            firebaseAuthDataSource.logout()
            _authState.value = AuthState.Unauthenticated
            Result.success(user)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Registration failed"
            _authState.value = AuthState.Error(errorMsg)
            Result.failure(e)
        }
    }

    override suspend fun loginAsGuest(): Result<User> {
        _authState.value = AuthState.Loading
        val guestUser = User(
            uid = "guest_${System.currentTimeMillis()}",
            email = "guest@squishshield.local",
            displayName = "User",
            isGuest = true,
            role = "GUEST"
        )
        firestoreDataSource.syncUserProfile(guestUser)
        _authState.value = AuthState.Authenticated(guestUser)
        return Result.success(guestUser)
    }

    override suspend fun logout(): Result<Unit> {
        firebaseAuthDataSource.logout()
        _authState.value = AuthState.Unauthenticated
        return Result.success(Unit)
    }

    override fun updateUserRole(role: String) {
        val state = _authState.value
        if (state is AuthState.Authenticated) {
            val updatedUser = state.user.copy(role = role)
            _authState.value = AuthState.Authenticated(updatedUser)
        }
    }
}
