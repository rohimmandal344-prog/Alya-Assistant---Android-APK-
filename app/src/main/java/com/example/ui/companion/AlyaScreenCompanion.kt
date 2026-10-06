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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.example.R
import com.example.core.model.VoiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CompanionCharacterState {
    IDLE,
    TALKING,
    FALLING,
    RUNNING,
    APP_LAUNCH
}

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

    // Physics Animation States
    val offsetX = remember { Animatable(180f) }
    val offsetY = remember { Animatable(350f) }
    val rotationAngle = remember { Animatable(0f) }
    val characterScale = remember { Animatable(1f) }

    var isDragging by remember { mutableStateOf(false) }
    var currentCharacterState by remember { mutableStateOf(CompanionCharacterState.IDLE) }
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

    // Sync character state with assistant speech
    LaunchedEffect(voiceState, companionStateTrigger) {
        when {
            voiceState == VoiceState.SPEAKING -> {
                currentCharacterState = CompanionCharacterState.TALKING
                speechBubbleText = latestAssistantText?.take(65)
            }
            companionStateTrigger.equals("RUNNING", ignoreCase = true) -> {
                currentCharacterState = CompanionCharacterState.RUNNING
                speechBubbleText = "Going fast! 🏃‍♀️💨"
            }
            companionStateTrigger.equals("FALLING", ignoreCase = true) -> {
                currentCharacterState = CompanionCharacterState.FALLING
            }
            companionStateTrigger.equals("APP_LAUNCH", ignoreCase = true) -> {
                currentCharacterState = CompanionCharacterState.APP_LAUNCH
                speechBubbleText = "Opening app for you! ✨"
                delay(2200)
                currentCharacterState = CompanionCharacterState.IDLE
            }
            else -> {
                if (!isDragging && currentCharacterState != CompanionCharacterState.FALLING) {
                    currentCharacterState = CompanionCharacterState.IDLE
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("alya_screen_companion_overlay")
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val companionWidthPx = with(density) { 130.dp.toPx() }
        val companionHeightPx = with(density) { 170.dp.toPx() }

        // Trigger Fall / Gravity Physics
        fun triggerFallGravity(initialVelocityY: Float = 600f) {
            scope.launch {
                currentCharacterState = CompanionCharacterState.FALLING
                speechBubbleText = "Kya kar rahe ho, baka! Kyaa~?! 😲"
                
                // Tumbling rotation during fall
                launch {
                    rotationAngle.animateTo(
                        targetValue = if (offsetX.value > screenWidthPx / 2) 28f else -28f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
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

                // Landing recovery bounce
                rotationAngle.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                
                currentCharacterState = CompanionCharacterState.IDLE
                speechBubbleText = "Ouch! संभल के करो ना! 💢"
                delay(2200)
                if (speechBubbleText?.contains("Ouch") == true) {
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
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            currentCharacterState = CompanionCharacterState.FALLING
                            speechBubbleText = "Hey! Mujhe kahan le jaa rahe ho? 🎀"
                        },
                        onDragEnd = {
                            isDragging = false
                            // User pushed or released Alya — trigger physics gravity drop
                            triggerFallGravity()
                        },
                        onDragCancel = {
                            isDragging = false
                            triggerFallGravity()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newX = (offsetX.value + dragAmount.x).coerceIn(0f, screenWidthPx - companionWidthPx)
                                val newY = (offsetY.value + dragAmount.y).coerceIn(40f, screenHeightPx - companionHeightPx)
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                                rotationAngle.snapTo((dragAmount.x * 0.8f).coerceIn(-35f, 35f))
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
                        .scale(characterScale.value)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showQuickMenu = !showQuickMenu
                            onTapCompanion()
                        },
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
                                    triggerFallGravity(900f)
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
