package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.audio.AudioPrewarmer
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlyaAudioPrewarmService — Background Initialization Service.
 *
 * Pre-warms the AudioRecord session, acoustic effects, and low-pass filter chain
 * when the app enters the background or starts a continuous voice session,
 * completely eliminating cold-start driver latency and mic initialization noise.
 */
class AlyaAudioPrewarmService : Service() {

    companion object {
        private const val TAG = "ALYA_PREWARM_SVC"
        const val ACTION_PREWARM = "com.example.ACTION_PREWARM_AUDIO"

        fun triggerPrewarm(context: Context) {
            try {
                val intent = Intent(context, AlyaAudioPrewarmService::class.java).apply {
                    action = ACTION_PREWARM
                }
                context.startService(intent)
            } catch (e: Exception) {
                AlyaLogger.w(TAG, "Unable to trigger audio pre-warm service: ${e.message}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PREWARM) {
            AlyaLogger.i(TAG, "Background pre-warming service triggered")
            serviceScope.launch {
                AudioPrewarmer.prewarm(applicationContext)
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
