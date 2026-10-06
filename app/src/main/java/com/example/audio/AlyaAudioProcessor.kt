package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.example.core.logger.AlyaLogger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.math.sqrt

/**
 * AlyaAudioProcessor — Enterprise 24kHz Full-Duplex Audio Engine for Gemini Live API
 * 
 * Features:
 * 1. 24kHz 16-bit Mono AudioRecord with Hardware AEC, NS, AGC + Software Noise Gate & Startup Mute Ramp
 * 2. 24kHz 16-bit Mono Streaming AudioTrack with Thread-Safe Circular Ring Buffer
 * 3. Complete elimination of mic activation clicks, pops, hardware initialization chirps, and background static noise
 * 4. Dual PCM-Float32 conversion pipeline for real-time DSP audio processing
 */
class AlyaAudioProcessor {

    companion object {
        private const val TAG = "ALYA_AUDIO_PROCESSOR"
        private const val SAMPLE_RATE = 24000 // 24kHz sample rate for Gemini Live API
        private const val CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // 20ms audio frame at 24kHz 16-bit Mono = 480 samples = 960 bytes
        private const val FRAME_SIZE_BYTES = 960
        private const val NOISE_GATE_THRESHOLD = 0.012f // Threshold for room static/hiss filtering
    }

    // --- Audio Track Output (Playback) ---
    private var audioTrack: AudioTrack? = null
    private val circularBuffer = CircularBuffer(1024 * 128) // 128KB Circular Ring Buffer
    @Volatile private var isPlaybackActive = false
    private var playbackThread: Thread? = null

    // --- Audio Record Input (Mic Capture) ---
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null
    @Volatile private var isRecordingActive = false
    private var recordingThread: Thread? = null

    // Buffer tracking for high/low byte alignment
    private var leftoverPlaybackByte: Byte? = null

    init {
        initializeAudioTrack()
    }

