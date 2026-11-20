package com.example.jarvis.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.jarvis.accessibility.AccessibilityBridge
import com.example.jarvis.notifications.NotificationAccessBridge
import com.example.jarvis.wakeword.OpenWakeWordModelStore

data class PermissionStatusSnapshot(
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean,
    val notificationListenerEnabled: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val speechRecognitionAvailable: Boolean,
    val onDeviceSpeechAvailable: Boolean,
    val openWakeWordModelsReady: Boolean
)

class PermissionHealthChecker(private val context: Context) {

    private val modelStore = OpenWakeWordModelStore(context.applicationContext)

    fun getSnapshot(): PermissionStatusSnapshot {
        val mic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(context.packageName)

        val onDeviceSpeech = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

        return PermissionStatusSnapshot(
            microphoneGranted = mic,
            notificationsGranted = notifs,
            exactAlarmsGranted = exactAlarms,
            accessibilityEnabled = AccessibilityBridge.isServiceEnabledInSystemSettings(context),
            accessibilityConnected = AccessibilityBridge.isConnected.value,
            notificationListenerEnabled = NotificationAccessBridge.isNotificationAccessEnabled(context),
            ignoringBatteryOptimizations = batteryUnrestricted,
            speechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context),
            onDeviceSpeechAvailable = onDeviceSpeech,
            openWakeWordModelsReady = modelStore.getReadiness().allModelsPresent
        )
    }
}
