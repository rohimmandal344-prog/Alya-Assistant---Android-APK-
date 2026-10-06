package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.logger.AlyaLogger
import com.example.core.logger.LogEntry
import com.example.core.model.ActionExecutionResult
import com.example.core.model.ActionIntent
import com.example.core.model.CapabilityInfo
import com.example.core.model.ConversationMessage
import com.example.core.model.MemoryItem
import com.example.core.model.VoiceState
import com.example.device.AlyaAutomationService
import com.example.device.AppInfo
import com.example.device.BatteryInfo
import com.example.device.SystemMetrics
import com.example.device.TimeContext
import com.example.orchestrator.AssistantOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab {
    CHAT,
    LIVE_VOICE,
    LOGS,
    DEVICE_CONTROL,
    PERMISSIONS,
    DIAGNOSTICS,
    SETTINGS
}

data class PermissionState(
    val hasMicrophone: Boolean = false,
    val hasLocation: Boolean = false,
    val hasNotifications: Boolean = false,
    val hasCamera: Boolean = false,
    val hasCalendar: Boolean = false,
    val hasCallLogs: Boolean = false,
    val hasContacts: Boolean = false,
    val hasPhone: Boolean = false,
    val hasPhotosAndVideos: Boolean = false,
    val hasPhysicalActivity: Boolean = false,
    val hasSms: Boolean = false,
    val isAccessibilityEnabled: Boolean = false
)

data class AlyaUiState(
    val selectedTab: AppTab = AppTab.CHAT,
    val voiceState: VoiceState = VoiceState.IDLE,
    val messages: List<ConversationMessage> = emptyList(),
    val memories: List<MemoryItem> = emptyList(),
    val speakingMessageId: Long? = null,
    val selectedLanguage: String = "en-US",
    val customApiKey: String = "",
    val backgroundTalkingEnabled: Boolean = true,
    val isLiveActive: Boolean = false,
    val isFlashlightOn: Boolean = false,
    val currentVolume: Int = 5,
    val maxVolume: Int = 15,
    val batteryInfo: BatteryInfo? = null,
    val timeContext: TimeContext? = null,
    val systemMetrics: SystemMetrics? = null,
    val capabilities: List<CapabilityInfo> = emptyList(),
    val installedApps: List<AppInfo> = emptyList(),
    val lastAction: ActionExecutionResult? = null,
    val pendingConfirmation: ActionIntent? = null,
    val permissions: PermissionState = PermissionState(),
    val logs: List<LogEntry> = emptyList(),
    val errorMessage: String? = null,
    val aiModel: String = "gemini-3.5-flash",
    val thinkingLevel: String = "low",
    val voicePitch: Float = 1.05f,
    val voiceSpeed: Float = 1.0f,
    val forceOffline: Boolean = false,
    val customWakeWord: String = "Alya",
    val wakeSensitivity: Float = 0.7f,
    val showAccessibilityDialog: Boolean = false,
    val hapticFeedbackEnabled: Boolean = true,
    val auditLogs: List<com.example.data.local.ActionAuditEntity> = emptyList(),
    val isCompanionOverlayEnabled: Boolean = true,
    val companionCharacterState: String = "IDLE",
    val companionExpression: String = "idle",
    val companionScale: Float = 0.16f
)

class AlyaViewModel(application: Application) : AndroidViewModel(application) {

    private val orchestrator = AssistantOrchestrator(application, viewModelScope)

    private val _uiState = MutableStateFlow(AlyaUiState())
    val uiState: StateFlow<AlyaUiState> = _uiState.asStateFlow()

