package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AlyaUiState

@Composable
fun PermissionsScreen(
    state: AlyaUiState,
    onRequestPermission: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("permissions_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Capability & Permission Center",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                text = "Declare and authorize legitimate system permissions to enable full offline/online capabilities.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // 1. Microphone
        item {
            PermissionCard(
                title = "Microphone",
                granted = state.permissions.hasMicrophone,
                icon = Icons.Default.Mic,
                why = "Enables real-time live voice streaming, wake-word detection ('Alya', 'Alia', 'Seno'), and multilingual speech-to-text translation.",
                whenUsed = "Always active during live call/session; strictly respects your microphone toggle state.",
                onAction = { onRequestPermission(android.Manifest.permission.RECORD_AUDIO) }
            )
        }

        // 2. Location
        item {
            PermissionCard(
                title = "Location (GPS & Local Context)",
                granted = state.permissions.hasLocation,
                icon = Icons.Default.LocationOn,
                why = "Enables voice-activated nearby place search, weather lookups, and navigation automation commands.",
                whenUsed = "Only queried when you explicitly ask about your current location, local weather, or request navigation.",
                onAction = { onRequestPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) }
            )
        }

        // 3. Notifications
        item {
            PermissionCard(
                title = "Notifications & Alerts",
                granted = state.permissions.hasNotifications,
                icon = Icons.Default.Notifications,
                why = "Required to display persistent foreground status controls and live audio interruption buttons.",
                whenUsed = "Only displayed when a background voice call or foreground assistant service is active.",
                onAction = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        onRequestPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        // 4. Camera (Torch & Media Capture)
        item {
            PermissionCard(
                title = "Camera (Torch Control)",
                granted = state.permissions.hasCamera,
                icon = Icons.Default.CameraAlt,
                why = "Allows the assistant to toggle physical device flashlight and camera capture on request.",
                whenUsed = "Used strictly to turn on/off physical flashlight on request; never accesses image stream without authorization.",
                onAction = { onRequestPermission(android.Manifest.permission.CAMERA) }
            )
        }

        // 5. Calendar Integration
        item {
            PermissionCard(
                title = "Calendar",
                granted = state.permissions.hasCalendar,
                icon = Icons.Default.CalendarMonth,
                why = "Enables Alya to view, schedule, update, or clear events and reminder alerts.",
                whenUsed = "Only accessed when managing calendar entries or schedules.",
                onAction = { onRequestPermission(android.Manifest.permission.READ_CALENDAR) }
            )
        }

        // 6. Call Logs Integration
        item {
            PermissionCard(
                title = "Call Logs",
                granted = state.permissions.hasCallLogs,
                icon = Icons.Default.History,
                why = "Required to view your recent calls list and voice-dial missed numbers.",
                whenUsed = "Only accessed on explicit call list requests.",
                onAction = { onRequestPermission(android.Manifest.permission.READ_CALL_LOG) }
            )
        }

        // 7. Contacts & Accounts
        item {
            PermissionCard(
                title = "Contacts and Accounts",
                granted = state.permissions.hasContacts,
                icon = Icons.Default.Contacts,
                why = "Allows searching contact names ('Call Rohim Mandal') and synchronizing profile credentials.",
                whenUsed = "Only active when resolving recipient names during voice dialing.",
                onAction = { onRequestPermission(android.Manifest.permission.READ_CONTACTS) }
            )
        }

        // 8. Phone Dialer & Calls
        item {
            PermissionCard(
                title = "Phone & Dialer State",
                granted = state.permissions.hasPhone,
                icon = Icons.Default.Phone,
                why = "Allows Alya to initiate phone calls directly, monitor ongoing incoming/outgoing calls, and manage audio focus.",
                whenUsed = "Only used when you say 'Call Rohim Mandal' or say 'dial 98765...'.",
                onAction = { onRequestPermission(android.Manifest.permission.CALL_PHONE) }
            )
        }

        // 9. Photos & Videos Media Library
        item {
            PermissionCard(
                title = "Photos and Videos Media",
                granted = state.permissions.hasPhotosAndVideos,
                icon = Icons.Default.Image,
                why = "Required to find and display locally captured images or videos upon request.",
                whenUsed = "Only invoked when accessing files or selecting device wallpaper.",
                onAction = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        onRequestPermission(android.Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        onRequestPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            )
        }

        // 10. Physical Activity Tracking
        item {
            PermissionCard(
                title = "Physical Activity Tracking",
                granted = state.permissions.hasPhysicalActivity,
                icon = Icons.Default.FitnessCenter,
                why = "Allows tracking step counts, speed, and real-time movement metrics locally.",
                whenUsed = "Only active when you request health metrics, speed, or current steps.",
                onAction = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        onRequestPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
            )
        }

        // 11. SMS & Messaging
        item {
            PermissionCard(
                title = "SMS and Messaging",
                granted = state.permissions.hasSms,
                icon = Icons.Default.Sms,
                why = "Allows reading, drafting, sending, and notifying you about incoming SMS text messages.",
                whenUsed = "Only utilized when sending a text or dictating SMS messages.",
                onAction = { onRequestPermission(android.Manifest.permission.READ_SMS) }
            )
        }

        // 12. Accessibility Automation Service
        item {
            PermissionCard(
                title = "Alya Automation Service (Accessibility)",
                granted = state.permissions.isAccessibilityEnabled,
                icon = Icons.Default.AccessibilityNew,
                why = "Enables full on-screen automation, page scrolling, element clicks, app launching, and gesture emulation.",
                whenUsed = "Required strictly to perform touch/gesture actions on other applications. Fully isolated and encrypted.",
                actionLabel = "Open Accessibility Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open App System Settings")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    granted: Boolean,
    icon: ImageVector,
    why: String,
    whenUsed: String,
    actionLabel: String = "Authorize",
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (granted) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Purpose: $why", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Activity: $whenUsed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)

            if (!granted) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp)
                }
            }
        }
    }
}
