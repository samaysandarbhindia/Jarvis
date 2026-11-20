package com.example.jarvis.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jarvis.accessibility.AccessibleElementInfo
import com.example.jarvis.ai.ToolSpec
import com.example.jarvis.automation.AutomationResult
import com.example.jarvis.database.AutomationLogEntity
import com.example.jarvis.permissions.PermissionStatusSnapshot
import com.example.jarvis.ui.components.GlassHudCard
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.StarkAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomationTab(
    permissions: PermissionStatusSnapshot,
    inspectedElements: List<AccessibleElementInfo>,
    lastResult: AutomationResult?,
    logs: List<AutomationLogEntity>,
    registeredTools: List<ToolSpec>,
    onOpenAccessibilitySettings: () -> Unit,
    onRunWorkflow: (String, String) -> Unit,
    onInspectScreen: () -> Unit,
    onClearLogs: () -> Unit
) {
    var targetApp by remember { mutableStateOf("YouTube") }
    var searchQuery by remember { mutableStateOf("Iron Man") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Accessibility Status Card
        item {
            val connected = permissions.accessibilityConnected
            GlassHudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (connected) EmeraldOnline.copy(alpha = 0.6f) else StarkAmber.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Accessibility status",
                            tint = if (connected) EmeraldOnline else StarkAmber
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (connected) "Accessibility Engine Connected" else "Accessibility Service Inactive",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (connected) EmeraldOnline else StarkAmber
                            )
                            Text(
                                text = if (connected) {
                                    "Multi-step UI inspection & automation ready."
                                } else {
                                    "Enable JARVIS Automation Engine in Android Settings to allow in-app automation."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onOpenAccessibilitySettings) {
                        Text("Settings")
                    }
                }
            }
        }

        // Multi-step Workflow Runner
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MULTI-STEP AUTOMATION RUNNER", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetApp,
                        onValueChange = { targetApp = it },
                        label = { Text("Target App") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Query") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRunWorkflow(targetApp, searchQuery) },
                        enabled = targetApp.isNotBlank() && searchQuery.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("run_automation_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Execute Workflow")
                    }
                    OutlinedButton(
                        onClick = onInspectScreen,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Inspect UI")
                    }
                }

                lastResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${res.workflowName}: ${res.summary}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (res.success) EmeraldOnline else CrimsonAlert
                    )
                    res.stepDetails.forEach { step ->
                        Text(
                            text = "• $step",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                    }
                }
            }
        }

        if (inspectedElements.isNotEmpty()) {
            item {
                GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "INSPECTED ACCESSIBLE NODES (${inspectedElements.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    inspectedElements.take(12).forEach { el ->
                        val label = el.text.ifBlank { el.contentDescription }.ifBlank { el.viewId }
                        Text(
                            text = "[${el.className}] $label (click=${el.isClickable}, edit=${el.isEditable})",
                            style = MaterialTheme.typography.labelSmall,
                            color = HudTextSecondary
                        )
                    }
                }
            }
        }

        // Registered Skills / AI Tools
        item {
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "REGISTERED NATIVE SKILLS (${registeredTools.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                registeredTools.forEach { spec ->
                    Text(
                        text = "• ${spec.name} — ${spec.description}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // Execution Logs Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXECUTION LOGS (${logs.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (logs.isNotEmpty()) {
                    IconButton(onClick = onClearLogs) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear automation logs",
                            tint = CrimsonAlert
                        )
                    }
                }
            }
        }

        items(logs, key = { it.id }) { log ->
            GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = log.workflowName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (log.status == "SUCCESS") EmeraldOnline else CrimsonAlert
                    )
                    Text(
                        text = "${log.durationMs}ms · ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HudTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = log.stepDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = HudTextSecondary
                )
            }
        }
    }
}
