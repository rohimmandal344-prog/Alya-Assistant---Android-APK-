package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognizerHelper(
    private val context: Context,
    private val onFinalResult: (String) -> Unit,
    private val onPartialResult: ((String) -> Unit)? = null,
    private val onErrorOccurred: ((String) -> Unit)? = null,
    private val onEndOfSpeechCallback: (() -> Unit)? = null
) : RecognitionListener {

    companion object {
        private const val TAG = AlyaLogger.TAG_STT
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

    private var lastRecognizedText: String = ""
    var currentLanguage: String = "en-US"

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            AlyaLogger.e(TAG, "SpeechRecognizer is not available on this device")
            onErrorOccurred?.invoke("Speech recognition is not supported on this device.")
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@SpeechRecognizerHelper)
            }

            val langTag = if (currentLanguage.isBlank() || currentLanguage.equals("auto", ignoreCase = true)) {
                Locale.getDefault().toLanguageTag()
            } else {
                currentLanguage
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US", "bn-IN", "es-ES", "fr-FR"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
            AlyaLogger.i(TAG, "SpeechRecognizer started listening")
        } catch (e: Exception) {
            _isListening.value = false
            AlyaLogger.e(TAG, "Exception starting speech recognizer", e)
            onErrorOccurred?.invoke("Could not start speech recognition: ${e.message}")
        }
    }

    fun stopListening() {
        _isListening.value = false
        _soundLevel.value = 0f
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error stopping recognizer: ${e.message}")
        } finally {
            speechRecognizer = null
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        AlyaLogger.d(TAG, "Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        AlyaLogger.d(TAG, "Beginning of speech detected")
    }

    override fun onRmsChanged(rmsdB: Float) {
        val normalized = (rmsdB.coerceIn(0f, 10f) / 10f)
        _soundLevel.value = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
        _soundLevel.value = 0f
        AlyaLogger.d(TAG, "End of speech detected")
        onEndOfSpeechCallback?.invoke()
    }

    override fun onError(error: Int) {
        _isListening.value = false
        _soundLevel.value = 0f
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (timeout)"
            else -> "Speech recognition error code: $error"
        }
        AlyaLogger.w(TAG, "SpeechRecognizer error: $errorMessage ($error)")

        // Ignore benign timeout or no-match without bothering user repeatedly
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            onErrorOccurred?.invoke(errorMessage)
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        _soundLevel.value = 0f
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()
        if (!text.isNullOrEmpty() && text != lastRecognizedText) {
            lastRecognizedText = text
            AlyaLogger.i(TAG, "Final speech recognition result: \"$text\"")
            onFinalResult(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.trim()
        if (!text.isNullOrEmpty()) {
            AlyaLogger.d(TAG, "Partial speech recognition: \"$text\"")
            onPartialResult?.invoke(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
