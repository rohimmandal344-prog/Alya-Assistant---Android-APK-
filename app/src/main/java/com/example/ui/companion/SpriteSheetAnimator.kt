package com.example.ui.companion

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.CompanionCharacterState

/**
 * Configuration data class defining frame timing and sprite sequences for a character state.
 */
data class SpriteAnimationConfig(
    val frameDrawables: List<Int>,
    val frameDurationMs: Int = 200,
    val bobbingAmplitude: Float = 0f,
    val breathingScaleAmplitude: Float = 0f
)

/**
 * SpriteSheetAnimator is a high-performance Jetpack Compose animation component designed
 * for 2D anime character companions. It handles idle, talking, falling, and running states
 * with smooth crossfade interpolation, frame sequencing (e.g. lip-sync frames between open/closed mouth),
 * micro-breathing/bobbing dynamics, and squash-and-stretch physics.
 */
@Composable
fun SpriteSheetAnimator(
    state: CompanionCharacterState,
    modifier: Modifier = Modifier,
    contentDescription: String = "Alya Companion Sprite",
    talkingSpeedFactor: Float = 1.0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sprite_sheet_animator_transition")

    // Idle breathing & micro-bobbing cycle (1.8s loop)
    val idleBreathingScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breathing_scale"
    )

    val idleBobbingOffset by infiniteTransition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_bobbing_offset"
    )

    // Talking Lip-Sync frame alternator (switches between mouth open and mouth closed)
    val lipSyncProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (220 / talkingSpeedFactor.coerceIn(0.5f, 2.0f)).toInt(),
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lip_sync_progress"
    )

    // Talking subtle head tilt & accent motion
    val talkingSway by infiniteTransition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "talking_sway"
    )

    // Falling tumbling flutter
    val fallingFlutter by infiniteTransition.animateFloat(
        initialValue = -4.0f,
        targetValue = 4.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 140, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "falling_flutter"
    )

    // Running fast cadence step alternator
    val runningStep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "running_step"
    )

    // Walking cadence (slower than running)
    val walkingStep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "walking_step"
    )

    // Select the active frame drawable based on state and current frame sub-progress
    @DrawableRes val activeDrawableRes = when (state) {
        CompanionCharacterState.IDLE -> {
            R.drawable.img_alya_idle_1791276523762
        }
        CompanionCharacterState.TALKING -> {
            // Alternate between Talking (mouth open) and Idle (mouth closed) to form natural lip sync
            if (lipSyncProgress > 0.45f) {
                R.drawable.img_alya_talking_1791276569045
            } else {
                R.drawable.img_alya_idle_1791276523762
            }
        }
        CompanionCharacterState.FALLING, CompanionCharacterState.HURT -> {
            R.drawable.alya_hurt_1791296382595
        }
        CompanionCharacterState.RUNNING, CompanionCharacterState.APP_LAUNCH -> {
            R.drawable.img_alya_running_1791276602919
        }
        CompanionCharacterState.WALKING -> {
            R.drawable.alya_walking_1791296332204
        }
        CompanionCharacterState.SITTING -> {
            R.drawable.alya_sitting_1791296347002
        }
        CompanionCharacterState.ANGRY_POUT -> {
            R.drawable.alya_angry_1791296361727
        }
        CompanionCharacterState.DRINKING_COFFEE -> {
            R.drawable.alya_coffee_1791295611380
        }
        CompanionCharacterState.STRETCHING -> {
            R.drawable.alya_stretching_1791295624514
        }
    }

    // Dynamic scale and offset modifications for smooth motion feel
    val dynamicScaleX = when (state) {
        CompanionCharacterState.IDLE -> idleBreathingScale
        CompanionCharacterState.TALKING -> 1.0f + (lipSyncProgress * 0.02f)
        CompanionCharacterState.FALLING, CompanionCharacterState.HURT -> 0.96f
        CompanionCharacterState.RUNNING, CompanionCharacterState.APP_LAUNCH -> 1.02f
        CompanionCharacterState.WALKING -> 1.0f
        CompanionCharacterState.SITTING -> 0.98f
        CompanionCharacterState.ANGRY_POUT -> 1.04f
        CompanionCharacterState.DRINKING_COFFEE -> 1.01f
        CompanionCharacterState.STRETCHING -> 1.03f
    }

    val dynamicScaleY = when (state) {
        CompanionCharacterState.IDLE -> idleBreathingScale
        CompanionCharacterState.TALKING -> 1.0f + ((1f - lipSyncProgress) * 0.02f)
        CompanionCharacterState.FALLING, CompanionCharacterState.HURT -> 1.05f // Stretch along vertical axis during gravity drop
        CompanionCharacterState.RUNNING, CompanionCharacterState.APP_LAUNCH -> if (runningStep > 0.5f) 1.04f else 0.98f
        CompanionCharacterState.WALKING -> if (walkingStep > 0.5f) 1.02f else 0.99f
        CompanionCharacterState.SITTING -> 0.95f // Slightly squashed while sitting
        CompanionCharacterState.ANGRY_POUT -> 1.02f
        CompanionCharacterState.DRINKING_COFFEE -> 1.01f
        CompanionCharacterState.STRETCHING -> 1.06f // Stretch upwards when stretching
    }

    val dynamicTranslationY = when (state) {
        CompanionCharacterState.IDLE -> idleBobbingOffset
        CompanionCharacterState.TALKING -> if (lipSyncProgress > 0.5f) -1.5f else 0.5f
        CompanionCharacterState.FALLING, CompanionCharacterState.HURT -> fallingFlutter
        CompanionCharacterState.RUNNING, CompanionCharacterState.APP_LAUNCH -> if (runningStep > 0.5f) -3f else 2f
        CompanionCharacterState.WALKING -> if (walkingStep > 0.5f) -2f else 1f
        CompanionCharacterState.SITTING -> 4f // Lower center of gravity
        CompanionCharacterState.ANGRY_POUT -> idleBobbingOffset
        CompanionCharacterState.DRINKING_COFFEE -> idleBobbingOffset
        CompanionCharacterState.STRETCHING -> -4f // Slight lift when stretching
    }

    val dynamicRotation = when (state) {
        CompanionCharacterState.TALKING -> talkingSway
        CompanionCharacterState.FALLING, CompanionCharacterState.HURT -> fallingFlutter * 1.5f
        CompanionCharacterState.RUNNING, CompanionCharacterState.APP_LAUNCH -> (runningStep - 0.5f) * 6f
        CompanionCharacterState.WALKING -> (walkingStep - 0.5f) * 4f
        CompanionCharacterState.ANGRY_POUT -> -2f
        CompanionCharacterState.STRETCHING -> 2f
        else -> 0f
    }

    Box(
        modifier = modifier
            .testTag("sprite_sheet_animator")
            .graphicsLayer {
                scaleX = dynamicScaleX
                scaleY = dynamicScaleY
                translationY = dynamicTranslationY
                rotationZ = dynamicRotation
            },
        contentAlignment = Alignment.Center
    ) {
        // Crossfade ensures ultra-smooth state transitions (idle <-> talking <-> falling <-> running)
        Crossfade(
            targetState = activeDrawableRes,
            animationSpec = tween(
                durationMillis = when (state) {
                    CompanionCharacterState.FALLING -> 120 // Fast snap for sudden pushes/drops
                    CompanionCharacterState.TALKING -> 80  // Snappy lip-sync transitions
                    else -> 220                           // Smooth easing for idle and runs
                }
            ),
            label = "sprite_frame_crossfade"
        ) { drawableId ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val transparentBitmap = remember(drawableId) {
                SpriteTransparencyHelper.getTransparentImageBitmap(context, drawableId)
            }
            Image(
                bitmap = transparentBitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}
