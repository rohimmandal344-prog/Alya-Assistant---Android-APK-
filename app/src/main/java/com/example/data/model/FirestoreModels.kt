package com.example.data.model

import com.google.firebase.Timestamp

data class UserProfile(
    val userId: String = "",
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class FirestoreMemory(
    val id: String = "",
    val userId: String = "",
    val category: String = "General",
    val content: String = "",
    val updatedAt: Timestamp? = null
)
