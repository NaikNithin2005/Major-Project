package com.example.android.data.source

import com.example.android.domain.model.User
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

open class FirestoreDataSource {
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    open suspend fun syncUserProfile(user: User) {
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
                    .set(userMap, SetOptions.merge())
                    .await()
            }
        } catch (ignored: Exception) {
            // Non-blocking fallback for offline or unprovisioned Firestore
        }
    }

    /**
     * Uploads a canonical threat document directly to:
     * users/{userId}/threats/{threatId}
     *
     * Ensures all fields comply with the canonical Firestore threat schema.
     */
    open suspend fun saveThreatEvent(userId: String, threatData: Map<String, Any>): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank for user-scoped threat storage"))
        }

        val threatId = threatData["id"] as? String
            ?: return Result.failure(IllegalArgumentException("Threat document must contain an 'id'"))

        return try {
            withTimeoutOrNull(10000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("threats")
                    .document(threatId)
                    .set(threatData, SetOptions.merge())
                    .await()
            } ?: return Result.failure(IllegalStateException("Firestore write timed out"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Uploads multiple canonical threat documents in atomic batches directly to:
     * users/{userId}/threats/{threatId}
     *
     * Firestore supports up to 500 operations per batch.
     */
    open suspend fun saveThreatEventsBatch(userId: String, threatDataList: List<Map<String, Any>>): Result<Int> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank for user-scoped threat storage"))
        }
        if (threatDataList.isEmpty()) {
            return Result.success(0)
        }

        return try {
            var savedCount = 0
            withTimeoutOrNull(25000L) {
                threatDataList.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    for (threatData in chunk) {
                        val threatId = (threatData["id"] as? String) ?: (threatData["scanId"] as? String) ?: continue
                        val docRef = firestore.collection("users")
                            .document(userId)
                            .collection("threats")
                            .document(threatId)
                        batch.set(docRef, threatData, SetOptions.merge())
                        savedCount++
                    }
                    batch.commit().await()
                }
            } ?: return Result.failure(IllegalStateException("Firestore batch write timed out"))

            Result.success(savedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all user threats from:
     * users/{userId}/threats/
     *
     * Used for full cloud threat history restoration upon reinstallation or new device login.
     */
    open suspend fun fetchUserThreats(userId: String): Result<List<Map<String, Any>>> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }

        return try {
            val list = withTimeoutOrNull(15000L) {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("threats")
                    .orderBy("detectedAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
                snapshot.documents.mapNotNull { it.data }
            } ?: return Result.failure(IllegalStateException("Firestore fetch timed out"))

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a threat document from users/{userId}/threats/{threatId}
     */
    open suspend fun deleteThreatEvent(userId: String, threatId: String): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank() || threatId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID and Threat ID cannot be blank"))
        }

        return try {
            withTimeoutOrNull(8000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("threats")
                    .document(threatId)
                    .delete()
                    .await()
            } ?: return Result.failure(IllegalStateException("Firestore delete timed out"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates action taken and review status in users/{userId}/threats/{threatId}
     */
    open suspend fun updateThreatAction(userId: String, threatId: String, action: String): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank() || threatId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID and Threat ID cannot be blank"))
        }

        val reviewStatus = when (action) {
            "BLOCK" -> "BLOCKED"
            "MARK_SAFE", "RESOLVED" -> "RESOLVED"
            else -> "REVIEWED"
        }
        val status = if (action == "RESOLVED" || action == "MARK_SAFE") "RESOLVED" else if (action == "BLOCK") "BLOCKED" else "REVIEWED"

        return try {
            withTimeoutOrNull(8000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("threats")
                    .document(threatId)
                    .update(
                        mapOf(
                            "actionTaken" to action,
                            "userAction" to action,
                            "reviewStatus" to reviewStatus,
                            "status" to status,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            } ?: return Result.failure(IllegalStateException("Firestore update timed out"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Persists user settings to Cloud Firestore under:
     * users/{userId}/preferences/settings
     */
    open suspend fun saveUserSettings(userId: String, settingsMap: Map<String, Any>): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }

        return try {
            withTimeoutOrNull(8000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .set(settingsMap, SetOptions.merge())
                    .await()
            } ?: return Result.failure(IllegalStateException("Firestore settings write timed out"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieves user settings from Cloud Firestore under:
     * users/{userId}/preferences/settings
     */
    open suspend fun fetchUserSettings(userId: String): Result<Map<String, Any>?> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not available"))
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank"))
        }

        return try {
            val doc = withTimeoutOrNull(8000L) {
                firestore.collection("users")
                    .document(userId)
                    .collection("preferences")
                    .document("settings")
                    .get()
                    .await()
            }
            Result.success(doc?.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
