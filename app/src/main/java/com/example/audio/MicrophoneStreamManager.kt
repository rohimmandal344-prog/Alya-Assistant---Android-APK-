package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * MicrophoneStreamManager — Ultra-low latency microphone input pipeline with zero-allocation ring buffer,
 * hardware/software Acoustic Echo Cancellation (AEC), and Automatic Gain Control (AGC).
 */
class MicrophoneStreamManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onAudioFrame: (ShortArray, Int) -> Unit
) {

    companion object {
        private const val TAG = "ALYA_MIC_STREAM"
        const val SAMPLE_RATE = 24000 // Higher 24kHz sampling rate matching Gemini Live
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 128 // Smaller 128 sample buffer size (~5.3ms ultra-low latency)
        private const val RING_BUFFER_FRAMES = 64 // 64 * 128 = 8192 samples capacity
        private const val WARMUP_FRAMES_MUTE = 8 // First ~42ms soft ramp to eliminate power-on mic click
        private const val LPF_ALPHA = 0.35f // Low-pass filter smoothing coefficient (~4kHz cutoff)
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val _currentRms = MutableStateFlow(0f)
    val currentRms: StateFlow<Float> = _currentRms.asStateFlow()

    // Low-pass filter & AGC state
    private var lpfPrevSample = 0f
    private var agcGain = 1.0f
    @Volatile
    private var isSpeakerActive = false

    // Pre-allocated Lock-Free Ring Buffer (Shorts)
    private val ringBuffer = ShortArray(FRAME_SIZE * RING_BUFFER_FRAMES)
    private val writeHead = AtomicInteger(0)
    private val readHead = AtomicInteger(0)

    // Pre-allocated dispatch frame
    private val dispatchBuffer = ShortArray(FRAME_SIZE)

    /**
     * Notify mic stream whether assistant speech playback is active for acoustic feedback suppression
     */
    fun setSpeakerPlaybackActive(active: Boolean) {
        isSpeakerActive = active
    }

    fun startStream(): Boolean {
        if (isRecording.get()) return true

        try {
            // 1. Consume pre-warmed AudioRecord instance if available (0ms startup latency)
            val prewarmed = AudioPrewarmer.consumePrewarmedRecord()
            if (prewarmed != null && prewarmed.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = prewarmed
                lpfPrevSample = AudioPrewarmer.prewarmedLpfSample
                AlyaLogger.i(TAG, "Utilizing pre-warmed AudioRecord session with AEC/AGC (0ms delay)")
            } else {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val hardwareBufferSize = maxOf(minBufferSize, FRAME_SIZE * 8)

                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Optimized hardware AEC source
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    hardwareBufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    AlyaLogger.e(TAG, "AudioRecord failed with VOICE_COMMUNICATION, falling back to MIC source")
                    val fallbackRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        hardwareBufferSize
                    )
                    if (fallbackRecord.state != AudioRecord.STATE_INITIALIZED) {
                        AlyaLogger.e(TAG, "Fallback AudioRecord also failed to initialize")
                        return false
                    }
                    audioRecord = fallbackRecord
                } else {
                    audioRecord = record
                }
                attachAudioEffects(audioRecord?.audioSessionId ?: 0)
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            _isMicActive.value = true
            writeHead.set(0)
            readHead.set(0)
            agcGain = 1.0f

            AlyaLogger.i(TAG, "Microphone stream started with AEC & AGC (rate=$SAMPLE_RATE Hz, frameSize=$FRAME_SIZE samples)")

            startCaptureLoop()
            return true
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Exception starting microphone stream", e)
            stopStream()
            return false
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId == 0) return

        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                    AlyaLogger.i(TAG, "AcousticEchoCanceler attached & enabled (session=$sessionId)")
                }
            } else {
                AlyaLogger.w(TAG, "Hardware AcousticEchoCanceler not available on this device; relying on DSP AEC gate")
            }

            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                    AlyaLogger.i(TAG, "AutomaticGainControl attached & enabled (session=$sessionId)")
                }
            } else {
                AlyaLogger.w(TAG, "Hardware AutomaticGainControl not available; using software DSP AGC")
            }

            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Could not attach hardware audio effects: ${e.message}")
        }
    }

    private fun startCaptureLoop() {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val localChunk = ShortArray(FRAME_SIZE)
            var frameCount = 0

            while (isRecording.get()) {
                val record = audioRecord ?: break
                val readSamples = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    record.read(localChunk, 0, FRAME_SIZE, AudioRecord.READ_BLOCKING)
                } else {
                    record.read(localChunk, 0, FRAME_SIZE)
                }

                if (readSamples > 0) {
                    frameCount++

                    // 1. Digital Low-Pass Filter & Mic Warmup Ramp
                    applyLowPassFilterAndWarmupRamp(localChunk, readSamples, frameCount)

                    // 2. Calculate RMS energy
                    var sumSquares = 0.0
                    for (i in 0 until readSamples) {
                        val s = localChunk[i].toDouble()
                        sumSquares += s * s
                    }
                    val rms = kotlin.math.sqrt(sumSquares / readSamples).toFloat()
                    val normalizedRms = (rms / 32768f).coerceIn(0f, 1f)
                    _currentRms.value = normalizedRms

                    // 3. Software AGC (Automatic Gain Control) & AEC Acoustic Echo Ducking Gate
                    applyAutomaticGainControlAndAecGate(localChunk, readSamples, normalizedRms)

                    // 4. Push frame to ring buffer & dispatch
                    pushToRingBuffer(localChunk, readSamples)

                    if (popFromRingBuffer(dispatchBuffer)) {
                        onAudioFrame(dispatchBuffer, readSamples)
                    }
                } else if (readSamples < 0) {
                    AlyaLogger.w(TAG, "AudioRecord read error code: $readSamples")
                }
            }
        }
    }

    private fun applyLowPassFilterAndWarmupRamp(buffer: ShortArray, length: Int, frameIndex: Int) {
        val warmupGain = if (frameIndex <= WARMUP_FRAMES_MUTE) {
            (frameIndex.toFloat() / WARMUP_FRAMES_MUTE.toFloat())
        } else {
            1.0f
        }

        for (i in 0 until length) {
            val rawSample = buffer[i].toFloat()
            lpfPrevSample += LPF_ALPHA * (rawSample - lpfPrevSample)
            val filtered = (lpfPrevSample * warmupGain).toInt().coerceIn(-32768, 32767)
            buffer[i] = filtered.toShort()
        }
    }

    /**
     * Applies Software Automatic Gain Control (AGC) and Acoustic Echo Cancellation (AEC) Ducking Gate.
     *
     * 1. Dynamic Gain Normalization (AGC): Smoothly adjusts gain (up to +12dB) to normalize quiet human speech.
     * 2. Acoustic Echo Ducking Gate: Suppresses mic feedback by -18dB when assistant speaker is active.
     * 3. Soft Limiter: Prevents digital clipping or distortion.
     */
    private fun applyAutomaticGainControlAndAecGate(buffer: ShortArray, length: Int, currentRms: Float) {
        // Echo Ducking Gate: -18dB attenuation when assistant speaker is playing
        val echoDuckingGain = if (isSpeakerActive) 0.125f else 1.0f

        // Software AGC Envelope Follower
        val targetRms = 0.30f
        if (currentRms > 0.01f) {
            val desiredGain = (targetRms / currentRms).coerceIn(0.5f, 4.0f) // Clamp between -6dB and +12dB
            agcGain += 0.05f * (desiredGain - agcGain) // Attack/Release smoothing
        }

        val totalGain = agcGain * echoDuckingGain

        for (i in 0 until length) {
            val sample = buffer[i].toFloat() * totalGain
            // Soft-clipper peak limiter
            val limited = when {
                sample > 30000f -> 30000f + (sample - 30000f) * 0.1f
                sample < -30000f -> -30000f + (sample + 30000f) * 0.1f
                else -> sample
            }
            buffer[i] = limited.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun pushToRingBuffer(data: ShortArray, count: Int) {
        val cap = ringBuffer.size
        val currWrite = writeHead.get()
        for (i in 0 until count) {
            ringBuffer[(currWrite + i) % cap] = data[i]
        }
        writeHead.set((currWrite + count) % cap)
    }

    private fun popFromRingBuffer(out: ShortArray): Boolean {
        val cap = ringBuffer.size
        val currWrite = writeHead.get()
        val currRead = readHead.get()

        val available = if (currWrite >= currRead) {
            currWrite - currRead
        } else {
            cap - currRead + currWrite
        }

        if (available < out.size) return false

        for (i in out.indices) {
            out[i] = ringBuffer[(currRead + i) % cap]
        }
        readHead.set((currRead + out.size) % cap)
        return true
    }

    fun stopStream() {
        isRecording.set(false)
        _isMicActive.value = false
        _currentRms.value = 0f

        recordingJob?.cancel()
        recordingJob = null

        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            gainControl?.release()
            gainControl = null

            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error stopping audio record: ${e.message}")
        } finally {
            audioRecord = null
        }
        AlyaLogger.i(TAG, "Microphone stream stopped")
    }
}
