package com.example.audio

import android.content.Context
import android.util.Base64
import com.example.BuildConfig
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * StreamTtsPrecacher — Streaming Audio Pre-Caching Engine.
 *
 * Buffers and synthesizes initial text tokens / first sentences concurrently as soon
 * as they arrive from Gemini LLM stream generation, storing pre-rendered audio chunks
 * in memory/cache to enable near-instant (sub-100ms) voice response playback.
 */
class StreamTtsPrecacher(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "ALYA_STREAM_PRECACHE"
        private const val GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    // Pre-cached audio files mapped by initial token text prompt
    private val precachedAudioMap = ConcurrentHashMap<String, File>()
    private var activePrecacheJob: Job? = null

    /**
     * Start pre-caching audio for the initial token chunk as soon as the first few tokens arrive.
     */
    fun onFirstTokensArrived(tokenChunk: String, customApiKey: String? = null) {
        val cleanChunk = tokenChunk
            .replace(Regex("""```json[\s\S]*?```"""), "")
            .replace(Regex("""\{[\s\S]*"character_state"[\s\S]*?\}"""), "")
            .replace(Regex("\\[pause:?[^\\]]*\\]", RegexOption.IGNORE_CASE), ", ")
            .replace(Regex("\\[[^\\]]+\\]"), "")
            .trim()

        if (cleanChunk.length < 4) return

        // Extract first sentence or initial 12-20 words for immediate audio synthesis
        val firstSentence = extractFirstSentence(cleanChunk)
        if (firstSentence.isBlank() || precachedAudioMap.containsKey(firstSentence)) return

        val apiKey = if (!customApiKey.isNullOrBlank()) {
            customApiKey.trim()
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return

        activePrecacheJob?.cancel()
        activePrecacheJob = scope.launch(Dispatchers.IO) {
            try {
                AlyaLogger.i(TAG, "Pre-caching initial TTS response for tokens: \"$firstSentence\"")
                val url = "$BASE_URL/$GEMINI_TTS_MODEL:generateContent?key=$apiKey"
                val jsonRoot = JSONObject()

                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Read in a natural, warm female voice: $firstSentence") })
                        })
                    })
                }
                jsonRoot.put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    val voiceConfig = JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", "Aoede")
                        })
                    }
                    put("speechConfig", JSONObject().apply { put("voiceConfig", voiceConfig) })
                }
                jsonRoot.put("generationConfig", genConfig)

                val body = jsonRoot.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    AlyaLogger.w(TAG, "Pre-cache request code ${response.code}")
                    return@launch
                }

                val respString = response.body?.string() ?: return@launch
                val respJson = JSONObject(respString)
                val candidates = respJson.optJSONArray("candidates") ?: return@launch
                if (candidates.length() == 0) return@launch

                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return@launch
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("inlineData")) {
                        val audioBase64 = part.getJSONObject("inlineData").optString("data")
                        if (!audioBase64.isNullOrEmpty()) {
                            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                            val formattedBytes = addWavHeaderIfNeeded(audioBytes)
                            val tempFile = File.createTempFile("alya_precache_", ".wav", context.cacheDir)
                            FileOutputStream(tempFile).use { it.write(formattedBytes) }

                            precachedAudioMap[firstSentence] = tempFile
                            AlyaLogger.i(TAG, "Successfully pre-cached initial TTS audio: ${tempFile.absolutePath}")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                AlyaLogger.w(TAG, "Stream TTS pre-caching failed: ${e.message}")
            }
        }
    }

    /**
     * Retrieve pre-cached audio file for exact or matching initial sentence, if available.
     */
    fun consumePrecachedAudio(text: String): File? {
        val clean = text.replace(Regex("\\[[^\\]]+\\]"), "").trim()
        val firstSentence = extractFirstSentence(clean)

        val cachedFile = precachedAudioMap.remove(firstSentence) ?: precachedAudioMap.remove(clean)
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
            AlyaLogger.i(TAG, "Consumed pre-cached TTS audio file with 0ms latency for: \"$firstSentence\"")
            return cachedFile
        }
        return null
    }

    fun clearPrecache() {
        activePrecacheJob?.cancel()
        activePrecacheJob = null
        precachedAudioMap.values.forEach { file ->
            try { file.delete() } catch (e: Exception) {}
        }
        precachedAudioMap.clear()
    }

    private fun extractFirstSentence(text: String): String {
        val delimiters = charArrayOf('.', '!', '?', '\n')
        val firstEnd = text.indexOfAny(delimiters)
        val sentence = if (firstEnd > 8) {
            text.substring(0, firstEnd + 1).trim()
        } else {
            val words = text.split(Regex("\\s+")).take(15)
            words.joinToString(" ")
        }
        return sentence
    }

    private fun addWavHeaderIfNeeded(rawBytes: ByteArray, sampleRate: Int = 24000): ByteArray {
        if (rawBytes.size >= 4 && rawBytes[0] == 'R'.code.toByte() && rawBytes[1] == 'I'.code.toByte() && rawBytes[2] == 'F'.code.toByte() && rawBytes[3] == 'F'.code.toByte()) {
            return rawBytes
        }
        val header = createWavHeader(rawBytes.size, sampleRate)
        val result = ByteArray(header.size + rawBytes.size)
        System.arraycopy(header, 0, result, 0, header.size)
        System.arraycopy(rawBytes, 0, result, header.size, rawBytes.size)
        return result
    }

    private fun createWavHeader(dataLen: Int, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalDataLen = dataLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (dataLen and 0xff).toByte()
        header[41] = ((dataLen shr 8) and 0xff).toByte()
        header[42] = ((dataLen shr 16) and 0xff).toByte()
        header[43] = ((dataLen shr 24) and 0xff).toByte()
        return header
    }
}
