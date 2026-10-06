package com.example.ui.companion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.model.CompanionCharacterState
import com.example.core.model.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AlyaScreenCompanionOverlay(
    isOverlayActive: Boolean,
    voiceState: VoiceState,
    latestAssistantText: String?,
    companionStateTrigger: String = "IDLE",
    companionExpression: String = "idle",
    onTapCompanion: () -> Unit,
    onCloseOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOverlayActive) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val companionWidthPx = with(density) { 130.dp.toPx() }
    val companionHeightPx = with(density) { 170.dp.toPx() }

    val velocityTracker = remember { VelocityTracker() }

    // Physics Animation States
    val offsetX = remember { Animatable(180f) }
    val offsetY = remember { Animatable(350f) }
    val rotationAngle = remember { Animatable(0f) }
    val characterScale = remember { Animatable(1f) }

    var isDragging by remember { mutableStateOf(false) }
    val serviceState by com.example.service.AlyaForegroundService.currentCompanionState.collectAsStateWithLifecycle()
    var currentCharacterState by remember { mutableStateOf(CompanionCharacterState.IDLE) }
    
    // Sync local state to Service state for system consistency
    LaunchedEffect(serviceState) {
        if (!isDragging) {
            currentCharacterState = serviceState
        }
    }

    // Sync local state back to Service if user triggers state change (e.g. via drag/tap)
    LaunchedEffect(currentCharacterState) {
        com.example.service.AlyaForegroundService.updateCompanionState(currentCharacterState)
    }
    var speechBubbleText by remember { mutableStateOf<String?>(null) }
    var showQuickMenu by remember { mutableStateOf(false) }

    // Breathing / Floating Idle Loop
    val infiniteTransition = rememberInfiniteTransition(label = "idle_breathing")
    val idleFloatY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_float_y"
    )

    // Lip-Sync / Speaking Frame Alternator
    val talkingLipSync by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(240, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lip_sync_loop"
    )

    // Reactive Sync: character state with assistant speech & speech bubble text
    LaunchedEffect(voiceState, currentCharacterState) {
        if (voiceState == VoiceState.SPEAKING) {
            currentCharacterState = CompanionCharacterState.TALKING
            speechBubbleText = latestAssistantText?.take(65)
        } else {
            // Update speech bubble based on current autonomous state
            speechBubbleText = when (currentCharacterState) {
                CompanionCharacterState.TALKING -> null
                CompanionCharacterState.DRINKING_COFFEE -> "Sip sip... Ah, ☕✨"
                CompanionCharacterState.STRETCHING -> "Stretch~ 🧘‍♀️💫"
                CompanionCharacterState.WALKING -> if (currentCharacterState == CompanionCharacterState.WALKING) "Let's explore! 👉" else null
                CompanionCharacterState.SITTING -> "Taking a break... 🎀"
                CompanionCharacterState.APP_LAUNCH -> "Opening app for you! ✨"
                CompanionCharacterState.HURT -> "Arre! संभल के! 😲"
                CompanionCharacterState.ANGRY_POUT -> "Baka! धक्का क्यों दिया? 💢"
                else -> if (voiceState == VoiceState.IDLE && !isDragging) null else speechBubbleText
            }
        }
    }

    // Handle special movement for Walking state in Compose
    LaunchedEffect(currentCharacterState) {
        if (currentCharacterState == CompanionCharacterState.WALKING && !isDragging) {
            val targetX = (20..((screenWidthPx - companionWidthPx - 20).toInt().coerceAtLeast(21))).random().toFloat()
            offsetX.animateTo(
                targetValue = targetX,
                animationSpec = tween(
                    durationMillis = (Math.abs(targetX - offsetX.value) * 8).toInt().coerceIn(1200, 5000),
                    easing = LinearEasing
                )
            )
            com.example.service.AlyaForegroundService.updateCompanionState(CompanionCharacterState.IDLE)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("alya_screen_companion_overlay")
    ) {
        // Trigger Fall / Gravity Physics with Momentum and Personality Reactions
        fun triggerFallGravity(isHurt: Boolean = false, initialVelocityX: Float = 0f, initialVelocityY: Float = 0f) {
            scope.launch {
                if (isHurt) {
                    currentCharacterState = CompanionCharacterState.HURT
                    speechBubbleText = listOf("Arre! 😲", "Hey! Watch it!", "Kya kar rahe ho?! 💢").random()
                } else {
                    currentCharacterState = CompanionCharacterState.FALLING
                    speechBubbleText = "Kya kar rahe ho, baka! Kyaa~?! 😲"
                }
                
                // Tumbling rotation based on lateral momentum
                launch {
                    val targetRotation = if (initialVelocityX > 200f) 50f else if (initialVelocityX < -200f) -50f else if (offsetX.value > screenWidthPx / 2) 45f else -45f
                    rotationAngle.animateTo(
                        targetValue = targetRotation,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }

                // Momentum Fling / Decay Phase
                if (Math.abs(initialVelocityX) > 100f || Math.abs(initialVelocityY) > 100f) {
                    launch {
                        offsetX.animateDecay(initialVelocityX, exponentialDecay())
                    }
                    launch {
                        offsetY.animateDecay(initialVelocityY, exponentialDecay())
                    }
                    // Wait for momentum to dissipate slightly before gravity takes over
                    delay(300)
                }

                // Gravity pull to bottom edge
                val targetFloorY = (screenHeightPx - companionHeightPx - 90f).coerceAtLeast(0f)
                offsetY.animateTo(
                    targetValue = targetFloorY,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                // Landing recovery
                if (isHurt) {
                    currentCharacterState = CompanionCharacterState.ANGRY_POUT
                    speechBubbleText = "संभल के! मैं गिर जाती अभी... जानबूझकर किया न तुमने? 😡"
                    rotationAngle.animateTo(0f, spring(Spring.DampingRatioLowBouncy))
                    delay(4000)
                } else {
                    rotationAngle.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                    currentCharacterState = CompanionCharacterState.IDLE
                    speechBubbleText = "Ouch! संभल के करो ना! 💢"
                    delay(2500)
                }
                
                if (!isDragging && voiceState == VoiceState.IDLE) {
                    currentCharacterState = CompanionCharacterState.IDLE
                    speechBubbleText = null
                }
            }
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = offsetX.value.roundToInt(),
                        y = (offsetY.value + (if (currentCharacterState == CompanionCharacterState.IDLE) idleFloatY else 0f)).roundToInt()
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            triggerFallGravity(isHurt = true)
                        },
                        onTap = {
                            showQuickMenu = !showQuickMenu
                            onTapCompanion()
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            currentCharacterState = CompanionCharacterState.FALLING
                            speechBubbleText = "Hey! Mujhe kahan le jaa rahe ho? 🎀"
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            isDragging = false
                            val velocity = velocityTracker.calculateVelocity()
                            triggerFallGravity(isHurt = false, initialVelocityX = velocity.x, initialVelocityY = velocity.y)
                        },
                        onDragCancel = {
                            isDragging = false
                            triggerFallGravity()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch {
                                val newX = (offsetX.value + dragAmount.x).coerceIn(-50f, screenWidthPx - companionWidthPx + 50f)
                                val newY = (offsetY.value + dragAmount.y).coerceIn(-50f, screenHeightPx - companionHeightPx + 50f)
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                                rotationAngle.snapTo((dragAmount.x * 1.2f).coerceIn(-40f, 40f))
                            }
                        }
                    )
                }
                .testTag("alya_companion_character")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Speech / Reaction Bubble
                AnimatedVisibility(
                    visible = !speechBubbleText.isNullOrBlank(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .width(170.dp)
                            .testTag("companion_speech_bubble")
                    ) {
                        Text(
                            text = speechBubbleText ?: "",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Interactive 2D Anime Sprite Container (Scaled Footprint ~15-20% of screen)
                Box(
                    modifier = Modifier
                        .size(width = 130.dp, height = 170.dp)
                        .rotate(rotationAngle.value)
                        .scale(characterScale.value),
                    contentAlignment = Alignment.Center
                ) {
                    // High-performance SpriteSheetAnimator with smooth frame transitions & state bobs
                    SpriteSheetAnimator(
                        state = currentCharacterState,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Alya Screen Companion"
                    )

                    // Subtle state halo indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (voiceState) {
                                    VoiceState.LISTENING -> Color(0xFF10B981)
                                    VoiceState.SPEAKING -> MaterialTheme.colorScheme.primary
                                    VoiceState.THINKING -> Color(0xFFA855F7)
                                    else -> Color.Transparent
                                }
                            )
                    )
                }

                // Mini Companion Actions Menu
                AnimatedVisibility(visible = showQuickMenu) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        tonalElevation = 4.dp,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    showQuickMenu = false
                                    onTapCompanion()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Speak",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    showQuickMenu = false
                                    triggerFallGravity(isHurt = false)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "Fall Physics",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    showQuickMenu = false
                                    onCloseOverlay()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
