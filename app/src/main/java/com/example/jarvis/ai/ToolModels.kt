package com.example.jarvis.ai

import kotlinx.serialization.json.JsonObject

enum class AssistantState(val displayLabel: String) {
    IDLE("STANDBY"),
    LISTENING("LISTENING"),
    PROCESSING("PROCESSING"),
    EXECUTING("EXECUTING"),
    SPEAKING("SPEAKING"),
    ERROR("ALERT")
}

enum class ToolPermissionRequirement {
    NONE,
    ACCESSIBILITY,
    EXACT_ALARM,
    NOTIFICATION_LISTENER
}

data class ToolSpec(
    val name: String,
    val description: String,
    val permissionRequirement: ToolPermissionRequirement = ToolPermissionRequirement.NONE,
    val requiresConfirmation: Boolean = false
)

data class PendingConfirmation(
    val id: String,
    val toolName: String,
    val title: String,
    val description: String,
    val arguments: Map<String, String>
)

data class ToolExecutionOutput(
    val toolName: String,
    val success: Boolean,
    val summary: String,
    val payload: JsonObject,
    val pendingConfirmation: PendingConfirmation? = null
)
