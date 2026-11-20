package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.jarvis.ai.ToolDispatcher
import com.example.jarvis.ai.ToolRegistry
import com.example.jarvis.apps.InstalledAppsManager
import com.example.jarvis.automation.AutomationEngine
import com.example.jarvis.automation.AutomationStep
import com.example.jarvis.conversations.ConversationRepository
import com.example.jarvis.database.JarvisDatabase
import com.example.jarvis.database.MemoryEntity
import com.example.jarvis.database.ReminderEntity
import com.example.jarvis.device.DeviceControlManager
import com.example.jarvis.memory.MemoryRepository
import com.example.jarvis.permissions.PermissionHealthChecker
import com.example.jarvis.reminders.ReminderScheduler
import com.example.jarvis.search.WebSearchClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: JarvisDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            JarvisDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun appNameMatchesJarvis() {
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS", appName)
    }

    @Test
    fun memoryRepositorySavesRetrievesAndExportsJson() = runTest {
        val repo = MemoryRepository(database.memoryDao())
        repo.saveMemory("preferred_language", "User prefers Hindi", MemoryEntity.CATEGORY_PREFERENCE)
        val retrieved = repo.retrieveRelevantMemories("Hindi")
        assertEquals(1, retrieved.size)
        assertEquals("preferred_language", retrieved.first().key)

        val exported = repo.exportMemoriesJson()
        assertTrue(exported.contains("User prefers Hindi"))

        repo.clearAll()
        assertTrue(repo.getAllOnce().isEmpty())

        val importResult = repo.importMemoriesJson(exported)
        assertEquals(1, importResult.getOrNull())
        assertEquals(1, repo.getAllOnce().size)
    }

    @Test
    fun conversationRepositoryCreatesMessagesAndSearches() = runTest {
        val repo = ConversationRepository(database.conversationDao())
        val convId = repo.createConversation("Morning Briefing")
        repo.addMessage(convId, "USER", "What is my battery percentage?")
        repo.addMessage(convId, "ASSISTANT", "Battery is at 90%.")

        val messages = repo.observeMessages(convId).first()
        assertEquals(2, messages.size)

        val matches = repo.searchMessages("battery")
        assertEquals(2, matches.size)

        repo.renameConversation(convId, "Battery Check")
        assertEquals("Battery Check", repo.getConversation(convId)?.title)
    }

    @Test
    fun reminderSchedulerStoresAndTogglesCompletion() = runTest {
        val scheduler = ReminderScheduler(context, database.reminderDao())
        val triggerAt = System.currentTimeMillis() + 600_000L
        val result = scheduler.scheduleReminder("Study at 7 PM", triggerAt, ReminderEntity.REPEAT_NONE)
        assertEquals("Study at 7 PM", result.title)

        val list = scheduler.allReminders.first()
        assertEquals(1, list.size)
        assertFalse(list.first().isCompleted)

        scheduler.toggleCompleted(list.first())
        val updated = scheduler.allReminders.first()
        assertTrue(updated.first().isCompleted)
    }

    @Test
    fun toolDispatcherRejectsUnknownToolsAndQueuesMessageConfirmation() = runTest {
        val appsManager = InstalledAppsManager(context)
        val deviceControl = DeviceControlManager(context)
        val automationEngine = AutomationEngine(appsManager, database.automationLogDao())
        val memoryRepo = MemoryRepository(database.memoryDao())
        val reminderScheduler = ReminderScheduler(context, database.reminderDao())

        val dispatcher = ToolDispatcher(
            appsManager = appsManager,
            deviceControl = deviceControl,
            automationEngine = automationEngine,
            memoryRepository = memoryRepo,
            reminderScheduler = reminderScheduler,
            webSearchClient = WebSearchClient(),
            notificationDao = database.capturedNotificationDao(),
            isNotificationReadingEnabled = { false }
        )

        val unknown = dispatcher.dispatch("arbitrary_code_exec", emptyMap())
        assertFalse(unknown.success)

        val battery = dispatcher.dispatch("get_battery_status", emptyMap())
        assertTrue(battery.success)
        assertTrue(battery.payload.containsKey("percentage"))

        val msgPrep = dispatcher.dispatch(
            "prepare_message",
            mapOf(
                "platform" to JsonPrimitive("whatsapp"),
                "recipient" to JsonPrimitive("Rahul"),
                "message" to JsonPrimitive("I will arrive in 10 minutes")
            )
        )
        assertTrue(msgPrep.success)
        assertTrue(msgPrep.pendingConfirmation != null)
    }

    @Test
    fun automationEngineFailsCleanlyWhenAccessibilityDisabled() = runTest {
        val appsManager = InstalledAppsManager(context)
        val engine = AutomationEngine(appsManager, database.automationLogDao())
        val res = engine.executeWorkflow(
            "YouTube Search",
            listOf(
                AutomationStep.LaunchApp("YouTube"),
                AutomationStep.ClickElement("search")
            )
        )
        assertFalse(res.success)
        assertTrue(res.summary.contains("Accessibility", ignoreCase = true))
    }

    @Test
    fun permissionHealthCheckerReturnsSnapshotWithoutCrash() {
        val checker = PermissionHealthChecker(context)
        val snap = checker.getSnapshot()
        assertFalse(snap.accessibilityConnected)
        assertFalse(snap.openWakeWordModelsReady)
    }

    @Test
    fun openWakeWordStoreAndEngineReportMissingModelsHonestly() {
        val store = com.example.jarvis.wakeword.OpenWakeWordModelStore(context)
        store.clearAllModels()
        val readiness = store.getReadiness()
        assertFalse(readiness.allModelsPresent)
        assertEquals(3, readiness.missingLabels().size)

        val engine = com.example.jarvis.wakeword.OpenWakeWordTfliteEngine()
        val availability = engine.checkAvailability(context)
        assertFalse(availability.available)
        assertTrue(availability.detail.contains("melspectrogram.tflite"))
    }

    @Test
    fun toolRegistryHasAllRequiredTools() {
        assertEquals(ToolRegistry.specs.size, ToolRegistry.functionDeclarations.size)
        val names = ToolRegistry.specs.map { it.name }.toSet()
        val expected = setOf(
            "open_app",
            "list_installed_apps",
            "search_installed_apps",
            "get_battery_status",
            "get_device_status",
            "get_volume",
            "set_volume",
            "toggle_flashlight",
            "open_settings",
            "open_website",
            "search_web",
            "create_note",
            "retrieve_memory",
            "save_memory",
            "set_reminder",
            "set_alarm",
            "prepare_message",
            "dial_number",
            "read_notifications",
            "accessibility_click",
            "accessibility_type",
            "accessibility_scroll",
            "execute_automation"
        )
        assertTrue(names.containsAll(expected))
    }
}
