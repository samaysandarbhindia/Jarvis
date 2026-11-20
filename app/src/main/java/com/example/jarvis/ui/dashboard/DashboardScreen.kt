package com.example.jarvis.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.jarvis.ai.AssistantState
import com.example.jarvis.ai.PendingConfirmation
import com.example.jarvis.device.DeviceTelemetry
import com.example.jarvis.settings.JarvisSettings
import com.example.jarvis.ui.components.ConfirmationBannerCard
import com.example.jarvis.ui.components.GlassHudCard
import com.example.jarvis.ui.components.JarvisCoreVisualizer
import com.example.jarvis.wakeword.VoiceActivationState
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisPanelNavy
import com.example.ui.theme.StarkAmber
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    settings: JarvisSettings,
    assistantState: AssistantState,
    statusSubtitle: String,
    streamingResponse: String,
    partialTranscript: String,
    audioLevel: Float,
    isListening: Boolean,
    isSpeaking: Boolean,
    telemetry: DeviceTelemetry,
    isTorchOn: Boolean,
    voiceActivationState: VoiceActivationState,
    errorBanner: String?,
    pendingConfirmations: List<PendingConfirmation>,
    onToggleMic: () -> Unit,
    onMicPermissionDenied: () -> Unit,
    onStopSpeaking: () -> Unit,
    onToggleMute: () -> Unit,
    onSubmitPrompt: (String) -> Unit,
    onConfirmAction: (PendingConfirmation) -> Unit,
    onDismissAction: (PendingConfirmation) -> Unit,
    onDismissError: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenReminders: () -> Unit,
    onLaunchYouTube: () -> Unit,
    onToggleFlashlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var commandText by remember { mutableStateOf("") }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleMic()
        } else {
            onMicPermissionDenied()
        }
    }

    val dateStr = remember(nowMillis / 60_000L) {
        SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date(nowMillis))
    }
    val timeStr = remember(nowMillis / 1000L) {
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(nowMillis))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Telemetry HUD Bar
            GlassHudCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = JarvisPanelNavy.copy(alpha = 0.72f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dateStr.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Battery Telemetry Badge
                        TelemetryBadge(
                            icon = if (telemetry.battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                            label = "${telemetry.battery.percentage}%",
                            tint = if (telemetry.battery.percentage <= 15) CrimsonAlert else EmeraldOnline
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Network Telemetry Badge
                        TelemetryBadge(
                            icon = if (telemetry.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            label = telemetry.networkType,
                            tint = if (telemetry.isConnected) ElectricCyan else StarkAmber
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dashboard_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open JARVIS Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Wake-Word & Conversation Shortcut Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val wakeLabel = when (voiceActivationState) {
                        VoiceActivationState.ACTIVE_OPEN_WAKE_WORD -> "VOICE: OPENWAKEWORD ON"
                        VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH -> "VOICE: ON-DEVICE TRIGGER"
                        VoiceActivationState.PERMISSION_DENIED -> "VOICE: MIC PERMISSION NEEDED"
                        VoiceActivationState.ENGINE_UNAVAILABLE -> "VOICE: MANUAL MIC READY"
                        VoiceActivationState.ERROR -> "VOICE: SERVICE ALERT"
                        VoiceActivationState.STOPPED -> "VOICE: MANUAL MIC READY"
                    }
                    val wakeTint = when (voiceActivationState) {
                        VoiceActivationState.ACTIVE_OPEN_WAKE_WORD,
                        VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH -> EmeraldOnline
                        VoiceActivationState.PERMISSION_DENIED,
                        VoiceActivationState.ERROR -> CrimsonAlert
                        else -> ElectricCyan
                    }
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(8.dp),
                        color = JarvisPanelElevated.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, wakeTint.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Wake-word service status",
                                tint = wakeTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = wakeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = wakeTint
                            )
                        }
                    }

                    TextButton(
                        onClick = onOpenConversations,
                        modifier = Modifier.testTag("dashboard_conversations_shortcut")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Open Conversations",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Conversations", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Animated JARVIS AI Core + Circular Waveform
            JarvisCoreVisualizer(
                assistantState = assistantState,
                audioLevel = audioLevel,
                assistantName = settings.assistantName,
                coreSize = 228.dp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Error Banner (transparent real errors)
            AnimatedVisibility(visible = !errorBanner.isNullOrBlank()) {
                GlassHudCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    borderColor = CrimsonAlert
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorBanner.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonAlert,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissError) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // Pending sensitive confirmations
            pendingConfirmations.forEach { pending ->
                ConfirmationBannerCard(
                    pending = pending,
                    onConfirm = { onConfirmAction(pending) },
                    onCancel = { onDismissAction(pending) },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Live Transcript / Assistant Response Readout
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                val readout = when {
                    isListening && partialTranscript.isNotBlank() -> "\"$partialTranscript\""
                    streamingResponse.isNotBlank() -> streamingResponse
                    else -> statusSubtitle
                }
                Text(
                    text = readout,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Action Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onLaunchYouTube,
                    label = { Text("YouTube") },
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = JarvisPanelNavy,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                AssistChip(
                    onClick = onToggleFlashlight,
                    label = { Text(if (isTorchOn) "Flash ON" else "Flash OFF") },
                    leadingIcon = {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = null,
                            tint = if (isTorchOn) StarkAmber else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = JarvisPanelNavy,
                        labelColor = if (isTorchOn) StarkAmber else MaterialTheme.colorScheme.onSurface
                    )
                )
                AssistChip(
                    onClick = onOpenReminders,
                    label = { Text("Reminders") },
                    leadingIcon = {
                        Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = JarvisPanelNavy,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                AssistChip(
                    onClick = onOpenDiagnostics,
                    label = { Text("Diagnostics") },
                    leadingIcon = {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = JarvisPanelNavy,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Voice Controls Row (Mute, Large Manual Start Listening Mic, Interrupt Speech)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (settings.ttsMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (settings.ttsMuted) "Unmute JARVIS voice" else "Mute JARVIS voice",
                            tint = if (settings.ttsMuted) StarkAmber else HudTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledIconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                onToggleMic()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) CrimsonAlert else MaterialTheme.colorScheme.primary,
                            contentColor = JarvisDeepBlack
                        ),
                        modifier = Modifier
                            .size(76.dp)
                            .testTag("mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop listening" else "Start listening",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    if (isSpeaking) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("interrupt_speech_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Interrupt JARVIS speech",
                                tint = CrimsonAlert
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isListening) "Listening — Tap to Stop" else "Start Listening",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isListening) CrimsonAlert else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text Command Input Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input"),
                    placeholder = {
                        Text("Command JARVIS in English, Hindi, or Hinglish...")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandText.isNotBlank()) {
                                onSubmitPrompt(commandText)
                                commandText = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = JarvisPanelNavy.copy(alpha = 0.8f),
                        unfocusedContainerColor = JarvisPanelNavy.copy(alpha = 0.6f)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            onSubmitPrompt(commandText)
                            commandText = ""
                        }
                    },
                    enabled = commandText.isNotBlank(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = JarvisDeepBlack
                    ),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command"
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = JarvisPanelElevated.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}
