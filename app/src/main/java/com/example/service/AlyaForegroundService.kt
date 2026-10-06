package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.core.logger.AlyaLogger
import com.example.core.model.CompanionCharacterState
import com.example.core.model.VoiceState
import com.example.ui.companion.SpriteTransparencyHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlyaForegroundService : Service() {

    companion object {
        private const val TAG = "ALYA_SERVICE"
        private const val CHANNEL_ID = "alya_foreground_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.ACTION_START"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_SHOW_OVERLAY = "com.example.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.ACTION_HIDE_OVERLAY"
        const val EXTRA_STATUS = "extra_status"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _currentCompanionState = MutableStateFlow(CompanionCharacterState.IDLE)
        val currentCompanionState: StateFlow<CompanionCharacterState> = _currentCompanionState.asStateFlow()

        // Persistent states for overlay mapping
        @Volatile var currentVoiceState: VoiceState = VoiceState.IDLE
            private set
        @Volatile var currentSpeakerText: String? = null
            private set

        private var stateChangeListener: ((VoiceState, String?) -> Unit)? = null
        private var companionStateChangeListener: ((CompanionCharacterState) -> Unit)? = null

        fun startService(context: Context, statusText: String = "Alya is active") {
            val intent = Intent(context, AlyaForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_STATUS, statusText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AlyaForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun showOverlayAction(context: Context) {
            val intent = Intent(context, AlyaForegroundService::class.java).apply {
                action = ACTION_SHOW_OVERLAY
            }
            context.startService(intent)
        }

        fun hideOverlayAction(context: Context) {
            val intent = Intent(context, AlyaForegroundService::class.java).apply {
                action = ACTION_HIDE_OVERLAY
            }
            context.startService(intent)
        }

        fun updateState(voiceState: VoiceState, text: String?) {
            currentVoiceState = voiceState
            currentSpeakerText = text
            stateChangeListener?.invoke(voiceState, text)
            
            // Auto-sync companion state based on voice state
            when (voiceState) {
                VoiceState.SPEAKING -> updateCompanionState(CompanionCharacterState.TALKING)
                VoiceState.IDLE, VoiceState.LISTENING, VoiceState.INTERRUPTED -> {
                    if (_currentCompanionState.value == CompanionCharacterState.TALKING) {
                        updateCompanionState(CompanionCharacterState.IDLE)
                    }
                }
                else -> {}
            }
        }

        fun updateCompanionState(state: CompanionCharacterState) {
            _currentCompanionState.value = state
            companionStateChangeListener?.invoke(state)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayImageView: ImageView? = null
    private var overlaySpeechBubble: TextView? = null
    private var springJob: kotlinx.coroutines.Job? = null
    private var behaviorJob: kotlinx.coroutines.Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    private fun animateSpringTo(params: WindowManager.LayoutParams, rootLayout: View, targetX: Int, targetY: Int) {
        springJob?.cancel()
        springJob = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            var currX = params.x.toFloat()
            var currY = params.y.toFloat()
            var velX = 0f
            var velY = 0f

            val stiffness = 120f
            val damping = 12f
            val dt = 0.016f

            while (Math.abs(targetX - currX) > 1f || Math.abs(targetY - currY) > 1f || Math.abs(velX) > 1f || Math.abs(velY) > 1f) {
                val forceX = stiffness * (targetX - currX) - damping * velX
                velX += forceX * dt
                currX += velX * dt

                val forceY = stiffness * (targetY - currY) - damping * velY
                velY += forceY * dt
                currY += velY * dt

                params.x = currX.toInt()
                params.y = currY.toInt()

                if (overlayView != null) {
                    try {
                        windowManager?.updateViewLayout(rootLayout, params)
                    } catch (e: Exception) {
                        break
                    }
                }
                kotlinx.coroutines.delay(16)
            }
            params.x = targetX
            params.y = targetY
            if (overlayView != null) {
                try {
                    windowManager?.updateViewLayout(rootLayout, params)
                } catch (e: Exception) {}
            }
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var animationTick = 0
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (overlayView == null) return
            animationTick++

            val imageView = overlayImageView ?: return
            val density = resources.displayMetrics.density

            val bobOffset = (Math.sin(animationTick * 0.15) * 5 * density).toInt()
            val lp = imageView.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.gravity = Gravity.CENTER_HORIZONTAL
                lp.topMargin = (10 * density).toInt() + bobOffset
                imageView.layoutParams = lp
            }

            if (currentVoiceState == VoiceState.SPEAKING) {
                val drawableId = if (animationTick % 2 == 0) {
                    R.drawable.img_alya_talking_1791276569045
                } else {
                    R.drawable.img_alya_idle_1791276523762
                }
                val bmp = SpriteTransparencyHelper.getTransparentBitmap(this@AlyaForegroundService, drawableId)
                imageView.setImageBitmap(bmp)
            } else {
                val state = _currentCompanionState.value
                val drawableId = when (state) {
                    CompanionCharacterState.WALKING -> R.drawable.alya_walking_1791296332204
                    CompanionCharacterState.SITTING -> R.drawable.alya_sitting_1791296347002
                    CompanionCharacterState.ANGRY_POUT -> R.drawable.alya_angry_1791296361727
                    CompanionCharacterState.HURT, CompanionCharacterState.FALLING -> R.drawable.alya_hurt_1791296382595
                    CompanionCharacterState.DRINKING_COFFEE -> R.drawable.alya_coffee_1791295611380
                    CompanionCharacterState.STRETCHING -> R.drawable.alya_stretching_1791295624514
                    CompanionCharacterState.RUNNING -> R.drawable.img_alya_running_1791276602919
                    else -> R.drawable.img_alya_idle_1791276523762
                }
                
                val bmp = SpriteTransparencyHelper.getTransparentBitmap(this@AlyaForegroundService, drawableId)
                imageView.setImageBitmap(bmp)

                // Apply special transforms for system overlay
                when (state) {
                    CompanionCharacterState.WALKING -> {
                        // Move overlay laterally across the screen during walking behavior
                        val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                        if (params != null) {
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val compWidth = (130 * density).toInt()
                            
                            // Determine direction based on tick or randomized state
                            val moveDirection = if ((animationTick / 40) % 2 == 0) 4 else -4
                            val targetX = params.x + (moveDirection * density).toInt()
                            
                            // Boundary collision check
                            if (targetX > 0 && targetX < (screenWidth - compWidth)) {
                                params.x = targetX
                                try { windowManager?.updateViewLayout(overlayView, params) } catch (e: Exception) {}
                            }
                        }
                    }
                    else -> {}
                }
            }

            mainHandler.postDelayed(this, 150)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        _isRunning.value = true
        AlyaLogger.i(TAG, "Foreground service created")

        // Register live voice state changes to refresh companion view elements
        stateChangeListener = { voiceState, text ->
            updateOverlayView(voiceState, text)
        }
        companionStateChangeListener = { _ ->
            // Update overlay if visible
        }

        startBehaviorLoop()
    }

    private fun startBehaviorLoop() {
        behaviorJob?.cancel()
        behaviorJob = serviceScope.launch {
            while (true) {
                // Decision interval: 10-20 seconds
                delay((10000..20000).random().toLong())
                
                if (currentVoiceState != VoiceState.IDLE) continue
                if (_currentCompanionState.value == CompanionCharacterState.HURT || 
                    _currentCompanionState.value == CompanionCharacterState.ANGRY_POUT ||
                    _currentCompanionState.value == CompanionCharacterState.FALLING) continue

                // Choose a random behavior
                val behaviorType = (0..3).random()
                when (behaviorType) {
                    0 -> {
                        val idles = listOf(CompanionCharacterState.DRINKING_COFFEE, CompanionCharacterState.STRETCHING)
                        updateCompanionState(idles.random())
                        delay(5000)
                    }
                    1 -> {
                        // Walking / Wandering
                        updateCompanionState(CompanionCharacterState.WALKING)
                        delay(4000)
                    }
                    2 -> {
                        // Sitting
                        updateCompanionState(CompanionCharacterState.SITTING)
                        delay(6000)
                    }
                    else -> {
                        updateCompanionState(CompanionCharacterState.IDLE)
                        delay(3000)
                    }
                }
                
                if (currentVoiceState == VoiceState.IDLE) {
                    updateCompanionState(CompanionCharacterState.IDLE)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AlyaLogger.i(TAG, "Stopping foreground service via action")
                hideOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                _isRunning.value = false
                return START_NOT_STICKY
            }
            ACTION_SHOW_OVERLAY -> {
                showOverlay()
                return START_STICKY
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlay()
                return START_STICKY
            }
        }

        val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Listening for commands…"
        val notification = buildNotification(status)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            _isRunning.value = true
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error starting foreground service", e)
        }

        return START_STICKY
    }

    /**
     * Spawns a system-wide floating WindowManager view (SYSTEM_ALERT_WINDOW overlay)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (overlayView != null) return

        // Verify overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlyaLogger.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission is not granted.")
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = 100
                y = 200
            }

            val density = resources.displayMetrics.density
            val compWidth = (130 * density).toInt()
            val compHeight = (170 * density).toInt()

            val rootLayout = FrameLayout(this)

            // Character Sprite
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(compWidth, compHeight).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                
                val currentDrawable = getDrawableForVoiceState(currentVoiceState)
                val bmp = SpriteTransparencyHelper.getTransparentBitmap(this@AlyaForegroundService, currentDrawable)
                setImageBitmap(bmp)
            }
            overlayImageView = imageView

            // Speech bubble Text view
            val speechBubble = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (180 * density).toInt(),
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = (175 * density).toInt() // positioned above the character sprite
                }
                
                setBackgroundResource(android.R.drawable.toast_frame)
                background?.alpha = 220
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                gravity = Gravity.CENTER
                
                if (!currentSpeakerText.isNullOrBlank()) {
                    text = currentSpeakerText
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
            overlaySpeechBubble = speechBubble

            rootLayout.addView(speechBubble)
            rootLayout.addView(imageView)

            // Implement intuitive touchscreen dragging and activity launch behavior
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            val dragThreshold = 5 * density
            var isDragging = false

            val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    // Personality reaction on double-tap: Tripped / Falling
                    updateCompanionState(CompanionCharacterState.FALLING)
                    serviceScope.launch {
                        updateOverlayView(currentVoiceState, listOf("Arre! संभल के! 😲", "Hey! Watch it!", "Kya kar rahe ho?! 💢").random())
                        delay(1200)
                        updateCompanionState(CompanionCharacterState.ANGRY_POUT)
                        updateOverlayView(currentVoiceState, "संभल के! मैं गिर जाती अभी... जानबूझकर किया न तुमने? 😡")
                        delay(3500)
                        if (_currentCompanionState.value == CompanionCharacterState.ANGRY_POUT) {
                            updateCompanionState(CompanionCharacterState.IDLE)
                            updateOverlayView(currentVoiceState, null)
                        }
                    }
                    return true
                }
                
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    // Resume MainActivity
                    val pm = packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    }
                    return true
                }
            })

            rootLayout.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        springJob?.cancel()
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold) {
                            isDragging = true
                            params.x = initialX + dx.toInt()
                            params.y = initialY - dy.toInt() // Subtract dy since gravity is BOTTOM aligned
                            windowManager?.updateViewLayout(rootLayout, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val compWidth = (130 * density).toInt()
                            
                            val targetRestingX = if (params.x < (screenWidth - compWidth) / 2) {
                                30
                            } else {
                                screenWidth - compWidth - 30
                            }
                            val targetRestingY = params.y
                            
                            animateSpringTo(params, rootLayout, targetRestingX, targetRestingY)
                        }
                        true
                    }
                    else -> false
                }
            }

            overlayView = rootLayout
            windowManager?.addView(rootLayout, params)
            mainHandler.post(animationRunnable)
            AlyaLogger.i(TAG, "System alert overlay shown successfully.")
        } catch (e: Exception) {
            AlyaLogger.e(TAG, "Error displaying window overlay", e)
        }
    }

    /**
     * Removes the system-wide floating overlay
     */
    private fun hideOverlay() {
        mainHandler.removeCallbacks(animationRunnable)
        overlayImageView = null
        overlaySpeechBubble = null
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                AlyaLogger.w(TAG, "Error removing overlayView from WindowManager")
            }
            overlayView = null
            AlyaLogger.i(TAG, "System alert overlay hidden.")
        }
    }

    /**
     * Dynamically updates the overlay views based on active VoiceState and transcripts
     */
    private fun updateOverlayView(voiceState: VoiceState, text: String?) {
        val imageView = overlayImageView ?: return
        val speechBubble = overlaySpeechBubble ?: return

        val drawableId = getDrawableForVoiceState(voiceState)
        val bmp = SpriteTransparencyHelper.getTransparentBitmap(this, drawableId)
        imageView.post {
            imageView.setImageBitmap(bmp)
        }

        speechBubble.post {
            if (!text.isNullOrBlank()) {
                speechBubble.text = text
                speechBubble.visibility = View.VISIBLE
            } else {
                speechBubble.visibility = View.GONE
            }
        }
    }

    private fun getDrawableForVoiceState(voiceState: VoiceState): Int {
        return when (voiceState) {
            VoiceState.SPEAKING -> R.drawable.img_alya_talking_1791276569045
            VoiceState.THINKING -> R.drawable.img_alya_running_1791276602919
            VoiceState.LISTENING -> R.drawable.img_alya_idle_1791276523762
            else -> R.drawable.img_alya_idle_1791276523762
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlyaForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alya Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alya Voice & Automation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active background assistant status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        behaviorJob?.cancel()
        hideOverlay()
        stateChangeListener = null
        _isRunning.value = false
        AlyaLogger.i(TAG, "Foreground service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
