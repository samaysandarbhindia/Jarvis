package com.example.jarvis.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.jarvis.ai.AssistantState
import com.example.jarvis.ai.PendingConfirmation
import com.example.jarvis.database.ConversationEntity
import com.example.jarvis.database.MessageEntity
import com.example.jarvis.ui.components.ConfirmationBannerCard
import com.example.jarvis.ui.components.GlassHudCard
import com.example.ui.theme.ArcCobalt
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.JarvisDeepBlack
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisPanelNavy
import com.example.ui.theme.StarkAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    isExpandedWidth: Boolean,
    conversations: List<ConversationEntity>,
    activeConversationId: Long?,
    messages: List<MessageEntity>,
    saveConversationsAutomatically: Boolean,
    conversationSearch: String,
    streamingResponse: String,
    assistantState: AssistantState,
    isListening: Boolean,
    isSpeaking: Boolean,
    pendingConfirmations: List<PendingConfirmation>,
    onSearchChanged: (String) -> Unit,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onRenameConversation: (Long, String) -> Unit,
    onDeleteConversation: (Long) -> Unit,
    onSendPrompt: (String) -> Unit,
    onToggleMic: () -> Unit,
    onMicPermissionDenied: () -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onRegenerateLast: () -> Unit,
    onConfirmAction: (PendingConfirmation) -> Unit,
    onDismissAction: (PendingConfirmation) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHistorySheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    Row(modifier = modifier.fillMaxSize()) {
        if (isExpandedWidth) {
            Surface(
                modifier = Modifier
                    .width(290.dp)
                    .fillMaxHeight(),
                color = JarvisPanelNavy.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                ConversationHistoryPane(
                    conversations = conversations,
                    activeId = activeConversationId,
                    searchQuery = conversationSearch,
                    onSearchChanged = onSearchChanged,
                    onSelect = onSelectConversation,
                    onNew = onNewConversation,
                    onRenameRequest = { renameTarget = it },
                    onDelete = onDeleteConversation
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isExpandedWidth) {
                        IconButton(
                            onClick = { showHistorySheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("chat_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Conversation history",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "NEURAL CONSOLE",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!saveConversationsAutomatically) {
                            Text(
                                text = "Ephemeral Privacy Mode (History not saved)",
                                style = MaterialTheme.typography.labelSmall,
                                color = StarkAmber
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSpeaking) {
                        IconButton(onClick = onStopSpeaking) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop speaking",
                                tint = CrimsonAlert
                            )
                        }
                    }
                    if (messages.any { it.role == "USER" } &&
                        assistantState != AssistantState.PROCESSING
                    ) {
                        IconButton(
                            onClick = onRegenerateLast,
                            modifier = Modifier.testTag("regenerate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate last response",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = onNewConversation,
                        modifier = Modifier.testTag("new_conversation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New conversation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Messages List
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size, streamingResponse) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(messages.size - 1)
                }
            }

            if (messages.isEmpty() && streamingResponse.isBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassHudCard(modifier = Modifier.widthIn(max = 420.dp)) {
                        Text(
                            text = "JARVIS Neural Session Ready",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ask a question in English, Hindi, or Hinglish, launch apps, automate workflows, set reminders, or inspect device status.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HudTextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message = msg,
                            onSpeak = { onSpeakText(msg.content) }
                        )
                    }
                    if (streamingResponse.isNotBlank()) {
                        item(key = "streaming") {
                            MessageBubble(
                                message = MessageEntity(
                                    id = -1L,
                                    conversationId = 0L,
                                    role = "ASSISTANT",
                                    content = streamingResponse
                                ),
                                onSpeak = { onSpeakText(streamingResponse) }
                            )
                        }
                    }
                }
            }

            pendingConfirmations.forEach { pending ->
                ConfirmationBannerCard(
                    pending = pending,
                    onConfirm = { onConfirmAction(pending) },
                    onCancel = { onDismissAction(pending) },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            ChatInputBar(
                isListening = isListening,
                onToggleMic = onToggleMic,
                onMicPermissionDenied = onMicPermissionDenied,
                onSend = onSendPrompt
            )
        }
    }

    if (showHistorySheet && !isExpandedWidth) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = JarvisPanelNavy
        ) {
            Box(modifier = Modifier.height(480.dp)) {
                ConversationHistoryPane(
                    conversations = conversations,
                    activeId = activeConversationId,
                    searchQuery = conversationSearch,
                    onSearchChanged = onSearchChanged,
                    onSelect = {
                        onSelectConversation(it)
                        showHistorySheet = false
                    },
                    onNew = {
                        onNewConversation()
                        showHistorySheet = false
                    },
                    onRenameRequest = { renameTarget = it },
                    onDelete = onDeleteConversation
                )
            }
        }
    }

    renameTarget?.let { target ->
        var newTitle by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Conversation") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameConversation(target.id, newTitle)
                    renameTarget = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    onSpeak: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == "USER"
    val isTool = message.role == "TOOL"

    if (isTool) {
        val ok = message.toolStatus == "SUCCESS"
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = JarvisPanelElevated.copy(alpha = 0.65f),
            border = BorderStroke(
                1.dp,
                if (ok) EmeraldOnline.copy(alpha = 0.4f) else CrimsonAlert.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Tool execution",
                    tint = if (ok) EmeraldOnline else CrimsonAlert,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.toolName ?: "native_tool",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (ok) EmeraldOnline else CrimsonAlert
                    )
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary
                    )
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) ArcCobalt.copy(alpha = 0.25f) else JarvisPanelNavy.copy(alpha = 0.9f),
            border = BorderStroke(
                1.dp,
                if (isUser) ArcCobalt.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(message.content)) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                tint = HudTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak response",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    isListening: Boolean,
    onToggleMic: () -> Unit,
    onMicPermissionDenied: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onToggleMic() else onMicPermissionDenied()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) onToggleMic() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Voice input",
                tint = if (isListening) CrimsonAlert else MaterialTheme.colorScheme.primary
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .testTag("chat_input_field"),
            placeholder = { Text("Message JARVIS...") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    if (text.isNotBlank()) {
                        onSend(text)
                        text = ""
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = JarvisPanelNavy,
                unfocusedContainerColor = JarvisPanelNavy.copy(alpha = 0.6f)
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilledIconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = JarvisDeepBlack
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("chat_send_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
        }
    }
}

@Composable
private fun ConversationHistoryPane(
    conversations: List<ConversationEntity>,
    activeId: Long?,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onRenameRequest: (ConversationEntity) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SESSIONS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search sessions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (conversations.isEmpty()) {
            Text(
                text = "No saved conversations yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = HudTextSecondary
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(conversations, key = { it.id }) { conv ->
                    val selected = conv.id == activeId
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) JarvisPanelElevated else JarvisPanelNavy,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(conv.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                        .format(Date(conv.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HudTextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onRenameRequest(conv) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Rename conversation",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(conv.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete conversation",
                                    tint = CrimsonAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
