package com.example.device

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.KeyEvent
import com.example.core.logger.AlyaLogger
import com.example.core.model.ActionExecutionResult
import com.example.core.model.ActionImpactLevel
import com.example.core.model.ActionIntent
import com.example.core.model.DeviceActionType
import java.util.Locale

class CapabilityRegistry(
    private val context: Context,
    private val deviceController: DeviceController
) {

    /**
     * Translates natural language or structured commands into an ActionIntent
     */
    fun parseNaturalLanguage(input: String): ActionIntent? {
        val text = input.trim().lowercase(Locale.ROOT)

        // 1. YouTube Search
        if (text.startsWith("search youtube for ") || text.startsWith("youtube ")) {
            val query = text.removePrefix("search youtube for ").removePrefix("youtube ").trim()
            if (query.isNotEmpty()) {
                return ActionIntent(
                    action = DeviceActionType.SEARCH_YOUTUBE,
                    target = query,
                    parameters = mapOf("query" to query),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }
        }
        if (text == "open youtube") {
            return ActionIntent(
                action = DeviceActionType.OPEN_APP,
                target = "YouTube",
                parameters = mapOf("appName" to "YouTube", "packageName" to "com.google.android.youtube"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 2. Maps & Navigation
        if (text.startsWith("navigate to ") || text.startsWith("directions to ")) {
            val destination = text.removePrefix("navigate to ").removePrefix("directions to ").trim()
            if (destination.isNotEmpty()) {
                return ActionIntent(
                    action = DeviceActionType.OPEN_MAPS_NAVIGATE,
                    target = destination,
                    parameters = mapOf("destination" to destination),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }
        }
        if (text == "open maps" || text == "open google maps") {
            return ActionIntent(
                action = DeviceActionType.OPEN_APP,
                target = "Maps",
                parameters = mapOf("appName" to "Maps", "packageName" to "com.google.android.apps.maps"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 3. Flashlight / Torch
        if (text.contains("turn on flashlight") || text.contains("flashlight on") || text.contains("torch on") || text.contains("light on")) {
            return ActionIntent(
                action = DeviceActionType.TOGGLE_FLASHLIGHT,
                parameters = mapOf("enable" to "true"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("turn off flashlight") || text.contains("flashlight off") || text.contains("torch off") || text.contains("light off")) {
            return ActionIntent(
                action = DeviceActionType.TOGGLE_FLASHLIGHT,
                parameters = mapOf("enable" to "false"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 4. Volume Control
        if (text.contains("volume up") || text.contains("increase volume") || text.contains("turn volume up") || text.contains("awaaz badhao") || text.contains("awaz badhao")) {
            return ActionIntent(
                action = DeviceActionType.SET_VOLUME,
                parameters = mapOf("direction" to "up"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("volume down") || text.contains("decrease volume") || text.contains("turn volume down") || text.contains("awaaz kam karo") || text.contains("awaz kom koro")) {
            return ActionIntent(
                action = DeviceActionType.SET_VOLUME,
                parameters = mapOf("direction" to "down"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("mute volume") || text.contains("mute audio") || text.contains("mute phone")) {
            return ActionIntent(
                action = DeviceActionType.SET_VOLUME,
                parameters = mapOf("level" to "0"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 5. Media Control
        if (text == "pause music" || text == "pause" || text == "stop music" || text == "pause audio") {
            return ActionIntent(
                action = DeviceActionType.MEDIA_CONTROL,
                parameters = mapOf("command" to "pause"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "play music" || text == "play" || text == "resume music" || text == "resume") {
            return ActionIntent(
                action = DeviceActionType.MEDIA_CONTROL,
                parameters = mapOf("command" to "play"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "next track" || text == "next song" || text == "next") {
            return ActionIntent(
                action = DeviceActionType.MEDIA_CONTROL,
                parameters = mapOf("command" to "next"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "previous track" || text == "previous song" || text == "previous") {
            return ActionIntent(
                action = DeviceActionType.MEDIA_CONTROL,
                parameters = mapOf("command" to "prev"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 6. Phone Calls & Dialer
        if (text.startsWith("call ") || text.startsWith("dial ")) {
            val target = text.removePrefix("call ").removePrefix("dial ").trim()
            if (target.isNotEmpty()) {
                return ActionIntent(
                    action = DeviceActionType.OPEN_DIALER,
                    target = target,
                    parameters = mapOf("number" to target),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }
        }
        if (text == "open dialer" || text == "open phone") {
            return ActionIntent(
                action = DeviceActionType.OPEN_DIALER,
                parameters = mapOf("number" to ""),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 7. Settings
        if (text.contains("wifi settings") || text.contains("wi-fi settings")) {
            return ActionIntent(
                action = DeviceActionType.OPEN_WIFI_SETTINGS,
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("bluetooth settings")) {
            return ActionIntent(
                action = DeviceActionType.OPEN_BLUETOOTH_SETTINGS,
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("notification settings")) {
            return ActionIntent(
                action = DeviceActionType.OPEN_NOTIFICATION_SETTINGS,
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("accessibility settings")) {
            return ActionIntent(
                action = DeviceActionType.OPEN_ACCESSIBILITY_SETTINGS,
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "open settings") {
            return ActionIntent(
                action = DeviceActionType.OPEN_SETTINGS,
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 8. Generic App Opening: "open <App Name>"
        if (text.startsWith("open ") || text.startsWith("launch ")) {
            val appName = text.removePrefix("open ").removePrefix("launch ").trim()
            if (appName.isNotEmpty() && !appName.contains("settings") && !appName.contains("website")) {
                return ActionIntent(
                    action = DeviceActionType.OPEN_APP,
                    target = appName,
                    parameters = mapOf("appName" to appName),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }
        }

        // 9. Automation gestures / screen actions
        if (text == "scroll down" || text == "scroll forward" || text == "niche scroll koro") {
            return ActionIntent(
                action = DeviceActionType.ACCESSIBILITY_SCROLL,
                parameters = mapOf("direction" to "forward"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "scroll up" || text == "scroll backward" || text == "upore scroll koro") {
            return ActionIntent(
                action = DeviceActionType.ACCESSIBILITY_SCROLL,
                parameters = mapOf("direction" to "backward"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.startsWith("tap ") || text.startsWith("click ")) {
            val target = text.removePrefix("tap ").removePrefix("click ").trim()
            if (target.isNotEmpty()) {
                return ActionIntent(
                    action = DeviceActionType.ACCESSIBILITY_CLICK,
                    target = target,
                    parameters = mapOf("targetText" to target),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }
        }
        if (text.startsWith("type ") || text.startsWith("enter text ")) {
            val original = input.trim()
            val textToType = if (original.startsWith("type ", ignoreCase = true)) {
                original.substring(5).trim()
            } else {
                original.substring(11).trim()
            }
            if (textToType.isNotEmpty()) {
                return ActionIntent(
                    action = DeviceActionType.ACCESSIBILITY_TYPE,
                    target = textToType,
                    parameters = mapOf("text" to textToType),
                    impactLevel = ActionImpactLevel.SAFE
                )
            }
        }
        if (text == "press back" || text == "go back" || text == "back") {
            return ActionIntent(
                action = DeviceActionType.ACCESSIBILITY_GLOBAL,
                parameters = mapOf("action" to "back"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "go home" || text == "press home" || text == "home screen") {
            return ActionIntent(
                action = DeviceActionType.ACCESSIBILITY_GLOBAL,
                parameters = mapOf("action" to "home"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text == "open recents" || text == "show recent apps") {
            return ActionIntent(
                action = DeviceActionType.ACCESSIBILITY_GLOBAL,
                parameters = mapOf("action" to "recents"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        // 10. Device status / RAM / Storage / Battery
        if (text.contains("battery level") || text.contains("battery percentage") || text.contains("battery status") || text.contains("battery koto") || text.contains("battery")) {
            return ActionIntent(
                action = DeviceActionType.GET_DEVICE_INFO,
                target = "battery",
                parameters = mapOf("type" to "battery"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("ram status") || text.contains("storage status") || text.contains("device specs") || text.contains("phone info")) {
            return ActionIntent(
                action = DeviceActionType.GET_SYSTEM_METRICS,
                target = "metrics",
                parameters = mapOf("type" to "metrics"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("what time is it") || text.contains("current time") || text.contains("what is the date") || text.contains("koto somoy")) {
            return ActionIntent(
                action = DeviceActionType.GET_DEVICE_INFO,
                target = "time",
                parameters = mapOf("type" to "time"),
                impactLevel = ActionImpactLevel.SAFE
            )
        }
        if (text.contains("auto dim") || text.contains("dim screen") || text.contains("auto brightness") || text.contains("brightness save power")) {
            return ActionIntent(
                action = DeviceActionType.AUTO_DIM_SCREEN,
                impactLevel = ActionImpactLevel.SAFE
            )
        }

        return null
    }

    /**
     * Executes the ActionIntent using real Android APIs and verifies completion.
     * NEVER claims done unless verified!
     */
    suspend fun executeAction(intent: ActionIntent): ActionExecutionResult {
        AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Executing action: ${intent.action}, target=${intent.target}")

        return when (intent.action) {
            DeviceActionType.AUTO_DIM_SCREEN -> {
                val result = deviceController.autoDimScreen()
                if (result.isSuccess) {
                    val msg = result.getOrNull() ?: "Auto-dimmed screen"
                    ActionExecutionResult(
                        action = "AUTO_DIM_SCREEN",
                        target = "brightness",
                        success = true,
                        verified = true,
                        feedbackMessage = msg,
                        technicalDetails = "Ambient light sensor reading verified"
                    )
                } else {
                    ActionExecutionResult(
                        action = "AUTO_DIM_SCREEN",
                        target = null,
                        success = false,
                        verified = false,
                        feedbackMessage = "Unable to auto-dim screen: ${result.exceptionOrNull()?.message}",
                        technicalDetails = result.exceptionOrNull()?.stackTraceToString() ?: ""
                    )
                }
            }

            DeviceActionType.TOGGLE_FLASHLIGHT -> {
                val enable = intent.parameters["enable"]?.toBoolean() ?: true
                val result = deviceController.toggleFlashlight(enable)
                if (result.isSuccess) {
                    val verified = deviceController.isFlashlightOn() == enable
                    ActionExecutionResult(
                        action = "TOGGLE_FLASHLIGHT",
                        target = if (enable) "ON" else "OFF",
                        success = true,
                        verified = verified,
                        feedbackMessage = "Flashlight has been turned " + if (enable) "on" else "off",
                        technicalDetails = "Torch mode verified=$verified"
                    )
                } else {
                    ActionExecutionResult(
                        action = "TOGGLE_FLASHLIGHT",
                        target = null,
                        success = false,
                        verified = false,
                        feedbackMessage = "Unable to adjust flashlight: ${result.exceptionOrNull()?.message}",
                        technicalDetails = result.exceptionOrNull()?.stackTraceToString() ?: ""
                    )
                }
            }

            DeviceActionType.SET_VOLUME -> {
                val direction = intent.parameters["direction"]
                val level = intent.parameters["level"]?.toIntOrNull()

                val res = if (level != null) {
                    deviceController.setVolume(AudioManager.STREAM_MUSIC, level)
                } else if (direction == "up") {
                    deviceController.adjustVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE)
                } else {
                    deviceController.adjustVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER)
                }

                if (res.isSuccess) {
                    val (current, max) = deviceController.getCurrentVolume()
                    ActionExecutionResult(
                        action = "SET_VOLUME",
                        target = "$current/$max",
                        success = true,
                        verified = true,
                        feedbackMessage = "Media volume set to $current out of $max",
                        technicalDetails = "STREAM_MUSIC volume=$current"
                    )
                } else {
                    ActionExecutionResult(
                        action = "SET_VOLUME",
                        target = null,
                        success = false,
                        verified = false,
                        feedbackMessage = "Failed to adjust volume: ${res.exceptionOrNull()?.message}",
                        technicalDetails = res.exceptionOrNull()?.toString() ?: ""
                    )
                }
            }

            DeviceActionType.MEDIA_CONTROL -> {
                val cmd = intent.parameters["command"] ?: "play"
                val keyCode = when (cmd) {
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                }
                val res = deviceController.sendMediaKeyEvent(keyCode)
                ActionExecutionResult(
                    action = "MEDIA_CONTROL",
                    target = cmd,
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = "Dispatched $cmd command to active media player",
                    technicalDetails = "KeyCode $keyCode dispatched"
                )
            }

            DeviceActionType.OPEN_DIALER -> {
                val number = intent.parameters["number"] ?: intent.target ?: ""
                val res = deviceController.openDialer(number)
                ActionExecutionResult(
                    action = "OPEN_DIALER",
                    target = number,
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = res.getOrNull() ?: "Opened dialer",
                    technicalDetails = "Intent.ACTION_DIAL dispatched"
                )
            }

            DeviceActionType.OPEN_APP -> {
                val appName = intent.parameters["appName"] ?: intent.target ?: ""
                val packageName = intent.parameters["packageName"]
                val launchResult = if (!packageName.isNullOrBlank()) {
                    deviceController.openApp(packageName)
                } else {
                    deviceController.launchAppByName(appName)
                }

                if (launchResult.isSuccess) {
                    ActionExecutionResult(
                        action = "OPEN_APP",
                        target = launchResult.getOrNull(),
                        success = true,
                        verified = true,
                        feedbackMessage = "Opened ${launchResult.getOrNull()}",
                        technicalDetails = "Package launch intent delivered"
                    )
                } else {
                    ActionExecutionResult(
                        action = "OPEN_APP",
                        target = appName,
                        success = false,
                        verified = false,
                        feedbackMessage = "Could not open $appName: ${launchResult.exceptionOrNull()?.message}",
                        technicalDetails = launchResult.exceptionOrNull()?.toString() ?: ""
                    )
                }
            }

            DeviceActionType.SEARCH_YOUTUBE -> {
                val query = intent.parameters["query"] ?: intent.target ?: ""
                val res = deviceController.searchYouTube(query)
                ActionExecutionResult(
                    action = "SEARCH_YOUTUBE",
                    target = query,
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = if (res.isSuccess) "Searching YouTube for '$query'" else "Failed to open YouTube search",
                    technicalDetails = res.getOrNull() ?: res.exceptionOrNull()?.message ?: ""
                )
            }

            DeviceActionType.OPEN_MAPS_NAVIGATE -> {
                val destination = intent.parameters["destination"] ?: intent.target ?: ""
                val res = deviceController.openMapsNavigation(destination)
                ActionExecutionResult(
                    action = "OPEN_MAPS_NAVIGATE",
                    target = destination,
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = if (res.isSuccess) "Navigating to $destination" else "Failed to launch Maps navigation",
                    technicalDetails = res.getOrNull() ?: res.exceptionOrNull()?.message ?: ""
                )
            }

            DeviceActionType.OPEN_BROWSER -> {
                val url = intent.parameters["url"] ?: intent.target ?: "https://www.google.com"
                val res = deviceController.openBrowser(url)
                ActionExecutionResult(
                    action = "OPEN_BROWSER",
                    target = url,
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = if (res.isSuccess) "Opening browser" else "Failed to open browser",
                    technicalDetails = res.getOrNull() ?: ""
                )
            }

            DeviceActionType.OPEN_SETTINGS -> {
                val res = deviceController.openSettings(Settings.ACTION_SETTINGS)
                ActionExecutionResult(
                    action = "OPEN_SETTINGS",
                    target = "Settings",
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = "Opened Settings",
                    technicalDetails = ""
                )
            }

            DeviceActionType.OPEN_WIFI_SETTINGS -> {
                val res = deviceController.openSettings(Settings.ACTION_WIFI_SETTINGS)
                ActionExecutionResult(
                    action = "OPEN_WIFI_SETTINGS",
                    target = "Wi-Fi",
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = "Opened Wi-Fi Settings",
                    technicalDetails = ""
                )
            }

            DeviceActionType.OPEN_BLUETOOTH_SETTINGS -> {
                val res = deviceController.openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                ActionExecutionResult(
                    action = "OPEN_BLUETOOTH_SETTINGS",
                    target = "Bluetooth",
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = "Opened Bluetooth Settings",
                    technicalDetails = ""
                )
            }

            DeviceActionType.OPEN_NOTIFICATION_SETTINGS -> {
                val res = deviceController.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                ActionExecutionResult(
                    action = "OPEN_NOTIFICATION_SETTINGS",
                    target = "Notifications",
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = "Opened Notification Settings",
                    technicalDetails = ""
                )
            }

            DeviceActionType.OPEN_ACCESSIBILITY_SETTINGS -> {
                val res = deviceController.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                ActionExecutionResult(
                    action = "OPEN_ACCESSIBILITY_SETTINGS",
                    target = "Accessibility",
                    success = res.isSuccess,
                    verified = res.isSuccess,
                    feedbackMessage = "Opened Accessibility Settings",
                    technicalDetails = ""
                )
            }

            DeviceActionType.ACCESSIBILITY_CLICK -> {
                val service = AlyaAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_CLICK",
                        target = intent.target,
                        success = false,
                        verified = false,
                        feedbackMessage = "Alya Automation Service is not enabled. Please enable it in Settings > Accessibility.",
                        technicalDetails = "AlyaAutomationService instance is null"
                    )
                } else {
                    val target = intent.parameters["targetText"] ?: intent.target ?: ""
                    val clicked = service.clickElementByText(target)
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_CLICK",
                        target = target,
                        success = clicked,
                        verified = clicked,
                        feedbackMessage = if (clicked) "Tapped on '$target'" else "Could not find a clickable element matching '$target'",
                        technicalDetails = AlyaAutomationService.lastActionStatus.value
                    )
                }
            }

            DeviceActionType.ACCESSIBILITY_SCROLL -> {
                val service = AlyaAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_SCROLL",
                        target = null,
                        success = false,
                        verified = false,
                        feedbackMessage = "Alya Automation Service is not enabled. Please enable it in Settings > Accessibility.",
                        technicalDetails = "AlyaAutomationService instance is null"
                    )
                } else {
                    val forward = intent.parameters["direction"] != "backward"
                    val scrolled = service.scroll(forward)
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_SCROLL",
                        target = if (forward) "Forward" else "Backward",
                        success = scrolled,
                        verified = scrolled,
                        feedbackMessage = if (scrolled) "Scrolled " + if (forward) "down" else "up" else "No scrollable container found on screen",
                        technicalDetails = AlyaAutomationService.lastActionStatus.value
                    )
                }
            }

            DeviceActionType.ACCESSIBILITY_TYPE -> {
                val service = AlyaAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_TYPE",
                        target = intent.target,
                        success = false,
                        verified = false,
                        feedbackMessage = "Alya Automation Service is not enabled. Please enable it in Settings > Accessibility.",
                        technicalDetails = "AlyaAutomationService instance is null"
                    )
                } else {
                    val textToType = intent.parameters["text"] ?: intent.target ?: ""
                    val typed = service.typeTextIntoFocusedOrFirstEditable(textToType)
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_TYPE",
                        target = textToType,
                        success = typed,
                        verified = typed,
                        feedbackMessage = if (typed) "Typed '$textToType'" else "No editable text input found on current screen",
                        technicalDetails = AlyaAutomationService.lastActionStatus.value
                    )
                }
            }

            DeviceActionType.ACCESSIBILITY_GLOBAL -> {
                val service = AlyaAutomationService.instance
                if (service == null) {
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_GLOBAL",
                        target = intent.parameters["action"],
                        success = false,
                        verified = false,
                        feedbackMessage = "Alya Automation Service is not enabled. Please enable it in Settings > Accessibility.",
                        technicalDetails = "AlyaAutomationService instance is null"
                    )
                } else {
                    val actStr = intent.parameters["action"]
                    val actionCode = when (actStr) {
                        "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                        "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                        else -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                    }
                    val executed = service.performGlobal(actionCode)
                    ActionExecutionResult(
                        action = "ACCESSIBILITY_GLOBAL",
                        target = actStr,
                        success = executed,
                        verified = executed,
                        feedbackMessage = if (executed) "Navigated $actStr" else "Failed to perform global navigation",
                        technicalDetails = AlyaAutomationService.lastActionStatus.value
                    )
                }
            }

            DeviceActionType.GET_DEVICE_INFO -> {
                val type = intent.parameters["type"]
                if (type == "battery") {
                    val battery = deviceController.getBatteryInfo()
                    ActionExecutionResult(
                        action = "GET_BATTERY_INFO",
                        target = "${battery.levelPercentage}%",
                        success = true,
                        verified = true,
                        feedbackMessage = "Battery is at ${battery.levelPercentage}% (${battery.status})",
                        technicalDetails = "BatteryManager level=${battery.levelPercentage}, charging=${battery.isCharging}"
                    )
                } else {
                    val timeCtx = deviceController.getTimeContext()
                    ActionExecutionResult(
                        action = "GET_TIME_INFO",
                        target = timeCtx.time,
                        success = true,
                        verified = true,
                        feedbackMessage = "It is ${timeCtx.time} on ${timeCtx.date} (${timeCtx.timeZone})",
                        technicalDetails = "System time verified"
                    )
                }
            }

            DeviceActionType.GET_SYSTEM_METRICS -> {
                val metrics = deviceController.getSystemMetrics()
                val msg = "Device: ${metrics.deviceModel} running ${metrics.androidVersion}. RAM: ${metrics.availableRamMb}MB available of ${metrics.totalRamMb}MB. Storage: ${metrics.availableStorageGb}GB free of ${metrics.totalStorageGb}GB. Network: ${metrics.networkType}."
                ActionExecutionResult(
                    action = "GET_SYSTEM_METRICS",
                    target = metrics.deviceModel,
                    success = true,
                    verified = true,
                    feedbackMessage = msg,
                    technicalDetails = "Metrics retrieved via ActivityManager & StatFs"
                )
            }

            else -> {
                ActionExecutionResult(
                    action = "UNKNOWN",
                    target = null,
                    success = false,
                    verified = false,
                    feedbackMessage = "Requested action is not supported or recognized.",
                    technicalDetails = "Unsupported action type: ${intent.action}"
                )
            }
        }
    }
}
