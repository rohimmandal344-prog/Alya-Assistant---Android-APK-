package com.example.core.model

enum class VoiceState {
    IDLE,
    CONNECTING,
    CONNECTED,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    RECONNECTING,
    ERROR,
    STOPPING
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    ACTION
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    EXECUTING,
    VERIFIED,
    ERROR
}

enum class ActionImpactLevel {
    SAFE,
    SENSITIVE,
    HIGH_IMPACT
}

enum class CapabilityState {
    GRANTED,
    AVAILABLE,
    DENIED,
    RESTRICTED,
    UNSUPPORTED,
    REQUIRES_USER_ACTION,
    TEMPORARILY_UNAVAILABLE,
    FAILED
}

data class CapabilityInfo(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val state: CapabilityState,
    val details: String = ""
)

enum class DeviceActionType {
    OPEN_APP,
    SEARCH_YOUTUBE,
    OPEN_MAPS_NAVIGATE,
    OPEN_BROWSER,
    OPEN_SETTINGS,
    OPEN_WIFI_SETTINGS,
    OPEN_BLUETOOTH_SETTINGS,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_DIALER,
    TOGGLE_FLASHLIGHT,
    SET_VOLUME,
    MEDIA_CONTROL,
    ACCESSIBILITY_CLICK,
    ACCESSIBILITY_SCROLL,
    ACCESSIBILITY_TYPE,
    ACCESSIBILITY_GLOBAL,
    GET_DEVICE_INFO,
    GET_SYSTEM_METRICS,
    AUTO_DIM_SCREEN,
    UNKNOWN
}

data class ActionIntent(
    val action: DeviceActionType,
    val target: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float = 1.0f,
    val impactLevel: ActionImpactLevel = ActionImpactLevel.SAFE,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)

data class ActionExecutionResult(
    val action: String,
    val target: String? = null,
    val success: Boolean,
    val verified: Boolean,
    val feedbackMessage: String,
    val technicalDetails: String = ""
)

data class ConversationMessage(
    val id: Long = 0,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val actionResult: ActionExecutionResult? = null
)

data class MemoryItem(
    val key: String,
    val category: String,
    val content: String,
    val updatedAt: Long = System.currentTimeMillis()
)

sealed class AlyaError(
    val userMessage: String,
    val technicalMessage: String,
    val recoverable: Boolean = true,
    val retryable: Boolean = true,
    val source: String = "SYSTEM"
) : Exception(userMessage) {

    class Network(userMessage: String, technical: String, retryable: Boolean = true) :
        AlyaError(userMessage, technical, recoverable = true, retryable = retryable, source = "NETWORK")

    class Ai(userMessage: String, technical: String, retryable: Boolean = true) :
        AlyaError(userMessage, technical, recoverable = true, retryable = retryable, source = "AI")

    class Audio(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = true, source = "AUDIO")

    class Microphone(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = false, source = "MICROPHONE")

    class Tts(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = true, source = "TTS")

    class Stt(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = true, source = "STT")

    class Accessibility(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = false, source = "ACCESSIBILITY")

    class Permission(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = false, source = "PERMISSION")

    class Authentication(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = false, source = "AUTH")

    class Device(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = true, source = "DEVICE")

    class AppLaunch(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = false, source = "APP_LAUNCH")

    class Database(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = false, source = "DATABASE")

    class Unknown(userMessage: String, technical: String) :
        AlyaError(userMessage, technical, recoverable = true, retryable = true, source = "UNKNOWN")
}