    init {
        // Collect reactive flows from Orchestrator and System
        viewModelScope.launch {
            orchestrator.messages.collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }
        viewModelScope.launch {
            orchestrator.memories.collect { mems ->
                _uiState.update { it.copy(memories = mems) }
            }
        }
        viewModelScope.launch {
            orchestrator.auditLogsFlow.collect { logs ->
                _uiState.update { it.copy(auditLogs = logs) }
            }
        }
        viewModelScope.launch {
            orchestrator.speakingMessageId.collect { id ->
                _uiState.update { it.copy(speakingMessageId = id) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.selectedLanguage.collect { lang ->
                _uiState.update { it.copy(selectedLanguage = lang) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.customApiKey.collect { key ->
                _uiState.update { it.copy(customApiKey = key) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.backgroundTalkingEnabled.collect { bg ->
                _uiState.update { it.copy(backgroundTalkingEnabled = bg) }
            }
        }
        viewModelScope.launch {
            orchestrator.voiceState.collect { vs ->
                _uiState.update { it.copy(voiceState = vs) }
            }
        }
        viewModelScope.launch {
            orchestrator.isLiveActive.collect { live ->
                _uiState.update { it.copy(isLiveActive = live) }
            }
        }
        viewModelScope.launch {
            orchestrator.companionTriggerFlow.collect { trigger ->
                _uiState.update { it.copy(companionCharacterState = trigger) }
            }
        }
        viewModelScope.launch {
            orchestrator.companionExpressionFlow.collect { expr ->
                _uiState.update { it.copy(companionExpression = expr) }
            }
        }
        viewModelScope.launch {
            orchestrator.lastAction.collect { act ->
                _uiState.update { it.copy(lastAction = act) }
            }
        }
        viewModelScope.launch {
            orchestrator.pendingConfirmation.collect { pending ->
                _uiState.update { it.copy(pendingConfirmation = pending) }
            }
        }
        viewModelScope.launch {
            orchestrator.errorMessage.collect { err ->
                _uiState.update { it.copy(errorMessage = err) }
            }
        }
        viewModelScope.launch {
            AlyaLogger.logsFlow.collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.aiModel.collect { model ->
                _uiState.update { it.copy(aiModel = model) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.voicePitch.collect { pitch ->
                _uiState.update { it.copy(voicePitch = pitch) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.voiceSpeed.collect { speed ->
                _uiState.update { it.copy(voiceSpeed = speed) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.forceOfflineMode.collect { offline ->
                _uiState.update { it.copy(forceOffline = offline) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.customWakeWord.collect { word ->
                _uiState.update { it.copy(customWakeWord = word) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.wakeSensitivity.collect { sens ->
                _uiState.update { it.copy(wakeSensitivity = sens) }
            }
        }
        viewModelScope.launch {
            orchestrator.preferencesManager.hapticFeedbackEnabled.collect { haptic ->
                _uiState.update { it.copy(hapticFeedbackEnabled = haptic) }
            }
        }

        refreshState()
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refreshState() {
        viewModelScope.launch {
            checkPermissions()
            val apps = orchestrator.deviceController.getInstalledApplications()
            val volume = orchestrator.deviceController.getCurrentVolume()
            val flashlight = orchestrator.deviceController.isFlashlightOn()
            val time = orchestrator.deviceController.getTimeContext()
            val battery = orchestrator.deviceController.getBatteryInfo()
            val metrics = orchestrator.deviceController.getSystemMetrics()
            val caps = orchestrator.deviceController.getCapabilitiesRegistry()

            _uiState.update {
                it.copy(
                    installedApps = apps,
                    currentVolume = volume.first,
                    maxVolume = volume.second,
                    isFlashlightOn = flashlight,
                    timeContext = time,
                    batteryInfo = battery,
                    systemMetrics = metrics,
                    capabilities = caps
                )
            }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val mic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val loc = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notif = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val cam = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        
        val cal = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val callLogs = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val contacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        
        val photos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        
        val activity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true
        
        val sms = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val access = AlyaAutomationService.instance != null

        val perms = PermissionState(
            hasMicrophone = mic,
            hasLocation = loc,
            hasNotifications = notif,
            hasCamera = cam,
            hasCalendar = cal,
            hasCallLogs = callLogs,
            hasContacts = contacts,
            hasPhone = phone,
            hasPhotosAndVideos = photos,
            hasPhysicalActivity = activity,
            hasSms = sms,
            isAccessibilityEnabled = access
        )
        _uiState.update { it.copy(permissions = perms) }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            orchestrator.processUserInput(text, speakResponse = false)
        }
    }

    fun togglePlayMessage(message: ConversationMessage) {
        orchestrator.togglePlayMessage(message)
    }

    fun deleteMemory(key: String) {
        orchestrator.deleteMemory(key)
    }

    fun setLanguage(languageTag: String) {
        orchestrator.setLanguage(languageTag)
    }

    fun setCustomApiKey(key: String) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setCustomApiKey(key)
        }
    }

    fun setBackgroundTalking(enabled: Boolean) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setBackgroundTalkingEnabled(enabled)
        }
    }

    fun startLiveVoice() {
        orchestrator.startLiveVoiceMode()
    }

    fun stopLiveVoice() {
        orchestrator.stopLiveVoiceMode()
    }

    fun triggerBargeIn() {
        orchestrator.handleBargeIn()
    }

    fun toggleMic() {
        orchestrator.toggleSpeechRecognition()
    }

    fun toggleFlashlight() {
        viewModelScope.launch {
            val newState = !_uiState.value.isFlashlightOn
            val res = orchestrator.deviceController.toggleFlashlight(newState)
            if (res.isSuccess) {
                _uiState.update { it.copy(isFlashlightOn = newState) }
            }
        }
    }

    fun setVolume(level: Int) {
        viewModelScope.launch {
            val res = orchestrator.deviceController.setVolume(android.media.AudioManager.STREAM_MUSIC, level)
            if (res.isSuccess) {
                val current = orchestrator.deviceController.getCurrentVolume()
                _uiState.update { it.copy(currentVolume = current.first, maxVolume = current.second) }
            }
        }
    }

    fun searchYouTube(query: String) {
        viewModelScope.launch {
            orchestrator.processUserInput("search youtube for $query", speakResponse = false)
        }
    }

    fun openMapsNavigation(destination: String) {
        viewModelScope.launch {
            orchestrator.processUserInput("navigate to $destination", speakResponse = false)
        }
    }

    fun openApp(packageName: String) {
        viewModelScope.launch {
            orchestrator.deviceController.openApp(packageName)
            refreshState()
        }
    }

    fun confirmAction(proceed: Boolean) {
        orchestrator.confirmPendingAction(proceed)
    }

    fun clearChat() {
        orchestrator.clearHistory()
    }

    fun clearAuditLogs() {
        orchestrator.clearAuditLogs()
    }

    fun dismissError() {
        orchestrator.dismissError()
    }

    fun setAiModel(model: String) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setAiModel(model)
        }
    }

    fun setVoicePitch(pitch: Float) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setVoicePitch(pitch)
        }
    }

    fun setVoiceSpeed(speed: Float) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setVoiceSpeed(speed)
        }
    }

    fun setForceOffline(offline: Boolean) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setForceOfflineMode(offline)
        }
    }

    fun setCustomWakeWord(word: String) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setCustomWakeWord(word)
        }
    }

    fun setWakeSensitivity(sensitivity: Float) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setWakeSensitivity(sensitivity)
        }
    }

    fun syncUserSession(user: com.google.firebase.auth.FirebaseUser?) {
        if (user != null) {
            viewModelScope.launch {
                try {
                    val firestoreRepo = com.example.data.repository.AlyaFirestoreRepository(getApplication())
                    firestoreRepo.syncUserProfile(
                        displayName = user.displayName ?: user.phoneNumber ?: "Alya User",
                        email = user.email ?: user.phoneNumber,
                        photoUrl = user.photoUrl?.toString()
                    )
                } catch (e: Exception) {
                    com.example.core.logger.AlyaLogger.w("ALYA_VIEWMODEL", "Firestore sync: ${e.message}")
                }
            }
        }
    }

    fun dismissAccessibilityDialog() {
        _uiState.update { it.copy(showAccessibilityDialog = false) }
    }

    fun triggerDeviceControlTask(actionName: String, actionBlock: () -> Unit) {
        val context = getApplication<Application>()
        val requiresAccessibility = actionName.contains("click", true) || 
                                    actionName.contains("scroll", true) || 
                                    actionName.contains("global", true) || 
                                    actionName.contains("accessibility", true) ||
                                    actionName.contains("automation", true)

        if (requiresAccessibility) {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
            val isServiceEnabled = com.example.device.AccessibilityHelper.isAccessibilityServiceEnabled(context)
            if (!accessibilityManager.isEnabled || !isServiceEnabled) {
                _uiState.update { it.copy(showAccessibilityDialog = true) }
                return
            }
        }
        actionBlock()
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            orchestrator.preferencesManager.setHapticFeedbackEnabled(enabled)
        }
    }

    fun toggleCompanionOverlay() {
        _uiState.update { it.copy(isCompanionOverlayEnabled = !it.isCompanionOverlayEnabled) }
    }

    fun setCompanionState(state: String, expression: String = "idle") {
        _uiState.update { it.copy(companionCharacterState = state, companionExpression = expression) }
    }

    override fun onCleared() {
        super.onCleared()
        orchestrator.release()
    }
}
