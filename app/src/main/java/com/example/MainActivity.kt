package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.AuthManager
import com.example.core.model.ConversationMessage
import com.example.ui.AlyaUiState
import com.example.ui.AlyaViewModel
import com.example.ui.AppTab
import com.example.ui.components.AccessibilityPermissionDialog
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.LiveVoiceScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.auth.FirebaseUser

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: AlyaViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val authManager = remember { AuthManager(applicationContext) }
                val currentUser by authManager.currentUser.collectAsStateWithLifecycle()
                var isGuestSession by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // Attempt silent auto-sign in on launch
                LaunchedEffect(Unit) {
                    authManager.attemptAutoSignIn(scope)
                }

                // Permission Launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    viewModel.checkPermissions()
                }

                if (currentUser == null && !isGuestSession) {
                    // Auth Gate: Require Authentication or Guest Mode
                    SignInScreen(
                        authManager = authManager,
                        onAuthSuccess = { user ->
                            viewModel.syncUserSession(user)
                            viewModel.refreshState()
                        },
                        onContinueAsGuest = {
                            isGuestSession = true
                            viewModel.setForceOffline(false)
                        }
                    )
                } else {
                    // Authenticated or Guest User Session
                    AlyaApp(
                        state = uiState,
                        currentUser = currentUser,
                        onSignOut = {
                            isGuestSession = false
                            authManager.signOut(scope)
                        },
                        onSelectTab = { viewModel.selectTab(it) },
                        onSendMessage = { viewModel.sendMessage(it) },
                        onToggleMic = {
                            if (!uiState.permissions.hasMicrophone) {
                                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.toggleMic()
                            }
                        },
                        onStartLive = {
                            if (!uiState.permissions.hasMicrophone) {
                                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.startLiveVoice()
                                viewModel.selectTab(AppTab.LIVE_VOICE)
                                // Hide overlay when entering immersive Live screen
                                com.example.service.AlyaForegroundService.hideOverlayAction(applicationContext)
                            }
                        },
                        onStopLive = {
                            viewModel.stopLiveVoice()
                            viewModel.selectTab(AppTab.CHAT)
                            // Resume overlay when leaving Live screen
                            com.example.service.AlyaForegroundService.showOverlayAction(applicationContext)
                        },
                        onTriggerBargeIn = { viewModel.triggerBargeIn() },
                        onToggleAudio = { viewModel.togglePlayMessage(it) },
                        onSetLanguage = { viewModel.setLanguage(it) },
                        onCustomApiKeyChange = { viewModel.setCustomApiKey(it) },
                        onBackgroundTalkingChange = { viewModel.setBackgroundTalking(it) },
                        onVoicePitchChange = { viewModel.setVoicePitch(it) },
                        onVoiceSpeedChange = { viewModel.setVoiceSpeed(it) },
                        onDeleteMemory = { viewModel.deleteMemory(it) },
                        onRequestPermission = { permissionLauncher.launch(it) },
                        onConfirmAction = { viewModel.confirmAction(it) },
                        onClearChat = { viewModel.clearChat() },
                        onDismissError = { viewModel.dismissError() },
                        onSetForceOffline = { viewModel.setForceOffline(it) },
                        onRefreshDiagnostics = { viewModel.refreshState() },
                        onDismissAccessibilityDialog = { viewModel.dismissAccessibilityDialog() },
                        onOpenAccessibilitySettings = {
                            viewModel.dismissAccessibilityDialog()
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                applicationContext.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                                applicationContext.startActivity(intent)
                            }
                        },
                        onHapticFeedbackChange = { viewModel.setHapticFeedbackEnabled(it) },
                        onClearAuditLogs = { viewModel.clearAuditLogs() }
                    )
                }
            }
        }
    }
}

