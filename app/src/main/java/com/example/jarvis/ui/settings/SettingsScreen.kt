package com.example.jarvis.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.jarvis.permissions.PermissionStatusSnapshot
import com.example.jarvis.settings.JarvisSettings
import com.example.jarvis.ui.components.GlassHudCard
import com.example.jarvis.voice.TtsVoiceOption
import com.example.jarvis.wakeword.OpenWakeWordModelReadiness
import com.example.jarvis.wakeword.OpenWakeWordSlot
import com.example.jarvis.wakeword.VoiceActivationMode
import com.example.jarvis.wakeword.VoiceActivationState
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.JarvisAccentTheme
import com.example.ui.theme.StarkAmber

@Composable
fun SettingsScreen(
    settings: JarvisSettings,
    permissions: PermissionStatusSnapshot,
    ttsVoices: List<TtsVoiceOption>,
    voiceActivationState: VoiceActivationState,
    voiceActivationDetail: String,
    openWakeWordReadiness: OpenWakeWordModelReadiness,
    hasOpenAiKey: Boolean,
    onUpdateSettings: ((JarvisSettings) -> JarvisSettings) -> Unit,
    onSetVoiceActivationEnabled: (Boolean) -> Unit,
    onSetVoiceActivationMode: (VoiceActivationMode) -> Unit,
    onImportOpenWakeWordModel: (OpenWakeWordSlot, Uri) -> Unit,
    onClearOpenWakeWordModels: () -> Unit,
    onMicPermissionDenied: () -> Unit,
    onSaveOpenAiKey: (String) -> Unit,
    onOpenSystemSettings: (String) -> Unit,
    onClearConversations: () -> Unit,
    onClearMemories: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var assistantNameInput by remember(settings.assistantName) { mutableStateOf(settings.assistantName) }
    var greetingInput by remember(settings.greetingMessage) { mutableStateOf(settings.greetingMessage) }
    var wakePhraseInput by remember(settings.wakePhrase) { mutableStateOf(settings.wakePhrase) }
    var customInstrInput by remember(settings.customSystemInstructions) {
        mutableStateOf(settings.customSystemInstructions)
    }
    var openAiInput by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var pendingModelSlot by remember { mutableStateOf<OpenWakeWordSlot?>(null) }

    val wakePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            onSetVoiceActivationEnabled(true)
        } else {
            onMicPermissionDenied()
        }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val slot = pendingModelSlot
        pendingModelSlot = null
        if (uri != null && slot != null) {
            onImportOpenWakeWordModel(slot, uri)
        }
    }

    val currentActivationMode = VoiceActivationMode.fromId(settings.voiceActivationMode)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "SYSTEM CONFIGURATION",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // GENERAL
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("GENERAL")
                OutlinedTextField(
                    value = assistantNameInput,
                    onValueChange = { assistantNameInput = it },
                    label = { Text("Assistant Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = greetingInput,
                    onValueChange = { greetingInput = it },
                    label = { Text("Default Greeting") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                assistantName = assistantNameInput.ifBlank { "JARVIS" },
                                greetingMessage = greetingInput.ifBlank { it.greetingMessage }
                            )
                        }
                    }
                ) {
                    Text("Save Identity")
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Recognition & Voice Language", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "en-IN" to "English / Hinglish",
                        "hi-IN" to "Hindi",
                        "en-US" to "English (US)"
                    ).forEach { (tag, label) ->
                        FilterChip(
                            selected = settings.languageTag == tag,
                            onClick = { onUpdateSettings { it.copy(languageTag = tag) } },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("HUD Accent Theme", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JarvisAccentTheme.entries.forEach { accent ->
                        FilterChip(
                            selected = settings.accentThemeId == accent.id,
                            onClick = { onUpdateSettings { it.copy(accentThemeId = accent.id) } },
                            label = { Text(accent.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                SettingSwitchRow(
                    title = "Speak Greeting on Launch",
                    subtitle = "Announce online status when app opens",
                    checked = settings.speakGreetingOnLaunch,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(speakGreetingOnLaunch = v) } }
                )
            }
        }

        // VOICE & OFFLINE ACTIVATION (NO PROPRIETARY KEYS)
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("VOICE & ACTIVATION")

                SettingSwitchRow(
                    title = "Prefer Offline Speech Recognition",
                    subtitle = if (permissions.onDeviceSpeechAvailable) {
                        "Android on-device SpeechRecognizer supported — runs locally when language pack is installed"
                    } else {
                        "System on-device SpeechRecognizer unavailable; uses standard Android SpeechRecognizer"
                    },
                    checked = settings.offlineSpeechPreferred,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(offlineSpeechPreferred = v) } },
                    testTag = "offline_speech_switch"
                )

                Text("Speech Speed (${String.format("%.2fx", settings.ttsSpeed)})", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.ttsSpeed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(ttsSpeed = v) } }
                )

                Text("Speech Pitch (${String.format("%.2fx", settings.ttsPitch)})", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.ttsPitch,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { v -> onUpdateSettings { it.copy(ttsPitch = v) } }
                )

                Text("Listening Timeout (${settings.listeningTimeoutSeconds}s)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.listeningTimeoutSeconds.toFloat(),
                    valueRange = 3f..30f,
                    onValueChange = { v -> onUpdateSettings { it.copy(listeningTimeoutSeconds = v.toInt()) } }
                )

                SettingSwitchRow(
                    title = "Auto-Listen After Spoken Reply",
                    subtitle = "Automatically reopen microphone after JARVIS finishes speaking",
                    checked = settings.autoListenAfterResponse,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(autoListenAfterResponse = v) } }
                )

                SettingSwitchRow(
                    title = "Interrupt Speech on Mic Tap",
                    subtitle = "Stop TTS immediately when microphone is activated",
                    checked = settings.interruptSpeechOnMic,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(interruptSpeechOnMic = v) } }
                )

                if (ttsVoices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Selected TTS Voice (${ttsVoices.size} available)", style = MaterialTheme.typography.labelMedium)
                    ttsVoices.take(4).forEach { voice ->
                        FilterChip(
                            selected = settings.ttsVoiceName == voice.name,
                            onClick = { onUpdateSettings { it.copy(ttsVoiceName = voice.name) } },
                            label = {
                                Text(
                                    "${voice.localeDisplay} (${voice.name.takeLast(14)})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Activation Mode (Modular · Zero API Keys)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoiceActivationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = currentActivationMode == mode,
                            onClick = { onSetVoiceActivationMode(mode) },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    text = currentActivationMode.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = wakePhraseInput,
                        onValueChange = { wakePhraseInput = it },
                        label = { Text("Wake Phrase (Default: JARVIS)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = wakePhraseInput.isNotBlank(),
                        onClick = {
                            onUpdateSettings { it.copy(wakePhrase = wakePhraseInput.trim()) }
                        }
                    ) {
                        Text("Apply")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Voice Trigger Sensitivity (${(settings.wakeWordSensitivity * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.wakeWordSensitivity,
                    valueRange = 0.1f..0.95f,
                    onValueChange = { v -> onUpdateSettings { it.copy(wakeWordSensitivity = v) } }
                )

                SettingSwitchRow(
                    title = "Background Microphone Service",
                    subtitle = voiceActivationDetail,
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { enable ->
                        if (!enable) {
                            onSetVoiceActivationEnabled(false)
                        } else {
                            val micOk = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (micOk) {
                                onSetVoiceActivationEnabled(true)
                            } else {
                                val perms = buildList {
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }.toTypedArray()
                                wakePermLauncher.launch(perms)
                            }
                        }
                    },
                    testTag = "wake_word_switch"
                )

                val stateHealthy = voiceActivationState == VoiceActivationState.ACTIVE_OPEN_WAKE_WORD ||
                    voiceActivationState == VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (openWakeWordReadiness.allModelsPresent) {
                        "openWakeWord LiteRT (.tflite): All 3 Apache-2.0 models installed locally"
                    } else {
                        "openWakeWord LiteRT (.tflite): Optional — missing ${openWakeWordReadiness.missingLabels().joinToString(", ")}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (openWakeWordReadiness.allModelsPresent || stateHealthy) EmeraldOnline else StarkAmber
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OpenWakeWordSlot.entries.forEach { slot ->
                        val installed = when (slot) {
                            OpenWakeWordSlot.MELSPECTROGRAM -> openWakeWordReadiness.hasMelspectrogram
                            OpenWakeWordSlot.EMBEDDING -> openWakeWordReadiness.hasEmbedding
                            OpenWakeWordSlot.WAKE_WORD -> openWakeWordReadiness.hasWakeWord
                        }
                        OutlinedButton(onClick = {
                            pendingModelSlot = slot
                            modelPickerLauncher.launch(arrayOf("*/*"))
                        }) {
                            Text(
                                text = if (installed) "${slot.fileName} ✓" else "Import ${slot.fileName}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    if (openWakeWordReadiness.hasMelspectrogram ||
                        openWakeWordReadiness.hasEmbedding ||
                        openWakeWordReadiness.hasWakeWord
                    ) {
                        TextButton(onClick = onClearOpenWakeWordModels) {
                            Text("Clear Models", color = CrimsonAlert)
                        }
                    }
                }
            }
        }

        // AI CONFIGURATION
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("AI CONFIGURATION")
                Text(
                    text = "Primary Engine: Firebase AI Logic (Gemini + App Check, zero client API key required).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldOnline
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("AI Provider Module", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.aiProviderId == "FIREBASE_GEMINI",
                        onClick = { onUpdateSettings { it.copy(aiProviderId = "FIREBASE_GEMINI") } },
                        label = { Text("Gemini (Firebase)") }
                    )
                    FilterChip(
                        selected = settings.aiProviderId == "OPENAI_CUSTOM",
                        onClick = { onUpdateSettings { it.copy(aiProviderId = "OPENAI_CUSTOM") } },
                        label = { Text("OpenAI (Modular)") }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Gemini Model", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon").forEach { model ->
                        FilterChip(
                            selected = settings.geminiModel == model,
                            onClick = { onUpdateSettings { it.copy(geminiModel = model) } },
                            label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text("Personality Style", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Classic JARVIS", "Concise Tactical", "Friendly Bilingual").forEach { style ->
                        FilterChip(
                            selected = settings.personalityStyle == style,
                            onClick = { onUpdateSettings { it.copy(personalityStyle = style) } },
                            label = { Text(style, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customInstrInput,
                    onValueChange = { customInstrInput = it },
                    label = { Text("Custom System Instructions") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(onClick = {
                    onUpdateSettings { it.copy(customSystemInstructions = customInstrInput.trim()) }
                }) {
                    Text("Apply Instructions")
                }

                if (settings.aiProviderId == "OPENAI_CUSTOM") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (hasOpenAiKey) "OpenAI Key: Stored in Android Keystore" else "OpenAI Key: Not set",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasOpenAiKey) EmeraldOnline else StarkAmber
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = openAiInput,
                            onValueChange = { openAiInput = it },
                            label = { Text("OpenAI API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            enabled = openAiInput.isNotBlank(),
                            onClick = {
                                onSaveOpenAiKey(openAiInput)
                                openAiInput = ""
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        // ANDROID PERMISSIONS & RESTRICTIONS
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("ANDROID PERMISSIONS & SERVICES")
                PermissionActionRow(
                    title = "Accessibility Automation Service",
                    status = if (permissions.accessibilityConnected) "Connected" else "Disabled",
                    onClick = { onOpenSystemSettings("accessibility") }
                )
                PermissionActionRow(
                    title = "Notification Listener Access",
                    status = if (permissions.notificationListenerEnabled) "Granted" else "Disabled",
                    onClick = { onOpenSystemSettings("notifications") }
                )
                PermissionActionRow(
                    title = "Exact Alarm Scheduling",
                    status = if (permissions.exactAlarmsGranted) "Allowed" else "Inexact fallback",
                    onClick = { onOpenSystemSettings("exact_alarm") }
                )
                PermissionActionRow(
                    title = "Battery Optimization Exemption",
                    status = if (permissions.ignoringBatteryOptimizations) "Unrestricted" else "Battery optimized (tap to manage)",
                    onClick = { onOpenSystemSettings("battery") }
                )
                PermissionActionRow(
                    title = "App Permissions & Info",
                    status = if (permissions.microphoneGranted) "Microphone Granted" else "Microphone Needed",
                    onClick = { onOpenSystemSettings("app") }
                )
                Text(
                    text = "Note: Android 14/15+ restricts background microphone usage with a persistent system indicator and foreground notification, and requires explicit user activation from the foreground app.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HudTextSecondary
                )
            }
        }

        // PRIVACY & DATA
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                SectionHeader("PRIVACY & DATA CONTROLS")
                SettingSwitchRow(
                    title = "Save Conversations Automatically",
                    subtitle = "Disable for ephemeral private sessions that are not persisted to Room",
                    checked = settings.saveConversationsAutomatically,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(saveConversationsAutomatically = v) } }
                )
                SettingSwitchRow(
                    title = "Enable Notification Reading",
                    subtitle = "Opt-in local capture of non-sensitive notifications",
                    checked = settings.notificationReadingEnabled,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(notificationReadingEnabled = v) } }
                )
                SettingSwitchRow(
                    title = "Exclude Sensitive & Banking Alerts",
                    subtitle = "Automatically skip OTP, verification codes, and financial notifications",
                    checked = settings.excludeSensitiveNotifications,
                    onCheckedChange = { v -> onUpdateSettings { it.copy(excludeSensitiveNotifications = v) } }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmAction = "CONVERSATIONS" }) {
                        Text("Clear Chats", color = CrimsonAlert)
                    }
                    OutlinedButton(onClick = { confirmAction = "MEMORIES" }) {
                        Text("Clear Memories", color = CrimsonAlert)
                    }
                }
            }
        }
    }

    confirmAction?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Confirm Permanent Deletion") },
            text = {
                Text(
                    if (kind == "CONVERSATIONS") "Delete all saved conversation sessions from Room?"
                    else "Delete all saved personal memories and preferences from Room?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (kind == "CONVERSATIONS") onClearConversations() else onClearMemories()
                    confirmAction = null
                }) {
                    Text("Delete", color = CrimsonAlert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    status: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
        }
        OutlinedButton(onClick = onClick) {
            Text("Manage")
        }
    }
}
