package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.VoiceState
import com.example.ui.AlyaUiState
import com.example.ui.components.AlyaOrb

@Composable
fun LiveVoiceScreen(
    state: AlyaUiState,
    onStopLive: () -> Unit,
    onTriggerBargeIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B),
                        Color(0xFF0F172A)
                    )
                )
            )
            .padding(24.dp)
            .testTag("live_voice_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (state.voiceState != VoiceState.IDLE) Color(0xFF10B981) else Color(0xFFEF4444),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE VOICE SESSION",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Alya",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Text(
                    text = when (state.voiceState) {
                        VoiceState.CONNECTING -> "Connecting to live voice…"
                        VoiceState.LISTENING -> "Listening to you…"
                        VoiceState.THINKING -> "Thinking…"
                        VoiceState.SPEAKING -> "Speaking…"
                        VoiceState.INTERRUPTED -> "Barge-in detected"
                        else -> "Session Active"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp
                )
            }

            // Center Dynamic Orb
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AlyaOrb(
                    voiceState = state.voiceState,
                    size = 200.dp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Last utterance or transcript preview
                val lastAssistantMsg = state.messages.lastOrNull()
                if (lastAssistantMsg != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(
                            text = lastAssistantMsg.content,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Controls Row
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barge-in button (Interrupt Alya when she is speaking)
                AnimatedVisibility(visible = state.voiceState == VoiceState.SPEAKING) {
                    Button(
                        onClick = onTriggerBargeIn,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .testTag("barge_in_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tap to Interrupt Alya", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStopLive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("end_live_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Live Voice Session",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "End Live Session",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
