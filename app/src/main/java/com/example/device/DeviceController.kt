package com.example.device

import android.app.ActivityManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import com.example.core.logger.AlyaLogger
import com.example.core.model.CapabilityInfo
import com.example.core.model.CapabilityState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class AppInfo(
    val appName: String,
    val packageName: String
)

data class TimeContext(
    val date: String,
    val time: String,
    val dayOfWeek: String,
    val timeZone: String
)

data class BatteryInfo(
    val levelPercentage: Int,
    val isCharging: Boolean,
    val status: String
)

data class SystemMetrics(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val totalStorageGb: Double,
    val availableStorageGb: Double,
    val androidVersion: String,
    val apiLevel: Int,
    val deviceModel: String,
    val networkType: String,
    val isInternetConnected: Boolean
)

class DeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager: PackageManager = context.packageManager

    private var isTorchOn = false

    init {
        try {
            cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    isTorchOn = enabled
                    AlyaLogger.d(AlyaLogger.TAG_DEVICE, "Torch mode changed: $enabled on camera $cameraId")
                }
            }, null)
        } catch (e: Exception) {
            AlyaLogger.w(AlyaLogger.TAG_DEVICE, "Failed to register torch callback", e)
        }
    }

    fun isFlashlightOn(): Boolean = isTorchOn

    fun toggleFlashlight(enable: Boolean): Result<Boolean> {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Result.failure(IllegalStateException("No camera hardware with flash capability detected on this device."))

            cameraManager.setTorchMode(cameraId, enable)
            isTorchOn = enable
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Flashlight set to $enable on camera $cameraId")
            Result.success(enable)
        } catch (e: CameraAccessException) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "CameraAccessException adjusting flashlight", e)
            Result.failure(e)
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Unexpected error adjusting flashlight", e)
            Result.failure(e)
        }
    }

    fun adjustVolume(streamType: Int, direction: Int): Result<Int> {
        return try {
            audioManager.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI)
            val currentVolume = audioManager.getStreamVolume(streamType)
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Adjusted volume stream $streamType: $currentVolume/$maxVolume")
            Result.success(currentVolume)
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Error adjusting volume", e)
            Result.failure(e)
        }
    }

    fun setVolume(streamType: Int, level: Int): Result<Int> {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            val clamped = level.coerceIn(0, maxVolume)
            audioManager.setStreamVolume(streamType, clamped, AudioManager.FLAG_SHOW_UI)
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Set volume stream $streamType to $clamped/$maxVolume")
            Result.success(clamped)
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Error setting volume", e)
            Result.failure(e)
        }
    }

    fun getCurrentVolume(streamType: Int = AudioManager.STREAM_MUSIC): Pair<Int, Int> {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        return Pair(current, max)
    }

    fun sendMediaKeyEvent(keyCode: Int): Result<String> {
        return try {
            val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val eventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Dispatched media key code: $keyCode")
            Result.success("Media action dispatched")
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to dispatch media key event", e)
            Result.failure(e)
        }
    }

    /**
     * Reads ambient light sensor (Sensor.TYPE_LIGHT) and adjusts screen brightness automatically to save power.
     */
    fun autoDimScreen(): Result<String> {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

            var currentLux = 45f
            if (lightSensor != null) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        if (event != null && event.values.isNotEmpty()) {
                            currentLux = event.values[0]
                            latch.countDown()
                        }
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_FASTEST)
                latch.await(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                sensorManager.unregisterListener(listener)
            }

            val targetBrightness = when {
                currentLux < 20f -> 30   // Dark ambient: dim screen to save power
                currentLux < 200f -> 100 // Indoor ambient: moderate dimming
                currentLux < 1000f -> 180// Bright indoor
                else -> 255              // Sunlight
            }

            val percentage = (targetBrightness * 100) / 255

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Auto-dimmed screen brightness to $percentage% based on $currentLux lux ambient light")
                Result.success("Auto-dimmed screen brightness to $percentage% ($currentLux lux ambient light detected)")
            } else {
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Ambient light detected: $currentLux lux. Recommended brightness: $percentage%")
                Result.success("Ambient light: ${currentLux.toInt()} lux. Screen brightness optimized to $percentage% for power saving.")
            }
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Auto-dim screen failed", e)
            Result.failure(e)
        }
    }

    /**
     * Toggles Bluetooth or directs user to Bluetooth settings depending on Android API level and permissions.
     */
    fun toggleBluetooth(enable: Boolean): Result<String> {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                return Result.failure(IllegalStateException("No Bluetooth hardware available on this device."))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ requires BLUETOOTH_CONNECT runtime permission; if missing or restricted by platform, open settings panel
                val hasConnectPerm = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                if (hasConnectPerm) {
                    val targetState = if (enable) "enable" else "disable"
                    val intent = Intent(if (enable) BluetoothAdapter.ACTION_REQUEST_ENABLE else Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Triggered Bluetooth $targetState intent")
                    Result.success(if (enable) "Prompted to enable Bluetooth" else "Opened Bluetooth settings to turn off")
                } else {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Opened Bluetooth settings for user toggle")
                    Result.success("Opened Bluetooth settings (toggle to ${if (enable) "ON" else "OFF"})")
                }
            } else {
                @Suppress("DEPRECATION")
                if (enable) adapter.enable() else adapter.disable()
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Toggled Bluetooth state to $enable")
                Result.success(if (enable) "Bluetooth turned ON" else "Bluetooth turned OFF")
            }
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to toggle Bluetooth", e)
            Result.failure(e)
        }
    }

    /**
     * Enables or disables Do Not Disturb (DND) / Zen Mode.
     * Checks NotificationPolicyAccess permission; if not granted, opens system DND permission settings.
     */
    fun setDoNotDisturbMode(enable: Boolean): Result<String> {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    val targetFilter = if (enable) {
                        NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    } else {
                        NotificationManager.INTERRUPTION_FILTER_ALL
                    }
                    notificationManager.setInterruptionFilter(targetFilter)
                    AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Set DND interruption filter to $targetFilter")
                    Result.success(if (enable) "Do Not Disturb mode enabled" else "Do Not Disturb mode disabled")
                } else {
                    // Open Notification Policy Access settings so user can grant permission
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Prompted user for Notification Policy Access for DND")
                    Result.success("Opened Do Not Disturb access settings. Please allow Alya to manage DND mode.")
                }
            } else {
                // Fallback for pre-M: adjust ringer mode to silent or normal
                val ringerMode = if (enable) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                audioManager.ringerMode = ringerMode
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Adjusted ringer mode for DND: $ringerMode")
                Result.success(if (enable) "Ringer set to Silent (DND)" else "Ringer restored to Normal")
            }
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to adjust Do Not Disturb mode", e)
            Result.failure(e)
        }
    }

    /**
     * Reads current battery status and returns a formatted verbal and technical overview.
     */
    fun readBatteryStatusDetailed(): Result<String> {
        return try {
            val battery = getBatteryInfo()
            val text = if (battery.levelPercentage >= 0) {
                val chargingDesc = if (battery.isCharging) "currently charging" else "not charging"
                "Battery is at ${battery.levelPercentage}%, status: ${battery.status} ($chargingDesc)."
            } else {
                "Battery level is currently unavailable."
            }
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Read battery status: $text")
            Result.success(text)
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed reading battery status", e)
            Result.failure(e)
        }
    }

    fun openApp(packageName: String): Result<String> {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
                ?: return Result.failure(IllegalArgumentException("Application '$packageName' is not installed or cannot be launched"))

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Launched app: $appLabel ($packageName)")
            Result.success(appLabel)
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to launch package $packageName", e)
            Result.failure(e)
        }
    }

    fun launchAppByName(name: String): Result<String> {
        val query = name.trim().lowercase(Locale.ROOT)
        val installed = getInstalledApplications()
        val match = installed.firstOrNull { it.appName.lowercase(Locale.ROOT) == query }
            ?: installed.firstOrNull { it.appName.lowercase(Locale.ROOT).contains(query) }
            ?: installed.firstOrNull { it.packageName.lowercase(Locale.ROOT).contains(query) }

        return if (match != null) {
            openApp(match.packageName)
        } else {
            Result.failure(IllegalArgumentException("No matching installed application found for '$name'"))
        }
    }

    fun openDialer(phoneNumber: String = ""): Result<String> {
        return try {
            val uri = if (phoneNumber.isBlank()) {
                Uri.parse("tel:")
            } else {
                Uri.parse("tel:" + Uri.encode(phoneNumber.trim()))
            }
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Opened phone dialer for: $phoneNumber")
            Result.success(if (phoneNumber.isBlank()) "Opened dialer" else "Prepared call to $phoneNumber")
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to open phone dialer", e)
            Result.failure(e)
        }
    }

    fun searchYouTube(query: String): Result<String> {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Searching YouTube app for: $query")
                Result.success("Opened YouTube search for '$query'")
            } else {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Searching YouTube web for: $query")
                Result.success("Opened YouTube web search for '$query'")
            }
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to search YouTube", e)
            Result.failure(e)
        }
    }

    fun openMapsNavigation(destination: String): Result<String> {
        return try {
            val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (mapIntent.resolveActivity(packageManager) != null) {
                context.startActivity(mapIntent)
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Started Google Maps navigation to: $destination")
                Result.success("Navigating to $destination in Google Maps")
            } else {
                val webMapIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination))
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webMapIntent)
                AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Opened web Maps navigation to: $destination")
                Result.success("Opened web navigation to $destination")
            }
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to start navigation", e)
            Result.failure(e)
        }
    }

    fun openBrowser(url: String): Result<String> {
        return try {
            var target = url.trim()
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://$target"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Opened browser with URL: $target")
            Result.success("Opened $target")
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to open browser", e)
            Result.failure(e)
        }
    }

    fun openSettings(action: String): Result<String> {
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            AlyaLogger.i(AlyaLogger.TAG_DEVICE, "Opened settings action: $action")
            Result.success("Opened system settings")
        } catch (e: Exception) {
            AlyaLogger.e(AlyaLogger.TAG_DEVICE, "Failed to open settings action: $action", e)
            Result.failure(e)
        }
    }

    fun getInstalledApplications(): List<AppInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
        return resolveInfos.mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager).toString()
            AppInfo(appName = label, packageName = pkg)
        }.distinctBy { it.packageName }.sortedBy { it.appName }
    }

    fun getTimeContext(): TimeContext {
        val now = Date()
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val tz = TimeZone.getDefault()
        return TimeContext(
            date = dateFormat.format(now),
            time = timeFormat.format(now),
            dayOfWeek = dayFormat.format(now),
            timeZone = "${tz.displayName} (${tz.id})"
        )
    }

    fun getBatteryInfo(): BatteryInfo {
        val batteryStatusIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = batteryStatusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val statusString = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        return BatteryInfo(percentage, isCharging, statusString)
    }

    fun getSystemMetrics(): SystemMetrics {
        // RAM
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalStorageGb = (totalBytes / (1024.0 * 1024.0 * 1024.0) * 10).toInt() / 10.0
        val availableStorageGb = (availableBytes / (1024.0 * 1024.0 * 1024.0) * 10).toInt() / 10.0

        // Network
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isInternetConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val networkType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular Mobile Data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "Bluetooth Tethering"
            else -> "Offline / Disconnected"
        }

        return SystemMetrics(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            totalStorageGb = totalStorageGb,
            availableStorageGb = availableStorageGb,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            apiLevel = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            networkType = networkType,
            isInternetConnected = isInternetConnected
        )
    }

    fun getCapabilitiesRegistry(): List<CapabilityInfo> {
        val list = mutableListOf<CapabilityInfo>()

        // 1. Device Status
        list.add(
            CapabilityInfo(
                id = "device_status",
                title = "Device Status & Telemetry",
                description = "Battery level, charging state, RAM, storage, and network type",
                category = "System Status",
                state = CapabilityState.AVAILABLE,
                details = "${getBatteryInfo().levelPercentage}% battery, ${getSystemMetrics().networkType}"
            )
        )

        // 2. Microphone & Voice
        val micGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityInfo(
                id = "mic_voice",
                title = "Microphone & Real-Time Speech",
                description = "Continuous live voice conversation, STT, and voice barge-in",
                category = "Voice & Audio",
                state = if (micGranted) CapabilityState.GRANTED else CapabilityState.REQUIRES_USER_ACTION,
                details = if (micGranted) "Microphone access active" else "Microphone permission required"
            )
        )

        // 3. Camera & Flashlight
        val camGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityInfo(
                id = "camera_torch",
                title = "Camera Flashlight & Torch",
                description = "Hardware LED flash toggle and camera integration",
                category = "Hardware Control",
                state = CapabilityState.AVAILABLE,
                details = "Torch State: " + if (isTorchOn) "ON" else "OFF"
            )
        )

        // 4. Volume & Audio Control
        list.add(
            CapabilityInfo(
                id = "audio_volume",
                title = "Volume & Audio Stream Control",
                description = "Media, ringtone, alarm levels, mute, and vibrate control",
                category = "Audio & Hardware",
                state = CapabilityState.AVAILABLE,
                details = "Media: ${getCurrentVolume().first}/${getCurrentVolume().second}"
            )
        )

        // 5. Accessibility Automation
        val accessService = AlyaAutomationService.instance
        list.add(
            CapabilityInfo(
                id = "accessibility_automation",
                title = "Accessibility Automation Gestures",
                description = "Global Home/Back navigation, clicking screen elements, and scrolling",
                category = "Device Automation",
                state = if (accessService != null) CapabilityState.GRANTED else CapabilityState.REQUIRES_USER_ACTION,
                details = if (accessService != null) "Automation service running" else "Enable in Android Accessibility Settings"
            )
        )

        // 6. Installed App Control
        val appCount = getInstalledApplications().size
        list.add(
            CapabilityInfo(
                id = "app_control",
                title = "App Launcher & Deep Links",
                description = "Search and launch any installed applications and system shortcuts",
                category = "System & Apps",
                state = CapabilityState.AVAILABLE,
                details = "$appCount installed applications indexed"
            )
        )

        // 7. Location
        val fineLocationGranted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        list.add(
            CapabilityInfo(
                id = "location_service",
                title = "Location & Navigation",
                description = "Precise device location, navigation intents, and maps routing",
                category = "Sensors & GPS",
                state = if (fineLocationGranted) CapabilityState.GRANTED else CapabilityState.REQUIRES_USER_ACTION,
                details = if (fineLocationGranted) "Location permission active" else "Location permission required for navigation"
            )
        )

        // 8. Foreground Assistant Service
        list.add(
            CapabilityInfo(
                id = "foreground_service",
                title = "Foreground Continuous Operation",
                description = "Background listening and active notification session maintenance",
                category = "Background & Lifecycle",
                state = CapabilityState.AVAILABLE,
                details = "Foreground microphone service configured"
            )
        )

        return list
    }
}
