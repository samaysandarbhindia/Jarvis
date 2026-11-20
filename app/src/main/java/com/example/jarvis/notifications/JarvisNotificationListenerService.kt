package com.example.jarvis.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.jarvis.database.CapturedNotificationEntity
import com.example.jarvis.database.JarvisDatabase
import com.example.jarvis.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object NotificationAccessBridge {
    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isListenerConnected.value = connected
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }
}

class JarvisNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationAccessBridge.setConnected(true)
    }

    override fun onListenerDisconnected() {
        NotificationAccessBridge.setConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (sbn.isOngoing) return

        scope.launch {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()
            if (!settings.notificationReadingEnabled) return@launch
            if (settings.excludedNotificationPackages.contains(pkg)) return@launch

            val extras = notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return@launch

            if (settings.excludeSensitiveNotifications && isSensitive(notification, title, text)) {
                return@launch
            }

            val appName = runCatching {
                val ai = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(ai).toString()
            }.getOrDefault(pkg.substringAfterLast("."))

            val dao = JarvisDatabase.getInstance(applicationContext).capturedNotificationDao()
            dao.insertNotification(
                CapturedNotificationEntity(
                    packageName = pkg,
                    appName = appName,
                    title = title.ifBlank { appName },
                    text = text,
                    timestamp = sbn.postTime
                )
            )
        }
    }

    private fun isSensitive(notification: Notification, title: String, text: String): Boolean {
        if (notification.visibility == Notification.VISIBILITY_SECRET) return true
        val combined = "$title $text".lowercase()
        val sensitiveKeywords = listOf(
            "otp", "one-time password", "verification code", "2fa",
            "cvv", "pin", "bank", "debited", "credited", "upi", "password"
        )
        return sensitiveKeywords.any { combined.contains(it) }
    }

    override fun onDestroy() {
        scope.cancel()
        NotificationAccessBridge.setConnected(false)
        super.onDestroy()
    }
}
