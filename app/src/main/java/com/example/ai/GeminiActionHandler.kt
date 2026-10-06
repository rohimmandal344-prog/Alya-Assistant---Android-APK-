package com.example.ai

import android.content.Context
import com.example.core.logger.AlyaLogger
import com.example.core.model.ActionExecutionResult
import com.example.core.model.ActionImpactLevel
import com.example.core.model.ActionIntent
import com.example.core.model.DeviceActionType
import com.example.device.AccessibilityHelper
import com.example.device.CapabilityRegistry
import com.example.device.HapticHelper

data class GeminiFunctionCall(
    val name: String,
    val args: Map<String, Any?>
)

data class GeminiResponsePayload(
    val text: String?,
    val functionCalls: List<GeminiFunctionCall>
)

class GeminiActionHandler(
    private val context: Context,
    private val capabilityRegistry: CapabilityRegistry
) {

    companion object {
        private const val TAG = "ALYA_ACTION_HANDLER"
    }

    /**
     * Maps Gemini FunctionCall names & args into strongly-typed Android ActionIntents
     */
    fun mapFunctionCallToActionIntent(functionCall: GeminiFunctionCall): ActionIntent? {
        val name = functionCall.name.trim()
        val args = functionCall.args

        AlyaLogger.i(TAG, "Mapping Gemini FunctionCall '$name' with args: $args")

        return when (name) {
            "openApp" -> {
                val appName = args["appName"]?.toString()?.trim() ?: ""
                if (appName.isNotEmpty()) {
                    ActionIntent(
                        action = DeviceActionType.OPEN_APP,
                        target = appName,
                        parameters = mapOf("appName" to appName),
                        impactLevel = ActionImpactLevel.SAFE
                    )
                } else null
            }

            "adjustVolume" -> {
                val level = args["level"]?.toString()?.trim()
                val direction = args["direction"]?.toString()?.lowercase()?.trim()
                if (level != null) {
                    ActionIntent(
                        action = DeviceActionType.SET_VOLUME,
                        target = "$level%",
                        parameters = mapOf("level" to level),
                        impactLevel = ActionImpactLevel.SAFE
                    )
                } else if (direction != null) {
                    ActionIntent(
                        action = DeviceActionType.SET_VOLUME,
                        target = direction,
                        parameters = mapOf("direction" to direction),
                        impactLevel = ActionImpactLevel.SAFE
                    )
                } else {
                    ActionIntent(
                        action = DeviceActionType.SET_VOLUME,
                        parameters = mapOf("direction" to "up"),
                        impactLevel = ActionImpactLevel.SAFE
                    )
                }
            }

            "toggleSystemSetting" -> {
                val setting = args["setting"]?.toString()?.lowercase()?.trim() ?: "flashlight"
                val state = args["state"]?.toString()?.toBoolean() ?: true

                when (setting) {
                    "flashlight", "torch" -> {
                        ActionIntent(
                            action = DeviceActionType.TOGGLE_FLASHLIGHT,
                            parameters = mapOf("enable" to state.toString()),
                            impactLevel = ActionImpactLevel.SAFE
                        )
                    }
                    "wifi", "wi-fi" -> {
                        ActionIntent(
                            action = DeviceActionType.OPEN_WIFI_SETTINGS,
                            impactLevel = ActionImpactLevel.SAFE
                        )
                    }
                    "bluetooth" -> {
                        ActionIntent(
                            action = DeviceActionType.OPEN_BLUETOOTH_SETTINGS,
                            impactLevel = ActionImpactLevel.SAFE
                        )
                    }
                    else -> {
                        ActionIntent(
                            action = DeviceActionType.OPEN_SETTINGS,
                            impactLevel = ActionImpactLevel.SAFE
                        )
                    }
                }
            }

            "performGlobalAction" -> {
                val action = args["action"]?.toString()?.lowercase()?.trim() ?: "back"
                ActionIntent(
                    action = DeviceActionType.ACCESSIBILITY_GLOBAL,
                    target = action,
                    parameters = mapOf("action" to action),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }

            "clickElement" -> {
                val targetText = args["selectorText"]?.toString()?.trim() ?: args["targetText"]?.toString()?.trim() ?: ""
                if (targetText.isNotEmpty()) {
                    ActionIntent(
                        action = DeviceActionType.ACCESSIBILITY_CLICK,
                        target = targetText,
                        parameters = mapOf("targetText" to targetText),
                        impactLevel = ActionImpactLevel.SAFE
                    )
                } else null
            }

            "scrollScreen" -> {
                val direction = args["direction"]?.toString()?.lowercase()?.trim() ?: "down"
                val forward = direction == "down" || direction == "forward"
                ActionIntent(
                    action = DeviceActionType.ACCESSIBILITY_SCROLL,
                    target = if (forward) "down" else "up",
                    parameters = mapOf("direction" to if (forward) "forward" else "backward"),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }

            "autoDimScreen" -> {
                ActionIntent(
                    action = DeviceActionType.AUTO_DIM_SCREEN,
                    impactLevel = ActionImpactLevel.SAFE
                )
            }

            else -> {
                AlyaLogger.w(TAG, "Unrecognized Gemini FunctionCall name: $name")
                null
            }
        }
    }

    /**
     * Executes the Gemini FunctionCall via CapabilityRegistry and verifies Android completion
     */
    suspend fun executeFunctionCall(functionCall: GeminiFunctionCall): ActionExecutionResult {
        val intent = mapFunctionCallToActionIntent(functionCall)
            ?: return ActionExecutionResult(
                action = functionCall.name,
                target = null,
                success = false,
                verified = false,
                feedbackMessage = "Action '${functionCall.name}' is not supported.",
                technicalDetails = "No action intent mapping available for ${functionCall.name}"
            )

        // Pre-check for Accessibility permission if action requires screen automation
        val isAccessibilityAction = intent.action == DeviceActionType.ACCESSIBILITY_CLICK ||
                intent.action == DeviceActionType.ACCESSIBILITY_SCROLL ||
                intent.action == DeviceActionType.ACCESSIBILITY_TYPE ||
                intent.action == DeviceActionType.ACCESSIBILITY_GLOBAL

        if (isAccessibilityAction && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
            AlyaLogger.w(TAG, "Accessibility permission missing for action ${intent.action}")
            return ActionExecutionResult(
                action = intent.action.name,
                target = intent.target,
                success = false,
                verified = false,
                feedbackMessage = "Alya Accessibility Service is required to perform screen clicks, scrolling, and global actions. Please enable it in Settings.",
                technicalDetails = "AccessibilityService instance is not running"
            )
        }

        // Execute action on device
        val result = capabilityRegistry.executeAction(intent)

        if (result.success) {
            HapticHelper.vibrateSuccess(context)
            AlyaLogger.i(TAG, "Successfully executed function call '${functionCall.name}': ${result.feedbackMessage}")
        } else {
            AlyaLogger.w(TAG, "Failed executing function call '${functionCall.name}': ${result.feedbackMessage}")
        }

        return result
    }
}
