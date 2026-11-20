package com.example.jarvis.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jarvis.accessibility.AccessibleElementInfo
import com.example.jarvis.ai.ToolSpec
import com.example.jarvis.apps.InstalledAppInfo
import com.example.jarvis.automation.AutomationResult
import com.example.jarvis.database.AutomationLogEntity
import com.example.jarvis.database.MemoryEntity
import com.example.jarvis.database.ReminderEntity
import com.example.jarvis.permissions.PermissionStatusSnapshot
import com.example.ui.theme.JarvisMidnightNavy

enum class HubTab(val label: String) {
    APPS("Apps"),
    MEMORY("Memory"),
    AUTOMATION("Automation & Skills"),
    REMINDERS("Reminders")
}

@Composable
fun HubScreen(
    selectedTab: HubTab,
    onSelectTab: (HubTab) -> Unit,
    installedApps: List<InstalledAppInfo>,
    appsLoading: Boolean,
    onRefreshApps: () -> Unit,
    onLaunchApp: (String) -> Unit,
    memories: List<MemoryEntity>,
    onSaveMemory: (String, String, String) -> Unit,
    onUpdateMemory: (MemoryEntity) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    onExportMemoriesJson: suspend () -> String,
    onImportMemoriesJson: (String) -> Unit,
    permissions: PermissionStatusSnapshot,
    inspectedElements: List<AccessibleElementInfo>,
    lastAutomationResult: AutomationResult?,
    automationLogs: List<AutomationLogEntity>,
    registeredTools: List<ToolSpec>,
    onOpenAccessibilitySettings: () -> Unit,
    onRunAppSearchWorkflow: (String, String) -> Unit,
    onInspectScreen: () -> Unit,
    onClearLogs: () -> Unit,
    reminders: List<ReminderEntity>,
    onCreateReminder: (String, Long, String) -> Unit,
    onToggleReminderDone: (ReminderEntity) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = JarvisMidnightNavy.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            HubTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    modifier = Modifier.testTag("hub_tab_${tab.name.lowercase()}")
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (selectedTab) {
                HubTab.APPS -> AppsTab(
                    apps = installedApps,
                    isLoading = appsLoading,
                    onRefresh = onRefreshApps,
                    onLaunch = onLaunchApp
                )

                HubTab.MEMORY -> MemoryTab(
                    memories = memories,
                    onSave = onSaveMemory,
                    onUpdate = onUpdateMemory,
                    onDelete = onDeleteMemory,
                    onClearAll = onClearAllMemories,
                    onExportJson = onExportMemoriesJson,
                    onImportJson = onImportMemoriesJson
                )

                HubTab.AUTOMATION -> AutomationTab(
                    permissions = permissions,
                    inspectedElements = inspectedElements,
                    lastResult = lastAutomationResult,
                    logs = automationLogs,
                    registeredTools = registeredTools,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onRunWorkflow = onRunAppSearchWorkflow,
                    onInspectScreen = onInspectScreen,
                    onClearLogs = onClearLogs
                )

                HubTab.REMINDERS -> RemindersTab(
                    reminders = reminders,
                    permissions = permissions,
                    onCreateReminder = onCreateReminder,
                    onToggleDone = onToggleReminderDone,
                    onDeleteReminder = onDeleteReminder,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings
                )
            }
        }
    }
}
