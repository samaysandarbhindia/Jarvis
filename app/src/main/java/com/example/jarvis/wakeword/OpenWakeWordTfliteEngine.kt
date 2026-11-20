package com.example.jarvis.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

enum class OpenWakeWordSlot(val fileName: String, val label: String) {
    MELSPECTROGRAM("melspectrogram.tflite", "Melspectrogram (.tflite)"),
    EMBEDDING("embedding_model.tflite", "Speech Embedding (.tflite)"),
    WAKE_WORD("hey_jarvis.tflite", "Wake-Word Classifier (hey_jarvis.tflite)")
}

data class OpenWakeWordModelReadiness(
    val hasMelspectrogram: Boolean,
    val hasEmbedding: Boolean,
    val hasWakeWord: Boolean
) {
    val allModelsPresent: Boolean
        get() = hasMelspectrogram && hasEmbedding && hasWakeWord

    fun missingLabels(): List<String> = buildList {
        if (!hasMelspectrogram) add(OpenWakeWordSlot.MELSPECTROGRAM.fileName)
        if (!hasEmbedding) add(OpenWakeWordSlot.EMBEDDING.fileName)
        if (!hasWakeWord) add(OpenWakeWordSlot.WAKE_WORD.fileName)
    }
}

/**
 * Manages optional local Apache-2.0 openWakeWord TensorFlow Lite (.tflite) models
 * stored in app-private storage (imported by the user via Android Storage Access Framework).
 */
class OpenWakeWordModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "wakeword_models")

    fun getModelFile(slot: OpenWakeWordSlot): File {
        if (!modelsDir.exists()) modelsDir.mkdirs()
        return File(modelsDir, slot.fileName)
    }

    fun getReadiness(): OpenWakeWordModelReadiness {
        return OpenWakeWordModelReadiness(
            hasMelspectrogram = isValidModelFile(getModelFile(OpenWakeWordSlot.MELSPECTROGRAM)),
            hasEmbedding = isValidModelFile(getModelFile(OpenWakeWordSlot.EMBEDDING)),
            hasWakeWord = isValidModelFile(getModelFile(OpenWakeWordSlot.WAKE_WORD))
        )
    }

    fun importModel(slot: OpenWakeWordSlot, sourceUri: Uri): Result<String> {
        return runCatching {
            val tempFile = File(appContext.cacheDir, "tmp_${slot.fileName}")
            appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: error("Could not read selected file.")

            if (tempFile.length() < 1024L) {
                tempFile.delete()
                error("Selected file is too small to be a valid .tflite model.")
            }

            // Verify LiteRT can actually parse and allocate tensors for this .tflite flatbuffer
            Interpreter(tempFile, Interpreter.Options().apply { numThreads = 2 }).use { interpreter ->
                require(interpreter.inputTensorCount >= 1 && interpreter.outputTensorCount >= 1) {
                    "Invalid TFLite model tensor layout."
                }
            }

            val target = getModelFile(slot)
            tempFile.copyTo(target, overwrite = true)
            tempFile.delete()
            "Verified & imported ${slot.fileName} (${target.length() / 1024} KB)"
        }
    }

    fun clearAllModels() {
        OpenWakeWordSlot.entries.forEach { slot ->
            runCatching { getModelFile(slot).delete() }
        }
    }

    private fun isValidModelFile(file: File): Boolean = file.exists() && file.length() > 1024L
}

/**
 * Real 3-stage openWakeWord LiteRT (TensorFlow Lite, Apache-2.0) inference engine:
 *   16kHz PCM AudioRecord (1280 samples / 80ms)
 *     -> melspectrogram.tflite ([1, 1280] float32 in int16 scale -> mel frames x 32)
 *     -> 76-frame sliding mel window -> embedding_model.tflite ([1, 76, 32, 1] -> 96-dim embedding)
 *     -> 16-frame embedding buffer -> hey_jarvis.tflite ([1, 16, 96] -> score in [0..1])
 *
 * Requires the 3 open-source .tflite files to be imported locally; never fakes detection.
 */
class OpenWakeWordTfliteEngine : WakeWordEngine {

    override val engineId: String = VoiceActivationMode.OPEN_WAKE_WORD_TFLITE.id
    override val displayName: String = "openWakeWord (LiteRT .tflite)"

