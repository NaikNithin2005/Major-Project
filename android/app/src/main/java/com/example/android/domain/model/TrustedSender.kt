package com.example.android.domain.model

data class TrustedSender(
    val sender: String,
    val addedTimestamp: Long,
    val trustLevel: String = "VERIFIED",
    val userId: String? = null
)
