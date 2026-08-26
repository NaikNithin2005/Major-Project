package com.example.android.domain.model

data class User(
    val uid: String,
    val email: String,
    val displayName: String,
    val isGuest: Boolean = false
)
