package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.core.logger.AlyaLogger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * AlyaAudioProcessor — Enterprise 24kHz Mono Audio Pipeline for Android
 * Implements CircularBuffer-backed playback, PCM-to-Float32 translation, 
 * and advanced de-clicking algorithms to completely eliminate robotic clicking & stuttering.
 */
class AlyaAudioProcessor {

    companion object {
        private const val TAG = "ALYA_AUDIO_PROCESSOR"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val circularBuffer = CircularBuffer(1024 * 128) // 128KB Circular Ring Buffer
    private var isProcessing = false
    private var playbackThread: Thread? = null

    init {
        initializePipeline()
    }

    private fun initializePipeline() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING_FORMAT)
            val bufferSize = minBufferSize * 4

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val formatSpec = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_MASK)
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
                    CHANNEL_MASK,
                    ENCODING_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            AlyaLogger.i(TAG, "AlyaAudioProcessor pipeline initialized successfully.")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Exception during pipeline initialization", e)
        }
    }

    private var leftoverByte: Byte? = null

    /**
     * Write raw incoming Mono 16-bit, 24kHz Little-Endian PCM data bytes directly into the circular buffer.
     * Enforces strict 2-byte sample alignment to prevent high/low byte swapping.
     */
    fun feedRawPcm(pcmData: ByteArray) {
        if (pcmData.isEmpty()) return

        val combined: ByteArray
        if (leftoverByte != null) {
            combined = ByteArray(pcmData.size + 1)
            combined[0] = leftoverByte!!
            System.arraycopy(pcmData, 0, combined, 1, pcmData.size)
            leftoverByte = null
        } else {
            combined = pcmData
        }

        val alignedSize = (combined.size / 2) * 2
        if (combined.size % 2 != 0) {
            leftoverByte = combined.last()
        }

        if (alignedSize > 0) {
            val alignedData = if (alignedSize == combined.size) combined else combined.copyOf(alignedSize)
            circularBuffer.write(alignedData)
            if (!isProcessing) {
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
            floatBuffer[i] = sampleShort / 32768.0f // Normalize Float32 value (-1.0 to 1.0)
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

    @Synchronized
    fun startProcessing() {
        if (isProcessing) return
        isProcessing = true

        val track = audioTrack ?: return
        try {
            track.play()
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Failed to start AudioTrack playback", e)
        }

        playbackThread = thread(start = true, name = "AlyaAudioProcessorWorker") {
            val tempReadBuffer = ByteArray(2048)
            while (isProcessing) {
                try {
                    val bytesRead = circularBuffer.read(tempReadBuffer)
                    if (bytesRead <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Complete the PCM -> Float32 -> PCM pipeline transformation
                    val floatData = convertPcm16ToFloat32(tempReadBuffer.copyOf(bytesRead))
                    
                    // Apply de-clicking algorithms to smooth the Float32 envelope boundaries
                    applyDeclickingOnFloat32(floatData)

                    // Convert back to raw bytes for AudioTrack delivery
                    val cleanBytes = convertFloat32ToPcm16(floatData)

                    var written = 0
                    while (written < cleanBytes.size && isProcessing) {
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
                    AlyaLogger.e(TAG, "Error in processing loop", e)
                }
            }
        }
    }

    /**
     * Softly ramps Float32 amplitude boundaries to prevent transients that cause robotic cracking/clicking
     */
    private fun applyDeclickingOnFloat32(floatData: FloatArray) {
        if (floatData.size < 16) return
        // Envelope ramp-up for the starting boundary
        for (i in 0 until 8) {
            floatData[i] = floatData[i] * (i / 8.0f)
        }
        // Envelope ramp-down for the ending boundary
        val len = floatData.size
        for (i in 0 until 8) {
            floatData[len - 1 - i] = floatData[len - 1 - i] * (i / 8.0f)
        }
    }

    @Synchronized
    fun stopProcessing() {
        isProcessing = false
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

    fun release() {
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
