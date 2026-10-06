package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.example.BuildConfig
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = AlyaLogger.TAG_TTS
        private const val GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeSpeechJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private var currentPitch: Float = 1.08f
    private var currentRate: Float = 1.0f
    private var currentLanguageTag: String = "en-US"

    private var focusRequest: AudioFocusRequest? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        AlyaLogger.i(TAG, "Initializing TextToSpeech fallback engine...")
        tts = TextToSpeech(context.applicationContext, this)
    }

    private var onCurrentSpeechCompleted: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage(currentLanguageTag)
            tts?.setPitch(1.05f)
            tts?.setSpeechRate(1.0f)

            // Select natural female voice if available
            try {
                val voices = tts?.voices
                if (voices != null) {
                    val femaleVoice = voices.firstOrNull { voice ->
                        voice.locale.language == Locale.forLanguageTag(currentLanguageTag).language &&
                                (voice.name.contains("female", ignoreCase = true) ||
                                        voice.name.contains("en-us-x-sfg", ignoreCase = true) ||
                                        voice.name.contains("en-us-x-iol", ignoreCase = true) ||
                                        voice.name.contains("hi-in-x-hie", ignoreCase = true) ||
                                        voice.name.contains("hi-in-x-hic", ignoreCase = true) ||
                                        voice.name.contains("en-us-x-tpf", ignoreCase = true))
                    }
                    if (femaleVoice != null) {
                        tts?.voice = femaleVoice
                        AlyaLogger.i(TAG, "Selected natural female voice: ${femaleVoice.name}")
                    }
                }
            } catch (e: Exception) {
                AlyaLogger.w(TAG, "Unable to inspect system voices: ${e.message}")
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    AlyaLogger.d(TAG, "TTS started utterance: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    releaseAudioFocus()
                    AlyaLogger.d(TAG, "TTS finished utterance: $utteranceId")
                    val cb = onCurrentSpeechCompleted
                    onCurrentSpeechCompleted = null
                    cb?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    releaseAudioFocus()
                    AlyaLogger.e(TAG, "TTS error on utterance: $utteranceId")
                    val cb = onCurrentSpeechCompleted
                    onCurrentSpeechCompleted = null
                    cb?.invoke()
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    releaseAudioFocus()
                    AlyaLogger.e(TAG, "TTS error $errorCode on utterance: $utteranceId")
                    val cb = onCurrentSpeechCompleted
                    onCurrentSpeechCompleted = null
                    cb?.invoke()
                }
            })

            _isInitialized.value = true
            AlyaLogger.i(TAG, "TTS fallback engine ready")
        } else {
            _isInitialized.value = false
            AlyaLogger.e(TAG, "Failed to initialize TTS engine (status code: $status)")
        }
    }

    fun setLanguage(languageTag: String) {
        currentLanguageTag = languageTag
        applyLanguage(languageTag)
    }

    private fun applyLanguage(languageTag: String) {
        try {
            val locale = Locale.forLanguageTag(languageTag)
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                AlyaLogger.w(TAG, "Language $languageTag not fully supported in system TTS, using fallback")
                tts?.language = Locale.getDefault()
            } else {
                AlyaLogger.i(TAG, "TTS language configured to: $languageTag ($locale)")
            }
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Exception applying TTS language: ${e.message}")
        }
    }

    fun setVoiceParameters(pitch: Float, rate: Float) {
        currentPitch = pitch
        currentRate = rate
        tts?.setPitch(pitch)
        tts?.setSpeechRate(rate)
    }

    /**
     * Toggles speech playback for a specific message (Tap-To-Play feature)
     */
    fun togglePlayMessage(messageId: Long, text: String, customApiKey: String? = null) {
        if (_speakingMessageId.value == messageId && _isSpeaking.value) {
            AlyaLogger.i(TAG, "Stopping audio playback for message #$messageId")
            stop()
        } else {
            _speakingMessageId.value = messageId
            speak(text, customApiKey = customApiKey)
        }
    }

    /**
     * Speaks the provided text using Gemini Realistic Female Voice (Aoede) with warm tone,
     * or smoothly falls back to on-device TTS if offline or unavailable.
     */
    fun speak(text: String, customApiKey: String? = null, onComplete: (() -> Unit)? = null) {
        val cleanText = text
            .replace(Regex("\\[pause:?[^\\]]*\\]", RegexOption.IGNORE_CASE), ", ")
            .replace(Regex("\\[[^\\]]+\\]"), "")
            .trim()
        if (cleanText.isEmpty()) {
            onComplete?.invoke()
            return
        }

        stop() // Prevent overlapping speech
        onCurrentSpeechCompleted = onComplete

        val apiKey = if (!customApiKey.isNullOrBlank()) {
            customApiKey.trim()
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        activeSpeechJob = scope.launch {
            if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                val geminiSuccess = tryGeminiTtsPlayback(cleanText, apiKey)
                if (geminiSuccess) {
                    return@launch
                }
            }

            // Fast fallback to on-device realistic TTS
            withContext(Dispatchers.Main) {
                speakOnDevice(cleanText)
            }
        }
    }

    val streamPrecacher = StreamTtsPrecacher(context, scope)

    fun onFirstTokensArrived(tokenChunk: String, customApiKey: String? = null) {
        streamPrecacher.onFirstTokensArrived(tokenChunk, customApiKey)
    }

    private suspend fun tryGeminiTtsPlayback(text: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if initial audio response was pre-cached during token streaming
            val cachedFile = streamPrecacher.consumePrecachedAudio(text)
            if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                withContext(Dispatchers.Main) {
                    playAudioFile(cachedFile)
                }
                return@withContext true
            }

            val url = "$BASE_URL/$GEMINI_TTS_MODEL:generateContent?key=$apiKey"
            val jsonRoot = JSONObject()

            // Request realistic female persona voice (Aoede) with warm tone
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "Read in a natural, warm, expressive female voice: $text") })
                    })
                })
            }
            jsonRoot.put("contents", contentsArray)

            val genConfig = JSONObject().apply {
                put("responseModalities", JSONArray().apply { put("AUDIO") })
                val voiceConfig = JSONObject().apply {
                    put("prebuiltVoiceConfig", JSONObject().apply {
                        put("voiceName", "Aoede") // Realistic warm female voice
                    })
                }
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", voiceConfig)
                })
            }
            jsonRoot.put("generationConfig", genConfig)

            val body = jsonRoot.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                AlyaLogger.w(TAG, "Gemini TTS API responded with code ${response.code}, falling back")
                return@withContext false
            }

            val respString = response.body?.string() ?: return@withContext false
            val respJson = JSONObject(respString)
            val candidates = respJson.optJSONArray("candidates") ?: return@withContext false
            if (candidates.length() == 0) return@withContext false

            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: return@withContext false
            var audioBase64: String? = null
            var mimeType: String = "audio/mp3"

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("inlineData")) {
                    val inlineData = part.getJSONObject("inlineData")
                    audioBase64 = inlineData.optString("data")
                    mimeType = inlineData.optString("mimeType", "audio/mp3")
                    break
                }
            }

            if (audioBase64.isNullOrEmpty()) {
                return@withContext false
            }

            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            val formattedBytes = addWavHeaderIfNeeded(audioBytes)
            val tempAudioFile = File.createTempFile("alya_tts_", ".wav", context.cacheDir)
            FileOutputStream(tempAudioFile).use { it.write(formattedBytes) }

            withContext(Dispatchers.Main) {
                playAudioFile(tempAudioFile)
            }
            true
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Gemini TTS synthesis failed (${e.message}), using on-device TTS")
            false
        }
    }

    private fun createWavHeader(dataLen: Int, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalDataLen = dataLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (dataLen and 0xff).toByte()
        header[41] = ((dataLen shr 8) and 0xff).toByte()
        header[42] = ((dataLen shr 16) and 0xff).toByte()
        header[43] = ((dataLen shr 24) and 0xff).toByte()
        return header
    }

    private fun addWavHeaderIfNeeded(rawBytes: ByteArray, sampleRate: Int = 24000): ByteArray {
        if (rawBytes.size >= 4 && rawBytes[0] == 'R'.code.toByte() && rawBytes[1] == 'I'.code.toByte() && rawBytes[2] == 'F'.code.toByte() && rawBytes[3] == 'F'.code.toByte()) {
            return rawBytes
        }
        if (rawBytes.size >= 3 && rawBytes[0] == 'I'.code.toByte() && rawBytes[1] == 'D'.code.toByte() && rawBytes[2] == '3'.code.toByte()) {
            return rawBytes
        }
        val header = createWavHeader(rawBytes.size, sampleRate)
        val result = ByteArray(header.size + rawBytes.size)
        System.arraycopy(header, 0, result, 0, header.size)
        System.arraycopy(rawBytes, 0, result, header.size, rawBytes.size)
        return result
    }

    private fun playAudioFile(file: File) {
        try {
            requestAudioFocus()
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    releaseAudioFocus()
                    file.delete()
                    val cb = onCurrentSpeechCompleted
                    onCurrentSpeechCompleted = null
                    cb?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    _isSpeaking.value = false
                    _speakingMessageId.value = null
                    releaseAudioFocus()
                    file.delete()
                    AlyaLogger.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    val cb = onCurrentSpeechCompleted
                    onCurrentSpeechCompleted = null
                    cb?.invoke()
                    true
                }
                start()
            }
            _isSpeaking.value = true
            AlyaLogger.i(TAG, "Playing realistic Gemini TTS audio")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error playing audio file", e)
            file.delete()
            speakOnDevice("")
        }
    }

    private fun speakOnDevice(cleanText: String) {
        if (cleanText.isEmpty()) return
        if (!_isInitialized.value) {
            initializeTts()
            return
        }

        requestAudioFocus()

        val utteranceId = "alya_${UUID.randomUUID()}"
        val params = android.os.Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val result = tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            AlyaLogger.e(TAG, "TTS speak failed with code: $result")
            _isSpeaking.value = false
            _speakingMessageId.value = null
            releaseAudioFocus()
        }
    }

    /**
     * Halts speech cleanly. Essential for barge-in / interruption.
     */
    fun stop() {
        activeSpeechJob?.cancel()
        activeSpeechJob = null

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Error stopping media player: ${e.message}")
        }

        if (_isSpeaking.value) {
            AlyaLogger.i(TAG, "Stopping active TTS")
            tts?.stop()
            _isSpeaking.value = false
            _speakingMessageId.value = null
            releaseAudioFocus()
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stop()
                    }
                }
                .build()

            focusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                    ) {
                        stop()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
        _speakingMessageId.value = null
        AlyaLogger.i(TAG, "TTS manager shut down")
    }
}
