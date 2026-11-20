package com.example.jarvis.ai

import android.util.Log
import com.example.jarvis.database.MemoryEntity
import com.example.jarvis.database.MessageEntity
import com.example.jarvis.security.KeystoreSecretStore
import com.example.jarvis.settings.JarvisSettings
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface AiTurnEvent {
    data class StateChanged(val state: AssistantState) : AiTurnEvent
    data class ToolExecuted(val output: ToolExecutionOutput) : AiTurnEvent
    data class StreamingChunk(val accumulatedText: String) : AiTurnEvent
    data class Completed(
        val finalText: String,
        val toolOutputs: List<ToolExecutionOutput>,
        val pendingConfirmations: List<PendingConfirmation>
    ) : AiTurnEvent
    data class Failed(val errorMessage: String) : AiTurnEvent
}

interface AiProvider {
    val providerId: String
    val displayName: String

    fun processTurn(
        userPrompt: String,
        history: List<MessageEntity>,
        memories: List<MemoryEntity>,
        settings: JarvisSettings,
        toolDispatcher: ToolDispatcher
    ): Flow<AiTurnEvent>
}

object JarvisPromptBuilder {
    fun buildSystemPrompt(settings: JarvisSettings, memories: List<MemoryEntity>): String {
        val styleNote = when (settings.personalityStyle) {
            "Concise Tactical" -> "Be ultra-concise, direct, and tactical, addressing the user respectfully."
            "Friendly Bilingual" -> "Be warm, approachable, and seamlessly adapt to Hindi, English, or Hinglish."
            else -> "Respond as JARVIS: intelligent, calm, polite, slightly futuristic, concise, and helpful. Address the user politely (e.g., 'sir' when appropriate in English, or natural respectful Hindi/Hinglish when speaking Hindi/Hinglish)."
        }
        val langInstruction = when (settings.languageTag) {
            "hi-IN" -> "Prefer responding in natural Hindi (Devanagari or clear Hinglish if asked), unless the user writes in pure English."
            "en-US" -> "Respond in clear English unless the user addresses you in Hindi or Hinglish."
            else -> "Understand English, Hindi, and Hinglish fluently and reply in the matching language/register."
        }
        val memoryBlock = if (memories.isEmpty()) {
            "No saved personal memories yet."
        } else {
            memories.take(15).joinToString("\n") { "- [${it.category}] ${it.key}: ${it.content}" }
        }
        val custom = settings.customSystemInstructions.trim()
        return buildString {
            appendLine("You are ${settings.assistantName}, a native Android personal AI assistant.")
            appendLine(styleNote)
            appendLine(langInstruction)
            appendLine()
            appendLine("CRITICAL OPERATIONAL RULES:")
            appendLine("1. Always use registered native tools whenever the user asks to open apps, check battery/device status, change volume, toggle flashlight, search the web, save/retrieve memories, set reminders/alarms, read notifications, prepare messages, or automate in-app steps.")
            appendLine("2. Never fabricate device telemetry, search results, or action outcomes. Report ONLY the actual tool execution result.")
            appendLine("3. If a tool reports a missing permission (like Accessibility Service or Notification Access) or an uninstalled app, clearly explain how the user can enable it or that the app is not installed.")
            appendLine("4. Never claim a message was sent silently; prepare_message requires user confirmation and opens the app composer.")
            if (custom.isNotEmpty()) {
                appendLine()
                appendLine("USER CUSTOM INSTRUCTIONS:")
                appendLine(custom)
            }
            appendLine()
            appendLine("SAVED USER MEMORIES & PREFERENCES:")
            appendLine(memoryBlock)
        }
    }
}

class FirebaseAiProvider : AiProvider {
    override val providerId: String = "FIREBASE_GEMINI"
    override val displayName: String = "Google Gemini (Firebase AI Logic)"

    override fun processTurn(
        userPrompt: String,
        history: List<MessageEntity>,
        memories: List<MemoryEntity>,
        settings: JarvisSettings,
        toolDispatcher: ToolDispatcher
    ): Flow<AiTurnEvent> = flow {
        emit(AiTurnEvent.StateChanged(AssistantState.PROCESSING))

        val modelName = validateModelName(settings.geminiModel)
        val systemInstructionText = JarvisPromptBuilder.buildSystemPrompt(settings, memories)

        // Lazily construct generative model with registered FunctionDeclarations
        val model by lazy {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = modelName,
                tools = listOf(Tool.functionDeclarations(ToolRegistry.functionDeclarations)),
                systemInstruction = content { text(systemInstructionText) }
            )
        }

        val chatHistory: List<Content> = history
            .filter { it.role == "USER" || it.role == "ASSISTANT" }
            .takeLast(16)
            .map { msg ->
                val role = if (msg.role == "USER") "user" else "model"
                content(role) { text(msg.content) }
            }

        val toolOutputs = mutableListOf<ToolExecutionOutput>()
        val pendingConfirmations = mutableListOf<PendingConfirmation>()