@Composable
fun AlyaApp(
    state: AlyaUiState,
    currentUser: FirebaseUser?,
    onSignOut: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onSendMessage: (String) -> Unit,
    onToggleMic: () -> Unit,
    onStartLive: () -> Unit,
    onStopLive: () -> Unit,
    onTriggerBargeIn: () -> Unit,
    onToggleAudio: (ConversationMessage) -> Unit,
    onSetLanguage: (String) -> Unit,
    onCustomApiKeyChange: (String) -> Unit,
    onBackgroundTalkingChange: (Boolean) -> Unit,
    onVoicePitchChange: (Float) -> Unit,
    onVoiceSpeedChange: (Float) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onRequestPermission: (String) -> Unit,
    onConfirmAction: (Boolean) -> Unit,
    onClearChat: () -> Unit,
    onDismissError: () -> Unit,
    onSetForceOffline: (Boolean) -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onDismissAccessibilityDialog: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onClearAuditLogs: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    AccessibilityPermissionDialog(
        show = state.showAccessibilityDialog,
        onDismiss = onDismissAccessibilityDialog
    )

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            onDismissError()
        }
    }

    // Back handling: pop tab to CHAT if on secondary screen
    BackHandler(enabled = state.selectedTab != AppTab.CHAT) {
        if (state.selectedTab == AppTab.LIVE_VOICE && state.isLiveActive) {
            onStopLive()
        }
        onSelectTab(AppTab.CHAT)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Hide bottom bar during immersive live voice session
            AnimatedVisibility(visible = state.selectedTab != AppTab.LIVE_VOICE) {
                NavigationBar(
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = state.selectedTab == AppTab.CHAT,
                        onClick = { onSelectTab(AppTab.CHAT) },
                        icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chat") },
                        label = { Text("Chat") },
                        modifier = Modifier.testTag("nav_chat")
                    )
                    NavigationBarItem(
                        selected = state.selectedTab == AppTab.LIVE_VOICE,
                        onClick = { onStartLive() },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Live") },
                        label = { Text("Live") },
                        modifier = Modifier.testTag("nav_live")
                    )
                    NavigationBarItem(
                        selected = state.selectedTab == AppTab.LOGS,
                        onClick = { onSelectTab(AppTab.LOGS) },
                        icon = { Icon(Icons.Default.History, contentDescription = "Logs") },
                        label = { Text("Logs") },
                        modifier = Modifier.testTag("nav_logs")
                    )
                    NavigationBarItem(
                        selected = state.selectedTab == AppTab.SETTINGS,
                        onClick = { onSelectTab(AppTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (state.selectedTab) {
                AppTab.CHAT -> ChatScreen(
                    state = state,
                    onSendMessage = onSendMessage,
                    onToggleMic = onToggleMic,
                    onStartLive = onStartLive,
                    onClearChat = onClearChat,
                    onToggleAudio = onToggleAudio
                )
                AppTab.LIVE_VOICE -> LiveVoiceScreen(
                    state = state,
                    onStopLive = onStopLive,
                    onTriggerBargeIn = onTriggerBargeIn
                )
                AppTab.LOGS -> LogsScreen(
                    auditLogs = state.auditLogs,
                    onClearLogs = onClearAuditLogs
                )
                else -> SettingsScreen(
                    state = state,
                    currentUser = currentUser,
                    onSignOut = onSignOut,
                    onLanguageChange = onSetLanguage,
                    onCustomApiKeyChange = onCustomApiKeyChange,
                    onBackgroundTalkingChange = onBackgroundTalkingChange,
                    onHapticFeedbackChange = onHapticFeedbackChange,
                    onDeleteMemory = onDeleteMemory,
                    onVoicePitchChange = onVoicePitchChange,
                    onVoiceSpeedChange = onVoiceSpeedChange,
                    onClearChat = onClearChat
                )
            }

            // Action Confirmation Dialog
            if (state.pendingConfirmation != null) {
                AlertDialog(
                    onDismissRequest = { onConfirmAction(false) },
                    title = {
                        Text(
                            text = "Authorize Action?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            state.pendingConfirmation.confirmationPrompt
                                ?: "Alya is requesting to execute: ${state.pendingConfirmation.action} on ${state.pendingConfirmation.target ?: "device"}. Do you want to proceed?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { onConfirmAction(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Confirm & Execute")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { onConfirmAction(false) }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
