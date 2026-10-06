package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.FirestoreMemory
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AlyaFirestoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun syncUserProfile(displayName: String?, email: String?, photoUrl: String?): Result<Unit> {
        return try {
            val uid = requireUserId()
            val userRef = db.collection("users").document(uid)
            val snapshot = userRef.get().await()

            if (!snapshot.exists()) {
                val data = mapOf(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                userRef.set(data).await()
            } else {
                val updates = mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "photoUrl" to photoUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                userRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "anonymous"
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun saveMemory(memoryId: String, category: String, content: String): Result<String> {
        return try {
            val uid = requireUserId()
            val memoryRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            val snapshot = memoryRef.get().await()

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "id" to memoryId,
                    "userId" to uid,
                    "category" to category,
                    "content" to content,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                memoryRef.set(payload).await()
            } else {
                val payload = mapOf(
                    "id" to memoryId,
                    "userId" to uid,
                    "category" to category,
                    "content" to content,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                memoryRef.update(payload).await()
            }
            Result.success(memoryId)
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "anonymous"
            handleFirestoreError(e, OperationType.WRITE, "users/$uid/memories/$memoryId")
            Result.failure(e)
        }
    }

    fun observeMemories(): Flow<List<FirestoreMemory>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/memories"
        emitAll(
            db.collection("users").document(uid).collection("memories")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(FirestoreMemory::class.java) }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            val path = "users/$uid/memories/$memoryId"
            db.collection("users").document(uid).collection("memories").document(memoryId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            val uid = auth.currentUser?.uid ?: "anonymous"
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/memories/$memoryId")
            Result.failure(e)
        }
    }
}
