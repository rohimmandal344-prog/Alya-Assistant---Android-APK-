package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.model.ConversationMessage
import com.example.core.model.MessageRole
import com.example.core.model.VoiceState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlyaOrb(
    voiceState: VoiceState,
    size: Dp = 140.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val innerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inner_alpha"
    )

    val (colors, activeScale) = when (voiceState) {
        VoiceState.SPEAKING -> Pair(
            listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFC084FC)),
            pulseScale * 1.05f
        )
        VoiceState.LISTENING -> Pair(
            listOf(Color(0xFF34D399), Color(0xFF06B6D4), Color(0xFF3B82F6)),
            pulseScale
        )
        VoiceState.THINKING -> Pair(
            listOf(Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFFF43F5E)),
            pulseScale * 0.98f
        )
        VoiceState.INTERRUPTED -> Pair(
            listOf(Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFFDC2626)),
            1.0f
        )
        VoiceState.CONNECTING, VoiceState.RECONNECTING -> Pair(
            listOf(Color(0xFF94A3B8), Color(0xFF64748B), Color(0xFF475569)),
            pulseScale * 0.95f
        )
        else -> Pair(
            listOf(Color(0xFF0EA5E9), Color(0xFF6366F1), Color(0xFF4338CA)),
            1.0f
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag("alya_orb")
    ) {
        // Outer glowing ripple
        Box(
            modifier = Modifier
                .size(size)
                .scale(activeScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(colors.first().copy(alpha = 0.35f * innerAlpha), Color.Transparent)
                    )
                )
        )

        // Mid core
        Box(
            modifier = Modifier
                .size(size * 0.72f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(colors = colors)
                )
        )

        // Portrait Logo in the center of the glowing acoustic ring
        Image(
            painter = painterResource(id = R.drawable.alya_logo),
            contentDescription = "Alya Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size * 0.65f)
                .clip(CircleShape)
        )
    }
}

@Composable
fun MessageBubble(
    message: ConversationMessage,
    isSpeakingThisMessage: Boolean = false,
    onToggleAudio: ((ConversationMessage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isUser) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.alya_logo),
                        contentDescription = "Alya",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Alya",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "You",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formattedTime,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            // Header speaker action icon button
            if (onToggleAudio != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onToggleAudio(message) },
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("play_audio_btn_${message.id}")
                ) {
                    Icon(
                        imageVector = if (isSpeakingThisMessage) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeakingThisMessage) "Stop audio" else "Play speech",
                        tint = if (isSpeakingThisMessage) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Click-To-Play Message Bubble Surface (Tap anywhere on the message to play TTS)
        Surface(
            onClick = {
                onToggleAudio?.invoke(message)
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primary
            else if (isSpeakingThisMessage)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = if (isSpeakingThisMessage) 6.dp else 2.dp,
            border = if (isSpeakingThisMessage)
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else null,
            modifier = Modifier.testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )

                // Click-To-Play status footer for AI responses
                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSpeakingThisMessage) Icons.Default.GraphicEq else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeakingThisMessage) "Playing Audio" else "Tap to play audio",
                            tint = if (isSpeakingThisMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSpeakingThisMessage) "Reading aloud… (tap to stop)" else "Tap to listen",
                            fontSize = 11.sp,
                            color = if (isSpeakingThisMessage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            fontWeight = if (isSpeakingThisMessage) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }

                // Verified Action Result Card
                if (message.actionResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.actionResult.verified)
                                Color(0xFF10B981).copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (message.actionResult.verified) Icons.Default.VerifiedUser else Icons.Default.Error,
                                contentDescription = "Verification status",
                                tint = if (message.actionResult.verified) Color(0xFF059669) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (message.actionResult.verified) "Verified Execution" else "Execution Failed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (message.actionResult.verified) Color(0xFF047857) else MaterialTheme.colorScheme.error
                                )
                                if (message.actionResult.technicalDetails.isNotEmpty()) {
                                    Text(
                                        text = message.actionResult.technicalDetails,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
