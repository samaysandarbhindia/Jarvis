package com.example.jarvis.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.jarvis.accessibility.AccessibilityBridge
import com.example.jarvis.accessibility.AccessibleElementInfo
import com.example.jarvis.ai.AiTurnEvent
import com.example.jarvis.ai.AssistantState
import com.example.jarvis.ai.PendingConfirmation
import com.example.jarvis.ai.ToolRegistry
import com.example.jarvis.ai.ToolSpec
import com.example.jarvis.apps.InstalledAppInfo
import com.example.jarvis.automation.AutomationResult
import com.example.jarvis.core.di.AppContainer
import com.example.jarvis.database.AutomationLogEntity
import com.example.jarvis.database.CapturedNotificationEntity
import com.example.jarvis.database.ConversationEntity
import com.example.jarvis.database.MemoryEntity
import com.example.jarvis.database.MessageEntity
import com.example.jarvis.database.ReminderEntity
import com.example.jarvis.device.DeviceTelemetry
import com.example.jarvis.permissions.PermissionStatusSnapshot
import android.net.Uri
import com.example.jarvis.security.KeystoreSecretStore
import com.example.jarvis.settings.JarvisSettings
import com.example.jarvis.voice.TtsVoiceOption
import com.example.jarvis.voice.VoiceEvent
import com.example.jarvis.wakeword.OpenWakeWordModelReadiness
import com.example.jarvis.wakeword.OpenWakeWordSlot
import com.example.jarvis.wakeword.VoiceActivationBus
import com.example.jarvis.wakeword.VoiceActivationForegroundService
import com.example.jarvis.wakeword.VoiceActivationMode
import com.example.jarvis.wakeword.VoiceActivationState
import com.example.jarvis.wakeword.WakeDetectionEvent
import com.example.jarvis.wakeword.WakeWordPhraseMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JarvisViewModel(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    val settings: StateFlow<JarvisSettings> = container.settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, JarvisSettings())

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _statusSubtitle = MutableStateFlow("Good day, sir. JARVIS is online. How may I assist you?")
    val statusSubtitle: StateFlow<String> = _statusSubtitle.asStateFlow()

    private val _streamingResponse = MutableStateFlow("")
    val streamingResponse: StateFlow<String> = _streamingResponse.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _pendingConfirmations = MutableStateFlow<List<PendingConfirmation>>(emptyList())
    val pendingConfirmations: StateFlow<List<PendingConfirmation>> = _pendingConfirmations.asStateFlow()

    // Conversations & Active Chat
    private val _conversationSearch = MutableStateFlow("")
    val conversationSearch: StateFlow<String> = _conversationSearch.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = _conversationSearch
        .flatMapLatest { q -> container.conversationRepository.searchConversations(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConversationId = MutableStateFlow<Long?>(null)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else container.conversationRepository.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Ephemeral messages when "Save Conversations Automatically" is toggled OFF in Privacy
    private val _ephemeralMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val ephemeralMessages: StateFlow<List<MessageEntity>> = _ephemeralMessages.asStateFlow()

    // Memories, Reminders, Logs, Notifications
    val memories: StateFlow<List<MemoryEntity>> = container.memoryRepository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = container.reminderScheduler.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automationLogs: StateFlow<List<AutomationLogEntity>> = container.automationEngine.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val capturedNotifications: StateFlow<List<CapturedNotificationEntity>> =
        container.database.capturedNotificationDao().getRecentNotifications()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Voice & Activation
    val isListening: StateFlow<Boolean> = container.voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = container.voiceEngine.isSpeaking
    val audioLevel: StateFlow<Float> = combine(
        container.voiceEngine.isListening,
        container.voiceEngine.audioLevel,
        VoiceActivationBus.backgroundAudioLevel
    ) { listening, activeLevel, bgLevel ->
        if (listening) activeLevel else bgLevel
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    val partialTranscript: StateFlow<String> = container.voiceEngine.partialTranscript
    val availableTtsVoices: StateFlow<List<TtsVoiceOption>> = container.voiceEngine.availableVoices
    val ttsReady: StateFlow<Boolean> = container.voiceEngine.ttsReady
    val isOnDeviceSpeechAvailable: Boolean
        get() = container.voiceEngine.isOnDeviceRecognitionAvailable
    val voiceActivationState: StateFlow<VoiceActivationState> = VoiceActivationBus.state
    val voiceActivationDetail: StateFlow<String> = VoiceActivationBus.statusDetail
    private val _openWakeWordReadiness = MutableStateFlow(container.openWakeWordModelStore.getReadiness())
    val openWakeWordReadiness: StateFlow<OpenWakeWordModelReadiness> = _openWakeWordReadiness.asStateFlow()

    // Device & Permissions & Apps
    private val _deviceTelemetry = MutableStateFlow(container.deviceControlManager.getDeviceTelemetry())
    val deviceTelemetry: StateFlow<DeviceTelemetry> = _deviceTelemetry.asStateFlow()

    val isTorchOn: StateFlow<Boolean> = container.deviceControlManager.isTorchOn

    private val _permissionsSnapshot = MutableStateFlow(container.permissionHealthChecker.getSnapshot())
    val permissionsSnapshot: StateFlow<PermissionStatusSnapshot> = _permissionsSnapshot.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading.asStateFlow()

    private val _inspectedUiElements = MutableStateFlow<List<AccessibleElementInfo>>(emptyList())
    val inspectedUiElements: StateFlow<List<AccessibleElementInfo>> = _inspectedUiElements.asStateFlow()

    private val _lastAutomationResult = MutableStateFlow<AutomationResult?>(null)
    val lastAutomationResult: StateFlow<AutomationResult?> = _lastAutomationResult.asStateFlow()

    val registeredTools: List<ToolSpec> = ToolRegistry.specs

    private var activeTurnJob: Job? = null
    private var shouldAutoListenAfterTts = false

    init {
        observeVoiceAndWakeEvents()
        refreshInstalledApps()
        startTelemetryTicker()
    }

    private fun observeVoiceAndWakeEvents() {
        viewModelScope.launch {
            combine(isListening, isSpeaking) { listening, speaking ->
                listening to speaking
            }.collect { (listening, speaking) ->
                if (listening) {
                    _assistantState.value = AssistantState.LISTENING
                } else if (speaking && _assistantState.value != AssistantState.PROCESSING &&
                    _assistantState.value != AssistantState.EXECUTING
                ) {
                    _assistantState.value = AssistantState.SPEAKING
                } else if (!listening && !speaking && _assistantState.value == AssistantState.SPEAKING) {
                    _assistantState.value = AssistantState.IDLE
                } else if (!listening && !speaking && _assistantState.value == AssistantState.LISTENING) {
                    if (activeTurnJob?.isActive != true) {
                        _assistantState.value = AssistantState.IDLE
                    }
                }
            }
        }

        viewModelScope.launch {
            container.voiceEngine.events.collect { event ->
                when (event) {
                    is VoiceEvent.FinalTranscript -> {
                        val wakeCheck = WakeWordPhraseMatcher.match(
                            event.text,
                            settings.value.wakePhrase
                        )
                        if (wakeCheck != null && wakeCheck.trailingCommand.isNullOrBlank()) {
                            _statusSubtitle.value = event.text
                            onWakeWordDetected(wakeCheck)
                        } else {
                            val cleanPrompt = wakeCheck?.trailingCommand ?: event.text
                            _statusSubtitle.value = cleanPrompt
                            submitUserPrompt(cleanPrompt, spokenInput = true)
                        }
                    }

                    is VoiceEvent.SpeechError -> {
                        _assistantState.value = AssistantState.ERROR
                        _statusSubtitle.value = event.message
                        _errorBanner.value = event.message
                    }

                    VoiceEvent.TtsCompleted -> {
                        if (_assistantState.value == AssistantState.SPEAKING) {
                            _assistantState.value = AssistantState.IDLE
                        }
                        if (shouldAutoListenAfterTts) {
                            shouldAutoListenAfterTts = false
                            startListening()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            VoiceActivationBus.wakeEvents.collect { event ->
                onWakeWordDetected(event)
            }
        }
    }

    private fun startTelemetryTicker() {
        viewModelScope.launch {
            while (true) {
                _deviceTelemetry.value = container.deviceControlManager.getDeviceTelemetry()
                _permissionsSnapshot.value = container.permissionHealthChecker.getSnapshot()
                delay(5_000L)
            }
        }
    }

    fun refreshDiagnostics() {
        _deviceTelemetry.value = container.deviceControlManager.getDeviceTelemetry()
        _permissionsSnapshot.value = container.permissionHealthChecker.getSnapshot()
        _openWakeWordReadiness.value = container.openWakeWordModelStore.getReadiness()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _appsLoading.value = true
            _installedApps.value = container.installedAppsManager.getLaunchableApps()
            _appsLoading.value = false
        }
    }

    fun onWakeWordDetected(event: WakeDetectionEvent = WakeDetectionEvent(settings.value.wakePhrase)) {
        val trailing = event.trailingCommand?.trim()
        if (!trailing.isNullOrEmpty()) {
            _statusSubtitle.value = trailing
            submitUserPrompt(trailing, spokenInput = true)
            return
        }
        val greeting = "Yes, sir. How may I help you?"
        _statusSubtitle.value = greeting
        if (!settings.value.ttsMuted) {
            shouldAutoListenAfterTts = true
            speakText(greeting)
        } else {
            startListening()
        }
    }

    fun onMicrophonePermissionDenied() {
        val message = "Microphone permission denied. Grant RECORD_AUDIO in App Settings to use voice commands."
        _assistantState.value = AssistantState.ERROR
        _errorBanner.value = message
        _statusSubtitle.value = message
        refreshDiagnostics()
    }

    fun toggleListening() {
        if (isListening.value) {
            container.voiceEngine.stopListening()
            _assistantState.value = AssistantState.IDLE
        } else {
            startListening()
        }
    }

    fun startListening() {
        _errorBanner.value = null
        if (isSpeaking.value && settings.value.interruptSpeechOnMic) {
            container.voiceEngine.stopSpeaking()
        }
        val current = settings.value
        _assistantState.value = AssistantState.LISTENING
        val modeSuffix = if (current.offlineSpeechPreferred && isOnDeviceSpeechAvailable) {
            " · Offline"
        } else {
            ""
        }
        _statusSubtitle.value = "Listening (${current.languageTag}$modeSuffix)..."
        container.voiceEngine.startListening(
            languageTag = current.languageTag,
            timeoutSeconds = current.listeningTimeoutSeconds,
            preferOffline = current.offlineSpeechPreferred
        )
    }

    fun stopSpeaking() {
        shouldAutoListenAfterTts = false
        container.voiceEngine.stopSpeaking()
        if (_assistantState.value == AssistantState.SPEAKING) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun speakText(text: String) {
        val s = settings.value
        if (s.ttsMuted) return
        _assistantState.value = AssistantState.SPEAKING
        container.voiceEngine.speak(
            text = text,
            languageTag = s.languageTag,
            voiceName = s.ttsVoiceName,
            speed = s.ttsSpeed,
            pitch = s.ttsPitch,
            muted = s.ttsMuted
        )
    }

    fun toggleMute() {
        val nextMuted = !settings.value.ttsMuted
        if (nextMuted) stopSpeaking()
        updateSettings { it.copy(ttsMuted = nextMuted) }
    }

    fun submitUserPrompt(rawPrompt: String, spokenInput: Boolean = false) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty()) return

        _errorBanner.value = null
        activeTurnJob?.cancel()
        activeTurnJob = viewModelScope.launch {
            val currentSettings = settings.value
            val persist = currentSettings.saveConversationsAutomatically

            val convId = if (persist) {
                val existingId = _activeConversationId.value
                if (existingId != null && container.conversationRepository.getConversation(existingId) != null) {
                    existingId
                } else {
                    val created = container.conversationRepository.createConversation(
                        title = prompt.take(36)
                    )
                    _activeConversationId.value = created
                    created
                }
            } else {
                0L
            }

            val historyBefore = if (persist && convId > 0) {
                container.conversationRepository.getMessagesOnce(convId)
            } else {
                _ephemeralMessages.value
            }

            val userMsg = MessageEntity(
                id = System.currentTimeMillis(),
                conversationId = convId,
                role = "USER",
                content = prompt
            )
            if (persist && convId > 0) {
                container.conversationRepository.addMessage(convId, "USER", prompt)
            } else {
                _ephemeralMessages.value = _ephemeralMessages.value + userMsg
            }

            val memoriesList = container.memoryRepository.getAllOnce()
            val provider = container.getActiveAiProvider(currentSettings.aiProviderId)

            _streamingResponse.value = ""
            try {
                provider.processTurn(
                    userPrompt = prompt,
                    history = historyBefore,
                    memories = memoriesList,
                    settings = currentSettings,
                    toolDispatcher = container.toolDispatcher
                ).collect { event ->
                    when (event) {
                        is AiTurnEvent.StateChanged -> {
                            _assistantState.value = event.state
                            _statusSubtitle.value = when (event.state) {
                                AssistantState.PROCESSING -> "Processing via ${provider.displayName}..."
                                AssistantState.EXECUTING -> "Executing native Android operation..."
                                else -> _statusSubtitle.value
                            }
                        }

                        is AiTurnEvent.ToolExecuted -> {
                            val toolMsg = MessageEntity(
                                id = System.nanoTime(),
                                conversationId = convId,
                                role = "TOOL",
                                content = event.output.summary,
                                toolName = event.output.toolName,
                                toolStatus = if (event.output.success) "SUCCESS" else "ERROR"
                            )
                            if (persist && convId > 0) {
                                container.conversationRepository.addMessage(
                                    conversationId = convId,
                                    role = "TOOL",
                                    content = event.output.summary,
                                    toolName = event.output.toolName,
                                    toolStatus = if (event.output.success) "SUCCESS" else "ERROR"
                                )
                            } else {
                                _ephemeralMessages.value = _ephemeralMessages.value + toolMsg
                            }
                        }

                        is AiTurnEvent.StreamingChunk -> {
                            _streamingResponse.value = event.accumulatedText
                        }

                        is AiTurnEvent.Completed -> {
                            _streamingResponse.value = ""
                            val assistantMsg = MessageEntity(
                                id = System.nanoTime(),
                                conversationId = convId,
                                role = "ASSISTANT",
                                content = event.finalText
                            )
                            if (persist && convId > 0) {
                                container.conversationRepository.addMessage(
                                    convId,
                                    "ASSISTANT",
                                    event.finalText
                                )
                            } else {
                                _ephemeralMessages.value = _ephemeralMessages.value + assistantMsg
                            }

                            if (event.pendingConfirmations.isNotEmpty()) {
                                _pendingConfirmations.value =
                                    _pendingConfirmations.value + event.pendingConfirmations
                            }

                            _statusSubtitle.value = event.finalText
                            shouldAutoListenAfterTts = spokenInput && currentSettings.autoListenAfterResponse

                            if (!currentSettings.ttsMuted) {
                                speakText(event.finalText)
                            } else {
                                _assistantState.value = AssistantState.IDLE
                            }
                            refreshDiagnostics()
                        }

                        is AiTurnEvent.Failed -> {
                            _streamingResponse.value = ""
                            _assistantState.value = AssistantState.ERROR
                            _errorBanner.value = event.errorMessage
                            _statusSubtitle.value = event.errorMessage
                            val errMsg = MessageEntity(
                                id = System.nanoTime(),
                                conversationId = convId,
                                role = "ASSISTANT",
                                content = "Error: ${event.errorMessage}"
                            )
                            if (persist && convId > 0) {
                                container.conversationRepository.addMessage(
                                    convId,
                                    "ASSISTANT",
                                    "Error: ${event.errorMessage}"
                                )
                            } else {
                                _ephemeralMessages.value = _ephemeralMessages.value + errMsg
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during turn", e)
                _assistantState.value = AssistantState.ERROR
                _errorBanner.value = e.localizedMessage ?: "Assistant turn failed."
            }
        }
    }

    fun confirmPendingAction(pending: PendingConfirmation) {
        _pendingConfirmations.value = _pendingConfirmations.value.filterNot { it.id == pending.id }
        viewModelScope.launch {
            _assistantState.value = AssistantState.EXECUTING
            val result = container.toolDispatcher.executeConfirmedAction(pending)
            _statusSubtitle.value = result.summary
            val convId = _activeConversationId.value
            if (convId != null && settings.value.saveConversationsAutomatically) {
                container.conversationRepository.addMessage(
                    conversationId = convId,
                    role = "TOOL",
                    content = result.summary,
                    toolName = result.toolName,
                    toolStatus = if (result.success) "SUCCESS" else "ERROR"
                )
            }
            if (!settings.value.ttsMuted) {
                speakText(result.summary)
            } else {
                _assistantState.value = AssistantState.IDLE
            }
        }
    }

    fun dismissPendingAction(pending: PendingConfirmation) {
        _pendingConfirmations.value = _pendingConfirmations.value.filterNot { it.id == pending.id }
    }

    fun dismissError() {
        _errorBanner.value = null
        if (_assistantState.value == AssistantState.ERROR) {
            _assistantState.value = AssistantState.IDLE
        }
    }

    // Conversation actions
    fun setConversationSearch(query: String) {
        _conversationSearch.value = query
    }

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
    }

    fun startNewConversation() {
        viewModelScope.launch {
            if (settings.value.saveConversationsAutomatically) {
                val id = container.conversationRepository.createConversation("New Session")
                _activeConversationId.value = id
            } else {
                _ephemeralMessages.value = emptyList()
            }
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            container.conversationRepository.renameConversation(id, newTitle)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            container.conversationRepository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    fun regenerateLastResponse() {
        val msgs = if (settings.value.saveConversationsAutomatically) {
            activeMessages.value
        } else {
            _ephemeralMessages.value
        }
        val lastUser = msgs.lastOrNull { it.role == "USER" } ?: return
        viewModelScope.launch {
            val convId = _activeConversationId.value
            if (settings.value.saveConversationsAutomatically && convId != null) {
                container.conversationRepository.deleteMessagesFrom(convId, lastUser.id)
            } else {
                val idx = _ephemeralMessages.value.indexOfLast { it.id == lastUser.id }
                if (idx >= 0) {
                    _ephemeralMessages.value = _ephemeralMessages.value.take(idx)
                }
            }
            submitUserPrompt(lastUser.content, spokenInput = false)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            container.conversationRepository.clearAllConversations()
            _activeConversationId.value = null
            _ephemeralMessages.value = emptyList()
        }
    }

    // Quick Device & App actions
    fun launchApp(appNameOrPackage: String) {
        viewModelScope.launch {
            val res = container.installedAppsManager.launchApp(appNameOrPackage)
            _statusSubtitle.value = res.message
            if (!res.success) _errorBanner.value = res.message
        }
    }

    fun toggleFlashlight() {
        container.deviceControlManager.setFlashlight(null)
            .onSuccess { on ->
                _statusSubtitle.value = "Flashlight turned ${if (on) "ON" else "OFF"}."
                refreshDiagnostics()
            }
            .onFailure { err ->
                _errorBanner.value = err.localizedMessage
            }
    }

    fun setStreamVolume(stream: String, percent: Int) {
        container.deviceControlManager.setVolumePercent(percent, stream)
            .onSuccess { refreshDiagnostics() }
            .onFailure { _errorBanner.value = it.localizedMessage }
    }

    fun openSystemSettings(section: String) {
        container.deviceControlManager.openSystemSettings(section)
            .onFailure { _errorBanner.value = it.localizedMessage }
    }

    // Memory actions
    fun saveMemory(key: String, content: String, category: String) {
        viewModelScope.launch {
            container.memoryRepository.saveMemory(key, content, category)
        }
    }

    fun updateMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            container.memoryRepository.updateMemory(memory)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            container.memoryRepository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            container.memoryRepository.clearAll()
        }
    }

    suspend fun exportMemoriesJson(): String = container.memoryRepository.exportMemoriesJson()

    fun importMemoriesJson(json: String) {
        viewModelScope.launch {
            container.memoryRepository.importMemoriesJson(json)
                .onSuccess { count ->
                    _statusSubtitle.value = "Imported $count memories into Room database."
                }
                .onFailure { err ->
                    _errorBanner.value = err.localizedMessage
                }
        }
    }

    // Reminders
    fun createReminder(title: String, triggerAtMillis: Long, repeat: String) {
        viewModelScope.launch {
            val res = container.reminderScheduler.scheduleReminder(title, triggerAtMillis, repeat)
            _statusSubtitle.value = "Scheduled reminder '${res.title}'."
        }
    }

    fun toggleReminderDone(reminder: ReminderEntity) {
        viewModelScope.launch {
            container.reminderScheduler.toggleCompleted(reminder)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            container.reminderScheduler.deleteReminder(id)
        }
    }

    // Automation & Accessibility
    fun runAppSearchAutomation(appName: String, query: String) {
        viewModelScope.launch {
            _assistantState.value = AssistantState.EXECUTING
            val result = container.automationEngine.executeAppSearchWorkflow(appName, query)
            _lastAutomationResult.value = result
            _statusSubtitle.value = result.summary
            if (!result.success) _errorBanner.value = result.summary
            _assistantState.value = AssistantState.IDLE
        }
    }

    fun inspectActiveAccessibilityTree() {
        val service = AccessibilityBridge.getService()
        if (service == null) {
            _errorBanner.value = "Enable JARVIS Accessibility Service in Android Settings first."
            return
        }
        _inspectedUiElements.value = service.inspectScreen(50)
    }

    fun clearAutomationLogs() {
        viewModelScope.launch {
            container.database.automationLogDao().clearLogs()
        }
    }

    // Notifications
    fun clearCapturedNotifications() {
        viewModelScope.launch {
            container.database.capturedNotificationDao().clearNotifications()
        }
    }

    fun summarizeNotifications() {
        val list = capturedNotifications.value
        if (list.isEmpty()) {
            _statusSubtitle.value = "No captured notifications available to summarize."
            return
        }
        val digest = list.take(10).joinToString("\n") { "- [${it.appName}] ${it.title}: ${it.text}" }
        submitUserPrompt("Please summarize my recent notifications concisely:\n$digest")
    }

    fun readNotificationsAloud() {
        val list = capturedNotifications.value
        if (list.isEmpty()) {
            speakText("There are no captured notifications at the moment, sir.")
            return
        }
        val speech = list.take(5).joinToString(". ") { "From ${it.appName}: ${it.title}, ${it.text}" }
        speakText(speech)
    }

    // Voice Activation Foreground Service Control
    fun setVoiceActivationEnabled(enabled: Boolean) {
        val current = settings.value
        val mode = VoiceActivationMode.fromId(current.voiceActivationMode)
        if (!enabled) {
            updateSettings { it.copy(wakeWordEnabled = false) }
            VoiceActivationForegroundService.stopService(appContext)
            return
        }
        if (mode == VoiceActivationMode.MANUAL_ONLY) {
            // Switch to Android On-Device Trigger by default if user explicitly turns on background activation
            val targetMode = if (openWakeWordReadiness.value.allModelsPresent) {
                VoiceActivationMode.OPEN_WAKE_WORD_TFLITE
            } else {
                VoiceActivationMode.ON_DEVICE_SPEECH_TRIGGER
            }
            updateSettings { it.copy(wakeWordEnabled = true, voiceActivationMode = targetMode.id) }
            VoiceActivationForegroundService.startService(
                context = appContext,
                mode = targetMode,
                wakePhrase = current.wakePhrase,
                sensitivity = current.wakeWordSensitivity,
                languageTag = current.languageTag
            )
        } else {
            updateSettings { it.copy(wakeWordEnabled = true) }
            VoiceActivationForegroundService.startService(
                context = appContext,
                mode = mode,
                wakePhrase = current.wakePhrase,
                sensitivity = current.wakeWordSensitivity,
                languageTag = current.languageTag
            )
        }
    }

    fun setVoiceActivationMode(mode: VoiceActivationMode) {
        val current = settings.value
        updateSettings { it.copy(voiceActivationMode = mode.id) }
        if (mode == VoiceActivationMode.MANUAL_ONLY) {
            updateSettings { it.copy(wakeWordEnabled = false, voiceActivationMode = mode.id) }
            VoiceActivationForegroundService.stopService(appContext)
        } else if (current.wakeWordEnabled) {
            VoiceActivationForegroundService.startService(
                context = appContext,
                mode = mode,
                wakePhrase = current.wakePhrase,
                sensitivity = current.wakeWordSensitivity,
                languageTag = current.languageTag
            )
        }
    }

    fun importOpenWakeWordModel(slot: OpenWakeWordSlot, uri: Uri) {
        viewModelScope.launch {
            val result = container.openWakeWordModelStore.importModel(slot, uri)
            _openWakeWordReadiness.value = container.openWakeWordModelStore.getReadiness()
            refreshDiagnostics()
            result.onSuccess { msg ->
                _statusSubtitle.value = msg
                _errorBanner.value = null
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to verify .tflite model."
            }
        }
    }

    fun clearOpenWakeWordModels() {
        container.openWakeWordModelStore.clearAllModels()
        _openWakeWordReadiness.value = container.openWakeWordModelStore.getReadiness()
        refreshDiagnostics()
        if (settings.value.wakeWordEnabled &&
            settings.value.voiceActivationMode == VoiceActivationMode.OPEN_WAKE_WORD_TFLITE.id
        ) {
            setVoiceActivationEnabled(false)
        }
    }

    // Settings & Encrypted Secrets
    fun updateSettings(transform: (JarvisSettings) -> JarvisSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }

    fun saveOpenAiKey(key: String) {
        container.secretStore.saveSecret(KeystoreSecretStore.KEY_OPENAI_API, key)
    }

    fun hasOpenAiKey(): Boolean = container.secretStore.hasSecret(KeystoreSecretStore.KEY_OPENAI_API)

    override fun onCleared() {
        super.onCleared()
        container.voiceEngine.shutdown()
    }

    class Factory(
        private val context: Context,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JarvisViewModel(context.applicationContext, container) as T
        }
    }

    companion object {
        private const val TAG = "JarvisViewModel"
    }
}
