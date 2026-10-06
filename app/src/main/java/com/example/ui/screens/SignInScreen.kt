package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.auth.AuthManager
import com.google.firebase.auth.FirebaseUser

private const val ALYA_LOGO_URL = "https://res.cloudinary.com/xbks6nu5/image/upload/v1789891823/Alisa.Mikhailova.Kujou.600.4066893_lp4uzw.jpg"

enum class AuthMode(val title: String) {
    GOOGLE("Google"),
    PHONE("Phone OTP"),
    MANUAL("Manual (Email)")
}

@Composable
fun SignInScreen(
    authManager: AuthManager,
    onAuthSuccess: (FirebaseUser) -> Unit,
    onContinueAsGuest: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var selectedAuthMode by remember { mutableStateOf(AuthMode.GOOGLE) }

    // Phone Auth State
    var phoneName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var isOtpSent by remember { mutableStateOf(false) }

    // Manual Auth State
    var isSignUp by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualEmail by remember { mutableStateOf("") }
    var manualPassword by remember { mutableStateOf("") }
    var manualConfirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("sign_in_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing Logo Container
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(ALYA_LOGO_URL)
                            .crossfade(true)
                            .error(R.drawable.alya_logo)
                            .placeholder(R.drawable.alya_logo)
                            .build(),
                        contentDescription = "Alya Assistant",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Alya Assistant",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Smart AI Voice & Device Assistant",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in to activate real-time voice, human memory sync, and intelligent Android automation.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Auth Methods Selector Tab
            item {
                TabRow(
                    selectedTabIndex = selectedAuthMode.ordinal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AuthMode.values().forEach { mode ->
                        Tab(
                            selected = selectedAuthMode == mode,
                            onClick = {
                                selectedAuthMode = mode
                                errorMessage = null
                                successMessage = null
                            },
                            text = {
                                Text(
                                    text = mode.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedAuthMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Active Tab Content
            item {
                when (selectedAuthMode) {
                    AuthMode.GOOGLE -> {
                        // 1. Continue with Google
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "One-tap Google Identity Services & Encrypted Cloud Sync",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        successMessage = null
                                        authManager.signInWithGoogle(
                                            activity = context as Activity,
                                            scope = scope,
                                            onSuccess = { user ->
                                                isLoading = false
                                                onAuthSuccess(user)
                                            },
                                            onError = { error ->
                                                isLoading = false
                                                errorMessage = error
                                            },
                                            onCancelled = {
                                                isLoading = false
                                            }
                                        )
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(28.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("continue_with_google_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Continue with Google",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AuthMode.PHONE -> {
                        // 2. Continue with Phone Number + Name + OTP Verify
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = if (!isOtpSent) "Continue with Phone Number" else "Verify 6-Digit OTP",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (!isOtpSent) {
                                    OutlinedTextField(
                                        value = phoneName,
                                        onValueChange = { phoneName = it },
                                        label = { Text("Your Full Name") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            autoCorrect = false,
                                            keyboardType = KeyboardType.Text
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("phone_name_input")
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = phoneNumber,
                                        onValueChange = { phoneNumber = it },
                                        label = { Text("Phone Number (with Country Code)") },
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            autoCorrect = false,
                                            keyboardType = KeyboardType.Phone
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("phone_number_input")
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            var formattedPhone = phoneNumber.trim().replace(" ", "").replace("-", "")
                                            if (formattedPhone.isNotEmpty() && !formattedPhone.startsWith("+")) {
                                                formattedPhone = "+$formattedPhone"
                                            }

                                            if (formattedPhone.length >= 10 && formattedPhone.startsWith("+")) {
                                                isLoading = true
                                                errorMessage = null
                                                successMessage = null
                                                authManager.sendPhoneOtp(
                                                    phoneNumber = formattedPhone,
                                                    activity = context as Activity,
                                                    scope = scope,
                                                    displayName = phoneName.trim(),
                                                    onCodeSent = { vid ->
                                                        isLoading = false
                                                        verificationId = vid
                                                        isOtpSent = true
                                                        successMessage = "Verification OTP code requested for $formattedPhone"
                                                    },
                                                    onInstantSuccess = { user ->
                                                        isLoading = false
                                                        onAuthSuccess(user)
                                                    },
                                                    onError = { err ->
                                                        isLoading = false
                                                        errorMessage = err
                                                    }
                                                )
                                            } else {
                                                errorMessage = "Please enter a valid phone number with international country code (e.g. +91 9876543210 or +1 555 123 4567)."
                                            }
                                        },
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("send_phone_otp_button")
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Send Verification OTP")
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Verification code sent to $phoneNumber",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { if (it.length <= 6) otpCode = it },
                                        label = { Text("6-Digit OTP Code") },
                                        placeholder = { Text("123456") },
                                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("phone_otp_input")
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Button(
                                        onClick = {
                                            val vid = verificationId
                                            if (vid.isNullOrBlank()) {
                                                errorMessage = "Verification session expired. Please request a new OTP."
                                            } else if (otpCode.length == 6) {
                                                isLoading = true
                                                errorMessage = null
                                                authManager.verifyPhoneOtp(
                                                    verificationId = vid,
                                                    otpCode = otpCode,
                                                    displayName = phoneName.trim(),
                                                    scope = scope,
                                                    onSuccess = { user ->
                                                        isLoading = false
                                                        onAuthSuccess(user)
                                                    },
                                                    onError = { err ->
                                                        isLoading = false
                                                        errorMessage = err
                                                    }
                                                )
                                            } else {
                                                errorMessage = "Please enter the complete 6-digit OTP code."
                                            }
                                        },
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("verify_phone_otp_button")
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Verify OTP & Continue")
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            isOtpSent = false
                                            otpCode = ""
                                            verificationId = null
                                            errorMessage = null
                                        },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text("Change Phone Number", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    AuthMode.MANUAL -> {
                        // 3. Manual Sign In / Sign Up (Email & Password)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = if (isSignUp) "Create Alya Account (Sign Up)" else "Manual Sign In (Email)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isSignUp) {
                                    OutlinedTextField(
                                        value = manualName,
                                        onValueChange = { manualName = it },
                                        label = { Text("Full Name") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            autoCorrect = false,
                                            keyboardType = KeyboardType.Text
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_name_input")
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                OutlinedTextField(
                                    value = manualEmail,
                                    onValueChange = { manualEmail = it },
                                    label = { Text("Email Address") },
                                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        autoCorrect = false,
                                        keyboardType = KeyboardType.Email
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_email_input")
                                    )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = manualPassword,
                                    onValueChange = { manualPassword = it },
                                    label = { Text("Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        autoCorrect = false,
                                        keyboardType = KeyboardType.Password
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Password Visibility"
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_password_input")
                                )

                                if (isSignUp) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = manualConfirmPassword,
                                        onValueChange = { manualConfirmPassword = it },
                                        label = { Text("Confirm Password") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            autoCorrect = false,
                                            keyboardType = KeyboardType.Password
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("manual_confirm_password_input")
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        if (manualEmail.isBlank() || !manualEmail.contains("@")) {
                                            errorMessage = "Please enter a valid email address."
                                        } else if (manualPassword.length < 6) {
                                            errorMessage = "Password must be at least 6 characters long."
                                        } else if (isSignUp && manualPassword != manualConfirmPassword) {
                                            errorMessage = "Passwords do not match."
                                        } else {
                                            isLoading = true
                                            errorMessage = null
                                            successMessage = null
                                            if (isSignUp) {
                                                authManager.signUpWithEmail(
                                                    email = manualEmail,
                                                    password = manualPassword,
                                                    displayName = manualName,
                                                    scope = scope,
                                                    onSuccess = { user ->
                                                        isLoading = false
                                                        onAuthSuccess(user)
                                                    },
                                                    onError = { err ->
                                                        isLoading = false
                                                        errorMessage = err
                                                    }
                                                )
                                            } else {
                                                authManager.signInWithEmail(
                                                    email = manualEmail,
                                                    password = manualPassword,
                                                    scope = scope,
                                                    onSuccess = { user ->
                                                        isLoading = false
                                                        onAuthSuccess(user)
                                                    },
                                                    onError = { err ->
                                                        isLoading = false
                                                        errorMessage = err
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("manual_auth_submit_button")
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            if (isSignUp) Icons.Default.Person else Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isSignUp) "Sign Up & Create Account" else "Sign In")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isSignUp) "Already have an account?" else "Don't have an account?",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = {
                                        isSignUp = !isSignUp
                                        errorMessage = null
                                        successMessage = null
                                    }) {
                                        Text(
                                            text = if (isSignUp) "Sign In" else "Sign Up",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Guest Mode Option
            item {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        authManager.signInAnonymously(
                            scope = scope,
                            onSuccess = { user ->
                                isLoading = false
                                onAuthSuccess(user)
                            },
                            onGuestFallback = {
                                isLoading = false
                                onContinueAsGuest?.invoke()
                            },
                            onError = { err ->
                                isLoading = false
                                errorMessage = err
                            }
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier.testTag("continue_as_guest_button")
                ) {
                    Text(
                        text = "Continue as Guest / Offline Assistant",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Error / Feedback Message
            item {
                AnimatedVisibility(visible = errorMessage != null || successMessage != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        } else if (successMessage != null) {
                            Text(
                                text = successMessage ?: "",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
