package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.device.AppInfo
import com.example.ui.AlyaUiState

@Composable
fun DeviceControlScreen(
    state: AlyaUiState,
    onToggleFlashlight: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onQuickAction: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("device_control_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Device Controls & Automation",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Real hardware APIs and verified Android automation.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Hardware Controls: Flashlight & Volume
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hardware APIs",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Flashlight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Camera Flashlight", fontWeight = FontWeight.Medium)
                                Text(
                                    if (state.isFlashlightOn) "Turned ON" else "Turned OFF",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.isFlashlightOn,
                            onCheckedChange = { onToggleFlashlight() },
                            modifier = Modifier.testTag("flashlight_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Volume
                    Text("Media Volume (${state.currentVolume}/${state.maxVolume})", fontWeight = FontWeight.Medium)
                    Slider(
                        value = state.currentVolume.toFloat(),
                        onValueChange = { onSetVolume(it.toInt()) },
                        valueRange = 0f..state.maxVolume.toFloat(),
                        steps = state.maxVolume - 1,
                        modifier = Modifier.testTag("volume_slider")
                    )
                }
            }
        }

        // Real-Time Battery Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.batteryInfo?.isCharging == true) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = if (state.batteryInfo?.isCharging == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Battery Status", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    text = if (state.batteryInfo != null) {
                                        "${state.batteryInfo.levelPercentage}% • ${state.batteryInfo.status}"
                                    } else {
                                        "Tap to check battery level"
                                    },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { onQuickAction("battery status") },
                            modifier = Modifier.testTag("refresh_battery_button")
                        ) {
                            Text("Query", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // System Settings Shortcuts
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("System Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onQuickAction("open wifi settings") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Wi-Fi", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onQuickAction("open bluetooth settings") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bluetooth", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onQuickAction("open accessibility settings") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accessibility", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onQuickAction("open notification settings") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Notifications", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onQuickAction("turn on bluetooth") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Toggle BT", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onQuickAction("enable do not disturb") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DoNotDisturb, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Toggle DND", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Automation Gestures
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Accessibility Automation Gestures", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(
                        "Dispatches real gestures and global actions via AlyaAutomationService.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onQuickAction("scroll down") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scroll Down", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onQuickAction("scroll up") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scroll Up", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onQuickAction("go home") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onQuickAction("go back") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Quick Launch Installed Apps
        item {
            Text("Installed Applications", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        items(state.installedApps.take(8)) { app ->
            Card(
                onClick = { onLaunchApp(app.packageName) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_item_${app.packageName}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(app.appName, fontWeight = FontWeight.Medium)
                        Text(app.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = "Launch", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
