package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRoute {
    SPEAKER,
    EARPIECE,
    WIRED_HEADSET,
    BLUETOOTH,
    UNKNOWN
}

class VoiceController(private val context: Context) {

    companion object {
        private const val TAG = AlyaLogger.TAG_AUDIO
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentRoute = MutableStateFlow(AudioRoute.SPEAKER)
    val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val audioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    AlyaLogger.i(TAG, "Headset plug event: state=$state")
                    updateAudioRoute()
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    AlyaLogger.w(TAG, "Audio becoming noisy (headphones unplugged)")
                    updateAudioRoute()
                }
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                    AlyaLogger.i(TAG, "Bluetooth SCO state updated: $state")
                    _isBluetoothConnected.value = (state == AudioManager.SCO_AUDIO_STATE_CONNECTED)
                    updateAudioRoute()
                }
            }
        }
    }

    init {
        registerAudioReceiver()
        updateAudioRoute()
    }

    private fun registerAudioReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        try {
            context.registerReceiver(audioReceiver, filter)
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Could not register audio receiver", e)
        }
    }

    fun updateAudioRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            var detected = AudioRoute.SPEAKER
            for (device in devices) {
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                        detected = AudioRoute.BLUETOOTH
                        break
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        detected = AudioRoute.WIRED_HEADSET
                        break
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                        if (detected == AudioRoute.SPEAKER) detected = AudioRoute.SPEAKER
                    }
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                        if (detected == AudioRoute.SPEAKER) detected = AudioRoute.EARPIECE
                    }
                }
            }
            _currentRoute.value = detected
            AlyaLogger.d(TAG, "Current audio route: $detected")
        } else {
            @Suppress("DEPRECATION")
            _currentRoute.value = if (audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn) {
                AudioRoute.BLUETOOTH
            } else if (audioManager.isWiredHeadsetOn) {
                AudioRoute.WIRED_HEADSET
            } else {
                AudioRoute.SPEAKER
            }
        }
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            audioManager.isSpeakerphoneOn = on
            AlyaLogger.i(TAG, "Speakerphone set to: $on")
            updateAudioRoute()
        } catch (e: Exception) {
            AlyaLogger.w(TAG, "Failed to toggle speakerphone", e)
        }
    }

    fun release() {
        try {
            context.unregisterReceiver(audioReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}
