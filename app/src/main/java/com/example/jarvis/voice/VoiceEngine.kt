package com.example.jarvis.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class TtsVoiceOption(
    val name: String,
    val localeDisplay: String,
    val languageTag: String
)

sealed interface VoiceEvent {
    data class FinalTranscript(val text: String) : VoiceEvent
    data class SpeechError(val message: String) : VoiceEvent
    data object TtsCompleted : VoiceEvent
}

class VoiceEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private val _activeUsingOnDevice = MutableStateFlow(false)
    val activeUsingOnDevice: StateFlow<Boolean> = _activeUsingOnDevice.asStateFlow()

    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VoiceEvent> = _events.asSharedFlow()

    private var timeoutRunnable: Runnable? = null

    val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    val isOnDeviceRecognitionAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    init {
        initTts()
    }

    private fun initTts() {
        mainHandler.post {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    _ttsReady.value = true
                    tts?.setLanguage(Locale.forLanguageTag("en-IN"))
                    refreshVoices()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            _events.tryEmit(VoiceEvent.TtsCompleted)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                        }
                    })
                } else {
                    _ttsReady.value = false
                    Log.e(TAG, "TextToSpeech initialization failed with status=$status")
                }
            }
        }
    }

    private fun refreshVoices() {
        val list = runCatching {
            tts?.voices
                ?.filter {
                    val lang = it.locale.language.lowercase()
                    lang == "en" || lang == "hi"
                }
                ?.sortedWith(compareBy({ it.locale.language }, { it.name }))
                ?.map {
                    TtsVoiceOption(
                        name = it.name,
                        localeDisplay = it.locale.displayName,
                        languageTag = it.locale.toLanguageTag()
                    )
                } ?: emptyList()
        }.getOrDefault(emptyList())
        _availableVoices.value = list
    }

    fun startListening(
        languageTag: String = "en-IN",
        timeoutSeconds: Int = 10,
        preferOffline: Boolean = true
    ) {
        mainHandler.post {
            val useOnDevice = preferOffline && isOnDeviceRecognitionAvailable
            if (!isSpeechRecognitionAvailable && !useOnDevice) {
                _events.tryEmit(
                    VoiceEvent.SpeechError(
                        "Speech recognition service is unavailable on this device."
                    )
                )
                return@post
            }

            cancelTimeout()
            destroyRecognizerInternal()

            try {
                val recognizer = if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    _activeUsingOnDevice.value = true
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
                } else {
                    _activeUsingOnDevice.value = false
                    SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                }
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        cancelTimeout()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // SpeechRecognizer rmsdB roughly spans -2f..10f
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _audioLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _audioLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        cancelTimeout()
                        _isListening.value = false
                        _audioLevel.value = 0f
                        val msg = mapSpeechError(error, languageTag, _activeUsingOnDevice.value)
                        _events.tryEmit(VoiceEvent.SpeechError(msg))
                    }

                    override fun onResults(results: Bundle?) {
                        cancelTimeout()
                        _isListening.value = false
                        _audioLevel.value = 0f
                        val matches = results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        if (best.isNotEmpty()) {
                            _events.tryEmit(VoiceEvent.FinalTranscript(best))
                        } else {
                            _events.tryEmit(VoiceEvent.SpeechError("No speech detected."))
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotEmpty()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                }

                recognizer.startListening(intent)
                _isListening.value = true

                val runnable = Runnable {
                    if (_isListening.value) {
                        stopListening()
                        _events.tryEmit(VoiceEvent.SpeechError("Listening timed out."))
                    }
                }
                timeoutRunnable = runnable
                mainHandler.postDelayed(runnable, timeoutSeconds.coerceIn(3, 30) * 1000L)
            } catch (e: Exception) {
                _isListening.value = false
                _audioLevel.value = 0f
                _events.tryEmit(
                    VoiceEvent.SpeechError(
                        "Failed to start speech recognizer: ${e.localizedMessage}"
                    )
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            cancelTimeout()
            runCatching { speechRecognizer?.stopListening() }
            _isListening.value = false
            _audioLevel.value = 0f
        }
    }

    fun speak(
        text: String,
        languageTag: String = "en-IN",
        voiceName: String = "",
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        muted: Boolean = false
    ) {
        if (muted || text.isBlank()) return
        val cleanText = text
            .replace(Regex("[*_`#>]"), "")
            .replace(Regex("https?://\\S+"), "link attached")
            .trim()
        if (cleanText.isEmpty()) return

        mainHandler.post {
            val engine = tts ?: return@post
            if (!_ttsReady.value) return@post

            val locale = when (languageTag) {
                "hi-IN" -> Locale.forLanguageTag("hi-IN")
                "en-US" -> Locale.US
                else -> Locale.forLanguageTag("en-IN")
            }
            engine.setLanguage(locale)

            if (voiceName.isNotBlank()) {
                engine.voices?.firstOrNull { it.name == voiceName }?.let { chosen ->
                    engine.voice = chosen
                }
            }
            engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

            val utteranceId = UUID.randomUUID().toString()
            engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            runCatching { tts?.stop() }
            _isSpeaking.value = false
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun destroyRecognizerInternal() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    fun shutdown() {
        mainHandler.post {
            cancelTimeout()
            destroyRecognizerInternal()
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
        }
    }

    private fun mapSpeechError(code: Int, languageTag: String, onDevice: Boolean): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client interrupted."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try speaking again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is currently busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input detected before timeout."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            "Speech recognition does not support $languageTag on this device."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            if (onDevice) {
                "Offline speech pack for $languageTag is not downloaded on this device. Download it in system settings or toggle off Offline Speech Preference."
            } else {
                "Speech recognition language pack for $languageTag is currently unavailable."
            }
        else -> "Speech recognition error (code $code)."
    }

    companion object {
        private const val TAG = "VoiceEngine"
    }
}
