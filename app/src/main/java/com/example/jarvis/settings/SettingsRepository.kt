package com.example.jarvis.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.jarvis.wakeword.VoiceActivationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jarvis_settings")

data class JarvisSettings(
    // General
    val assistantName: String = "JARVIS",
    val languageTag: String = "en-IN", // Supports English + Hinglish; "hi-IN" for Hindi; "en-US" for US English
    val accentThemeId: String = "ELECTRIC_CYAN",
    val greetingMessage: String = "Good day, sir. JARVIS is online. How may I assist you?",
    val speakGreetingOnLaunch: Boolean = false,
    // Voice & Activation
    val ttsVoiceName: String = "",
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsMuted: Boolean = false,
    val autoListenAfterResponse: Boolean = false,
    val interruptSpeechOnMic: Boolean = true,
    val listeningTimeoutSeconds: Int = 10,
    val offlineSpeechPreferred: Boolean = true,
    val voiceActivationMode: String = VoiceActivationMode.MANUAL_ONLY.id,
    val wakePhrase: String = "JARVIS",
    val wakeWordSensitivity: Float = 0.65f,
    val wakeWordEnabled: Boolean = false,
    // AI Configuration
    val geminiModel: String = "gemini-3.8-flash",
    val personalityStyle: String = "Classic JARVIS",
    val customSystemInstructions: String = "",
    val aiProviderId: String = "FIREBASE_GEMINI", // Modular: FIREBASE_GEMINI or OPENAI_CUSTOM
    // Privacy & Notifications
    val saveConversationsAutomatically: Boolean = true,
    val audioLocalOnlyMode: Boolean = true,
    val notificationReadingEnabled: Boolean = false,
    val excludeSensitiveNotifications: Boolean = true,
    val excludedNotificationPackages: Set<String> = setOf(
        "com.android.systemui",
        "com.google.android.apps.walletnfcrel",
        "com.phonepe.app",
        "net.one97.paytm"
    )
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val GREETING_MESSAGE = stringPreferencesKey("greeting_message")
        val SPEAK_GREETING_ON_LAUNCH = booleanPreferencesKey("speak_greeting_on_launch")

        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val TTS_SPEED = floatPreferencesKey("tts_speed")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_MUTED = booleanPreferencesKey("tts_muted")
        val AUTO_LISTEN = booleanPreferencesKey("auto_listen")
        val INTERRUPT_SPEECH = booleanPreferencesKey("interrupt_speech")
        val LISTENING_TIMEOUT = intPreferencesKey("listening_timeout")
        val OFFLINE_SPEECH_PREFERRED = booleanPreferencesKey("offline_speech_preferred")
        val VOICE_ACTIVATION_MODE = stringPreferencesKey("voice_activation_mode")
        val WAKE_PHRASE = stringPreferencesKey("wake_phrase")
        val WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        val WAKE_ENABLED = booleanPreferencesKey("wake_enabled")

        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val PERSONALITY_STYLE = stringPreferencesKey("personality_style")
        val CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        val AI_PROVIDER_ID = stringPreferencesKey("ai_provider_id")

        val SAVE_CONVERSATIONS = booleanPreferencesKey("save_conversations")
        val AUDIO_LOCAL_ONLY = booleanPreferencesKey("audio_local_only")
        val NOTIFICATION_READING = booleanPreferencesKey("notification_reading")
        val EXCLUDE_SENSITIVE_NOTIFS = booleanPreferencesKey("exclude_sensitive_notifs")
        val EXCLUDED_NOTIF_PACKAGES = stringSetPreferencesKey("excluded_notif_packages")
    }

    val settingsFlow: Flow<JarvisSettings> = context.settingsDataStore.data.map { prefs ->
        readFromPrefs(prefs)
    }

    suspend fun update(transform: (JarvisSettings) -> JarvisSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = readFromPrefs(prefs)
            val next = transform(current)
            prefs[Keys.ASSISTANT_NAME] = next.assistantName
            prefs[Keys.LANGUAGE_TAG] = next.languageTag
            prefs[Keys.ACCENT_THEME] = next.accentThemeId
            prefs[Keys.GREETING_MESSAGE] = next.greetingMessage
            prefs[Keys.SPEAK_GREETING_ON_LAUNCH] = next.speakGreetingOnLaunch
            prefs[Keys.TTS_VOICE_NAME] = next.ttsVoiceName
            prefs[Keys.TTS_SPEED] = next.ttsSpeed.coerceIn(0.5f, 2.0f)
            prefs[Keys.TTS_PITCH] = next.ttsPitch.coerceIn(0.5f, 2.0f)
            prefs[Keys.TTS_MUTED] = next.ttsMuted
            prefs[Keys.AUTO_LISTEN] = next.autoListenAfterResponse
            prefs[Keys.INTERRUPT_SPEECH] = next.interruptSpeechOnMic
            prefs[Keys.LISTENING_TIMEOUT] = next.listeningTimeoutSeconds.coerceIn(3, 30)
            prefs[Keys.OFFLINE_SPEECH_PREFERRED] = next.offlineSpeechPreferred
            prefs[Keys.VOICE_ACTIVATION_MODE] = next.voiceActivationMode
            prefs[Keys.WAKE_PHRASE] = next.wakePhrase.ifBlank { "JARVIS" }
            prefs[Keys.WAKE_SENSITIVITY] = next.wakeWordSensitivity.coerceIn(0.1f, 0.95f)
            prefs[Keys.WAKE_ENABLED] = next.wakeWordEnabled
            prefs[Keys.GEMINI_MODEL] = next.geminiModel
            prefs[Keys.PERSONALITY_STYLE] = next.personalityStyle
            prefs[Keys.CUSTOM_INSTRUCTIONS] = next.customSystemInstructions
            prefs[Keys.AI_PROVIDER_ID] = next.aiProviderId
            prefs[Keys.SAVE_CONVERSATIONS] = next.saveConversationsAutomatically
            prefs[Keys.AUDIO_LOCAL_ONLY] = next.audioLocalOnlyMode
            prefs[Keys.NOTIFICATION_READING] = next.notificationReadingEnabled
            prefs[Keys.EXCLUDE_SENSITIVE_NOTIFS] = next.excludeSensitiveNotifications
            prefs[Keys.EXCLUDED_NOTIF_PACKAGES] = next.excludedNotificationPackages
        }
    }

    private fun readFromPrefs(prefs: Preferences): JarvisSettings = JarvisSettings(
        assistantName = prefs[Keys.ASSISTANT_NAME] ?: "JARVIS",
        languageTag = prefs[Keys.LANGUAGE_TAG] ?: "en-IN",
        accentThemeId = prefs[Keys.ACCENT_THEME] ?: "ELECTRIC_CYAN",
        greetingMessage = prefs[Keys.GREETING_MESSAGE]
            ?: "Good day, sir. JARVIS is online. How may I assist you?",
        speakGreetingOnLaunch = prefs[Keys.SPEAK_GREETING_ON_LAUNCH] ?: false,
        ttsVoiceName = prefs[Keys.TTS_VOICE_NAME] ?: "",
        ttsSpeed = prefs[Keys.TTS_SPEED] ?: 1.0f,
        ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
        ttsMuted = prefs[Keys.TTS_MUTED] ?: false,
        autoListenAfterResponse = prefs[Keys.AUTO_LISTEN] ?: false,
        interruptSpeechOnMic = prefs[Keys.INTERRUPT_SPEECH] ?: true,
        listeningTimeoutSeconds = prefs[Keys.LISTENING_TIMEOUT] ?: 10,
        offlineSpeechPreferred = prefs[Keys.OFFLINE_SPEECH_PREFERRED] ?: true,
        voiceActivationMode = prefs[Keys.VOICE_ACTIVATION_MODE] ?: VoiceActivationMode.MANUAL_ONLY.id,
        wakePhrase = prefs[Keys.WAKE_PHRASE] ?: "JARVIS",
        wakeWordSensitivity = prefs[Keys.WAKE_SENSITIVITY] ?: 0.65f,
        wakeWordEnabled = prefs[Keys.WAKE_ENABLED] ?: false,
        geminiModel = prefs[Keys.GEMINI_MODEL] ?: "gemini-3.8-flash",
        personalityStyle = prefs[Keys.PERSONALITY_STYLE] ?: "Classic JARVIS",
        customSystemInstructions = prefs[Keys.CUSTOM_INSTRUCTIONS] ?: "",
        aiProviderId = prefs[Keys.AI_PROVIDER_ID] ?: "FIREBASE_GEMINI",
        saveConversationsAutomatically = prefs[Keys.SAVE_CONVERSATIONS] ?: true,
        audioLocalOnlyMode = prefs[Keys.AUDIO_LOCAL_ONLY] ?: true,
        notificationReadingEnabled = prefs[Keys.NOTIFICATION_READING] ?: false,
        excludeSensitiveNotifications = prefs[Keys.EXCLUDE_SENSITIVE_NOTIFS] ?: true,
        excludedNotificationPackages = prefs[Keys.EXCLUDED_NOTIF_PACKAGES] ?: setOf(
            "com.android.systemui",
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "net.one97.paytm"
        )
    )
}
