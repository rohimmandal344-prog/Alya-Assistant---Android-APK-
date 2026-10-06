package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.core.logger.AlyaLogger
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class GeminiAudioTrackPlayer {

    companion object {
        private const val TAG = "ALYA_PCM_PLAYER"
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var isPlaying = false
    private var playbackThread: Thread? = null

    init {
        initializeAudioTrack()
    }

    private fun initializeAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            // Use a large enough buffer (4x min buffer size) to act as a smooth jitter buffer
            val bufferSize = minBufferSize * 4

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA) // Ensures proper A2DP high-quality stereo/mono stream
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val audioFormatSpec = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build()

            audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormatSpec)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }

            AlyaLogger.i(TAG, "Initialized native AudioTrack with 24kHz sample rate, USAGE_MEDIA")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Failed to initialize AudioTrack", e)
        }
    }

    private var leftoverByte: Byte? = null

    /**
     * Decode and queue raw PCM chunk (Mono 16-bit, 24kHz, Little-Endian).
     * Enforces strict 2-byte sample alignment to prevent low/high byte swapping.
     */
    fun queueAudio(pcmData: ByteArray) {
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
            audioQueue.offer(alignedData)
            if (!isPlaying) {
                startPlayback()
            }
        }
    }

    @Synchronized
    fun startPlayback() {
        if (isPlaying) return
        isPlaying = true

        val track = audioTrack ?: return
        try {
            track.play()
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error starting AudioTrack playback", e)
        }

        playbackThread = thread(start = true, name = "AlyaPcmPlayback") {
            val buffer = ByteArray(4096)
            while (isPlaying) {
                try {
                    val data = audioQueue.poll() ?: {
                        Thread.sleep(10)
                        null
                    }() ?: continue

                    // Smooth fade-in on write boundaries to avoid clipping and clicking noise
                    applyDeclickingFilter(data)

                    var written = 0
                    while (written < data.size && isPlaying) {
                        val trackInstance = audioTrack ?: break
                        val result = trackInstance.write(data, written, data.size - written)
                        if (result < 0) {
                            AlyaLogger.e(TAG, "AudioTrack write error: $result")
                            break
                        }
                        written += result
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    AlyaLogger.e(TAG, "Error in playback loop", e)
                }
            }
        }
    }

    /**
     * Apply a simple envelope filter to raw 16-bit PCM bytes to prevent harsh hardware cracking/clicks
     */
    private fun applyDeclickingFilter(data: ByteArray) {
        if (data.size < 40) return
        // Softly ramp up the first 10 samples (20 bytes) to avoid abrupt starting transient
        for (i in 0 until 10) {
            val shortIndex = i * 2
            if (shortIndex + 1 < data.size) {
                var value = ((data[shortIndex + 1].toInt() shl 8) or (data[shortIndex].toInt() and 0xFF)).toShort()
                val scale = i / 10f
                value = (value * scale).toInt().toShort()
                data[shortIndex] = (value.toInt() and 0xFF).toByte()
                data[shortIndex + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
            }
        }
    }

    @Synchronized
    fun stopPlayback() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
        audioQueue.clear()

        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error pausing AudioTrack: ${e.message}")
        }
    }

    /**
     * Converts a raw 16-bit signed PCM little-endian byte array to normalized float values (-1.0 to 1.0)
     * as requested by PCM Decoder specifications.
     */
    fun convertPcm16ToFloat32(pcmData: ByteArray): FloatArray {
        val size = pcmData.size / 2
        val floatData = FloatArray(size)
        for (i in 0 until size) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val shortVal = (high shl 8) or low
            floatData[i] = shortVal / 32768.0f
        }
        return floatData
    }

    fun release() {
        stopPlayback()
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }
}
