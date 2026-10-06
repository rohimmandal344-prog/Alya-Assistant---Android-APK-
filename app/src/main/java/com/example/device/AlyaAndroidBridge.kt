package com.example.device

import android.content.Context
import android.webkit.JavascriptInterface
import com.example.ai.GeminiActionHandler
import com.example.ai.GeminiFunctionCall
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * AlyaAndroidBridge — Native Android JavaScript Interface.
 *
 * Exposes device control and accessibility methods to WebView / Web UI at `window.AndroidBridge` and `window.AlyaNative`.
 * Supports asynchronous model tool call executions and status returns.
 */
class AlyaAndroidBridge(
    private val context: Context,
    private val deviceController: DeviceController,
    private val capabilityRegistry: CapabilityRegistry,
    private val actionHandler: GeminiActionHandler,
    private val onActionTriggered: ((String, String) -> Unit)? = null
) {

    companion object {
        private const val TAG = "ALYA_JS_BRIDGE"
        const val JAVASCRIPT_INTERFACE_NAME = "AndroidBridge"
        const val JAVASCRIPT_INTERFACE_ALIAS = "AlyaNative"
    }

    @JavascriptInterface
    fun executeToolCall(toolJsonString: String): String {
        AlyaLogger.i(TAG, "executeToolCall called with payload: $toolJsonString")
        return try {
            val json = JSONObject(toolJsonString)
            val name = json.optString("name", "")
            val argsObj = json.optJSONObject("args")
            val argsMap = mutableMapOf<String, Any?>()
            if (argsObj != null) {
                val keys = argsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    argsMap[key] = argsObj.get(key)
                }
            }

            val functionCall = GeminiFunctionCall(name, argsMap)
            val result = runBlocking(Dispatchers.IO) {
                actionHandler.executeFunctionCall(functionCall)
            }

            onActionTriggered?.invoke(name, result.feedbackMessage)

            val resp = JSONObject().apply {
                put("success", result.success)
                put("verified", result.verified)
                put("message", result.feedbackMessage)
                put("technicalDetails", result.technicalDetails)
            }
            resp.toString()
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error executing tool call from JS bridge", e)
            val errorResp = JSONObject().apply {
                put("success", false)
                put("verified", false)
                put("message", "Error: ${e.message}")
            }
            errorResp.toString()
        }
    }

    @JavascriptInterface
    fun openApp(appName: String): String {
        val res = deviceController.launchAppByName(appName)
        val resp = JSONObject().apply {
            put("success", res.isSuccess)
            put("message", res.getOrNull() ?: res.exceptionOrNull()?.message ?: "Failed to open app")
        }
        return resp.toString()
    }

    @JavascriptInterface
    fun adjustVolume(level: Int, streamType: String): String {
        val stream = if (streamType.equals("alarm", true)) android.media.AudioManager.STREAM_ALARM else android.media.AudioManager.STREAM_MUSIC
        val res = deviceController.setVolume(stream, level)
        val resp = JSONObject().apply {
            put("success", res.isSuccess)
            put("message", "Volume adjusted to $level")
        }
        return resp.toString()
    }

    @JavascriptInterface
    fun toggleSystemSetting(setting: String, state: Boolean): String {
        return when (setting.lowercase()) {
            "flashlight", "torch" -> {
                val res = deviceController.toggleFlashlight(state)
                JSONObject().apply {
                    put("success", res.isSuccess)
                    put("message", if (state) "Flashlight turned on" else "Flashlight turned off")
                }.toString()
            }
            "bluetooth" -> {
                val res = deviceController.toggleBluetooth(state)
                JSONObject().apply {
                    put("success", res.isSuccess)
                    put("message", res.getOrNull() ?: "Bluetooth updated")
                }.toString()
            }
            "dnd", "do_not_disturb" -> {
                val res = deviceController.setDoNotDisturbMode(state)
                JSONObject().apply {
                    put("success", res.isSuccess)
                    put("message", res.getOrNull() ?: "Do Not Disturb updated")
                }.toString()
            }
            else -> {
                val res = deviceController.openSettings(android.provider.Settings.ACTION_SETTINGS)
                JSONObject().apply {
                    put("success", res.isSuccess)
                    put("message", "Opened system settings")
                }.toString()
            }
        }
    }

    @JavascriptInterface
    fun performGlobalAction(action: String): String {
        val service = AlyaAutomationService.instance
        if (service == null) {
            return JSONObject().apply {
                put("success", false)
                put("message", "Accessibility Service is not enabled. Please enable Alya in Accessibility Settings.")
                put("requiresAccessibility", true)
            }.toString()
        }

        val actionCode = when (action.lowercase()) {
            "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            else -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        }

        val success = service.performGlobal(actionCode)
        return JSONObject().apply {
            put("success", success)
            put("message", "Executed global action $action")
        }.toString()
    }

    @JavascriptInterface
    fun clickElement(selectorText: String): String {
        val service = AlyaAutomationService.instance
        if (service == null) {
            return JSONObject().apply {
                put("success", false)
                put("message", "Accessibility Service is not enabled. Please enable Alya in Accessibility Settings.")
                put("requiresAccessibility", true)
            }.toString()
        }

        val success = service.clickElementByText(selectorText)
        return JSONObject().apply {
            put("success", success)
            put("message", if (success) "Clicked on '$selectorText'" else "Could not find clickable element for '$selectorText'")
        }.toString()
    }

    @JavascriptInterface
    fun scrollScreen(direction: String): String {
        val service = AlyaAutomationService.instance
        if (service == null) {
            return JSONObject().apply {
                put("success", false)
                put("message", "Accessibility Service is not enabled. Please enable Alya in Accessibility Settings.")
                put("requiresAccessibility", true)
            }.toString()
        }

        val forward = direction.equals("down", true) || direction.equals("forward", true)
        val success = service.scroll(forward)
        return JSONObject().apply {
            put("success", success)
            put("message", if (success) "Scrolled $direction" else "No scrollable element found")
        }.toString()
    }

    @JavascriptInterface
    fun getBatteryStatus(): String {
        val info = deviceController.getBatteryInfo()
        return JSONObject().apply {
            put("success", true)
            put("percentage", info.levelPercentage)
            put("isCharging", info.isCharging)
            put("status", info.status)
            put("message", "Battery is at ${info.levelPercentage}% (${info.status})")
        }.toString()
    }

    @JavascriptInterface
    fun isAccessibilityEnabled(): Boolean {
        return AccessibilityHelper.isAccessibilityServiceEnabled(context)
    }
}
