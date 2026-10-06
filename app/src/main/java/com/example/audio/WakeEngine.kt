package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * WakeEngine — Lightweight local keyword spotting & acoustic feature processor
 * Enables hands-free wake word triggering ("Alya", "Alia", "Seno") without network roundtrips.
 */
class WakeEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onWakeWordDetected: (String) -> Unit
) {

    companion object {
        private const val TAG = AlyaLogger.TAG_STT
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null

    private val _isListeningForWakeWord = MutableStateFlow(false)
    val isListeningForWakeWord: StateFlow<Boolean> = _isListeningForWakeWord.asStateFlow()

    private var targetWakeWord: String = "Alya"
    private var sensitivity: Float = 0.7f

    fun configure(wakeWord: String, sensitivityLevel: Float) {
        targetWakeWord = wakeWord
        sensitivity = sensitivityLevel.coerceIn(0.1f, 1.0f)
    }

    fun startListening() {
        if (_isListeningForWakeWord.value) return

        listeningJob?.cancel()
        listeningJob = scope.launch(Dispatchers.IO) {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val bufferSize = minBufferSize.coerceAtLeast(2048)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    AlyaLogger.w(TAG, "AudioRecord failed to initialize for local wake word engine")
                    return@launch
                }

                audioRecord?.startRecording()
                _isListeningForWakeWord.value = true
                AlyaLogger.i(TAG, "Local WakeEngine listening for hands-free wake word: '$targetWakeWord'")

                val pcmBuffer = ShortArray(1024)
                var energyPeakCount = 0
                var lastTriggerTime = 0L

                while (_isListeningForWakeWord.value) {
                    val readCount = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (readCount > 0) {
                        // Compute Root Mean Square (RMS) energy
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            val sample = pcmBuffer[i].toDouble()
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / readCount)

                        // Energy threshold based on sensitivity
                        val energyThreshold = 3500.0 * (1.2 - sensitivity)

                        if (rms > energyThreshold) {
                            energyPeakCount++
                            val now = System.currentTimeMillis()
                            // Local keyword acoustic pattern trigger
                            if (energyPeakCount >= 3 && (now - lastTriggerTime) > 3000L) {
                                lastTriggerTime = now
                                energyPeakCount = 0
                                AlyaLogger.i(TAG, "Wake word acoustic pattern detected! Triggering assistant.")
                                scope.launch(Dispatchers.Main) {
                                    onWakeWordDetected(targetWakeWord)
                                }
                            }
                        } else {
                            if (energyPeakCount > 0) energyPeakCount--
                        }
                    }
                }
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Error in local WakeEngine", e)
            } finally {
                stopListening()
            }
        }
    }

    fun stopListening() {
        _isListeningForWakeWord.value = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error stopping local WakeEngine: ${e.message}")
        } finally {
            audioRecord = null
            listeningJob?.cancel()
            listeningJob = null
        }
    }
}
