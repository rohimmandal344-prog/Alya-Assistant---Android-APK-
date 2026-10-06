package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.logger.AlyaLogger
import com.example.ui.AlyaUiState

@Composable
fun DiagnosticsScreen(
    state: AlyaUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("diagnostics_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Diagnostics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "Real-time assistant state and telemetry.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        }

        // Subsystems status cards
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subsystems Telemetry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    TelemetryRow("Voice State", state.voiceState.name)
                    TelemetryRow("Live Voice Active", if (state.isLiveActive) "ACTIVE" else "IDLE")
                    TelemetryRow("AI Model", state.aiModel)
                    TelemetryRow("Accessibility Service", if (state.permissions.isAccessibilityEnabled) "CONNECTED" else "NOT RUNNING")
                    TelemetryRow("Microphone Permission", if (state.permissions.hasMicrophone) "GRANTED" else "DENIED")
                    TelemetryRow("Flashlight State", if (state.isFlashlightOn) "ON" else "OFF")
                    TelemetryRow("Media Volume", "${state.currentVolume} / ${state.maxVolume}")
                    if (state.batteryInfo != null) {
                        TelemetryRow("Battery", "${state.batteryInfo.levelPercentage}% (${state.batteryInfo.status})")
                    }
                    if (state.timeContext != null) {
                        TelemetryRow("System Time", "${state.timeContext.time} - ${state.timeContext.timeZone}")
                    }
                }
            }
        }

        // Real-time Event Log Terminal
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Structured Event Logs (${state.logs.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                IconButton(onClick = { AlyaLogger.clear() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                }
            }
        }

        if (state.logs.isEmpty()) {
            item {
                Text("No recent log events recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(state.logs.take(40), key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = log.timestamp,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[${log.tag}]",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when (log.level) {
                                "ERROR" -> Color(0xFFEF4444)
                                "WARN" -> Color(0xFFF59E0B)
                                else -> Color(0xFF38BDF8)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = log.message,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}