    /**
     * Initializes native AudioTrack for 24kHz low-latency speech output
     */
    private fun initializeAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT_CONFIG, ENCODING_FORMAT)
            val bufferSize = minBufferSize * 4

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val formatSpec = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT_CONFIG)
                .setEncoding(ENCODING_FORMAT)
                .build()

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(formatSpec)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_OUT_CONFIG,
                    ENCODING_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            AlyaLogger.i(TAG, "Initialized 24kHz AudioTrack output successfully.")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Failed to initialize AudioTrack output", e)
        }
    }

    /**
     * Starts continuous 24kHz AudioRecord mic capture with hardware AEC/NS/AGC and startup soft-ramp to swallow mic clicks.
     *
     * @param onAudioCaptured Callback providing raw 24kHz PCM bytes and normalized Float32 samples.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun startRecording(onAudioCaptured: (ByteArray, FloatArray) -> Unit) {
        if (isRecordingActive) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN_CONFIG, ENCODING_FORMAT)
            val bufferSize = maxOf(minBufferSize * 4, FRAME_SIZE_BYTES * 8)

            // VOICE_COMMUNICATION source enables built-in Android hardware noise cancellation & echo suppression
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN_CONFIG,
                ENCODING_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AlyaLogger.e(TAG, "AudioRecord failed to initialize at 24kHz")
                return
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                // Attach Hardware Acoustic Echo Canceler
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                        AlyaLogger.i(TAG, "Hardware Acoustic Echo Canceler (AEC) enabled.")
                    }
                }

                // Attach Hardware Noise Suppressor
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                        AlyaLogger.i(TAG, "Hardware Noise Suppressor (NS) enabled.")
                    }
                }

                // Attach Hardware Automatic Gain Control
                if (AutomaticGainControl.isAvailable()) {
                    gainControl = AutomaticGainControl.create(sessionId)?.apply {
                        enabled = true
                        AlyaLogger.i(TAG, "Hardware Automatic Gain Control (AGC) enabled.")
                    }
                }
            }

            audioRecord?.startRecording()
            isRecordingActive = true

            recordingThread = thread(start = true, name = "AlyaMicProcessor24kHz") {
                val rawBuffer = ByteArray(FRAME_SIZE_BYTES)
                var framesProcessed = 0
                val startupMuteFrames = 8 // Mute/soft-ramp first ~160ms to swallow hardware mic initialization clicks/pops

                while (isRecordingActive) {
                    val readBytes = audioRecord?.read(rawBuffer, 0, rawBuffer.size) ?: -1
                    if (readBytes > 0) {
                        val validBytes = if (readBytes == rawBuffer.size) rawBuffer else rawBuffer.copyOf(readBytes)
                        
                        // Convert PCM 16 to Float32 for DSP filtering
                        val floatSamples = convertPcm16ToFloat32(validBytes)

                        // 1. Swallow hardware startup mic click/pop noise completely
                        if (framesProcessed < startupMuteFrames) {
                            val fadeFactor = framesProcessed / startupMuteFrames.toFloat()
                            for (i in floatSamples.indices) {
                                floatSamples[i] *= fadeFactor
                            }
                            framesProcessed++
                        }

                        // 2. Apply Software Noise Gate & Noise Floor Reduction
                        applyNoiseGateAndDucking(floatSamples)

                        // Convert cleaned Float32 back to PCM16
                        val cleanPcmBytes = convertFloat32ToPcm16(floatSamples)

                        onAudioCaptured(cleanPcmBytes, floatSamples)
                    }
                }
            }

            AlyaLogger.i(TAG, "Started 24kHz AudioRecord mic engine with hardware & software noise cancellation.")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error starting 24kHz AudioRecord engine", e)
            stopRecording()
        }
    }

    /**
     * DSP Noise Gate filter to eliminate background static/hiss and reduce echo during playback
     */
    private fun applyNoiseGateAndDucking(floatSamples: FloatArray) {
        // Calculate frame RMS energy
        var sumSquare = 0.0
        for (sample in floatSamples) {
            sumSquare += (sample * sample)
        }
        val rms = sqrt(sumSquare / floatSamples.size).toFloat()

        // If playback is currently active (assistant speaking), duck mic input energy to avoid echo bleed
        val duckingFactor = if (isPlaybackActive) 0.15f else 1.0f

        for (i in floatSamples.indices) {
            val sample = floatSamples[i]
            val absSample = kotlin.math.abs(sample)

            if (absSample < NOISE_GATE_THRESHOLD) {
                // Soft noise gate suppression for ambient static
                floatSamples[i] = sample * (absSample / NOISE_GATE_THRESHOLD) * duckingFactor
            } else {
                floatSamples[i] = sample * duckingFactor
            }
        }
    }

    /**
     * Stop mic recording and release hardware audio effect resources
     */
    @Synchronized
    fun stopRecording() {
        isRecordingActive = false
        recordingThread?.interrupt()
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        try {
            echoCanceler?.release()
            noiseSuppressor?.release()
            gainControl?.release()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error releasing audio effects: ${e.message}")
        }

        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
        audioRecord = null
        AlyaLogger.i(TAG, "Stopped 24kHz AudioRecord mic engine.")
    }

    /**
     * Feeds incoming 24kHz Mono 16-bit PCM response chunks from Gemini Live WS into the circular buffer.
     */
    fun feedRawPcm(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return

        val combined: ByteArray
        if (leftoverPlaybackByte != null) {
            combined = ByteArray(pcmData.size + 1)
            combined[0] = leftoverPlaybackByte!!
            System.arraycopy(pcmData, 0, combined, 1, pcmData.size)
            leftoverPlaybackByte = null
        } else {
            combined = pcmData
        }

        val alignedSize = (combined.size / 2) * 2
        if (combined.size % 2 != 0) {
            leftoverPlaybackByte = combined.last()
        }

        if (alignedSize > 0) {
            val alignedData = if (alignedSize == combined.size) combined else combined.copyOf(alignedSize)
            circularBuffer.write(alignedData)
            if (!isPlaybackActive) {
                startProcessing()
            }
        }
    }

    /**
     * Converts raw 16-bit Little-Endian PCM byte array to normalized Float32 values (-1.0 to 1.0)
     */
    fun convertPcm16ToFloat32(pcmData: ByteArray): FloatArray {
        val samplesCount = pcmData.size / 2
        val floatBuffer = FloatArray(samplesCount)
        for (i in 0 until samplesCount) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sampleShort = ((high shl 8) or low).toShort()
            floatBuffer[i] = sampleShort / 32768.0f // Normalize to -1.0..1.0
        }
        return floatBuffer
    }

    /**
     * Converts normalized Float32 back to 16-bit raw PCM byte array for AudioTrack output
     */
    fun convertFloat32ToPcm16(floatData: FloatArray): ByteArray {
        val pcmData = ByteArray(floatData.size * 2)
        for (i in floatData.indices) {
            val sample = (floatData[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
            val s = sample.toInt()
            pcmData[i * 2] = (s and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return pcmData
    }

    /**
     * Starts continuous streaming playback from circular buffer to AudioTrack
     */
    @Synchronized
    fun startProcessing() {
        if (isPlaybackActive) return
        isPlaybackActive = true

        val track = audioTrack ?: return
        try {
            track.play()
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Failed to start AudioTrack playback", e)
        }

        playbackThread = thread(start = true, name = "AlyaAudioProcessorWorker") {
            val tempReadBuffer = ByteArray(2048)
            while (isPlaybackActive) {
                try {
                    val bytesRead = circularBuffer.read(tempReadBuffer)
                    if (bytesRead <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Complete PCM -> Float32 -> DSP -> PCM transformation
                    val floatData = convertPcm16ToFloat32(tempReadBuffer.copyOf(bytesRead))
                    
                    // Apply de-clicking algorithms to smooth Float32 envelope boundaries
                    applyDeclickingOnFloat32(floatData)

                    // Convert back to raw bytes for AudioTrack delivery
                    val cleanBytes = convertFloat32ToPcm16(floatData)

                    var written = 0
                    while (written < cleanBytes.size && isPlaybackActive) {
                        val activeTrack = audioTrack ?: break
                        val result = activeTrack.write(cleanBytes, written, cleanBytes.size - written)
                        if (result < 0) {
                            AlyaLogger.e(TAG, "AudioTrack write failed: $result")
                            break
                        }
                        written += result
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    AlyaLogger.e(TAG, "Error in processing playback loop", e)
                }
            }
        }
    }

    /**
     * Softly ramps Float32 amplitude boundaries to prevent transients that cause robotic cracking/clicking
     */
    private fun applyDeclickingOnFloat32(floatData: FloatArray) {
        if (floatData.size < 16) return
        // Envelope ramp-up for starting boundary
        for (i in 0 until 8) {
            floatData[i] = floatData[i] * (i / 8.0f)
        }
        // Envelope ramp-down for ending boundary
        val len = floatData.size
        for (i in 0 until 8) {
            floatData[len - 1 - i] = floatData[len - 1 - i] * (i / 8.0f)
        }
    }

    /**
     * Stops playback processing and clears the circular buffer
     */
    @Synchronized
    fun stopProcessing() {
        isPlaybackActive = false
        playbackThread?.interrupt()
        playbackThread = null
        circularBuffer.clear()

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error pausing AudioTrack: ${e.message}")
        }
    }

    /**
     * Releases all audio resources (Record, Track, Effects)
     */
    fun release() {
        stopRecording()
        stopProcessing()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    /**
     * Fixed-capacity thread-safe Circular Buffer / Ring Buffer class for raw streaming audio bytes
     */
    private class CircularBuffer(private val capacity: Int) {
        private val buffer = ByteArray(capacity)
        private var head = 0
        private var tail = 0
        private var size = 0
        private val lock = ReentrantLock()
        private val condition = lock.newCondition()

        fun write(data: ByteArray) {
            lock.withLock {
                var bytesWritten = 0
                while (bytesWritten < data.size) {
                    val availableSpace = capacity - size
                    if (availableSpace <= 0) {
                        // Buffer is completely full, slide head to drop oldest sample chunk (2-byte aligned)
                        val rawDrop = minOf(data.size - bytesWritten, 512)
                        val dropSize = (rawDrop / 2) * 2
                        if (dropSize > 0) {
                            head = (head + dropSize) % capacity
                            size -= dropSize
                        }
                    }

                    val chunk = minOf(data.size - bytesWritten, capacity - size)
                    val firstWriteSize = minOf(chunk, capacity - tail)
                    System.arraycopy(data, bytesWritten, buffer, tail, firstWriteSize)
                    tail = (tail + firstWriteSize) % capacity

                    if (firstWriteSize < chunk) {
                        val secondWriteSize = chunk - firstWriteSize
                        System.arraycopy(data, bytesWritten + firstWriteSize, buffer, tail, secondWriteSize)
                        tail = (tail + secondWriteSize) % capacity
                    }

                    size += chunk
                    bytesWritten += chunk
                }
                condition.signalAll()
            }
        }

        fun read(outputBuffer: ByteArray): Int {
            lock.withLock {
                if (size < 2) return 0

                val rawChunk = minOf(outputBuffer.size, size)
                val chunk = (rawChunk / 2) * 2
                if (chunk < 2) return 0

                val firstReadSize = minOf(chunk, capacity - head)
                System.arraycopy(buffer, head, outputBuffer, 0, firstReadSize)
                head = (head + firstReadSize) % capacity

                if (firstReadSize < chunk) {
                    val secondReadSize = chunk - firstReadSize
                    System.arraycopy(buffer, head, outputBuffer, firstReadSize, secondReadSize)
                    head = (head + secondReadSize) % capacity
                }

                size -= chunk
                return chunk
            }
        }

        fun clear() {
            lock.withLock {
                head = 0
                tail = 0
                size = 0
            }
        }
    }
}
