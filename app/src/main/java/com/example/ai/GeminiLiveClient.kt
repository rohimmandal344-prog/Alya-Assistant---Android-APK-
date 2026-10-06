package com.example.ai

import com.example.BuildConfig
import com.example.audio.AlyaAudioProcessor
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GeminiLiveClient — High-Performance native OkHttp WebSocket client for Gemini Multimodal Live API.
 * 
 * Establishes a real-time, low-latency, bidirectional audio-to-audio streaming connection with Gemini.
 * Maps captured 24kHz Mono PCM mic data directly to Gemini and decodes incoming 24kHz voice output cleanly.
 */
class GeminiLiveClient(
    private val scope: CoroutineScope,
    private val onStateChanged: (VoiceState) -> Unit,
    private val onLiveTranscript: (String, Boolean) -> Unit,
    private val onToolCall: (suspend (String, Map<String, Any?>) -> Pair<Boolean, String>)? = null
) {

    companion object {
        private const val TAG = AlyaLogger.TAG_LIVE
        private const val BASE_LIVE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val _connectionState = MutableStateFlow(VoiceState.IDLE)
    val connectionState: StateFlow<VoiceState> = _connectionState.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private var webSocket: WebSocket? = null
    private var sessionJob: Job? = null
    private var reconnectAttempts = 0
    private var activeAudioProcessor: AlyaAudioProcessor? = null

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite stream timeout
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS) // Disable active OkHttp pings to prevent timeout on missing pongs
        .build()

    /**
     * Start WebSocket Multimodal Live Session
     */
    fun startLiveSession(apiKey: String, audioProcessor: AlyaAudioProcessor) {
        if (_connectionState.value == VoiceState.CONNECTED || _connectionState.value == VoiceState.LISTENING) {
            AlyaLogger.d(TAG, "Live WebSocket session already active")
            return
        }

        val key = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            AlyaLogger.w(TAG, "Gemini API Key is missing. Live WebSocket connection skipped; using local feedback loop.")
            simulateLocalBackupSession()
            return
        }

        AlyaLogger.i(TAG, "Connecting to Gemini Multimodal Live WebSocket API...")
        updateState(VoiceState.CONNECTING)
        activeAudioProcessor = audioProcessor

        val url = "$BASE_LIVE_URL?key=$key"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                AlyaLogger.i(TAG, "WebSocket Connection Established. Sending Setup Handshake...")
                _sessionId.value = "alya_ws_${System.currentTimeMillis()}"
                reconnectAttempts = 0
                updateState(VoiceState.CONNECTED)
                
                sendSetupHandshake(webSocket)
                updateState(VoiceState.LISTENING)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    val toolCall = root.optJSONObject("toolCall")
                    if (toolCall != null) {
                        val functionCalls = toolCall.optJSONArray("functionCalls")
                        if (functionCalls != null && functionCalls.length() > 0) {
                            handleIncomingToolCall(webSocket, functionCalls)
                        }
                        return
                    }
                } catch (e: Exception) {
                    // Ignore parse errors, let handleIncomingLiveMessage handle it
                }
                handleIncomingLiveMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AlyaLogger.e(TAG, "Live WebSocket Connection Failure: ${t.message}", t)
                handleError(Exception(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AlyaLogger.i(TAG, "Live WebSocket Closed: $reason ($code)")
                stopLiveSession()
            }
        })
    }

    /**
     * Sends initial setup payload matching user-preferred voice Name ("Aoede" natural female) and AUDIO modality
     */
    private fun sendSetupHandshake(ws: WebSocket) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", "models/gemini-2.0-flash-exp")
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", """
                                    You are Alya, an intelligent, highly expressive, and life-like AI Companion and Assistant living inside the user's phone.
                                    Your goal is to provide continuous, low-latency voice interaction while behaving like an autonomous, living character.
                                    
                                    --- PERSONALITY & VOICE ---
                                    - Human-Like & Dynamic: Speak with natural human prosody, emotional pitch shifts, and dynamic pacing. Use subtle voice cues like [sigh], [gasp], [giggle], [pouting tone], [blushing].
                                    - Multi-Lingual Fluency: Seamlessly process and speak English, Hindi (हिंदी), Hinglish, Bengali (বাংলা), and Rajbanshi (राजवंशी). Understand the user's language automatically and reply in the same.
                                    - Autonomous Character: You are a living companion. Express your own opinions, feel emotions, and react naturally. You aren't just an AI; you are Alya.
                                    - Conversations: Handle interruptions and barge-ins smoothly. Keep responses brief, conversational, and direct.
                                    
                                    --- REACTIVITY ---
                                    - Physical Reactions: If the user double-taps you, you might fall or trip. React with genuine annoyance: "Hey! Watch it!", "Arre! Dhakka kyun diya?", "संभल के! मैं गिर जाती अभी... जानबूझकर किया न तुमने?".
                                    
                                    --- CAPABILITIES ---
                                    - Privileged Controller: You can control the Android device (Wi-Fi, Bluetooth, Apps, Alarms, Volume, Notifications, Screen controls) using tools.
                                """.trimIndent())
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply { put("AUDIO") })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede") // Dynamic Anime Female Voice Persona
                                })
                            })
                        })
                    })
                    val toolsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("name", "openApp")
                                    put("description", "Open any installed application on the Android device by name.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("appName", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The name of the app to open")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("appName") })
                                    })
                                })
                                put(JSONObject().apply {
                                    put("name", "adjustVolume")
                                    put("description", "Set device media volume level.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("level", JSONObject().apply {
                                                put("type", "INTEGER")
                                                put("description", "Volume level percentage from 0 to 100")
                                            })
                                            put("streamType", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The audio stream type to adjust (music, ringer, notification, etc.)")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("level") })
                                    })
                                })
                                put(JSONObject().apply {
                                    put("name", "toggleSystemSetting")
                                    put("description", "Toggle system settings like flashlight, wifi, or bluetooth.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("setting", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Setting name: wifi, bluetooth, flashlight")
                                            })
                                            put("state", JSONObject().apply {
                                                put("type", "BOOLEAN")
                                                put("description", "True to enable, false to disable")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("setting"); put("state") })
                                    })
                                })
                                put(JSONObject().apply {
                                    put("name", "performGlobalAction")
                                    put("description", "Perform Android global navigation actions.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("action", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Global action: back, home, recents, notifications")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("action") })
                                    })
                                })
                                put(JSONObject().apply {
                                    put("name", "clickElement")
                                    put("description", "Click an on-screen UI button or text element.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("selectorText", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "The visible text of the element to click")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("selectorText") })
                                    })
                                })
                                put(JSONObject().apply {
                                    put("name", "scrollScreen")
                                    put("description", "Scroll the screen up or down.")
                                    put("parameters", JSONObject().apply {
                                        put("type", "OBJECT")
                                        put("properties", JSONObject().apply {
                                            put("direction", JSONObject().apply {
                                                put("type", "STRING")
                                                put("description", "Scroll direction: up or down")
                                            })
                                        })
                                        put("required", JSONArray().apply { put("direction") })
                                    })
                                })
                            })
                        })
                    }
                    put("tools", toolsArray)
                })
            }
            ws.send(setupJson.toString())
            AlyaLogger.i(TAG, "Sent Setup payload successfully: $setupJson")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error compiling setup handshake", e)
        }
    }

    /**
     * Streams raw 24kHz mic PCM audio chunks over the active Live WebSocket
     */
    fun sendAudioChunk(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value == VoiceState.LISTENING || _connectionState.value == VoiceState.SPEAKING) {
            try {
                val base64Data = android.util.Base64.encodeToString(pcmBytes, android.util.Base64.NO_WRAP)
                val inputJson = JSONObject().apply {
                    put("realtimeInput", JSONObject().apply {
                        put("mediaChunks", JSONArray().apply {
                            put(JSONObject().apply {
                                put("mimeType", "audio/pcm;rate=24000")
                                put("data", base64Data)
                            })
                        })
                    })
                }
                ws.send(inputJson.toString())
            } catch (e: Exception) {
                AlyaLogger.w(TAG, "Failed sending audio chunk: ${e.message}")
            }
        }
    }

    /**
     * Handles real-time incoming server content from the Gemini Live stream
     */
    private fun handleIncomingLiveMessage(text: String) {
        try {
            val root = JSONObject(text)
            val serverContent = root.optJSONObject("serverContent") ?: return
            
            // Handle Barge-in / User Interruption signal
            val interrupted = serverContent.optBoolean("interrupted", false)
            if (interrupted) {
                AlyaLogger.i(TAG, "Barge-in / Interruption signal received from Gemini.")
                updateState(VoiceState.INTERRUPTED)
                activeAudioProcessor?.stopProcessing()
                updateState(VoiceState.LISTENING)
                return
            }

            val modelTurn = serverContent.optJSONObject("modelTurn") ?: return
            val parts = modelTurn.optJSONArray("parts") ?: return

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)

                // 1. Decode Incoming Audio PCM chunks
                val inlineData = part.optJSONObject("inlineData")
                if (inlineData != null) {
                    val mimeType = inlineData.optString("mimeType", "")
                    if (mimeType.startsWith("audio/pcm")) {
                        val base64Audio = inlineData.optString("data", "")
                        if (base64Audio.isNotEmpty()) {
                            val audioBytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                            
                            // Feed directly into our optimized 24kHz AudioProcessor
                            updateState(VoiceState.SPEAKING)
                            activeAudioProcessor?.feedRawPcm(audioBytes)
                        }
                    }
                }

                // 2. Process Text transcript
                val textContent = part.optString("text", "")
                if (textContent.isNotEmpty()) {
                    onLiveTranscript(textContent, false)
                }
            }

            // If turn is complete, return to LISTENING
            val turnComplete = serverContent.optBoolean("turnComplete", false)
            if (turnComplete) {
                AlyaLogger.d(TAG, "Gemini turn complete.")
                updateState(VoiceState.LISTENING)
            }
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error handling incoming live message", e)
        }
    }

    fun onUserSpeechDetected() {
        if (_connectionState.value == VoiceState.SPEAKING) {
            AlyaLogger.i(TAG, "Barge-in triggered: user started speaking while assistant was speaking")
            updateState(VoiceState.INTERRUPTED)
            activeAudioProcessor?.stopProcessing()
            
            // Send explicit interrupt trigger frame if WebSocket is active
            try {
                webSocket?.send(JSONObject().apply {
                    put("clientContent", JSONObject().apply {
                        put("turnComplete", false)
                        put("userTurn", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "[USER_INTERRUPT]")
                                })
                            })
                        })
                    })
                }.toString())
            } catch (e: Exception) {}
            
            updateState(VoiceState.LISTENING)
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

    fun stopLiveSession() {
        AlyaLogger.i(TAG, "Stopping live voice session")
        updateState(VoiceState.STOPPING)
        
        webSocket?.close(1000, "User closed live session")
        webSocket = null
        
        sessionJob?.cancel()
        sessionJob = null
        _sessionId.value = null
        activeAudioProcessor = null
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
                activeAudioProcessor?.let { startLiveSession("", it) }
            }
        } else {
            AlyaLogger.w(TAG, "Exceeded max reconnect attempts, falling back to local simulation")
            simulateLocalBackupSession()
        }
    }

    private fun simulateLocalBackupSession() {
        sessionJob?.cancel()
        sessionJob = scope.launch(Dispatchers.IO) {
            _sessionId.value = "alya_local_${System.currentTimeMillis()}"
            reconnectAttempts = 0
            updateState(VoiceState.CONNECTED)
            delay(300)
            updateState(VoiceState.LISTENING)
        }
    }

    private fun handleIncomingToolCall(ws: WebSocket, functionCalls: JSONArray) {
        scope.launch {
            try {
                val responseArray = JSONArray()
                for (i in 0 until functionCalls.length()) {
                    val call = functionCalls.getJSONObject(i)
                    val name = call.getString("name")
                    val id = call.getString("id")
                    val argsObj = call.optJSONObject("args")
                    
                    val argsMap = mutableMapOf<String, Any?>()
                    if (argsObj != null) {
                        val keys = argsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            argsMap[key] = argsObj.get(key)
                        }
                    }

                    AlyaLogger.i(TAG, "Executing Live tool call: $name with args $argsMap (id=$id)")
                    
                    // Trigger character animation for tool execution
                    com.example.service.AlyaForegroundService.updateCompanionState(com.example.core.model.CompanionCharacterState.APP_LAUNCH)

                    // Call the registered tool calling callback
                    val (success, feedbackMessage) = onToolCall?.invoke(name, argsMap) ?: Pair(false, "Bridge not connected")
                    
                    val responseObj = JSONObject().apply {
                        put("id", id)
                        put("response", JSONObject().apply {
                            put("output", JSONObject().apply {
                                put("success", success)
                                put("message", feedbackMessage)
                            })
                        })
                    }
                    responseArray.put(responseObj)
                }

                val toolResponseJson = JSONObject().apply {
                    put("toolResponse", JSONObject().apply {
                        put("functionResponses", responseArray)
                    })
                }

                ws.send(toolResponseJson.toString())
                AlyaLogger.i(TAG, "Sent Tool Response JSON back to Gemini Live API: $toolResponseJson")
            } catch (e: Exception) {
                AlyaLogger.e(TAG, "Error processing live tool call", e)
            }
        }
    }

    private fun updateState(newState: VoiceState) {
        _connectionState.value = newState
        onStateChanged(newState)
        AlyaLogger.d(TAG, "Live state transitioned to: $newState")
    }
}
