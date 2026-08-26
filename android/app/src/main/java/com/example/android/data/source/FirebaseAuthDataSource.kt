package com.example.android.data.source

import com.example.android.domain.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthDataSource @Inject constructor() {
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUser(): User? {
        val fbUser = auth?.currentUser ?: return null
        val calculatedName = fbUser.displayName?.ifBlank { null }
            ?: fbUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
            ?: "User"
        return User(
            uid = fbUser.uid,
            email = fbUser.email ?: "user@aegisshield.local",
            displayName = calculatedName,
            isGuest = false
        )
    }

    suspend fun login(email: String, pass: String): User {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            val fallbackName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            return User(
                uid = "dev_${System.currentTimeMillis()}",
                email = email,
                displayName = fallbackName,
                isGuest = false
            )
        }
        
        val result = withTimeoutOrNull(10000L) {
            firebaseAuth.signInWithEmailAndPassword(email, pass).await()
        } ?: throw IllegalStateException("Connection timed out. Please check network/Firebase settings.")

        val fbUser = result.user ?: throw IllegalStateException("Firebase returned null user")
        val calculatedName = fbUser.displayName?.ifBlank { null }
            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
        return User(
            uid = fbUser.uid,
            email = fbUser.email ?: email,
            displayName = calculatedName,
            isGuest = false
        )
    }

    suspend fun register(name: String, email: String, pass: String): User {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            return User(
                uid = "dev_${System.currentTimeMillis()}",
                email = email,
                displayName = name,
                isGuest = false
            )
        }

        val result = withTimeoutOrNull(10000L) {
            firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
        } ?: throw IllegalStateException("Connection timed out. Please check network/Firebase settings.")

        val fbUser = result.user ?: throw IllegalStateException("Firebase returned null user")
        
        // Update user profile in Firebase Auth with 3s timeout
        try {
            withTimeoutOrNull(3000L) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                fbUser.updateProfile(profileUpdates).await()
            }
        } catch (ignored: Exception) {
        }

        return User(
            uid = fbUser.uid,
            email = fbUser.email ?: email,
            displayName = name,
            isGuest = false
        )
    }

    fun logout() {
        try {
            auth?.signOut()
        } catch (ignored: Exception) {
        }
    }
}
