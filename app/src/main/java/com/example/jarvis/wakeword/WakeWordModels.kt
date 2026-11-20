package com.example.jarvis.wakeword

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceActivationMode(
    val id: String,
    val label: String,
    val description: String
) {
    MANUAL_ONLY(
        id = "MANUAL_ONLY",
        label = "Manual Mic",
        description = "Reliable tap-to-listen activation via the large microphone button. Zero background mic usage."
    ),
    ON_DEVICE_SPEECH_TRIGGER(
        id = "ON_DEVICE_SPEECH_TRIGGER",
        label = "On-Device Trigger",
        description = "Uses local AudioRecord RMS energy + Android 12+ on-device SpeechRecognizer to detect \"Hey JARVIS\" without cloud audio streaming."
    ),
    OPEN_WAKE_WORD_TFLITE(
        id = "OPEN_WAKE_WORD_TFLITE",
        label = "openWakeWord (LiteRT)",
        description = "Runs the open-source Apache-2.0 openWakeWord 3-stage .tflite pipeline locally on-device with no API keys."
    );

    companion object {
        fun fromId(id: String): VoiceActivationMode =
            entries.firstOrNull { it.id == id } ?: MANUAL_ONLY
    }
}

enum class VoiceActivationState {
    STOPPED,
    ACTIVE_OPEN_WAKE_WORD,
    ACTIVE_ON_DEVICE_SPEECH,
    ENGINE_UNAVAILABLE,
    PERMISSION_DENIED,
    ERROR
}

data class WakeDetectionEvent(
    val detectedPhrase: String,
    val trailingCommand: String? = null,
    val engineId: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

object VoiceActivationBus {
    private val _state = MutableStateFlow(VoiceActivationState.STOPPED)
    val state: StateFlow<VoiceActivationState> = _state.asStateFlow()

    private val _statusDetail = MutableStateFlow("Manual microphone ready — tap Start Listening to speak")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _backgroundAudioLevel = MutableStateFlow(0f)
    val backgroundAudioLevel: StateFlow<Float> = _backgroundAudioLevel.asStateFlow()

    private val _wakeEvents = MutableSharedFlow<WakeDetectionEvent>(extraBufferCapacity = 8)
    val wakeEvents: SharedFlow<WakeDetectionEvent> = _wakeEvents.asSharedFlow()

    fun updateState(state: VoiceActivationState, detail: String) {
        _state.value = state
        _statusDetail.value = detail
        if (state != VoiceActivationState.ACTIVE_OPEN_WAKE_WORD &&
            state != VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH
        ) {
            _backgroundAudioLevel.value = 0f
        }
    }

    fun updateBackgroundAudioLevel(level: Float) {
        _backgroundAudioLevel.value = level.coerceIn(0f, 1f)
    }

    fun emitWakeDetected(event: WakeDetectionEvent) {
        _wakeEvents.tryEmit(event)
    }
}
