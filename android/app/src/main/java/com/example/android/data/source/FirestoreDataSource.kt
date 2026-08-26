package com.example.android.data.source

import com.example.android.domain.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreDataSource {
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncUserProfile(user: User) {
        val firestore = db ?: return
        try {
            withTimeoutOrNull(3000L) {
                val userMap = hashMapOf(
                    "uid" to user.uid,
                    "email" to user.email,
                    "displayName" to user.displayName,
                    "isGuest" to user.isGuest,
                    "lastActive" to System.currentTimeMillis()
                )
                firestore.collection("users")
                    .document(user.uid)
                    .set(userMap)
                    .await()
            }
        } catch (ignored: Exception) {
            // Non-blocking fallback for offline or unprovisioned Firestore
        }
    }
}
