package com.example.android.domain.model

sealed class AuthState {
    object Uninitialized : AuthState()
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}