    private var scope: CoroutineScope? = null
    private var workerJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private var melInterpreter: Interpreter? = null
    private var embInterpreter: Interpreter? = null
    private var wakeInterpreter: Interpreter? = null

    override fun checkAvailability(context: Context): EngineAvailability {
        val store = OpenWakeWordModelStore(context)
        val readiness = store.getReadiness()
        return if (readiness.allModelsPresent) {
            EngineAvailability(
                available = true,
                detail = "openWakeWord LiteRT models verified and ready"
            )
        } else {
            EngineAvailability(
                available = false,
                detail = "Optional openWakeWord .tflite models missing (${readiness.missingLabels().joinToString(", ")}). Import them in Config or use Manual Mic."
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
                "Microphone permission is required for openWakeWord detection."
            )
            return
        }

        val store = OpenWakeWordModelStore(context)
        val readiness = store.getReadiness()
        if (!readiness.allModelsPresent) {
            callbacks.onStatusChanged(
                VoiceActivationState.ENGINE_UNAVAILABLE,
                "Missing local models: ${readiness.missingLabels().joinToString(", ")}"
            )
            return
        }

        try {
            val options = Interpreter.Options().apply { numThreads = 2 }
            melInterpreter = Interpreter(store.getModelFile(OpenWakeWordSlot.MELSPECTROGRAM), options)
            embInterpreter = Interpreter(store.getModelFile(OpenWakeWordSlot.EMBEDDING), options)
            wakeInterpreter = Interpreter(store.getModelFile(OpenWakeWordSlot.WAKE_WORD), options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LiteRT interpreters", e)
            closeInterpreters()
            callbacks.onError("Failed to load openWakeWord LiteRT models: ${e.localizedMessage}")
            return
        }

        val sampleRate = 16_000
        val chunkSamples = 1_280 // 80ms frame expected by openWakeWord
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
            closeInterpreters()
            callbacks.onStatusChanged(
                VoiceActivationState.PERMISSION_DENIED,
                "Microphone access denied by system."
            )
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            closeInterpreters()
            callbacks.onError("AudioRecord could not initialize 16kHz microphone capture.")
            return
        }

        audioRecord = recorder
        recorder.startRecording()

        val threshold = (0.85f - config.sensitivity.coerceIn(0.1f, 0.95f) * 0.55f)
            .coerceIn(0.25f, 0.85f)

        callbacks.onStatusChanged(
            VoiceActivationState.ACTIVE_OPEN_WAKE_WORD,
            "openWakeWord LiteRT listening locally for \"${config.wakePhrase}\" (threshold ${"%.2f".format(threshold)})"
        )

        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = runScope
        workerJob = runScope.launch {
            runInferenceLoop(
                recorder = recorder,
                chunkSamples = chunkSamples,
                wakePhrase = config.wakePhrase,
                threshold = threshold,
                callbacks = callbacks
            )
        }
    }

