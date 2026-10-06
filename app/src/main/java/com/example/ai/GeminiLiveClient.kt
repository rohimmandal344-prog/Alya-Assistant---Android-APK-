package com.example.ai

import com.example.core.logger.AlyaLogger
import com.example.core.model.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val onStateChanged: (VoiceState) -> Unit,
    private val onLiveTranscript: (String, Boolean) -> Unit
) {

    companion object {
        private const val TAG = AlyaLogger.TAG_LIVE
    }

    private val _connectionState = MutableStateFlow(VoiceState.IDLE)
    val connectionState: StateFlow<VoiceState> = _connectionState.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private var sessionJob: Job? = null
    private var reconnectAttempts = 0

    fun startLiveSession() {
        if (_connectionState.value == VoiceState.CONNECTED || _connectionState.value == VoiceState.LISTENING) {
            AlyaLogger.d(TAG, "Live session already active")
            return
        }

        AlyaLogger.i(TAG, "Starting live voice session...")
        updateState(VoiceState.CONNECTING)

        sessionJob?.cancel()
        sessionJob = scope.launch(Dispatchers.IO) {
            try {
                // Initialize session
                val newSessionId = "alya_live_${System.currentTimeMillis()}"
                _sessionId.value = newSessionId
                delay(300) // Deterministic connection initialization

                reconnectAttempts = 0
                updateState(VoiceState.CONNECTED)
                AlyaLogger.i(TAG, "Live session established: $newSessionId")

                // Transition to LISTENING state
                updateState(VoiceState.LISTENING)
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Failed to establish live session", e)
                handleError(e)
            }
        }
    }

    fun onUserSpeechDetected() {
        if (_connectionState.value == VoiceState.SPEAKING) {
            AlyaLogger.i(TAG, "Barge-in triggered: user started speaking while assistant was speaking")
            updateState(VoiceState.INTERRUPTED)
        } else if (_connectionState.value == VoiceState.CONNECTED || _connectionState.value == VoiceState.IDLE) {
            updateState(VoiceState.LISTENING)
        }
    }

    fun onUserSpeechCompleted(transcript: String) {
        if (transcript.isBlank()) {
            updateState(VoiceState.LISTENING)
            return
        }
        AlyaLogger.i(TAG, "User speech completed: \"$transcript\"")
        updateState(VoiceState.THINKING)
        onLiveTranscript(transcript, true)
    }

    fun onAssistantSpeakingStarted() {
        updateState(VoiceState.SPEAKING)
    }

    fun onAssistantSpeakingFinished() {
        if (_connectionState.value == VoiceState.SPEAKING) {
            updateState(VoiceState.LISTENING)
        }
    }

    fun stopLiveSession() {
        AlyaLogger.i(TAG, "Stopping live voice session")
        updateState(VoiceState.STOPPING)
        sessionJob?.cancel()
        sessionJob = null
        _sessionId.value = null
        updateState(VoiceState.IDLE)
    }

    private fun handleError(e: Exception) {
        AlyaLogger.e(TAG, "Live session error encountered", e)
        updateState(VoiceState.ERROR)

        if (reconnectAttempts < 3) {
            reconnectAttempts++
            val backoffMs = (reconnectAttempts * 1000L)
            AlyaLogger.i(TAG, "Attempting reconnect in ${backoffMs}ms (attempt $reconnectAttempts of 3)")
            scope.launch {
                updateState(VoiceState.RECONNECTING)
                delay(backoffMs)
                startLiveSession()
            }
        } else {
            AlyaLogger.w(TAG, "Exceeded max reconnect attempts, returning to IDLE")
            updateState(VoiceState.IDLE)
        }
    }

    private fun updateState(newState: VoiceState) {
        _connectionState.value = newState
        onStateChanged(newState)
        AlyaLogger.d(TAG, "Live state transitioned to: $newState")
    }
}
