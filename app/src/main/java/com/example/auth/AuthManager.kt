package com.example.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.core.logger.AlyaLogger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "ALYA_AUTH"
    }

    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = firebaseAuth.currentUser
        AlyaLogger.i(TAG, "Auth state updated: user = ${firebaseAuth.currentUser?.email ?: firebaseAuth.currentUser?.phoneNumber ?: "null"}")
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    /**
     * Silent automatic sign-in on startup
     */
    fun attemptAutoSignIn(scope: CoroutineScope) {
        if (auth.currentUser != null) {
            _currentUser.value = auth.currentUser
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Web client ID not found: ${e.message}")
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    _currentUser.value = authResult.user
                    AlyaLogger.i(TAG, "Auto sign-in successful: ${authResult.user?.email}")
                }
            } catch (e: Exception) {
                AlyaLogger.d(TAG, "Auto sign-in not available: ${e.message}")
            }
        }
    }

    /**
     * Interactive Google Sign-In flow ("Continue with Google")
     */
    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            val err = "Google Sign-In configuration missing: default_web_client_id not found"
            AlyaLogger.e(TAG, err)
            onError(err)
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                AlyaLogger.i(TAG, "Launching Google Sign-In Credential Manager...")
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = user
                        AlyaLogger.i(TAG, "Google Sign-In succeeded for: ${user.email} (${user.uid})")
                        onSuccess(user)
                    } else {
                        onError("Authentication succeeded but user profile was null")
                    }
                } else {
                    val err = "Unexpected credential format received"
                    AlyaLogger.e(TAG, err)
                    onError(err)
                }
            } catch (e: GetCredentialCancellationException) {
                AlyaLogger.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}")
                onCancelled()
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Google Sign-In failed", e)
                onError(e.localizedMessage ?: "Sign-in failed. Please try again.")
            }
        }
    }

    /**
     * Manual Sign In with Email and Password
     */
    fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                AlyaLogger.i(TAG, "Attempting email sign-in for: $email")
                val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user
                if (user != null) {
                    _currentUser.value = user
                    AlyaLogger.i(TAG, "Email Sign-In succeeded for: ${user.email} (${user.uid})")
                    onSuccess(user)
                } else {
                    onError("Sign-in succeeded but user record was null")
                }
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Email Sign-In error", e)
                onError(formatAuthError(e))
            }
        }
    }

    /**
     * Manual Sign Up with Email, Password, and Display Name
     */
    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                AlyaLogger.i(TAG, "Creating new account for: $email ($displayName)")
                val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val user = authResult.user
                if (user != null) {
                    if (displayName.isNotBlank()) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName.trim())
                            .build()
                        user.updateProfile(profileUpdates).await()
                    }
                    _currentUser.value = user
                    AlyaLogger.i(TAG, "Email Sign-Up successful for: ${user.email} (${user.uid})")
                    onSuccess(user)
                } else {
                    onError("Sign-up succeeded but user record was null")
                }
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Email Sign-Up error", e)
                onError(formatAuthError(e))
            }
        }
    }

    /**
     * Phone Number Authentication - Send OTP
     */
    fun sendPhoneOtp(
        phoneNumber: String,
        activity: Activity,
        scope: CoroutineScope,
        displayName: String,
        onCodeSent: (String) -> Unit,
        onInstantSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                AlyaLogger.i(TAG, "Phone instant verification completed.")
                scope.launch {
                    try {
                        val authResult = auth.signInWithCredential(credential).await()
                        val user = authResult.user
                        if (user != null) {
                            if (displayName.isNotBlank()) {
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(displayName.trim())
                                    .build()
                                user.updateProfile(profileUpdates).await()
                            }
                            _currentUser.value = user
                            onInstantSuccess(user)
                        }
                    } catch (e: Exception) {
                        AlyaLogger.e(TAG, "Instant verification sign-in failed", e)
                        onError(e.localizedMessage ?: "Auto-verification failed")
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                AlyaLogger.e(TAG, "Phone verification failed", e)
                onError(e.localizedMessage ?: "Phone number verification failed. Ensure valid international format (e.g. +91...)")
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                AlyaLogger.i(TAG, "OTP Code sent successfully. VerificationId: $verificationId")
                onCodeSent(verificationId)
            }
        }

        try {
            val options = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber.trim())
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Failed to start phone verification", e)
            onError(e.localizedMessage ?: "Could not initiate phone verification.")
        }
    }

    /**
     * Phone Number Authentication - Verify OTP code
     */
    fun verifyPhoneOtp(
        verificationId: String,
        otpCode: String,
        displayName: String,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, otpCode.trim())
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                if (user != null) {
                    if (displayName.isNotBlank()) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName.trim())
                            .build()
                        user.updateProfile(profileUpdates).await()
                    }
                    _currentUser.value = user
                    AlyaLogger.i(TAG, "Phone OTP Sign-In succeeded for: ${user.phoneNumber} (${user.uid})")
                    onSuccess(user)
                } else {
                    onError("Verification succeeded but user record was null")
                }
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Phone OTP verification failed", e)
                onError(e.localizedMessage ?: "Invalid verification code. Please check and try again.")
            }
        }
    }

    /**
     * Guest / Anonymous Sign-In Fallback
     */
    fun signInAnonymously(
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onGuestFallback: () -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val authResult = auth.signInAnonymously().await()
                val user = authResult.user
                if (user != null) {
                    _currentUser.value = user
                    AlyaLogger.i(TAG, "Anonymous / Guest sign-in successful: ${user.uid}")
                    onSuccess(user)
                } else {
                    onGuestFallback()
                }
            } catch (e: Exception) {
                AlyaLogger.w(TAG, "Firebase Anonymous auth restricted/disabled (${e.message}), proceeding with Guest mode")
                onGuestFallback()
            }
        }
    }

    /**
     * Clean sign out
     */
    fun signOut(scope: CoroutineScope, onComplete: () -> Unit = {}) {
        scope.launch {
            try {
                auth.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                _currentUser.value = null
                AlyaLogger.i(TAG, "User signed out successfully")
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Error during sign out", e)
            } finally {
                onComplete()
            }
        }
    }

    fun release() {
        auth.removeAuthStateListener(authListener)
    }

    private fun formatAuthError(e: Exception): String {
        val msg = e.localizedMessage ?: ""
        return when {
            msg.contains("restricted to administrators", ignoreCase = true) -> {
                "Anonymous sign-in is restricted by your Firebase project rules. Proceeding in Guest mode."
            }
            msg.contains("operation is not allowed", ignoreCase = true) ||
            msg.contains("disabled for this Firebase project", ignoreCase = true) ||
            msg.contains("ERROR_OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
                "This authentication provider is currently disabled in your Firebase console. Go to console.firebase.google.com -> Build -> Authentication -> Sign-in method, then enable the 'Phone' or 'Email/Password' provider to authorize this sign-in method."
            }
            msg.contains("password is invalid", ignoreCase = true) || msg.contains("wrong password", ignoreCase = true) -> {
                "Incorrect password. Please verify and try again."
            }
            msg.contains("user-not-found", ignoreCase = true) || msg.contains("no user record", ignoreCase = true) -> {
                "No account found with this email. Please switch to Sign Up to create your account."
            }
            msg.contains("email-already-in-use", ignoreCase = true) || msg.contains("email already exists", ignoreCase = true) -> {
                "An account already exists with this email. Please switch to Sign In."
            }
            else -> msg.ifBlank { "Authentication request failed. Please try again." }
        }
    }
}
