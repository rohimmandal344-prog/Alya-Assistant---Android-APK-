package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AlyaFirestoreRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun syncUserProfile_authenticatedOwner_createsProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = AlyaFirestoreRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.syncUserProfile("Alice", ALICE_EMAIL, null)
        }
        assertTrue(result.isSuccess)

        val doc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).get().await()
        }
        assertTrue(doc.exists())
        assertEquals(uid, doc.getString("userId"))
        assertEquals("Alice", doc.getString("displayName"))
    }

    @Test
    fun saveMemory_authenticatedOwner_savesMemory() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = AlyaFirestoreRepository(firestore)
        val memoryId = UUID.randomUUID().toString()

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveMemory(memoryId, "Preferences", "User prefers dark mode")
        }
        assertTrue(result.isSuccess)

        val doc = withTimeout(DEFAULT_TIMEOUT_MS) {
            firestore.collection("users").document(uid).collection("memories").document(memoryId).get().await()
        }
        assertTrue(doc.exists())
        assertEquals("Preferences", doc.getString("category"))
        assertEquals("User prefers dark mode", doc.getString("content"))
    }

    @Test
    fun observeMemories_authenticatedOwner_emitsMemories() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = AlyaFirestoreRepository(firestore)
        val memoryId = UUID.randomUUID().toString()

        repository.saveMemory(memoryId, "Facts", "User lives in Tokyo")

        val memories = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMemories().first { list -> list.any { it.id == memoryId } }
        }
        assertTrue(memories.any { it.id == memoryId })
    }

    @Test
    fun saveMemory_unauthenticatedUser_fails() = runBlocking {
        auth.signOut()
        val repository = AlyaFirestoreRepository(firestore)
        val memoryId = UUID.randomUUID().toString()

        val result = repository.saveMemory(memoryId, "General", "Test content")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalStateException)
        assertTrue(exception?.message?.contains("User must be signed in") == true)
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = AlyaFirestoreRepository(firestore)
        val memoryId = UUID.randomUUID().toString()
        aliceRepo.saveMemory(memoryId, "Secrets", "Alice secret memory")

        // Switch to Bob
        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(aliceUid).collection("memories").document(memoryId).get().await()
            }
            fail("Expected permission denied when accessing another user's memories")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@test.com"
        const val BOB_EMAIL = "bob@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
