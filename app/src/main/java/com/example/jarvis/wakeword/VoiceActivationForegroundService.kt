package com.example.jarvis.wakeword

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Android 14/15/16 compliant microphone foreground service for configurable local voice activation.
 * Never activates automatically without explicit user action, checks RECORD_AUDIO permission,
 * shows a persistent notification while active, and uses modular open-source / Android-native
 * WakeWordEngine implementations with zero paid API keys.
 */
class VoiceActivationForegroundService : Service() {

    private var activeEngine: WakeWordEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEngine()
            VoiceActivationBus.updateState(
                VoiceActivationState.STOPPED,
                "Manual microphone ready — background voice activation stopped"
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val hasMicPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            VoiceActivationBus.updateState(
                VoiceActivationState.PERMISSION_DENIED,
                "Microphone permission is required before starting voice activation."
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val modeId = intent?.getStringExtra(EXTRA_MODE) ?: VoiceActivationMode.MANUAL_ONLY.id
        val mode = VoiceActivationMode.fromId(modeId)
        val wakePhrase = intent?.getStringExtra(EXTRA_WAKE_PHRASE)?.ifBlank { "JARVIS" } ?: "JARVIS"
        val sensitivity = intent?.getFloatExtra(EXTRA_SENSITIVITY, 0.65f)?.coerceIn(0.1f, 0.95f) ?: 0.65f
        val languageTag = intent?.getStringExtra(EXTRA_LANGUAGE_TAG)?.ifBlank { "en-IN" } ?: "en-IN"

        if (mode == VoiceActivationMode.MANUAL_ONLY) {
            stopEngine()
            VoiceActivationBus.updateState(
                VoiceActivationState.STOPPED,
                "Manual microphone mode selected — tap Start Listening to speak"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val candidateEngine: WakeWordEngine = when (mode) {
            VoiceActivationMode.OPEN_WAKE_WORD_TFLITE -> OpenWakeWordTfliteEngine()
            VoiceActivationMode.ON_DEVICE_SPEECH_TRIGGER -> OnDeviceSpeechWakeEngine()
            VoiceActivationMode.MANUAL_ONLY -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val availability = candidateEngine.checkAvailability(applicationContext)
        if (!availability.available) {
            // Do NOT keep microphone open or pretend background wake-word listening is active
            stopEngine()
            VoiceActivationBus.updateState(
                VoiceActivationState.ENGINE_UNAVAILABLE,
                availability.detail
            )
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        val initialNotification = buildNotification("Starting ${candidateEngine.displayName}...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException on Android 14+ if started from restricted state
            Log.e(TAG, "Unable to enter microphone foreground state", e)
            VoiceActivationBus.updateState(
                VoiceActivationState.ERROR,
                "Android blocked background microphone start: ${e.localizedMessage ?: "Open app to activate"}"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        startSelectedEngine(
            engine = candidateEngine,
            config = WakeWordEngineConfig(
                wakePhrase = wakePhrase,
                sensitivity = sensitivity,
                languageTag = languageTag
            )
        )
        return START_NOT_STICKY
    }

    private fun startSelectedEngine(engine: WakeWordEngine, config: WakeWordEngineConfig) {
        stopEngine()
        activeEngine = engine

        engine.start(
            context = applicationContext,
            config = config,
            callbacks = object : WakeWordCallbacks {
                override fun onWakeDetected(event: WakeDetectionEvent) {
                    VoiceActivationBus.emitWakeDetected(event)
                    launchAssistantFromWake(event)
                }

                override fun onAudioLevel(level: Float) {
                    VoiceActivationBus.updateBackgroundAudioLevel(level)
                }

                override fun onStatusChanged(state: VoiceActivationState, detail: String) {
                    VoiceActivationBus.updateState(state, detail)
                    when (state) {
                        VoiceActivationState.ACTIVE_OPEN_WAKE_WORD,
                        VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH -> {
                            updateNotification(detail)
                        }
                        VoiceActivationState.ENGINE_UNAVAILABLE,
                        VoiceActivationState.PERMISSION_DENIED,
                        VoiceActivationState.ERROR,
                        VoiceActivationState.STOPPED -> {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }

                override fun onError(message: String) {
                    VoiceActivationBus.updateState(VoiceActivationState.ERROR, message)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        )
    }

    private fun launchAssistantFromWake(event: WakeDetectionEvent) {
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WAKE_TRIGGERED, true)
            if (!event.trailingCommand.isNullOrBlank()) {
                putExtra(EXTRA_TRAILING_COMMAND, event.trailingCommand)
            }
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun stopEngine() {
        runCatching { activeEngine?.stop() }
        activeEngine = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_word_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.wake_word_channel_description)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceActivationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("JARVIS Voice Activation")
            .setContentText(statusText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        stopEngine()
        val current = VoiceActivationBus.state.value
        if (current == VoiceActivationState.ACTIVE_OPEN_WAKE_WORD ||
            current == VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH
        ) {
            VoiceActivationBus.updateState(
                VoiceActivationState.STOPPED,
                "Manual microphone ready — voice activation stopped"
            )
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VoiceActivationService"
        private const val CHANNEL_ID = "jarvis_voice_activation_channel"
        private const val NOTIFICATION_ID = 4041

        const val ACTION_START = "com.example.jarvis.wakeword.START"
        const val ACTION_STOP = "com.example.jarvis.wakeword.STOP"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_WAKE_PHRASE = "extra_wake_phrase"
        const val EXTRA_SENSITIVITY = "extra_sensitivity"
        const val EXTRA_LANGUAGE_TAG = "extra_language_tag"
        const val EXTRA_WAKE_TRIGGERED = "extra_wake_triggered"
        const val EXTRA_TRAILING_COMMAND = "extra_trailing_command"

        fun startService(
            context: Context,
            mode: VoiceActivationMode,
            wakePhrase: String,
            sensitivity: Float,
            languageTag: String
        ) {
            val intent = Intent(context, VoiceActivationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.id)
                putExtra(EXTRA_WAKE_PHRASE, wakePhrase)
                putExtra(EXTRA_SENSITIVITY, sensitivity)
                putExtra(EXTRA_LANGUAGE_TAG, languageTag)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VoiceActivationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
