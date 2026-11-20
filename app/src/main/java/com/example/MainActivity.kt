package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.jarvis.security.configureAppCheck
import com.example.jarvis.ui.JarvisViewModel
import com.example.jarvis.ui.chat.ChatScreen
import com.example.jarvis.ui.components.JarvisHudBackdrop
import com.example.jarvis.ui.dashboard.DashboardScreen
import com.example.jarvis.ui.hub.HubScreen
import com.example.jarvis.ui.hub.HubTab
import com.example.jarvis.ui.settings.SettingsScreen
import com.example.jarvis.ui.status.StatusScreen
import com.example.jarvis.wakeword.VoiceActivationForegroundService
import com.example.jarvis.wakeword.WakeDetectionEvent
import com.example.ui.theme.JarvisAccentTheme
import com.example.ui.theme.JarvisMidnightNavy
import com.example.ui.theme.JarvisTheme

object JarvisRoutes {
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat"
    const val HUB = "hub"
    const val STATUS = "status"
    const val SETTINGS = "settings"
}

private enum class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CORE(JarvisRoutes.DASHBOARD, "Core", Icons.Filled.Hub, Icons.Outlined.Hub),
    CHAT(JarvisRoutes.CHAT, "Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    HUB(JarvisRoutes.HUB, "Modules", Icons.Filled.Apps, Icons.Outlined.Apps),
    STATUS(JarvisRoutes.STATUS, "Status", Icons.Filled.Memory, Icons.Outlined.Memory),
    SETTINGS(JarvisRoutes.SETTINGS, "Config", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {

    private var viewModelRef: JarvisViewModel? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE any UI, ViewModel, or Firebase call
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val app = application as JarvisApplication
        val factory = JarvisViewModel.Factory(applicationContext, app.container)
        val viewModel = ViewModelProvider(this, factory)[JarvisViewModel::class.java]
        viewModelRef = viewModel

        if (intent?.getBooleanExtra(VoiceActivationForegroundService.EXTRA_WAKE_TRIGGERED, false) == true) {
            val trailing = intent.getStringExtra(VoiceActivationForegroundService.EXTRA_TRAILING_COMMAND)
            viewModel.onWakeWordDetected(
                WakeDetectionEvent(
                    detectedPhrase = "JARVIS",
                    trailingCommand = trailing
                )
            )
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            JarvisAppShell(
                viewModel = viewModel,
                widthSizeClass = windowSizeClass.widthSizeClass
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(VoiceActivationForegroundService.EXTRA_WAKE_TRIGGERED, false)) {
            val trailing = intent.getStringExtra(VoiceActivationForegroundService.EXTRA_TRAILING_COMMAND)
            viewModelRef?.onWakeWordDetected(
                WakeDetectionEvent(
                    detectedPhrase = "JARVIS",
                    trailingCommand = trailing
                )
            )
        }
    }
}

@Composable
fun JarvisAppShell(
    viewModel: JarvisViewModel,
    widthSizeClass: WindowWidthSizeClass
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
    val statusSubtitle by viewModel.statusSubtitle.collectAsStateWithLifecycle()
    val streamingResponse by viewModel.streamingResponse.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val telemetry by viewModel.deviceTelemetry.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val voiceActivationState by viewModel.voiceActivationState.collectAsStateWithLifecycle()
    val voiceActivationDetail by viewModel.voiceActivationDetail.collectAsStateWithLifecycle()
    val openWakeWordReadiness by viewModel.openWakeWordReadiness.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val pendingConfirmations by viewModel.pendingConfirmations.collectAsStateWithLifecycle()

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val ephemeralMessages by viewModel.ephemeralMessages.collectAsStateWithLifecycle()
    val conversationSearch by viewModel.conversationSearch.collectAsStateWithLifecycle()

    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val automationLogs by viewModel.automationLogs.collectAsStateWithLifecycle()
    val capturedNotifications by viewModel.capturedNotifications.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionsSnapshot.collectAsStateWithLifecycle()
    val inspectedElements by viewModel.inspectedUiElements.collectAsStateWithLifecycle()
    val lastAutomationResult by viewModel.lastAutomationResult.collectAsStateWithLifecycle()
    val ttsReady by viewModel.ttsReady.collectAsStateWithLifecycle()
    val ttsVoices by viewModel.availableTtsVoices.collectAsStateWithLifecycle()

    var selectedHubTab by remember { mutableStateOf(HubTab.APPS) }
    var launchGreetingSpoken by remember { mutableStateOf(false) }

    LaunchedEffect(ttsReady, settings.speakGreetingOnLaunch) {
        if (ttsReady && settings.speakGreetingOnLaunch && !launchGreetingSpoken) {
            launchGreetingSpoken = true
            viewModel.speakText(settings.greetingMessage)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: JarvisRoutes.DASHBOARD

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    JarvisTheme(accentTheme = JarvisAccentTheme.fromId(settings.accentThemeId)) {
        JarvisHudBackdrop {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = JarvisMidnightNavy.copy(alpha = 0.92f)
                        ) {
                            NavDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = JarvisMidnightNavy.copy(alpha = 0.92f)
                        ) {
                            NavDestination.entries.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.testTag("rail_${dest.route}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = JarvisRoutes.DASHBOARD,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(JarvisRoutes.DASHBOARD) {
                            DashboardScreen(
                                settings = settings,
                                assistantState = assistantState,
                                statusSubtitle = statusSubtitle,
                                streamingResponse = streamingResponse,
                                partialTranscript = partialTranscript,
                                audioLevel = audioLevel,
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                telemetry = telemetry,
                                isTorchOn = isTorchOn,
                                voiceActivationState = voiceActivationState,
                                errorBanner = errorBanner,
                                pendingConfirmations = pendingConfirmations,
                                onToggleMic = { viewModel.toggleListening() },
                                onMicPermissionDenied = { viewModel.onMicrophonePermissionDenied() },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onToggleMute = { viewModel.toggleMute() },
                                onSubmitPrompt = { viewModel.submitUserPrompt(it) },
                                onConfirmAction = { viewModel.confirmPendingAction(it) },
                                onDismissAction = { viewModel.dismissPendingAction(it) },
                                onDismissError = { viewModel.dismissError() },
                                onOpenConversations = { navigateTo(JarvisRoutes.CHAT) },
                                onOpenSettings = { navigateTo(JarvisRoutes.SETTINGS) },
                                onOpenDiagnostics = { navigateTo(JarvisRoutes.STATUS) },
                                onOpenReminders = {
                                    selectedHubTab = HubTab.REMINDERS
                                    navigateTo(JarvisRoutes.HUB)
                                },
                                onLaunchYouTube = { viewModel.launchApp("YouTube") },
                                onToggleFlashlight = { viewModel.toggleFlashlight() }
                            )
                        }

                        composable(JarvisRoutes.CHAT) {
                            val displayedMessages = if (settings.saveConversationsAutomatically) {
                                activeMessages
                            } else {
                                ephemeralMessages
                            }
                            ChatScreen(
                                isExpandedWidth = isExpanded,
                                conversations = conversations,
                                activeConversationId = activeConversationId,
                                messages = displayedMessages,
                                saveConversationsAutomatically = settings.saveConversationsAutomatically,
                                conversationSearch = conversationSearch,
                                streamingResponse = streamingResponse,
                                assistantState = assistantState,
                                isListening = isListening,
                                isSpeaking = isSpeaking,
                                pendingConfirmations = pendingConfirmations,
                                onSearchChanged = { viewModel.setConversationSearch(it) },
                                onSelectConversation = { viewModel.selectConversation(it) },
                                onNewConversation = { viewModel.startNewConversation() },
                                onRenameConversation = { id, t -> viewModel.renameConversation(id, t) },
                                onDeleteConversation = { viewModel.deleteConversation(it) },
                                onSendPrompt = { viewModel.submitUserPrompt(it) },
                                onToggleMic = { viewModel.toggleListening() },
                                onMicPermissionDenied = { viewModel.onMicrophonePermissionDenied() },
                                onSpeakText = { viewModel.speakText(it) },
                                onStopSpeaking = { viewModel.stopSpeaking() },
                                onRegenerateLast = { viewModel.regenerateLastResponse() },
                                onConfirmAction = { viewModel.confirmPendingAction(it) },
                                onDismissAction = { viewModel.dismissPendingAction(it) }
                            )
                        }

                        composable(JarvisRoutes.HUB) {
                            HubScreen(
                                selectedTab = selectedHubTab,
                                onSelectTab = { selectedHubTab = it },
                                installedApps = installedApps,
                                appsLoading = appsLoading,
                                onRefreshApps = { viewModel.refreshInstalledApps() },
                                onLaunchApp = { viewModel.launchApp(it) },
                                memories = memories,
                                onSaveMemory = { k, c, cat -> viewModel.saveMemory(k, c, cat) },
                                onUpdateMemory = { viewModel.updateMemory(it) },
                                onDeleteMemory = { viewModel.deleteMemory(it) },
                                onClearAllMemories = { viewModel.clearAllMemories() },
                                onExportMemoriesJson = { viewModel.exportMemoriesJson() },
                                onImportMemoriesJson = { viewModel.importMemoriesJson(it) },
                                permissions = permissions,
                                inspectedElements = inspectedElements,
                                lastAutomationResult = lastAutomationResult,
                                automationLogs = automationLogs,
                                registeredTools = viewModel.registeredTools,
                                onOpenAccessibilitySettings = { viewModel.openSystemSettings("accessibility") },
                                onRunAppSearchWorkflow = { appName, q ->
                                    viewModel.runAppSearchAutomation(appName, q)
                                },
                                onInspectScreen = { viewModel.inspectActiveAccessibilityTree() },
                                onClearLogs = { viewModel.clearAutomationLogs() },
                                reminders = reminders,
                                onCreateReminder = { t, ms, rep -> viewModel.createReminder(t, ms, rep) },
                                onToggleReminderDone = { viewModel.toggleReminderDone(it) },
                                onDeleteReminder = { viewModel.deleteReminder(it) },
                                onOpenExactAlarmSettings = { viewModel.openSystemSettings("exact_alarm") },
                                onOpenNotificationSettings = { viewModel.openSystemSettings("app") }
                            )
                        }

                        composable(JarvisRoutes.STATUS) {
                            StatusScreen(
                                telemetry = telemetry,
                                permissions = permissions,
                                settings = settings,
                                ttsReady = ttsReady,
                                ttsVoices = ttsVoices,
                                voiceActivationState = voiceActivationState,
                                voiceActivationDetail = voiceActivationDetail,
                                notifications = capturedNotifications,
                                onRefreshDiagnostics = { viewModel.refreshDiagnostics() },
                                onToggleFlashlight = { viewModel.toggleFlashlight() },
                                onSetVolume = { s, p -> viewModel.setStreamVolume(s, p) },
                                onOpenNotificationSettings = { viewModel.openSystemSettings("notifications") },
                                onSummarizeNotifications = {
                                    viewModel.summarizeNotifications()
                                    navigateTo(JarvisRoutes.CHAT)
                                },
                                onReadNotificationsAloud = { viewModel.readNotificationsAloud() },
                                onClearNotifications = { viewModel.clearCapturedNotifications() }
                            )
                        }

                        composable(JarvisRoutes.SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                permissions = permissions,
                                ttsVoices = ttsVoices,
                                voiceActivationState = voiceActivationState,
                                voiceActivationDetail = voiceActivationDetail,
                                openWakeWordReadiness = openWakeWordReadiness,
                                hasOpenAiKey = viewModel.hasOpenAiKey(),
                                onUpdateSettings = { viewModel.updateSettings(it) },
                                onSetVoiceActivationEnabled = { viewModel.setVoiceActivationEnabled(it) },
                                onSetVoiceActivationMode = { viewModel.setVoiceActivationMode(it) },
                                onImportOpenWakeWordModel = { slot, uri -> viewModel.importOpenWakeWordModel(slot, uri) },
                                onClearOpenWakeWordModels = { viewModel.clearOpenWakeWordModels() },
                                onMicPermissionDenied = { viewModel.onMicrophonePermissionDenied() },
                                onSaveOpenAiKey = { viewModel.saveOpenAiKey(it) },
                                onOpenSystemSettings = { viewModel.openSystemSettings(it) },
                                onClearConversations = { viewModel.clearAllConversations() },
                                onClearMemories = { viewModel.clearAllMemories() }
                            )
                        }
                    }
                }
            }
        }
    }
}
