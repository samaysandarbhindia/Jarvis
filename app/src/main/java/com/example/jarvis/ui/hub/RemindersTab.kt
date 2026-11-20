package com.example.jarvis.ui.hub

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.jarvis.database.ReminderEntity
import com.example.jarvis.permissions.PermissionStatusSnapshot
import com.example.jarvis.ui.components.GlassHudCard
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.StarkAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemindersTab(
    reminders: List<ReminderEntity>,
    permissions: PermissionStatusSnapshot,
    onCreateReminder: (String, Long, String) -> Unit,
    onToggleDone: (ReminderEntity) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permissions refreshed automatically */ }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACTIVE REMINDERS (${reminders.count { !it.isCompleted }})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Button(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_reminder_button")
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Reminder")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!permissions.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlassHudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = StarkAmber.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Allow Notifications so reminders can alert you.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text("Grant")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!permissions.exactAlarmsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlassHudCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = StarkAmber.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exact Alarm permission not granted (using standard inexact timing).",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onOpenExactAlarmSettings) {
                        Text("Allow")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (reminders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassHudCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No reminders scheduled", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Say \"Remind me to study at 7 PM\" or tap New Reminder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(reminders, key = { it.id }) { rem ->
                    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rem.isCompleted,
                                onCheckedChange = { onToggleDone(rem) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rem.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        textDecoration = if (rem.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                )
                                Text(
                                    text = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
                                        .format(Date(rem.triggerAtMillis)) +
                                        if (rem.repeatInterval != ReminderEntity.REPEAT_NONE) " (${rem.repeatInterval})" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudTextSecondary
                                )
                            }
                            IconButton(onClick = { onDeleteReminder(rem.id) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete reminder",
                                    tint = CrimsonAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var title by remember { mutableStateOf("") }
        var minutesDelay by remember { mutableIntStateOf(15) }
        var repeat by remember { mutableStateOf(ReminderEntity.REPEAT_NONE) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Schedule Reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Reminder Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Trigger Delay:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 15, 30, 60).forEach { mins ->
                            FilterChip(
                                selected = minutesDelay == mins,
                                onClick = { minutesDelay = mins },
                                label = { Text("${mins}m") }
                            )
                        }
                    }
                    Text("Repeat:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ReminderEntity.REPEAT_NONE,
                            ReminderEntity.REPEAT_DAILY,
                            ReminderEntity.REPEAT_WEEKLY
                        ).forEach { rep ->
                            FilterChip(
                                selected = repeat == rep,
                                onClick = { repeat = rep },
                                label = { Text(rep) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        val triggerAt = System.currentTimeMillis() + minutesDelay * 60_000L
                        onCreateReminder(title, triggerAt, repeat)
                        showCreateDialog = false
                    }
                ) {
                    Text("Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
