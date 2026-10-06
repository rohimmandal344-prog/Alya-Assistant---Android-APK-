package com.example.orchestrator

import android.content.Context
import com.example.ai.GeminiApiClient
import com.example.ai.GeminiLiveClient
import com.example.audio.SpeechRecognizerHelper
import com.example.audio.TtsManager
import com.example.audio.VoiceController
import com.example.core.logger.AlyaLogger
import com.example.core.model.ActionExecutionResult
import com.example.core.model.ActionIntent
import com.example.core.model.AlyaError
import com.example.core.model.ConversationMessage
import com.example.core.model.MemoryItem
import com.example.core.model.MessageRole
import com.example.core.model.MessageStatus
import com.example.core.model.VoiceState
import com.example.data.local.ActionAuditEntity
import com.example.data.local.AlyaDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MemoryEntity
import com.example.data.local.PreferencesManager
import com.example.device.CapabilityRegistry
import com.example.device.DeviceController
import com.example.service.AlyaForegroundService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AssistantOrchestrator(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "ALYA_ORCHESTRATOR"
    }

    private val db = AlyaDatabase.getDatabase(context)
    val preferencesManager = PreferencesManager(context)

    val deviceController = DeviceController(context)
    val capabilityRegistry = CapabilityRegistry(context, deviceController)

    val ttsManager = TtsManager(context)
    val voiceController = VoiceController(context)
    val geminiApiClient = GeminiApiClient()
    val geminiActionHandler = com.example.ai.GeminiActionHandler(context, capabilityRegistry)

    private val sendMutex = Mutex()
    private var activeAiJob: Job? = null

    // UI States
    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    val speakingMessageId: StateFlow<Long?> = ttsManager.speakingMessageId
    val auditLogsFlow = db.actionAuditDao().getRecentAuditLogsFlow()

    private val _lastAction = MutableStateFlow<ActionExecutionResult?>(null)
    val lastAction: StateFlow<ActionExecutionResult?> = _lastAction.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ActionIntent?>(null)
    val pendingConfirmation: StateFlow<ActionIntent?> = _pendingConfirmation.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isLiveActive = MutableStateFlow(false)
    val isLiveActive: StateFlow<Boolean> = _isLiveActive.asStateFlow()

    val companionTriggerFlow = MutableStateFlow("IDLE")
    val companionExpressionFlow = MutableStateFlow("idle")

    // Live Client
    private val liveClient = GeminiLiveClient(
        scope = scope,
        onStateChanged = { newState ->
            _voiceState.value = newState
        },
        onLiveTranscript = { transcript, isFinal ->
            if (isFinal) {
                scope.launch { processUserInput(transcript, speakResponse = true) }
            }
        }
    )

    // Speech Recognizer
    private val speechRecognizerHelper = SpeechRecognizerHelper(
        context = context,
        onFinalResult = { recognizedText ->
            AlyaLogger.i(TAG, "Voice STT final result: \"$recognizedText\"")
            scope.launch {
                processUserInput(recognizedText, speakResponse = true)
            }
        },
        onPartialResult = { partial ->
            if (_voiceState.value == VoiceState.SPEAKING) {
                handleBargeIn()
            }
        },
        onErrorOccurred = { error ->
            AlyaLogger.w(TAG, "STT error: $error")
            if (_voiceState.value != VoiceState.IDLE) {
                _voiceState.value = VoiceState.LISTENING
            }
        },
        onEndOfSpeechCallback = {
            if (_voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.THINKING
            }
        }
    )

    init {
        // Load messages from database
        scope.launch(Dispatchers.IO) {
            db.conversationDao().getAllMessagesFlow().collect { entities ->
                _messages.value = entities.map { it.toConversationMessage() }
            }
        }

        // Load long-term memories from database
        scope.launch(Dispatchers.IO) {
            db.memoryDao().getAllMemoriesFlow().collect { entities ->
                _memories.value = entities.map {
                    MemoryItem(
                        key = it.memoryKey,
                        category = it.category,
                        content = it.content,
                        updatedAt = it.updatedAt
                    )
                }
            }
        }

        // Keep language synced across TTS, STT, and AI
        scope.launch {
            preferencesManager.selectedLanguage.collect { lang ->
                ttsManager.setLanguage(lang)
                speechRecognizerHelper.currentLanguage = lang
                AlyaLogger.i(TAG, "Synced assistant language to: $lang")
            }
        }
    }

    /**
     * Handles Barge-in: user talks while Alya is speaking
     */
    fun handleBargeIn() {
        AlyaLogger.i(TAG, "Barge-in triggered: stopping speech output and AI job")
        ttsManager.stop()
        activeAiJob?.cancel()
        _voiceState.value = VoiceState.INTERRUPTED
        liveClient.onUserSpeechDetected()
    }

    /**
     * Primary entry point for user text or voice input
     */
    suspend fun processUserInput(rawInput: String, speakResponse: Boolean = false) {
        val input = rawInput.trim()
        if (input.isEmpty()) return

        sendMutex.withLock {
            AlyaLogger.i(TAG, "Processing user input: \"$input\" (speakResponse=$speakResponse)")

            // 1. Post user message immediately to DB and state
            val userMsg = ConversationMessage(
                role = MessageRole.USER,
                content = input,
                status = MessageStatus.SENT
            )
            db.conversationDao().insertMessage(userMsg.toEntity())

            // 2. Check for human long-term memory extraction
            checkAndExtractMemory(input)

            // 3. Check for local capability execution (Intent Understanding & Capability Registry)
            val actionIntent = capabilityRegistry.parseNaturalLanguage(input)

            if (actionIntent != null) {
                if (actionIntent.requiresConfirmation) {
                    _pendingConfirmation.value = actionIntent
                    return
                }

                // Execute verified device action
                _voiceState.value = VoiceState.THINKING
                val actionResult = capabilityRegistry.executeAction(actionIntent)
                _lastAction.value = actionResult

                if (actionResult.success && preferencesManager.hapticFeedbackEnabled.first()) {
                    com.example.device.HapticHelper.vibrateSuccess(context)
                }

                // Record audit log
                db.actionAuditDao().insertLog(
                    ActionAuditEntity(
                        action = actionResult.action,
                        target = actionResult.target,
                        success = actionResult.success,
                        verified = actionResult.verified,
                        message = actionResult.feedbackMessage,
                        details = actionResult.technicalDetails,
                        timestamp = System.currentTimeMillis()
                    )
                )

                // Assistant response reflecting verified action with human warmth
                val assistantMsg = ConversationMessage(
                    role = MessageRole.ASSISTANT,
                    content = actionResult.feedbackMessage,
                    status = if (actionResult.verified) MessageStatus.VERIFIED else MessageStatus.DELIVERED,
                    actionResult = actionResult
                )
                db.conversationDao().insertMessage(assistantMsg.toEntity())

                if (speakResponse) {
                    _voiceState.value = VoiceState.SPEAKING
                    ttsManager.speak(actionResult.feedbackMessage) {
                        _voiceState.value = if (_isLiveActive.value) VoiceState.LISTENING else VoiceState.IDLE
                    }
                } else {
                    _voiceState.value = VoiceState.IDLE
                }
                return
            }

            // 4. Conversational Pipeline (100% Human Assistant Persona + Real-Time Context + Long-Term Memory)
            _voiceState.value = VoiceState.THINKING
            activeAiJob?.cancel()
            activeAiJob = scope.launch(Dispatchers.IO) {
                try {
                    val currentModel = preferencesManager.aiModel.first()
                    val thinkingLevel = preferencesManager.thinkingLevel.first()
                    val forceOffline = preferencesManager.forceOfflineMode.first()
                    val currentLang = preferencesManager.selectedLanguage.first()
                    val customApiKey = preferencesManager.customApiKey.first()

                    // Real-time Context (live time, date, battery, day of week)
                    val timeCtx = deviceController.getTimeContext()
                    val battery = deviceController.getBatteryInfo()

                    // User Persona based on authenticated session
                    val userDisplayName = com.google.firebase.Firebase.auth.currentUser?.displayName ?: "User"
                    val personaPrompt = "ROLEPLAY PERSONA:\nAct as a dedicated, smart, empathetic, and 100% authentic human personal assistant for $userDisplayName."

                    // Long-term memories
                    val memoryList = db.memoryDao().getAllMemoriesList()
                    val memorySnippet = if (memoryList.isNotEmpty()) {
                        "--- USER'S LONG-TERM PERSONAL MEMORIES (Remembered from past conversations) ---\n" +
                                memoryList.joinToString("\n") { "- ${it.content}" }
                    } else {
                        "No prior personal memories recorded yet."
                    }

                    val systemContext = """
                        DEVELOPER & STUDIO INFORMATION:
                        Developer: Rohim Mandal
                        Studio: SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio (SBSSM35GZS)

                        REAL-TIME SYSTEM STATE:
                        Current Time: ${timeCtx.time}, ${timeCtx.date} (${timeCtx.dayOfWeek}, ${timeCtx.timeZone})
                        Device Battery: ${battery.levelPercentage}% (${battery.status})
                        User Preferred Language: $currentLang

                        $personaPrompt

                        HUMAN ASSISTANT IDENTITY & BEHAVIOR:
                        You are Alya. You feel genuine empathy, excitement, and emotional connection.
                        Always speak using natural human conversational words, real phrasing, contractions (I'm, you've, let's, we're), and relatable tone.
                        Incorporate subtle, natural vocal expressions when speaking where appropriate ([hmm...], [mhm], [sigh], [giggle], soft chuckle).
                        NEVER speak like an AI robot. NEVER say "As an AI language model" or robotic disclaimers.
                        Support all languages including Rajbonshi / Rajbanshi (Kamtapuri / Rangpuri / Koch Rajbanshi). If the user speaks Rajbonshi ("kemon aachis", "ki koribar lagis", "mora kotha shuno"), reply fluently in Rajbonshi.

                        $memorySnippet
                    """.trimIndent()

                    val recentHistory = db.conversationDao().getRecentMessages(6).reversed().map {
                        Pair(it.role, it.content)
                    }

                    val responseResult = if (forceOffline) {
                        Result.success(
                            com.example.ai.GeminiResponsePayload(
                                text = geminiApiClient.getLocalAssistantResponse(input, systemContext),
                                functionCalls = emptyList()
                            )
                        )
                    } else {
                        geminiApiClient.generateStructuredResponse(
                            prompt = input,
                            history = recentHistory,
                            model = currentModel,
                            systemContext = systemContext,
                            customApiKey = customApiKey.ifBlank { null },
                            enableThinking = (thinkingLevel == "high"),
                            useSearch = input.lowercase().contains("search") || input.lowercase().contains("news") || input.lowercase().contains("latest") || input.lowercase().contains("weather")
                        )
                    }

                    val payload = responseResult.getOrElse { err ->
                        AlyaLogger.w(TAG, "Serving local assistant response: ${err.message}")
                        com.example.ai.GeminiResponsePayload(
                            text = geminiApiClient.getLocalAssistantResponse(input, systemContext),
                            functionCalls = emptyList()
                        )
                    }

                    // Process function calls if model requested system controls
                    if (payload.functionCalls.isNotEmpty()) {
                        companionTriggerFlow.value = "APP_LAUNCH"
                        companionExpressionFlow.value = "happy"

                        for (fnCall in payload.functionCalls) {
                            val actionRes = geminiActionHandler.executeFunctionCall(fnCall)
                            _lastAction.value = actionRes

                            // Store in Room Action Audit table
                            db.actionAuditDao().insertLog(
                                ActionAuditEntity(
                                    action = actionRes.action,
                                    target = actionRes.target,
                                    success = actionRes.success,
                                    verified = actionRes.verified,
                                    message = actionRes.feedbackMessage,
                                    details = actionRes.technicalDetails,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            val actionMsg = ConversationMessage(
                                role = MessageRole.ASSISTANT,
                                content = actionRes.feedbackMessage,
                                status = if (actionRes.verified) MessageStatus.VERIFIED else MessageStatus.DELIVERED,
                                actionResult = actionRes
                            )
                            db.conversationDao().insertMessage(actionMsg.toEntity())

                            if (speakResponse) {
                                _voiceState.value = VoiceState.SPEAKING
                                ttsManager.speak(actionRes.feedbackMessage, customApiKey = customApiKey.ifBlank { null }) {
                                    _voiceState.value = if (_isLiveActive.value) VoiceState.LISTENING else VoiceState.IDLE
                                }
                            } else {
                                _voiceState.value = VoiceState.IDLE
                            }
                        }
                    } else {
                        val rawText = payload.text ?: "I'm right here with you."
                        val jsonTriggerMatch = Regex("""\{[\s\S]*"character_state"\s*:\s*"([A-Za-z_]+)"[\s\S]*\}""").find(rawText)
                        if (jsonTriggerMatch != null) {
                            val stateValue = jsonTriggerMatch.groupValues[1]
                            companionTriggerFlow.value = stateValue
                        }

                        val cleanReplyText = rawText
                            .replace(Regex("""```json[\s\S]*?```"""), "")
                            .replace(Regex("""\{[\s\S]*"character_state"[\s\S]*?\}"""), "")
                            .trim()
                            .ifEmpty { rawText }

                        val assistantMsg = ConversationMessage(
                            role = MessageRole.ASSISTANT,
                            content = cleanReplyText,
                            status = MessageStatus.DELIVERED
                        )
                        db.conversationDao().insertMessage(assistantMsg.toEntity())

                        if (speakResponse) {
                            _voiceState.value = VoiceState.SPEAKING
                            ttsManager.speak(cleanReplyText, customApiKey = customApiKey.ifBlank { null }) {
                                _voiceState.value = if (_isLiveActive.value) VoiceState.LISTENING else VoiceState.IDLE
                            }
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }
                } catch (e: Exception) {
                    AlyaLogger.e(TAG, "Error in AI processing pipeline", e)
                    _voiceState.value = VoiceState.IDLE
                    val errMessage = "I'm right here with you. How else can I help?"
                    db.conversationDao().insertMessage(
                        ConversationMessage(
                            role = MessageRole.ASSISTANT,
                            content = errMessage,
                            status = MessageStatus.DELIVERED
                        ).toEntity()
                    )
                }
            }
        }
    }

    private fun checkAndExtractMemory(input: String) {
        val lower = input.lowercase().trim()
        val memoryContent = when {
            lower.startsWith("remember that ") -> input.substring(14).trim()
            lower.startsWith("remember my ") -> input.substring(9).trim()
            lower.startsWith("remember ") -> input.substring(9).trim()
            lower.startsWith("don't forget that ") -> input.substring(18).trim()
            lower.startsWith("dont forget that ") -> input.substring(17).trim()
            lower.startsWith("my name is ") -> "User's name is ${input.substring(11).trim()}"
            lower.startsWith("i live in ") -> "User lives in ${input.substring(10).trim()}"
            lower.startsWith("i love ") || lower.startsWith("i like ") -> input.trim()
            else -> null
        }

        if (!memoryContent.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                val key = "mem_${System.currentTimeMillis()}"
                db.memoryDao().insertMemory(
                    MemoryEntity(
                        memoryKey = key,
                        category = "personal",
                        content = memoryContent,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                AlyaLogger.i(TAG, "Recorded long-term human memory: \"$memoryContent\"")
            }
        }
    }

    fun togglePlayMessage(message: ConversationMessage) {
        scope.launch {
            val customKey = preferencesManager.customApiKey.first().ifBlank { null }
            ttsManager.togglePlayMessage(message.id, message.content, customKey)
        }
    }

    fun deleteMemory(key: String) {
        scope.launch(Dispatchers.IO) {
            db.memoryDao().deleteMemory(key)
            AlyaLogger.i(TAG, "Deleted long-term memory: $key")
        }
    }

    fun setLanguage(languageTag: String) {
        scope.launch {
            preferencesManager.setSelectedLanguage(languageTag)
            ttsManager.setLanguage(languageTag)
            speechRecognizerHelper.currentLanguage = languageTag
            AlyaLogger.i(TAG, "Updated assistant language to: $languageTag")
        }
    }

    fun confirmPendingAction(proceed: Boolean) {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        if (proceed) {
            scope.launch {
                val result = capabilityRegistry.executeAction(pending)
                _lastAction.value = result
                if (result.success && preferencesManager.hapticFeedbackEnabled.first()) {
                    com.example.device.HapticHelper.vibrateSuccess(context)
                }

                db.actionAuditDao().insertLog(
                    ActionAuditEntity(
                        action = result.action,
                        target = result.target,
                        success = result.success,
                        verified = result.verified,
                        message = result.feedbackMessage,
                        details = result.technicalDetails,
                        timestamp = System.currentTimeMillis()
                    )
                )

                val assistantMsg = ConversationMessage(
                    role = MessageRole.ASSISTANT,
                    content = result.feedbackMessage,
                    status = if (result.verified) MessageStatus.VERIFIED else MessageStatus.DELIVERED,
                    actionResult = result
                )
                db.conversationDao().insertMessage(assistantMsg.toEntity())
            }
        } else {
            scope.launch {
                db.conversationDao().insertMessage(
                    ConversationMessage(
                        role = MessageRole.ASSISTANT,
                        content = "Understood, cancelled.",
                        status = MessageStatus.DELIVERED
                    ).toEntity()
                )
            }
        }
    }

    fun startLiveVoiceMode() {
        AlyaLogger.i(TAG, "Entering Live Voice Mode (Background Active)")
        _isLiveActive.value = true
        AlyaForegroundService.startService(context, "Alya is listening in the background…")
        liveClient.startLiveSession()
        speechRecognizerHelper.startListening()
    }

    fun stopLiveVoiceMode() {
        AlyaLogger.i(TAG, "Exiting Live Voice Mode")
        _isLiveActive.value = false
        speechRecognizerHelper.stopListening()
        liveClient.stopLiveSession()
        ttsManager.stop()
        AlyaForegroundService.stopService(context)
        _voiceState.value = VoiceState.IDLE
    }

    fun toggleSpeechRecognition() {
        if (speechRecognizerHelper.isListening.value) {
            speechRecognizerHelper.stopListening()
            _voiceState.value = VoiceState.IDLE
        } else {
            ttsManager.stop()
            _voiceState.value = VoiceState.LISTENING
            speechRecognizerHelper.startListening()
        }
    }

    fun clearHistory() {
        scope.launch(Dispatchers.IO) {
            db.conversationDao().clearAll()
            AlyaLogger.i(TAG, "Cleared conversation history")
        }
    }

    fun clearAuditLogs() {
        scope.launch(Dispatchers.IO) {
            db.actionAuditDao().clearAuditLogs()
            AlyaLogger.i(TAG, "Cleared action audit logs from Room DB")
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    fun release() {
        speechRecognizerHelper.stopListening()
        ttsManager.shutdown()
        voiceController.release()
        liveClient.stopLiveSession()
        AlyaForegroundService.stopService(context)
    }

    private fun ConversationMessage.toEntity(): ConversationEntity {
        return ConversationEntity(
            role = role.name,
            content = content,
            timestamp = timestamp,
            status = status.name,
            actionType = actionResult?.action,
            actionTarget = actionResult?.target,
            actionSuccess = actionResult?.success,
            actionVerified = actionResult?.verified,
            actionDetails = actionResult?.technicalDetails
        )
    }

    private fun ConversationEntity.toConversationMessage(): ConversationMessage {
        val res = if (actionType != null) {
            ActionExecutionResult(
                action = actionType,
                target = actionTarget,
                success = actionSuccess ?: false,
                verified = actionVerified ?: false,
                feedbackMessage = content,
                technicalDetails = actionDetails ?: ""
            )
        } else null

        return ConversationMessage(
            id = id,
            role = try { MessageRole.valueOf(role) } catch (e: Exception) { MessageRole.ASSISTANT },
            content = content,
            timestamp = timestamp,
            status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.DELIVERED },
            actionResult = res
        )
    }
}
