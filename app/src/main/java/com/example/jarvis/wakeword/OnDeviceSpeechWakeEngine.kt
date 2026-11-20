package com.example.jarvis.wakeword

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Android-native offline trigger using:
 * 1) Local AudioRecord RMS voice activity detection (no audio leaves device).
 * 2) Android 12+ (API 31+) SpeechRecognizer.createOnDeviceSpeechRecognizer when speech energy rises,
 *    checking locally for the configured wake phrase ("JARVIS" / "Hey JARVIS").
 *
 * If on-device speech recognition is unsupported by the device/OS, checkAvailability() returns false
 * so audio is never streamed continuously to the cloud.
 */
class OnDeviceSpeechWakeEngine : WakeWordEngine {

    override val engineId: String = VoiceActivationMode.ON_DEVICE_SPEECH_TRIGGER.id
    override val displayName: String = "Android On-Device Speech Trigger"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var monitorJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onDeviceRecognizer: SpeechRecognizer? = null
    @Volatile
    private var recognitionInFlight = false

    override fun checkAvailability(context: Context): EngineAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return EngineAvailability(
                available = false,
                detail = "On-device wake trigger requires Android 12+ (API 31+). Use Manual Mic or import openWakeWord .tflite models."
            )
        }
        val onDeviceSupported = runCatching {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        }.getOrDefault(false)

        return if (onDeviceSupported) {
            EngineAvailability(
                available = true,
                detail = "Android on-device SpeechRecognizer available (no cloud streaming)"
            )
        } else {
            EngineAvailability(
                available = false,
                detail = "Device lacks an installed Android on-device SpeechRecognizer pack. Use Manual Mic or import openWakeWord .tflite models."
            )
        }
    }

    override fun start(
        context: Context,
        config: WakeWordEngineConfig,
        callbacks: WakeWordCallbacks
    ) {
        stop()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callbacks.onStatusChanged(
                VoiceActivationState.PERMISSION_DENIED,
                "Microphone permission is required for voice activation."
            )
            return
        }

        val availability = checkAvailability(context)
        if (!availability.available) {
            callbacks.onStatusChanged(VoiceActivationState.ENGINE_UNAVAILABLE, availability.detail)
            return
        }

        val sampleRate = 16_000
        val chunkSamples = 1_600 // 100ms
        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(chunkSamples * 2 * 4)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufBytes
            )
        } catch (e: SecurityException) {
            callbacks.onStatusChanged(
                VoiceActivationState.PERMISSION_DENIED,
                "Microphone permission denied."
            )
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            callbacks.onError("AudioRecord could not initialize microphone stream.")
            return
        }

        audioRecord = recorder
        recorder.startRecording()

        // Higher sensitivity -> lower RMS threshold needed to trigger local recognizer check
        val rmsTriggerLevel = (0.28f - config.sensitivity.coerceIn(0.1f, 0.95f) * 0.20f)
            .coerceIn(0.05f, 0.26f)

        callbacks.onStatusChanged(
            VoiceActivationState.ACTIVE_ON_DEVICE_SPEECH,
            "On-device trigger listening for \"Hey ${config.wakePhrase}\" (offline)"
        )

        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = runScope
        monitorJob = runScope.launch {
            val pcm = ShortArray(chunkSamples)
            var lastRecognitionAt = 0L
            while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                if (recognitionInFlight) {
                    kotlinx.coroutines.delay(150L)
                    continue
                }
                val read = recorder.read(pcm, 0, chunkSamples, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                var sumSq = 0.0
                for (i in 0 until read) {
                    val s = pcm[i].toDouble()
                    sumSq += s * s
                }
                val rmsNorm = ((sqrt(sumSq / read) / 32768.0) * 6.0).toFloat().coerceIn(0f, 1f)
                callbacks.onAudioLevel(rmsNorm)

                val now = System.currentTimeMillis()
                if (rmsNorm >= rmsTriggerLevel && now - lastRecognitionAt > 2_000L) {
                    lastRecognitionAt = now
                    triggerLocalRecognizerPass(context.applicationContext, recorder, config, callbacks)
                }
            }
        }
    }

    private fun triggerLocalRecognizerPass(
        appContext: Context,
        recorder: AudioRecord,
        config: WakeWordEngineConfig,
        callbacks: WakeWordCallbacks
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        recognitionInFlight = true
        // Pause AudioRecord briefly so SpeechRecognizer has exclusive mic access on single-stream devices
        runCatching { recorder.stop() }

        mainHandler.post {
            try {
                destroyRecognizer()
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                onDeviceRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) {
                        callbacks.onAudioLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        Log.d(TAG, "On-device trigger pass ended with code=$error")
                        resumeVadMonitor(recorder)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        var detected: WakeDetectionEvent? = null
                        for (candidate in matches) {
                            detected = WakeWordPhraseMatcher.match(candidate, config.wakePhrase)
                            if (detected != null) break
                        }
                        if (detected != null) {
                            callbacks.onWakeDetected(detected.copy(engineId = engineId))
                        }
                        resumeVadMonitor(recorder)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        val matched = WakeWordPhraseMatcher.match(partial, config.wakePhrase)
                        if (matched != null && matched.trailingCommand == null) {
                            // Wait briefly or trigger if exact wake phrase only
                            destroyRecognizer()
                            callbacks.onWakeDetected(matched.copy(engineId = engineId))
                            resumeVadMonitor(recorder)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageTag)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed on-device trigger pass", e)
                resumeVadMonitor(recorder)
            }
        }
    }

    private fun resumeVadMonitor(recorder: AudioRecord) {
        destroyRecognizer()
        runCatching {
            if (scope != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording()
            }
        }
        recognitionInFlight = false
    }

    private fun destroyRecognizer() {
        runCatching {
            onDeviceRecognizer?.cancel()
            onDeviceRecognizer?.destroy()
        }
        onDeviceRecognizer = null
    }

    override fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        scope?.cancel()
        scope = null

        mainHandler.post { destroyRecognizer() }
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        recognitionInFlight = false
    }

    companion object {
        private const val TAG = "OnDeviceSpeechWake"
    }
}
