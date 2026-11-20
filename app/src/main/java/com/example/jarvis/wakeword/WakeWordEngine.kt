package com.example.jarvis.wakeword

import android.content.Context

data class EngineAvailability(
    val available: Boolean,
    val detail: String
)

data class WakeWordEngineConfig(
    val wakePhrase: String = "JARVIS",
    val sensitivity: Float = 0.65f,
    val languageTag: String = "en-IN"
)

interface WakeWordCallbacks {
    fun onWakeDetected(event: WakeDetectionEvent)
    fun onAudioLevel(level: Float)
    fun onStatusChanged(state: VoiceActivationState, detail: String)
    fun onError(message: String)
}

/**
 * Modular offline wake-word engine abstraction so open-source local detectors
 * (such as openWakeWord LiteRT, Android on-device SpeechRecognizer trigger, or future TFLite/ONNX modules)
 * can be swapped cleanly without coupling to UI or service code.
 */
interface WakeWordEngine {
    val engineId: String
    val displayName: String

    fun checkAvailability(context: Context): EngineAvailability
    fun start(context: Context, config: WakeWordEngineConfig, callbacks: WakeWordCallbacks)
    fun stop()
}

object WakeWordPhraseMatcher {

    fun match(transcript: String, wakePhrase: String = "JARVIS"): WakeDetectionEvent? {
        val raw = transcript.trim()
        if (raw.isEmpty()) return null

        val target = wakePhrase.trim().lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
            .ifEmpty { "jarvis" }

        val normalized = raw.lowercase()
            .replace(Regex("[,.;:!?]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val prefixes = listOf(
            "hey $target",
            "ok $target",
            "okay $target",
            "hi $target",
            "hello $target",
            target
        )

        for (prefix in prefixes) {
            if (normalized == prefix) {
                return WakeDetectionEvent(
                    detectedPhrase = prefix,
                    trailingCommand = null
                )
            }
            if (normalized.startsWith("$prefix ")) {
                val remainder = normalized.removePrefix(prefix).trim()
                return WakeDetectionEvent(
                    detectedPhrase = prefix,
                    trailingCommand = remainder.ifBlank { null }
                )
            }
        }

        // Also match if user addressed JARVIS mid-utterance (e.g. "please hey jarvis open youtube")
        val regex = Regex("""\b(?:hey\s+|ok\s+|okay\s+|hi\s+)?${Regex.escape(target)}\b""")
        val found = regex.find(normalized) ?: return null
        val after = normalized.substring(found.range.last + 1).trim()
        return WakeDetectionEvent(
            detectedPhrase = found.value.trim(),
            trailingCommand = after.ifBlank { null }
        )
    }
}
