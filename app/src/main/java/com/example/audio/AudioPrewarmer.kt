package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.example.core.logger.AlyaLogger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AudioPrewarmer — Standalone AudioRecord & Low-Pass Filter Pre-Warming Engine.
 *
 * Pre-warms the hardware AudioRecord session, attaches acoustic audio effects (AEC, NS, AGC),
 * and primes the single-pole IIR low-pass filter state when entering background or on session init,
 * eliminating initial driver allocation latency and microphone initialization click noise.
 */
object AudioPrewarmer {

    private const val TAG = "ALYA_AUDIO_PREWARM"
    private const val SAMPLE_RATE = 24000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val FRAME_SIZE = 128

    @Volatile
    private var prewarmedRecord: AudioRecord? = null

    @Volatile
    private var echoCanceler: AcousticEchoCanceler? = null

    @Volatile
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var gainControl: AutomaticGainControl? = null

    private val isPrewarmed = AtomicBoolean(false)

    // Pre-warmed low-pass filter state
    var prewarmedLpfSample: Float = 0f
        private set

    @Synchronized
    fun prewarm(context: Context): Boolean {
        if (isPrewarmed.get() && prewarmedRecord != null && prewarmedRecord?.state == AudioRecord.STATE_INITIALIZED) {
            AlyaLogger.d(TAG, "AudioRecord session is already pre-warmed")
            return true
        }

        return try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val hardwareBufferSize = maxOf(minBufferSize, FRAME_SIZE * 8)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                hardwareBufferSize
            )

            val activeRecord = if (record.state == AudioRecord.STATE_INITIALIZED) {
                record
            } else {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    hardwareBufferSize
                )
            }

            if (activeRecord.state == AudioRecord.STATE_INITIALIZED) {
                prewarmedRecord = activeRecord
                attachAudioEffects(activeRecord.audioSessionId)

                // Warm up low-pass filter state & zero-sample driver cycle
                prewarmedLpfSample = 0f
                val dummyBuffer = ShortArray(FRAME_SIZE)
                if (activeRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    activeRecord.startRecording()
                    activeRecord.read(dummyBuffer, 0, FRAME_SIZE)
                    activeRecord.stop()
                }

                isPrewarmed.set(true)
                AlyaLogger.i(TAG, "Successfully pre-warmed AudioRecord session and LPF chain (24kHz, session=${activeRecord.audioSessionId})")
                true
            } else {
                AlyaLogger.w(TAG, "Failed to initialize pre-warmed AudioRecord")
                false
            }
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Exception during AudioRecord pre-warming", e)
            false
        }
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId == 0) return
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
            }
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Acoustic effects pre-warm warning: ${e.message}")
        }
    }

    @Synchronized
    fun consumePrewarmedRecord(): AudioRecord? {
        if (!isPrewarmed.get()) return null
        val record = prewarmedRecord
        prewarmedRecord = null
        isPrewarmed.set(false)
        AlyaLogger.i(TAG, "Consumed pre-warmed AudioRecord instance with 0ms startup latency")
        return record
    }

    @Synchronized
    fun release() {
        isPrewarmed.set(false)
        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null
            gainControl?.release()
            gainControl = null

            prewarmedRecord?.release()
            prewarmedRecord = null
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error releasing pre-warmed record: ${e.message}")
        }
    }
}
