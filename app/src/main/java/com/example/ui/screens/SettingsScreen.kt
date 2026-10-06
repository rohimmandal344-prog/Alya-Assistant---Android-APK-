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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.model.MemoryItem
import com.example.ui.AlyaUiState
import com.google.firebase.auth.FirebaseUser

data class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String
)

val ALL_LANGUAGES = listOf(
    SupportedLanguage("en-US", "English (United States)", "English (US)"),
    SupportedLanguage("en-GB", "English (United Kingdom)", "English (UK)"),
    SupportedLanguage("rjs-IN", "Rajbonshi / Kamtapuri", "রাজবংশী (কামতাপুরী/রংপুরী)"),
    SupportedLanguage("hi-IN", "Hindi", "हिन्दी"),
    SupportedLanguage("bn-BD", "Bengali", "বাংলা"),
    SupportedLanguage("es-ES", "Spanish", "Español"),
    SupportedLanguage("fr-FR", "French", "Français"),
    SupportedLanguage("de-DE", "German", "Deutsch"),
    SupportedLanguage("ja-JP", "Japanese", "日本語"),
    SupportedLanguage("zh-CN", "Chinese (Simplified)", "简体中文"),
    SupportedLanguage("ar-SA", "Arabic", "العربية"),
    SupportedLanguage("ru-RU", "Russian", "Русский"),
    SupportedLanguage("pt-BR", "Portuguese", "Português"),
    SupportedLanguage("it-IT", "Italian", "Italiano"),
    SupportedLanguage("ur-PK", "Urdu", "اردو"),
    SupportedLanguage("id-ID", "Indonesian", "Bahasa Indonesia"),
    SupportedLanguage("ko-KR", "Korean", "한국어"),
    SupportedLanguage("ta-IN", "Tamil", "தமிழ்"),
    SupportedLanguage("te-IN", "Telugu", "తెలుగు"),
    SupportedLanguage("mr-IN", "Marathi", "मराठी"),
    SupportedLanguage("gu-IN", "Gujarati", "ગુજરાતી"),
    SupportedLanguage("pa-IN", "Punjabi", "ਪੰਜਾਬੀ"),
    SupportedLanguage("as-IN", "Assamese", "অসমীয়া")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AlyaUiState,
    currentUser: FirebaseUser?,
    onSignOut: () -> Unit,
    onLanguageChange: (String) -> Unit = {},
    onCustomApiKeyChange: (String) -> Unit = {},
    onBackgroundTalkingChange: (Boolean) -> Unit = {},
    onHapticFeedbackChange: (Boolean) -> Unit = {},
    onDeleteMemory: (String) -> Unit = {},
    onVoicePitchChange: (Float) -> Unit = {},
    onVoiceSpeedChange: (Float) -> Unit = {},
    onClearChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Assistant", "Guidelines", "Studio & Auth")

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        // Top Sub Tabs Row inside Settings
        TabRow(
            selectedTabIndex = selectedSubTab,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_sub_tabs")
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedSubTab) {
            0 -> AssistantSettingsTab(
                state = state,
                onLanguageChange = onLanguageChange,
                onCustomApiKeyChange = onCustomApiKeyChange,
                onBackgroundTalkingChange = onBackgroundTalkingChange,
                onHapticFeedbackChange = onHapticFeedbackChange,
                onDeleteMemory = onDeleteMemory,
                onVoicePitchChange = onVoicePitchChange,
                onVoiceSpeedChange = onVoiceSpeedChange,
                onClearChat = onClearChat
            )
            1 -> EnglishGuidelinesTab(state = state)
            2 -> StudioAndAuthTab(
                currentUser = currentUser,
                onSignOut = onSignOut
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantSettingsTab(
    state: AlyaUiState,
    onLanguageChange: (String) -> Unit,
    onCustomApiKeyChange: (String) -> Unit,
    onBackgroundTalkingChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onVoicePitchChange: (Float) -> Unit,
    onVoiceSpeedChange: (Float) -> Unit,
    onClearChat: () -> Unit
) {
    var expandedLanguageDropdown by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf(state.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var apiKeySavedMessage by remember { mutableStateOf(false) }

    val currentSelectedLanguage = ALL_LANGUAGES.find { it.code == state.selectedLanguage }
        ?: ALL_LANGUAGES.first()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Language Selection (All Languages + Rajbonshi dialect)
        item {
            SettingsCard(
                title = "Language & Dialect Selection",
                icon = Icons.Default.Language
            ) {
                Text(
                    text = "Alya understands and speaks natively in all supported global and Indian languages, including the Rajbonshi (Kamtapuri/Rangpuri) dialect.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExposedDropdownMenuBox(
                    expanded = expandedLanguageDropdown,
                    onExpandedChange = { expandedLanguageDropdown = !expandedLanguageDropdown }
                ) {
                    OutlinedTextField(
                        value = "${currentSelectedLanguage.displayName} (${currentSelectedLanguage.nativeName})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Active Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLanguageDropdown) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .testTag("language_selector")
                    )

                    ExposedDropdownMenu(
                        expanded = expandedLanguageDropdown,
                        onDismissRequest = { expandedLanguageDropdown = false }
                    ) {
                        ALL_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(lang.displayName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            lang.nativeName,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    onLanguageChange(lang.code)
                                    expandedLanguageDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Personal Gemini API Key Management
        item {
            SettingsCard(
                title = "Dynamic Gemini AI API Key",
                icon = Icons.Default.Key
            ) {
                Text(
                    text = "Configure your dynamic personal Gemini API key. When set, all assistant queries use your direct account quota.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        apiKeySavedMessage = false
                    },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.Security else Icons.Default.Key,
                                contentDescription = "Toggle Key Visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onCustomApiKeyChange(apiKeyInput)
                            apiKeySavedMessage = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_api_key_button")
                    ) {
                        Text("Save Key")
                    }

                    if (apiKeyInput.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                apiKeyInput = ""
                                onCustomApiKeyChange("")
                                apiKeySavedMessage = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Default")
                        }
                    }
                }

                if (apiKeySavedMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "API Key successfully updated for this session.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Human Natural Voice & Background Talking
        item {
            SettingsCard(
                title = "Natural Voice & Background Operation",
                icon = Icons.AutoMirrored.Filled.VolumeUp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Voice Operation", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Allows Alya to continue natural conversation via Foreground Service when the app is minimized.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.backgroundTalkingEnabled,
                        onCheckedChange = { onBackgroundTalkingChange(it) },
                        modifier = Modifier.testTag("background_talking_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Natural Female Voice Pitch: ${String.format("%.2f", state.voicePitch)}x", fontSize = 13.sp)
                Slider(
                    value = state.voicePitch,
                    onValueChange = onVoicePitchChange,
                    valueRange = 0.8f..1.4f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Speech Pace: ${String.format("%.2f", state.voiceSpeed)}x", fontSize = 13.sp)
                Slider(
                    value = state.voiceSpeed,
                    onValueChange = onVoiceSpeedChange,
                    valueRange = 0.7f..1.3f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. Haptic Feedback (VibrationManager)
        item {
            SettingsCard(
                title = "Haptic Feedback (VibrationManager)",
                icon = Icons.Default.Security
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Action Haptic Confirmation", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Vibrate using VibrationManager for tactile confirmation whenever device control tools execute successfully.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.hapticFeedbackEnabled,
                        onCheckedChange = { onHapticFeedbackChange(it) },
                        modifier = Modifier.testTag("haptic_feedback_switch")
                    )
                }
            }
        }

        // 4. Human Long-Term Memories
        item {
            SettingsCard(
                title = "Human Long-Term Memory",
                icon = Icons.Default.Psychology
            ) {
                Text(
                    text = "Alya remembers personal facts, relationships, and preferences shared during conversations.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (state.memories.isEmpty()) {
                    Text(
                        text = "No saved memories yet. Tell Alya \"Remember that my birthday is in May\" to store memories.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.memories.forEach { mem ->
                            MemoryCard(memory = mem, onDelete = { onDeleteMemory(mem.key) })
                        }
                    }
                }
            }
        }

        // 5. Data Management
        item {
            SettingsCard(
                title = "Data Management",
                icon = Icons.Default.Delete
            ) {
                Text(
                    text = "Clear your local conversation history.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onClearChat,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Chat History")
                }
            }
        }
    }
}

@Composable
private fun EnglishGuidelinesTab(state: AlyaUiState) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SettingsCard(
                title = "Device English Voice Commands Guide",
                icon = Icons.Default.Book
            ) {
                Text(
                    text = "Alya understands natural spoken English commands without requiring fixed robotic phrases. Here is a quick reference guide:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                CommandExampleRow("🔦 Flashlight", "\"Turn on flashlight\", \"Torch off\", \"Light on\"")
                CommandExampleRow("🔊 Volume", "\"Volume up\", \"Decrease volume\", \"Mute audio\"")
                CommandExampleRow("📺 YouTube", "\"Search YouTube for classical music\", \"Open YouTube\"")
                CommandExampleRow("🗺️ Navigation", "\"Navigate to Central Park\", \"Open Google Maps\"")
                CommandExampleRow("📱 App Launch", "\"Open WhatsApp\", \"Launch Camera\", \"Open Chrome\"")
                CommandExampleRow("📞 Dialer", "\"Call 9876543210\", \"Open dialer\"")
                CommandExampleRow("📊 Device Status", "\"Battery percentage\", \"RAM status\", \"Storage status\"")
                CommandExampleRow("🤖 Automation", "\"Scroll down\", \"Go back\", \"Press home screen\"")
            }
        }

        item {
            SettingsCard(
                title = "Android System Shortcuts",
                icon = Icons.Default.Settings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Wi-Fi", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bluetooth", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accessibility", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioAndAuthTab(
    currentUser: FirebaseUser?,
    onSignOut: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Developer & Studio Info Card
        item {
            SettingsCard(
                title = "Developer & Studio Credits",
                icon = Icons.Default.Business
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("App Developer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Rohim Mandal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Studio Full Name", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "SUPER BIND SAMSTAR MOBILE 35 GEN-Z Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Short Name: SBSSM35GZS",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Application", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Alya Assistant v1.0 (Production Native Android)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // User Account & Authentication
        item {
            SettingsCard(
                title = "Account & Cloud Sync",
                icon = Icons.Default.AccountCircle
            ) {
                if (currentUser != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!currentUser.photoUrl?.toString().isNullOrBlank()) {
                            AsyncImage(
                                model = currentUser.photoUrl,
                                contentDescription = "User Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser.displayName ?: "Authenticated User",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = currentUser.email ?: "No email registered",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "UID: ${currentUser.uid.take(10)}...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun CommandExampleRow(category: String, example: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(category, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Text(example, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryItem,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.category.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = memory.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Memory",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