        try {
            val finalText = withTimeout(60_000L) {
                val chat = model.startChat(history = chatHistory)
                var response = chat.sendMessage(userPrompt)

                var round = 0
                while (response.functionCalls.isNotEmpty() && round < 6) {
                    round++
                    emit(AiTurnEvent.StateChanged(AssistantState.EXECUTING))

                    val responseParts = mutableListOf<FunctionResponsePart>()
                    for (call in response.functionCalls) {
                        val output = toolDispatcher.dispatch(call.name, call.args)
                        toolOutputs.add(output)
                        output.pendingConfirmation?.let { pendingConfirmations.add(it) }
                        emit(AiTurnEvent.ToolExecuted(output))
                        responseParts.add(FunctionResponsePart(call.name, output.payload, call.id))
                    }

                    emit(AiTurnEvent.StateChanged(AssistantState.PROCESSING))
                    response = chat.sendMessage(
                        content("user") {
                            responseParts.forEach { part(it) }
                        }
                    )
                }

                val text = response.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    emit(AiTurnEvent.StreamingChunk(text))
                    text
                } else if (toolOutputs.isNotEmpty()) {
                    toolOutputs.joinToString("\n") { it.summary }
                } else {
                    "I received an empty response from Gemini. Please try rephrasing your request."
                }
            }

            emit(
                AiTurnEvent.Completed(
                    finalText = finalText,
                    toolOutputs = toolOutputs,
                    pendingConfirmations = pendingConfirmations
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini request timed out", e)
            emit(AiTurnEvent.Failed("Request timed out after 60 seconds. Please check your connection and try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic error", e)
            emit(AiTurnEvent.Failed(e.localizedMessage ?: "Gemini inference failed."))
        }
    }.flowOn(Dispatchers.IO)

    fun streamPlainReply(
        prompt: String,
        systemInstruction: String,
        modelName: String = "gemini-3.8-flash"
    ): Flow<String> = flow {
        val model by lazy {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = validateModelName(modelName),
                systemInstruction = content { text(systemInstruction) }
            )
        }
        withTimeout(60_000L) {
            val sb = StringBuilder()
            model.generateContentStream(prompt).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    sb.append(piece)
                    emit(sb.toString())
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun validateModelName(name: String): String {
        return when (name) {
            "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon" -> name
            else -> "gemini-3.8-flash"
        }
    }

    companion object {
        private const val TAG = "FirebaseAiProvider"
    }
}

/**
 * Modular secondary provider supporting OpenAI Chat Completions when the user
 * explicitly selects OpenAI and stores a key in Android Keystore via Settings.
 */
class OpenAiCompatibleProvider(
    private val secretStore: KeystoreSecretStore
) : AiProvider {
    override val providerId: String = "OPENAI_CUSTOM"
    override val displayName: String = "OpenAI Compatible (Modular)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    override fun processTurn(
        userPrompt: String,
        history: List<MessageEntity>,
        memories: List<MemoryEntity>,
        settings: JarvisSettings,
        toolDispatcher: ToolDispatcher
    ): Flow<AiTurnEvent> = flow {
        emit(AiTurnEvent.StateChanged(AssistantState.PROCESSING))
        val apiKey = secretStore.getSecret(KeystoreSecretStore.KEY_OPENAI_API)
        if (apiKey.isBlank()) {
            emit(
                AiTurnEvent.Failed(
                    "OpenAI API key is not configured. Add it under Settings -> AI Configuration, or switch back to Firebase Gemini AI."
                )
            )
            return@flow
        }

        try {
            val text = withContext(Dispatchers.IO) {
                withTimeout(45_000L) {
                    val messagesJson = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", JarvisPromptBuilder.buildSystemPrompt(settings, memories))
                        })
                        history.filter { it.role == "USER" || it.role == "ASSISTANT" }
                            .takeLast(12)
                            .forEach { msg ->
                                put(JSONObject().apply {
                                    put("role", if (msg.role == "USER") "user" else "assistant")
                                    put("content", msg.content)
                                })
                            }
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        })
                    }
                    val body = JSONObject().apply {
                        put("model", "gpt-4o-mini")
                        put("messages", messagesJson)
                    }.toString().toRequestBody("application/json".toMediaType())

                    val request = Request.Builder()
                        .url("https://api.openai.com/v1/chat/completions")
                        .header("Authorization", "Bearer $apiKey")
                        .post(body)
                        .build()

                    client.newCall(request).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("OpenAI HTTP ${resp.code}: ${raw.take(200)}")
                        }
                        val root = JSONObject(raw)
                        root.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                    }
                }
            }
            emit(AiTurnEvent.StreamingChunk(text))
            emit(AiTurnEvent.Completed(text, emptyList(), emptyList()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AiTurnEvent.Failed(e.localizedMessage ?: "OpenAI request failed."))
        }
    }
}
