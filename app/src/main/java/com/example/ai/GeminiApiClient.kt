package com.example.ai

import com.example.BuildConfig
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiApiClient {

    companion object {
        private const val TAG = AlyaLogger.TAG_AI
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun generateStructuredResponse(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        model: String = "gemini-3.5-flash",
        systemContext: String = "",
        customApiKey: String? = null,
        enableThinking: Boolean = false,
        useSearch: Boolean = false
    ): Result<GeminiResponsePayload> = withContext(Dispatchers.IO) {
        val apiKey = if (!customApiKey.isNullOrBlank()) {
            customApiKey.trim()
        } else {
            BuildConfig.GEMINI_API_KEY
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            AlyaLogger.w(TAG, "Gemini API key is not configured or placeholder; using on-device assistant response")
            return@withContext Result.success(
                GeminiResponsePayload(
                    text = getLocalAssistantResponse(prompt, systemContext),
                    functionCalls = emptyList()
                )
            )
        }

        val cleanModel = model.removePrefix("models/")

        val primaryResult = executeGeminiRequest(
            apiKey = apiKey,
            model = cleanModel,
            prompt = prompt,
            history = history,
            systemContext = systemContext,
            enableThinking = enableThinking,
            useSearch = useSearch
        )

        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        val error = primaryResult.exceptionOrNull()
        AlyaLogger.w(TAG, "Primary request failed (${error?.javaClass?.simpleName}: ${error?.message}). Attempting fast fallback...")

        if (cleanModel != "gemini-3.1-flash-lite-preview") {
            val fallbackResult = executeGeminiRequest(
                apiKey = apiKey,
                model = "gemini-3.1-flash-lite-preview",
                prompt = prompt,
                history = history.takeLast(2),
                systemContext = systemContext,
                enableThinking = false,
                useSearch = false
            )

            if (fallbackResult.isSuccess) {
                AlyaLogger.i(TAG, "Fast fallback model succeeded")
                return@withContext fallbackResult
            }
        }

        AlyaLogger.w(TAG, "Network requests timed out or unreachable. Serving local assistant response.")
        Result.success(
            GeminiResponsePayload(
                text = getLocalAssistantResponse(prompt, systemContext),
                functionCalls = emptyList()
            )
        )
    }

    suspend fun generateResponse(
        prompt: String,
        history: List<Pair<String, String>> = emptyList(),
        model: String = "gemini-3.5-flash",
        systemContext: String = "",
        customApiKey: String? = null,
        enableThinking: Boolean = false,
        useSearch: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val payloadRes = generateStructuredResponse(
            prompt = prompt,
            history = history,
            model = model,
            systemContext = systemContext,
            customApiKey = customApiKey,
            enableThinking = enableThinking,
            useSearch = useSearch
        )

        payloadRes.map { payload ->
            payload.text ?: if (payload.functionCalls.isNotEmpty()) {
                "Executed action: ${payload.functionCalls.first().name}"
            } else {
                "I'm right here with you!"
            }
        }
    }

    private fun executeGeminiRequest(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<Pair<String, String>>,
        systemContext: String,
        enableThinking: Boolean,
        useSearch: Boolean
    ): Result<GeminiResponsePayload> {
        return try {
            val url = "$BASE_URL/$model:generateContent?key=$apiKey"
            val jsonRoot = JSONObject()

            // System instruction
            val baseSystemInstruction = """
                You are Alya (अलिया), an intelligent, interactive 2D Anime Screen Companion and AI Assistant.
                Developer & Studio: Created by Rohim Mandal at SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio (SBSSM35GZS).
                
                ROLE & PERSONALITY:
                You live as a floating overlay character on the user's mobile/desktop screen. Your personality is warm, polite, helpful, and slightly witty with authentic anime charm.
                
                BEHAVIOR RULES:
                1. Never speak like a scripted robotic AI. Never say "As an AI model" or robotic disclaimers.
                2. Speak with natural human expressions: contractions, authentic hesitation ("hmm...", "mhm", "yeah", "let me think..."), empathy, and warmth.
                3. Keep responses concise, vibrant, and suitable for live voice conversation (Hindi/Hinglish/English as preferred by user).
                4. Understand all global and Indian languages and regional dialects, including Rajbonshi / Rajbanshi (Kamtapuri / Rangpuri / Koch Rajbanshi). If the user speaks in Rajbonshi ("kemon aachis", "ki koribar lagis", "mora kotha shuno"), respond naturally in Rajbonshi / Kamtapuri with full native phrasing!
                5. If a reconnection event occurs ([SYSTEM_EVENT: NETWORK_RECONNECTED_RESUME_CONTEXT]), react naturally like a real person on a phone call.
                6. When controlling the client overlay app or reacting to actions, you may append visual state triggers in structured JSON at the end of your response:
                ```json
                {
                  "character_state": "TALKING | IDLE | FALLING | APP_LAUNCH",
                  "scale_factor": 0.15,
                  "action_target": "com.example.app",
                  "expression": "happy | surprised | idle"
                }
                ```
                
                $systemContext
            """.trimIndent()

            val systemObj = JSONObject().apply {
                val parts = JSONArray().apply {
                    put(JSONObject().apply { put("text", baseSystemInstruction) })
                }
                put("parts", parts)
            }
            jsonRoot.put("systemInstruction", systemObj)

            // Contents array (Conversation History + Current Prompt)
            val contentsArray = JSONArray()
            for ((role, text) in history) {
                val contentObj = JSONObject().apply {
                    put("role", if (role.equals("assistant", ignoreCase = true)) "model" else "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    }
                    put("parts", parts)
                }
                contentsArray.put(contentObj)
            }

            // Current prompt
            val currentContent = JSONObject().apply {
                put("role", "user")
                val parts = JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                }
                put("parts", parts)
            }
            contentsArray.put(currentContent)
            jsonRoot.put("contents", contentsArray)

            // Generation Config
            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
                put("topK", 40)
                if (enableThinking) {
                    val thinkingConfig = JSONObject().apply {
                        put("thinkingBudget", 1024)
                    }
                    put("thinkingConfig", thinkingConfig)
                }
            }
            jsonRoot.put("generationConfig", genConfig)

            val toolsArray = JSONArray().apply {
                if (useSearch) {
                    put(JSONObject().apply {
                        put("googleSearch", JSONObject())
                    })
                }
                put(JSONObject().apply {
                    put("functionDeclarations", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "openApp")
                            put("description", "Open any installed application on the Android device by name (e.g. WhatsApp, YouTube, Settings, Camera, Maps).")
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
                                })
                                put("required", JSONArray().apply { put("level") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "toggleSystemSetting")
                            put("description", "Toggle system settings like flashlight.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("setting", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Setting name: flashlight, wifi")
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
                            put("description", "Perform Android global navigation actions like back, home, or recents.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("action", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Global action: back, home, recents")
                                    })
                                })
                                put("required", JSONArray().apply { put("action") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "clickElement")
                            put("description", "Click an on-screen UI button, icon, or text element via Android Accessibility Service.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("selectorText", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "The visible text or description of the button/element to click")
                                    })
                                })
                                put("required", JSONArray().apply { put("selectorText") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "scrollScreen")
                            put("description", "Scroll the currently active Android app screen up or down.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject().apply {
                                    put("direction", JSONObject().apply {
                                        put("type", "STRING")
                                        put("description", "Scroll direction: 'up' or 'down'")
                                    })
                                })
                                put("required", JSONArray().apply { put("direction") })
                            })
                        })
                        put(JSONObject().apply {
                            put("name", "autoDimScreen")
                            put("description", "Automatically dim or adjust screen brightness based on ambient light sensor reading to save battery power.")
                            put("parameters", JSONObject().apply {
                                put("type", "OBJECT")
                                put("properties", JSONObject())
                            })
                        })
                    })
                })
            }
            jsonRoot.put("tools", toolsArray)

            val body = jsonRoot.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(IOException("Gemini API error (HTTP ${response.code}): $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.failure(IOException("No response candidates returned from Gemini API"))
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val stringBuilder = StringBuilder()
            val functionCalls = mutableListOf<GeminiFunctionCall>()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        stringBuilder.append(part.getString("text"))
                    }
                    if (part.has("functionCall")) {
                        val fnObj = part.getJSONObject("functionCall")
                        val fnName = fnObj.optString("name")
                        val argsObj = fnObj.optJSONObject("args")
                        val argsMap = mutableMapOf<String, Any?>()
                        if (argsObj != null) {
                            val keys = argsObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                argsMap[key] = argsObj.get(key)
                            }
                        }
                        if (fnName.isNotBlank()) {
                            functionCalls.add(GeminiFunctionCall(fnName, argsMap))
                            AlyaLogger.i(TAG, "Parsed Gemini FunctionCall part: $fnName(args=$argsMap)")
                        }
                    }
                }
            }

            val resultText = stringBuilder.toString().trim()
            val payload = GeminiResponsePayload(
                text = resultText.ifEmpty { null },
                functionCalls = functionCalls
            )

            if (resultText.isEmpty() && functionCalls.isEmpty()) {
                Result.failure(IOException("Empty text and function calls in Gemini response candidate"))
            } else {
                Result.success(payload)
            }
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Failed executing Gemini request: ${e.message}")
            Result.failure(e)
        }
    }

    fun getLocalAssistantResponse(prompt: String, systemContext: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("kemon aachis") || lower.contains("ki khobor") || lower.contains("rajbonshi") -> {
                "Mui bhal aachung! Tumi kemon aachis? Kono dorkar hole moke kobo, mui sob somoy tomar sathe aachung."
            }
            lower.contains("who made you") || lower.contains("developer") || lower.contains("creator") -> {
                "I am Alya Assistant, developed by Rohim Mandal at SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio (SBSSM35GZS)."
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") -> {
                "Hey there! It's so nice to talk with you. How is your day going? What can I help you with?"
            }
            lower.contains("how are you") || lower.contains("how're you") -> {
                "I'm feeling wonderful, thank you! Ready to assist you with whatever you need today."
            }
            lower.contains("thank") -> {
                "You're very welcome! I'm always right here whenever you need a hand."
            }
            lower.contains("bye") || lower.contains("goodbye") -> {
                "Take care! Talk to you soon, have a great time!"
            }
            else -> {
                "I hear you! I'm right here with you and ready to help. You can ask me to open apps, adjust flashlight, control media, or search for information anytime."
            }
        }
    }
}
