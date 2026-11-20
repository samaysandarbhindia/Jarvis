package com.example.jarvis.ui.status

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.jarvis.database.CapturedNotificationEntity
import com.example.jarvis.device.DeviceTelemetry
import com.example.jarvis.permissions.PermissionStatusSnapshot
import com.example.jarvis.settings.JarvisSettings
import com.example.jarvis.ui.components.GlassHudCard
import com.example.jarvis.voice.TtsVoiceOption
import com.example.jarvis.wakeword.VoiceActivationState
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.StarkAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusScreen(
    telemetry: DeviceTelemetry,
    permissions: PermissionStatusSnapshot,
    settings: JarvisSettings,
    ttsReady: Boolean,
    ttsVoices: List<TtsVoiceOption>,
    voiceActivationState: VoiceActivationState,
    voiceActivationDetail: String,
    notifications: List<CapturedNotificationEntity>,
    onRefreshDiagnostics: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onSetVolume: (String, Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSummarizeNotifications: () -> Unit,
    onReadNotificationsAloud: () -> Unit,
    onClearNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE & DIAGNOSTICS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onRefreshDiagnostics) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh telemetry",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Hardware & Battery Telemetry
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${telemetry.manufacturer} ${telemetry.model}", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android ${telemetry.androidVersion} (API ${telemetry.sdkInt}) · Network: ${telemetry.networkType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary
                )
                Text(
                    text = "RAM: ${telemetry.ramUsedMb} / ${telemetry.ramTotalMb} MB · Storage Free: ${
                        String.format(Locale.US, "%.1f / %.1f GB", telemetry.storageFreeGb, telemetry.storageTotalGb)
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = EmeraldOnline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Battery ${telemetry.battery.percentage}% · ${telemetry.battery.chargingSource} · ${telemetry.battery.temperatureCelsius}°C · ${telemetry.battery.healthLabel}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Native Flashlight & Volume Controls
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FlashlightOn,
                            contentDescription = "Flashlight",
                            tint = if (telemetry.isTorchOn) StarkAmber else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Hardware Flashlight", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = if (telemetry.hasFlashlight) "CameraManager ready" else "No torch hardware detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = HudTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = telemetry.isTorchOn,
                        enabled = telemetry.hasFlashlight,
                        onCheckedChange = { onToggleFlashlight() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Media Volume (${telemetry.mediaVolumePercent}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = telemetry.mediaVolumePercent / 100f,
                    onValueChange = { onSetVolume("media", (it * 100).toInt()) }
                )
                Text("Alarm Volume (${telemetry.alarmVolumePercent}%)", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = telemetry.alarmVolumePercent / 100f,
                    onValueChange = { onSetVolume("alarm", (it * 100).toInt()) }
                )
            }
        }

        // System Diagnostics Matrix
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SUBSYSTEM DIAGNOSTICS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                DiagnosticRow("AI Engine", "Firebase AI Logic (${settings.geminiModel}) + App Check", true)
                DiagnosticRow("Speech Recognition", if (permissions.speechRecognitionAvailable) "Available" else "Unavailable", permissions.speechRecognitionAvailable)
                DiagnosticRow(
                    "On-Device Offline Speech",
                    if (permissions.onDeviceSpeechAvailable) "Supported by system" else "Unavailable (system fallback)",
                    permissions.onDeviceSpeechAvailable
                )
                DiagnosticRow("Text-to-Speech", if (ttsReady) "Ready (${ttsVoices.size} EN/HI voices)" else "Initializing", ttsReady)
                DiagnosticRow(
                    "Voice Activation Service",
                    voiceActivationDetail,
                    voiceActivationState == VoiceActivationState.ACTIVE_OPEN_WAKE_WORD ||
                        voiceActivationState == VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH ||
                        voiceActivationState == VoiceActivationState.STOPPED
                )
                DiagnosticRow(
                    "openWakeWord LiteRT Models",
                    if (permissions.openWakeWordModelsReady) "3 local .tflite models verified" else "Optional (.tflite models not imported)",
                    permissions.openWakeWordModelsReady
                )
                DiagnosticRow("Microphone Permission", if (permissions.microphoneGranted) "Granted" else "Not granted", permissions.microphoneGranted)
                DiagnosticRow("Accessibility Service", if (permissions.accessibilityConnected) "Connected" else "Disabled", permissions.accessibilityConnected)
                DiagnosticRow("Notification Listener", if (permissions.notificationListenerEnabled) "Enabled" else "Disabled", permissions.notificationListenerEnabled)
                DiagnosticRow("Exact Alarms", if (permissions.exactAlarmsGranted) "Allowed" else "Inexact fallback", permissions.exactAlarmsGranted)
                DiagnosticRow("Battery Optimization", if (permissions.ignoringBatteryOptimizations) "Unrestricted" else "Standard Android optimization", permissions.ignoringBatteryOptimizations)
            }
        }

        // Captured Notifications Reader
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NOTIFICATION READER (${notifications.size})", style = MaterialTheme.typography.labelLarge)
                    }
                    if (!permissions.notificationListenerEnabled) {
                        OutlinedButton(onClick = onOpenNotificationSettings) {
                            Text("Enable Access")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSummarizeNotifications,
                        enabled = notifications.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Summarize")
                    }
                    OutlinedButton(
                        onClick = onReadNotificationsAloud,
                        enabled = notifications.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read Aloud")
                    }
                    if (notifications.isNotEmpty()) {
                        IconButton(onClick = onClearNotifications) {
                            Icon(Icons.Default.ClearAll, contentDescription = "Clear notifications", tint = CrimsonAlert)
                        }
                    }
                }
            }
        }

        items(notifications, key = { it.id }) { notif ->
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(notif.appName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notif.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = HudTextSecondary
                    )
                }
                Text(notif.title, style = MaterialTheme.typography.titleMedium)
                if (notif.text.isNotBlank()) {
                    Text(notif.text, style = MaterialTheme.typography.bodyMedium, color = HudTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, detail: String, healthy: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (healthy) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (healthy) "Healthy" else "Attention needed",
            tint = if (healthy) EmeraldOnline else StarkAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = HudTextSecondary)
        }
    }
}
