package com.example.jarvis.ui.hub

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.jarvis.database.MemoryEntity
import com.example.jarvis.ui.components.GlassHudCard
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudTextSecondary
import kotlinx.coroutines.launch

@Composable
fun MemoryTab(
    memories: List<MemoryEntity>,
    onSave: (String, String, String) -> Unit,
    onUpdate: (MemoryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit,
    onExportJson: suspend () -> String,
    onImportJson: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntity?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = onExportJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                statusMsg = "Exported ${memories.size} memories."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()
            if (json.isNotBlank()) {
                onImportJson(json)
                statusMsg = "Imported memories from JSON file."
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_memory_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Memory")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { exportLauncher.launch("jarvis_memories.json") }) {
                    Icon(Icons.Default.FileUpload, contentDescription = "Export memories JSON")
                }
                IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Import memories JSON")
                }
                if (memories.isNotEmpty()) {
                    IconButton(onClick = { confirmClearAll = true }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Clear all memories",
                            tint = CrimsonAlert
                        )
                    }
                }
            }
        }

        statusMsg?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (memories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassHudCard {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No personal memories stored", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tell JARVIS \"Remember that I prefer Hindi\" or tap Add Memory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(memories, key = { it.id }) { mem ->
                    GlassHudCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${mem.category} · ${mem.key}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = mem.content,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            IconButton(onClick = { editingMemory = mem }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit memory")
                            }
                            IconButton(onClick = { onDelete(mem.id) }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete memory",
                                    tint = CrimsonAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemoryEditorDialog(
            initialKey = "",
            initialContent = "",
            initialCategory = MemoryEntity.CATEGORY_PREFERENCE,
            onDismiss = { showAddDialog = false },
            onConfirm = { k, c, cat ->
                onSave(k, c, cat)
                showAddDialog = false
            }
        )
    }

    editingMemory?.let { mem ->
        MemoryEditorDialog(
            initialKey = mem.key,
            initialContent = mem.content,
            initialCategory = mem.category,
            onDismiss = { editingMemory = null },
            onConfirm = { k, c, cat ->
                onUpdate(mem.copy(key = k, content = c, category = cat))
                editingMemory = null
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear All Memories?") },
            text = { Text("This permanently deletes all personal preferences and notes from the local Room database.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    confirmClearAll = false
                }) {
                    Text("Clear All", color = CrimsonAlert)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MemoryEditorDialog(
    initialKey: String,
    initialContent: String,
    initialCategory: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var key by remember { mutableStateOf(initialKey) }
    var content by remember { mutableStateOf(initialContent) }
    var category by remember { mutableStateOf(initialCategory) }

    val categories = listOf(
        MemoryEntity.CATEGORY_PREFERENCE,
        MemoryEntity.CATEGORY_FACT,
        MemoryEntity.CATEGORY_NOTE,
        MemoryEntity.CATEGORY_SUMMARY
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialKey.isBlank()) "Add Memory" else "Edit Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Memory Key (e.g. preferred_language)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.isNotBlank(),
                onClick = { onConfirm(key.ifBlank { "note" }, content, category) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
