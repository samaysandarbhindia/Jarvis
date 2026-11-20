package com.example.jarvis.core.di

import android.content.Context
import com.example.jarvis.ai.AiProvider
import com.example.jarvis.ai.FirebaseAiProvider
import com.example.jarvis.ai.OpenAiCompatibleProvider
import com.example.jarvis.ai.ToolDispatcher
import com.example.jarvis.apps.InstalledAppsManager
import com.example.jarvis.automation.AutomationEngine
import com.example.jarvis.conversations.ConversationRepository
import com.example.jarvis.database.JarvisDatabase
import com.example.jarvis.device.DeviceControlManager
import com.example.jarvis.memory.MemoryRepository
import com.example.jarvis.permissions.PermissionHealthChecker
import com.example.jarvis.reminders.ReminderScheduler
import com.example.jarvis.search.WebSearchClient
import com.example.jarvis.security.KeystoreSecretStore
import com.example.jarvis.settings.SettingsRepository
import com.example.jarvis.voice.VoiceEngine
import com.example.jarvis.wakeword.OpenWakeWordModelStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: JarvisDatabase by lazy { JarvisDatabase.getInstance(appContext) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val secretStore: KeystoreSecretStore by lazy { KeystoreSecretStore(appContext) }
    val openWakeWordModelStore: OpenWakeWordModelStore by lazy {
        OpenWakeWordModelStore(appContext)
    }

    val memoryRepository: MemoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(database.conversationDao())
    }

    val installedAppsManager: InstalledAppsManager by lazy { InstalledAppsManager(appContext) }
    val deviceControlManager: DeviceControlManager by lazy { DeviceControlManager(appContext) }
    val webSearchClient: WebSearchClient by lazy { WebSearchClient() }
    val permissionHealthChecker: PermissionHealthChecker by lazy {
        PermissionHealthChecker(appContext)
    }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(appContext, database.reminderDao())
    }

    val automationEngine: AutomationEngine by lazy {
        AutomationEngine(installedAppsManager, database.automationLogDao())
    }

    val voiceEngine: VoiceEngine by lazy { VoiceEngine(appContext) }

    val firebaseAiProvider: FirebaseAiProvider by lazy { FirebaseAiProvider() }
    val openAiProvider: OpenAiCompatibleProvider by lazy { OpenAiCompatibleProvider(secretStore) }

    fun getActiveAiProvider(providerId: String): AiProvider {
        return when (providerId) {
            openAiProvider.providerId -> openAiProvider
            else -> firebaseAiProvider
        }
    }

    val toolDispatcher: ToolDispatcher by lazy {
        ToolDispatcher(
            appsManager = installedAppsManager,
            deviceControl = deviceControlManager,
            automationEngine = automationEngine,
            memoryRepository = memoryRepository,
            reminderScheduler = reminderScheduler,
            webSearchClient = webSearchClient,
            notificationDao = database.capturedNotificationDao(),
            isNotificationReadingEnabled = {
                runBlocking { settingsRepository.settingsFlow.first().notificationReadingEnabled }
            }
        )
    }
}
