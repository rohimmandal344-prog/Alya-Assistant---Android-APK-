package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.model.ConversationMessage
import com.example.core.model.VoiceState
import com.example.ui.AlyaUiState
import com.example.ui.components.AlyaOrb
import com.example.ui.components.MessageBubble

private const val ALYA_LOGO_URL = "https://res.cloudinary.com/xbks6nu5/image/upload/v1789891823/Alisa.Mikhailova.Kujou.600.4066893_lp4uzw.jpg"

@Composable
fun ChatScreen(
    state: AlyaUiState,
    onSendMessage: (String) -> Unit,
    onToggleMic: () -> Unit,
    onStartLive: () -> Unit,
    onClearChat: () -> Unit,
    onToggleAudio: (ConversationMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Scroll to bottom smoothly when new messages arrive or content updates
    val lastMessageLength = state.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(state.messages.size, lastMessageLength) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_screen")
    ) {
        // Top Assistant Header
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Logo and Assistant Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(46.dp)
                    ) {
                        // Dynamic acoustic glow around logo indicating assistant voice state
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    when (state.voiceState) {
                                        VoiceState.LISTENING -> Color(0xFF10B981).copy(alpha = 0.35f)
                                        VoiceState.SPEAKING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                                        VoiceState.THINKING -> Color(0xFFA855F7).copy(alpha = 0.35f)
                                        VoiceState.INTERRUPTED -> Color(0xFFF59E0B).copy(alpha = 0.35f)
                                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                                    }
                                )
                        )

                        // Alya Assistant Logo loaded directly from Cloudinary URL with offline local fallback
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(ALYA_LOGO_URL)
                                .crossfade(true)
                                .error(R.drawable.alya_logo)
                                .placeholder(R.drawable.alya_logo)
                                .build(),
                            contentDescription = "Alya Assistant Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = when (state.voiceState) {
                                        VoiceState.LISTENING -> Color(0xFF10B981)
                                        VoiceState.SPEAKING -> MaterialTheme.colorScheme.primary
                                        VoiceState.THINKING -> Color(0xFFA855F7)
                                        VoiceState.INTERRUPTED -> Color(0xFFF59E0B)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                )
                                .testTag("alya_header_logo")
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Alya Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = when (state.voiceState) {
                                VoiceState.LISTENING -> "Listening…"
                                VoiceState.THINKING -> "Thinking…"
                                VoiceState.SPEAKING -> "Speaking…"
                                VoiceState.INTERRUPTED -> "Interrupted (Barge-in)"
                                else -> "Ready"
                            },
                            fontSize = 12.sp,
                            color = when (state.voiceState) {
                                VoiceState.LISTENING -> Color(0xFF10B981)
                                VoiceState.SPEAKING -> MaterialTheme.colorScheme.primary
                                VoiceState.THINKING -> Color(0xFFA855F7)
                                VoiceState.INTERRUPTED -> Color(0xFFF59E0B)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onStartLive,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("start_live_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Live Voice Mode",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Chat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Messages Thread
        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    AlyaOrb(voiceState = state.voiceState, size = 100.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "How can Alya assist you?",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Speak aloud, type a message, or request Android automation.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("messages_lazy_column"),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.messages, key = { if (it.id != 0L) it.id else it.timestamp }) { msg ->
                    val isSpeaking = state.speakingMessageId == msg.id || (msg.id == 0L && state.speakingMessageId == msg.timestamp)
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(280)) + slideInVertically(
                            animationSpec = tween(280),
                            initialOffsetY = { it / 3 }
                        )
                    ) {
                        MessageBubble(
                            message = msg,
                            isSpeakingThisMessage = isSpeaking,
                            onToggleAudio = onToggleAudio
                        )
                    }
                }
            }
        }

        // Bottom Input Row (Soft Keyboard Adaptive - imePadding & navigationBarsPadding)
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ask Alya or give a command…") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Microphone toggle button
                FilledIconButton(
                    onClick = onToggleMic,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.voiceState == VoiceState.LISTENING)
                            Color(0xFFEF4444)
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.testTag("mic_toggle_button")
                ) {
                    Icon(
                        imageVector = if (state.voiceState == VoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Toggle Microphone",
                        tint = if (state.voiceState == VoiceState.LISTENING) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Send text button
                FilledIconButton(
                    onClick = {
                        val text = textInput.trim()
                        if (text.isNotEmpty()) {
                            onSendMessage(text)
                            textInput = ""
                        }
                    },
                    modifier = Modifier.testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