    private fun CoroutineScope.runInferenceLoop(
        recorder: AudioRecord,
        chunkSamples: Int,
        wakePhrase: String,
        threshold: Float,
        callbacks: WakeWordCallbacks
    ) {
        val pcmBuffer = ShortArray(chunkSamples)
        val melBands = 32
        val embWindowFrames = 76
        val embStepFrames = 8
        val embDim = 96
        val wakeEmbeddingFrames = 16

        // Rolling mel frames buffer (max ~200 frames)
        val melHistory = ArrayDeque<FloatArray>(200)
        // Rolling speech embedding history (16 frames x 96 features)
        val embHistory = ArrayDeque<FloatArray>(wakeEmbeddingFrames + 4)
        var melFramesSinceLastEmb = 0
        var lastTriggerMs = 0L

        val melInputBuffer = ByteBuffer.allocateDirect(chunkSamples * 4).order(ByteOrder.nativeOrder())

        while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = recorder.read(pcmBuffer, 0, chunkSamples, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue

            // Compute normalized RMS level for UI ring telemetry
            var sumSq = 0.0
            melInputBuffer.clear()
            for (i in 0 until chunkSamples) {
                val sample = if (i < read) pcmBuffer[i].toFloat() else 0f
                if (i < read) sumSq += sample * sample
                // openWakeWord melspectrogram.tflite takes unnormalized int16-range float32 PCM
                melInputBuffer.putFloat(sample)
            }
            val rms = sqrt(sumSq / read.coerceAtLeast(1)) / 32768.0
            callbacks.onAudioLevel((rms * 6.0).toFloat().coerceIn(0f, 1f))

            val mel = melInterpreter ?: break
            val emb = embInterpreter ?: break
            val wake = wakeInterpreter ?: break

            try {
                // Stage 1: Melspectrogram
                val melOutShape = mel.getOutputTensor(0).shape() // e.g. [1, 1, 5, 32] or [1, 5, 32]
                val melOutCount = melOutShape.fold(1) { acc, d -> acc * d.coerceAtLeast(1) }
                val melOutBuf = ByteBuffer.allocateDirect(melOutCount * 4).order(ByteOrder.nativeOrder())
                mel.run(melInputBuffer, melOutBuf)
                melOutBuf.rewind()

                val frameCount = (melOutCount / melBands).coerceAtLeast(1)
                for (f in 0 until frameCount) {
                    val bandRow = FloatArray(melBands)
                    for (b in 0 until melBands) {
                        // openWakeWord normalization: mel / 10 + 2
                        val rawMel = melOutBuf.float
                        bandRow[b] = (rawMel / 10f) + 2f
                    }
                    melHistory.addLast(bandRow)
                    while (melHistory.size > 200) melHistory.removeFirst()
                    melFramesSinceLastEmb++
                }

                // Stage 2: Embedding model every 8 new mel frames once 76 frames exist
                if (melHistory.size >= embWindowFrames && melFramesSinceLastEmb >= embStepFrames) {
                    melFramesSinceLastEmb = 0
                    val window = melHistory.toList().takeLast(embWindowFrames)
                    val embInBuf = ByteBuffer.allocateDirect(embWindowFrames * melBands * 4)
                        .order(ByteOrder.nativeOrder())
                    for (row in window) {
                        for (v in row) embInBuf.putFloat(v)
                    }

                    val embOutBuf = ByteBuffer.allocateDirect(embDim * 4).order(ByteOrder.nativeOrder())
                    emb.run(embInBuf, embOutBuf)
                    embOutBuf.rewind()
                    val embedding = FloatArray(embDim) { embOutBuf.float }
                    embHistory.addLast(embedding)
                    while (embHistory.size > wakeEmbeddingFrames) embHistory.removeFirst()

                    // Stage 3: Wake-word classifier once 16 embeddings exist
                    if (embHistory.size >= wakeEmbeddingFrames) {
                        val wakeInBuf = ByteBuffer.allocateDirect(wakeEmbeddingFrames * embDim * 4)
                            .order(ByteOrder.nativeOrder())
                        for (vec in embHistory) {
                            for (v in vec) wakeInBuf.putFloat(v)
                        }
                        val wakeOutBuf = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
                        wake.run(wakeInBuf, wakeOutBuf)
                        wakeOutBuf.rewind()
                        val score = wakeOutBuf.float

                        val now = System.currentTimeMillis()
                        if (score >= threshold && now - lastTriggerMs > 2_500L) {
                            lastTriggerMs = now
                            Log.i(TAG, "openWakeWord detected '$wakePhrase' locally (score=$score)")
                            callbacks.onWakeDetected(
                                WakeDetectionEvent(
                                    detectedPhrase = wakePhrase,
                                    trailingCommand = null,
                                    engineId = engineId,
                                    timestampMillis = now
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "openWakeWord LiteRT inference failure", e)
                callbacks.onError("openWakeWord model inference error: ${e.localizedMessage}")
                break
            }
        }
    }

    override fun stop() {
        workerJob?.cancel()
        workerJob = null
        scope?.cancel()
        scope = null

        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        closeInterpreters()
    }

    private fun closeInterpreters() {
        runCatching { melInterpreter?.close() }
        runCatching { embInterpreter?.close() }
        runCatching { wakeInterpreter?.close() }
        melInterpreter = null
        embInterpreter = null
        wakeInterpreter = null
    }

    companion object {
        private const val TAG = "OpenWakeWordLiteRT"
    }
}
