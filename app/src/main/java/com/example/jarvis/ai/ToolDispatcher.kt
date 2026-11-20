package com.example.jarvis.ai

import com.example.jarvis.accessibility.AccessibilityBridge
import com.example.jarvis.apps.InstalledAppsManager
import com.example.jarvis.automation.AutomationEngine
import com.example.jarvis.database.CapturedNotificationDao
import com.example.jarvis.database.MemoryEntity
import com.example.jarvis.database.ReminderEntity
import com.example.jarvis.device.DeviceControlManager
import com.example.jarvis.memory.MemoryRepository
import com.example.jarvis.notifications.NotificationAccessBridge
import com.example.jarvis.reminders.ReminderScheduler
import com.example.jarvis.search.WebSearchClient
import com.example.jarvis.security.InputValidator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class ToolDispatcher(
    private val appsManager: InstalledAppsManager,
    private val deviceControl: DeviceControlManager,
    private val automationEngine: AutomationEngine,
    private val memoryRepository: MemoryRepository,
    private val reminderScheduler: ReminderScheduler,
    private val webSearchClient: WebSearchClient,
    private val notificationDao: CapturedNotificationDao,
    private val isNotificationReadingEnabled: () -> Boolean
) {

    suspend fun dispatch(
        toolName: String,
        args: Map<String, JsonElement>,
        confirmedBypass: Boolean = false
    ): ToolExecutionOutput {
        val start = System.currentTimeMillis()
        val spec = ToolRegistry.findSpec(toolName)
        if (spec == null) {
            return failureOutput(toolName, "Unknown or unregistered tool '$toolName'.")
        }

        // Permission gate checks
        if (spec.permissionRequirement == ToolPermissionRequirement.ACCESSIBILITY &&
            AccessibilityBridge.getService() == null
        ) {
            val msg = "JARVIS Accessibility Service is not enabled. Please enable it in Settings -> Permissions -> Accessibility."
            automationEngine.logToolExecution(toolName, msg, false, System.currentTimeMillis() - start)
            return failureOutput(toolName, msg)
        }

        if (spec.permissionRequirement == ToolPermissionRequirement.NOTIFICATION_LISTENER &&
            (!isNotificationReadingEnabled() || AccessibilityBridge.let { false })
        ) {
            // Check NotificationAccessBridge inside branch below for detailed message
        }

        val output = when (toolName) {
            "open_app" -> handleOpenApp(args)
            "list_installed_apps" -> handleListApps(args)
            "search_installed_apps" -> handleSearchApps(args)
            "get_battery_status" -> handleBatteryStatus()
            "get_device_status" -> handleDeviceStatus()
            "get_volume" -> handleGetVolume(args)
            "set_volume" -> handleSetVolume(args)
            "toggle_flashlight" -> handleToggleFlashlight(args)
            "open_settings" -> handleOpenSettings(args)
            "open_website" -> handleOpenWebsite(args)
            "search_web" -> handleSearchWeb(args)
            "create_note" -> handleCreateNote(args)
            "retrieve_memory" -> handleRetrieveMemory(args)
            "save_memory" -> handleSaveMemory(args)
            "set_reminder" -> handleSetReminder(args)
            "set_alarm" -> handleSetAlarm(args)
            "prepare_message" -> handlePrepareMessage(args, confirmedBypass)
            "dial_number" -> handleDialNumber(args)
            "read_notifications" -> handleReadNotifications(args)
            "accessibility_inspect" -> handleAccessibilityInspect()
            "accessibility_click" -> handleAccessibilityClick(args)
            "accessibility_type" -> handleAccessibilityType(args)
            "accessibility_scroll" -> handleAccessibilityScroll(args)
            "execute_automation" -> handleExecuteAutomation(args)
            else -> failureOutput(toolName, "Unhandled tool '$toolName'.")
        }

        val elapsed = System.currentTimeMillis() - start
        if (toolName != "execute_automation") {
            automationEngine.logToolExecution(toolName, output.summary, output.success, elapsed)
        }
        return output
    }

    suspend fun executeConfirmedAction(pending: PendingConfirmation): ToolExecutionOutput {
        val jsonArgs = pending.arguments.mapValues { (_, v) ->
            kotlinx.serialization.json.JsonPrimitive(v)
        }
        return dispatch(pending.toolName, jsonArgs, confirmedBypass = true)
    }

    private suspend fun handleOpenApp(args: Map<String, JsonElement>): ToolExecutionOutput {
        val appName = str(args, "app_name")
            ?: return failureOutput("open_app", "Missing required parameter 'app_name'.")
        val res = appsManager.launchApp(appName)
        return ToolExecutionOutput(
            toolName = "open_app",
            success = res.success,
            summary = res.message,
            payload = buildJsonObject {
                put("success", res.success)
                put("appName", res.appName)
                put("packageName", res.packageName ?: "")
                put("message", res.message)
            }
        )
    }

    private suspend fun handleListApps(args: Map<String, JsonElement>): ToolExecutionOutput {
        val limit = int(args, "limit")?.coerceIn(1, 100) ?: 25
        val apps = appsManager.getLaunchableApps().take(limit)
        return ToolExecutionOutput(
            toolName = "list_installed_apps",
            success = true,
            summary = "Found ${apps.size} installed launchable applications.",
            payload = buildJsonObject {
                put("success", true)
                put("count", apps.size)
                put("apps", buildJsonArray {
                    apps.forEach { app ->
                        add(buildJsonObject {
                            put("name", app.appName)
                            put("packageName", app.packageName)
                        })
                    }
                })
            }
        )
    }

    private suspend fun handleSearchApps(args: Map<String, JsonElement>): ToolExecutionOutput {
        val query = str(args, "query")
            ?: return failureOutput("search_installed_apps", "Missing 'query'.")
        val matches = appsManager.searchApps(query).take(20)
        return ToolExecutionOutput(
            toolName = "search_installed_apps",
            success = true,
            summary = "Found ${matches.size} installed app(s) matching '$query'.",
            payload = buildJsonObject {
                put("success", true)
                put("matches", buildJsonArray {
                    matches.forEach { app ->
                        add(buildJsonObject {
                            put("name", app.appName)
                            put("packageName", app.packageName)
                        })
                    }
                })
            }
        )
    }

    private fun handleBatteryStatus(): ToolExecutionOutput {
        val b = deviceControl.getBatteryTelemetry()
        val summary = "Battery is at ${b.percentage}% (${b.chargingSource}, ${b.temperatureCelsius}°C, health: ${b.healthLabel})."
        return ToolExecutionOutput(
            toolName = "get_battery_status",
            success = true,
            summary = summary,
            payload = buildJsonObject {
                put("success", true)
                put("percentage", b.percentage)
                put("isCharging", b.isCharging)
                put("chargingSource", b.chargingSource)
                put("temperatureCelsius", b.temperatureCelsius)
                put("voltageMv", b.voltageMv)
                put("health", b.healthLabel)
            }
        )
    }

    private fun handleDeviceStatus(): ToolExecutionOutput {
        val d = deviceControl.getDeviceTelemetry()
        val summary = "${d.manufacturer} ${d.model} (Android ${d.androidVersion}), Battery ${d.battery.percentage}%, Network ${d.networkType}, RAM ${d.ramUsedMb}/${d.ramTotalMb} MB."
        return ToolExecutionOutput(
            toolName = "get_device_status",
            success = true,
            summary = summary,
            payload = buildJsonObject {
                put("success", true)
                put("device", "${d.manufacturer} ${d.model}")
                put("androidVersion", d.androidVersion)
                put("sdkInt", d.sdkInt)
                put("networkType", d.networkType)
                put("isConnected", d.isConnected)
                put("batteryPercent", d.battery.percentage)
                put("isCharging", d.battery.isCharging)
                put("ramUsedMb", d.ramUsedMb)
                put("ramTotalMb", d.ramTotalMb)
                put("storageFreeGb", String.format(Locale.US, "%.1f", d.storageFreeGb))
                put("storageTotalGb", String.format(Locale.US, "%.1f", d.storageTotalGb))
                put("mediaVolumePercent", d.mediaVolumePercent)
                put("isTorchOn", d.isTorchOn)
            }
        )
    }

    private fun handleGetVolume(args: Map<String, JsonElement>): ToolExecutionOutput {
        val streamName = str(args, "stream") ?: "media"
        val streamType = when (streamName.lowercase()) {
            "ring" -> android.media.AudioManager.STREAM_RING
            "alarm" -> android.media.AudioManager.STREAM_ALARM
            "notification" -> android.media.AudioManager.STREAM_NOTIFICATION
            else -> android.media.AudioManager.STREAM_MUSIC
        }
        val pct = deviceControl.getVolumePercent(streamType)
        return ToolExecutionOutput(
            toolName = "get_volume",
            success = true,
            summary = "$streamName volume is at $pct%.",
            payload = buildJsonObject {
                put("success", true)
                put("stream", streamName)
                put("percent", pct)
            }
        )
    }

    private fun handleSetVolume(args: Map<String, JsonElement>): ToolExecutionOutput {
        val percent = int(args, "percent")
            ?: return failureOutput("set_volume", "Missing integer 'percent'.")
        val stream = str(args, "stream") ?: "media"
        val res = deviceControl.setVolumePercent(percent, stream)
        return res.fold(
            onSuccess = { actual ->
                ToolExecutionOutput(
                    toolName = "set_volume",
                    success = true,
                    summary = "Set $stream volume to $actual%.",
                    payload = buildJsonObject {
                        put("success", true)
                        put("stream", stream)
                        put("percent", actual)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("set_volume", err.localizedMessage ?: "Failed to adjust volume.")
            }
        )
    }

    private fun handleToggleFlashlight(args: Map<String, JsonElement>): ToolExecutionOutput {
        val enabled = bool(args, "enabled")
        val res = deviceControl.setFlashlight(enabled)
        return res.fold(
            onSuccess = { isOn ->
                val stateLabel = if (isOn) "ON" else "OFF"
                ToolExecutionOutput(
                    toolName = "toggle_flashlight",
                    success = true,
                    summary = "Flashlight turned $stateLabel.",
                    payload = buildJsonObject {
                        put("success", true)
                        put("flashlightOn", isOn)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("toggle_flashlight", err.localizedMessage ?: "Failed to toggle flashlight.")
            }
        )
    }

    private fun handleOpenSettings(args: Map<String, JsonElement>): ToolExecutionOutput {
        val section = str(args, "section") ?: "settings"
        val res = deviceControl.openSystemSettings(section)
        return res.fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "open_settings",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject {
                        put("success", true)
                        put("section", section)
                        put("message", msg)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("open_settings", err.localizedMessage ?: "Could not open settings.")
            }
        )
    }

    private fun handleOpenWebsite(args: Map<String, JsonElement>): ToolExecutionOutput {
        val url = str(args, "url")
            ?: return failureOutput("open_website", "Missing 'url'.")
        val res = deviceControl.openWebsite(url)
        return res.fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "open_website",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject {
                        put("success", true)
                        put("url", url)
                        put("message", msg)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("open_website", err.localizedMessage ?: "Invalid URL.")
            }
        )
    }

    private suspend fun handleSearchWeb(args: Map<String, JsonElement>): ToolExecutionOutput {
        val query = str(args, "query")
            ?: return failureOutput("search_web", "Missing 'query'.")
        val res = webSearchClient.search(query)
        return res.fold(
            onSuccess = { list ->
                val summary = "Found ${list.size} web result(s) for '$query'."
                ToolExecutionOutput(
                    toolName = "search_web",
                    success = true,
                    summary = summary,
                    payload = buildJsonObject {
                        put("success", true)
                        put("query", query)
                        put("results", buildJsonArray {
                            list.forEach { item ->
                                add(buildJsonObject {
                                    put("title", item.title)
                                    put("snippet", item.snippet)
                                    put("url", item.url)
                                    put("source", item.source)
                                })
                            }
                        })
                    }
                )
            },
            onFailure = { err ->
                failureOutput("search_web", err.localizedMessage ?: "Web search failed.")
            }
        )
    }

    private suspend fun handleCreateNote(args: Map<String, JsonElement>): ToolExecutionOutput {
        val title = str(args, "title") ?: "Note"
        val content = str(args, "content")
            ?: return failureOutput("create_note", "Note content cannot be empty.")
        val id = memoryRepository.saveMemory(title, content, MemoryEntity.CATEGORY_NOTE)
        return ToolExecutionOutput(
            toolName = "create_note",
            success = true,
            summary = "Saved note '$title'.",
            payload = buildJsonObject {
                put("success", true)
                put("id", id)
                put("title", title)
                put("content", content)
            }
        )
    }

    private suspend fun handleRetrieveMemory(args: Map<String, JsonElement>): ToolExecutionOutput {
        val query = str(args, "query").orEmpty()
        val memories = memoryRepository.retrieveRelevantMemories(query)
        return ToolExecutionOutput(
            toolName = "retrieve_memory",
            success = true,
            summary = "Retrieved ${memories.size} memory record(s).",
            payload = buildJsonObject {
                put("success", true)
                put("count", memories.size)
                put("memories", buildJsonArray {
                    memories.forEach { m ->
                        add(buildJsonObject {
                            put("key", m.key)
                            put("content", m.content)
                            put("category", m.category)
                        })
                    }
                })
            }
        )
    }

    private suspend fun handleSaveMemory(args: Map<String, JsonElement>): ToolExecutionOutput {
        val key = str(args, "key") ?: "user_preference"
        val content = str(args, "content")
            ?: return failureOutput("save_memory", "Missing memory 'content'.")
        val category = str(args, "category") ?: MemoryEntity.CATEGORY_PREFERENCE
        val id = memoryRepository.saveMemory(key, content, category)
        return ToolExecutionOutput(
            toolName = "save_memory",
            success = true,
            summary = "Saved to memory ($key): $content",
            payload = buildJsonObject {
                put("success", true)
                put("id", id)
                put("key", key)
                put("content", content)
                put("category", category)
            }
        )
    }

    private suspend fun handleSetReminder(args: Map<String, JsonElement>): ToolExecutionOutput {
        val title = str(args, "title") ?: "JARVIS Reminder"
        val minutesFromNow = int(args, "minutes_from_now")
        val hour24 = int(args, "hour_24")
        val minute = int(args, "minute") ?: 0
        val daysOffset = int(args, "days_offset") ?: 0
        val repeat = str(args, "repeat") ?: ReminderEntity.REPEAT_NONE

        val triggerMillis = when {
            minutesFromNow != null && minutesFromNow > 0 ->
                System.currentTimeMillis() + minutesFromNow * 60_000L
            hour24 != null -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DAY_OF_YEAR, daysOffset.coerceAtLeast(0))
                    set(Calendar.HOUR_OF_DAY, hour24.coerceIn(0, 23))
                    set(Calendar.MINUTE, minute.coerceIn(0, 59))
                }
                if (cal.timeInMillis <= System.currentTimeMillis() && daysOffset == 0) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                cal.timeInMillis
            }
            else -> System.currentTimeMillis() + 15 * 60_000L
        }

        val scheduled = reminderScheduler.scheduleReminder(title, triggerMillis, repeat)
        val formattedTime = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
            .format(Date(scheduled.triggerAtMillis))
        val exactNote = if (scheduled.exactScheduled) "exact alarm" else "inexact alarm (grant Exact Alarms in Settings for pinpoint timing)"
        val summary = "Scheduled reminder '${scheduled.title}' for $formattedTime ($exactNote)."
        return ToolExecutionOutput(
            toolName = "set_reminder",
            success = true,
            summary = summary,
            payload = buildJsonObject {
                put("success", true)
                put("reminderId", scheduled.reminderId)
                put("title", scheduled.title)
                put("formattedTime", formattedTime)
                put("exactScheduled", scheduled.exactScheduled)
            }
        )
    }

    private fun handleSetAlarm(args: Map<String, JsonElement>): ToolExecutionOutput {
        val hour = int(args, "hour_24")
            ?: return failureOutput("set_alarm", "Missing 'hour_24'.")
        val minute = int(args, "minute") ?: 0
        val label = str(args, "label") ?: "JARVIS Alarm"
        val res = deviceControl.createSystemAlarm(hour, minute, label)
        return res.fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "set_alarm",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject {
                        put("success", true)
                        put("message", msg)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("set_alarm", err.localizedMessage ?: "Failed to create alarm.")
            }
        )
    }

    private fun handlePrepareMessage(
        args: Map<String, JsonElement>,
        confirmedBypass: Boolean
    ): ToolExecutionOutput {
        val platform = str(args, "platform") ?: "whatsapp"
        val recipient = str(args, "recipient") ?: "Contact"
        val message = str(args, "message")
            ?: return failureOutput("prepare_message", "Message text cannot be empty.")

        if (!confirmedBypass) {
            val confirmation = PendingConfirmation(
                id = UUID.randomUUID().toString(),
                toolName = "prepare_message",
                title = "Confirm ${platform.replaceFirstChar { it.uppercase() }} Message",
                description = "Open $platform composer for $recipient with:\n\"$message\"",
                arguments = mapOf(
                    "platform" to platform,
                    "recipient" to recipient,
                    "message" to message
                )
            )
            return ToolExecutionOutput(
                toolName = "prepare_message",
                success = true,
                summary = "Prepared draft message for $recipient. Awaiting explicit user confirmation before opening $platform.",
                payload = buildJsonObject {
                    put("success", true)
                    put("awaitingConfirmation", true)
                    put("platform", platform)
                    put("recipient", recipient)
                    put("message", message)
                },
                pendingConfirmation = confirmation
            )
        }

        val res = deviceControl.prepareMessageIntent(platform, recipient, message)
        return res.fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "prepare_message",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject {
                        put("success", true)
                        put("message", msg)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("prepare_message", err.localizedMessage ?: "Failed to open messaging app.")
            }
        )
    }

    private fun handleDialNumber(args: Map<String, JsonElement>): ToolExecutionOutput {
        val number = str(args, "phone_number")
            ?: return failureOutput("dial_number", "Missing 'phone_number'.")
        val res = deviceControl.openDialer(number)
        return res.fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "dial_number",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject {
                        put("success", true)
                        put("message", msg)
                    }
                )
            },
            onFailure = { err ->
                failureOutput("dial_number", err.localizedMessage ?: "Invalid phone number.")
            }
        )
    }

    private suspend fun handleReadNotifications(args: Map<String, JsonElement>): ToolExecutionOutput {
        if (!NotificationAccessBridge.isNotificationAccessEnabled(
                // Checked via static bridge + settings toggle
                com.example.JarvisApplication.appContext
            ) || !isNotificationReadingEnabled()
        ) {
            return failureOutput(
                "read_notifications",
                "Notification Access is disabled. Enable both Android Notification Access and the JARVIS Notification Reader toggle in Settings."
            )
        }
        val limit = int(args, "limit")?.coerceIn(1, 30) ?: 10
        val recent = notificationDao.getRecentNotificationsOnce().take(limit)
        return ToolExecutionOutput(
            toolName = "read_notifications",
            success = true,
            summary = "Retrieved ${recent.size} captured notification(s).",
            payload = buildJsonObject {
                put("success", true)
                put("count", recent.size)
                put("notifications", buildJsonArray {
                    recent.forEach { n ->
                        add(buildJsonObject {
                            put("app", n.appName)
                            put("title", n.title)
                            put("text", n.text)
                        })
                    }
                })
            }
        )
    }

    private fun handleAccessibilityInspect(): ToolExecutionOutput {
        val service = AccessibilityBridge.getService()
            ?: return failureOutput("accessibility_inspect", "Accessibility Service not active.")
        val elements = service.inspectScreen(40)
        return ToolExecutionOutput(
            toolName = "accessibility_inspect",
            success = true,
            summary = "Inspected ${elements.size} accessible UI elements.",
            payload = buildJsonObject {
                put("success", true)
                put("activePackage", AccessibilityBridge.activePackage.value ?: "")
                put("elements", buildJsonArray {
                    elements.forEach { e ->
                        add(buildJsonObject {
                            put("text", e.text)
                            put("description", e.contentDescription)
                            put("clickable", e.isClickable)
                            put("editable", e.isEditable)
                        })
                    }
                })
            }
        )
    }

    private fun handleAccessibilityClick(args: Map<String, JsonElement>): ToolExecutionOutput {
        val service = AccessibilityBridge.getService()
            ?: return failureOutput("accessibility_click", "Accessibility Service not active.")
        val target = str(args, "target")
            ?: return failureOutput("accessibility_click", "Missing 'target'.")
        return service.findAndClick(target).fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "accessibility_click",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject { put("success", true); put("message", msg) }
                )
            },
            onFailure = { err ->
                failureOutput("accessibility_click", err.localizedMessage ?: "Click failed.")
            }
        )
    }

    private fun handleAccessibilityType(args: Map<String, JsonElement>): ToolExecutionOutput {
        val service = AccessibilityBridge.getService()
            ?: return failureOutput("accessibility_type", "Accessibility Service not active.")
        val text = str(args, "text")
            ?: return failureOutput("accessibility_type", "Missing 'text'.")
        if (InputValidator.containsSensitiveAuthPattern(text)) {
            return failureOutput("accessibility_type", "Blocked entering sensitive credential/CAPTCHA text.")
        }
        val targetField = str(args, "target_field")
        val submit = bool(args, "submit") ?: false
        return service.enterText(targetField, text, submit).fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "accessibility_type",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject { put("success", true); put("message", msg) }
                )
            },
            onFailure = { err ->
                failureOutput("accessibility_type", err.localizedMessage ?: "Text entry failed.")
            }
        )
    }

    private fun handleAccessibilityScroll(args: Map<String, JsonElement>): ToolExecutionOutput {
        val service = AccessibilityBridge.getService()
            ?: return failureOutput("accessibility_scroll", "Accessibility Service not active.")
        val direction = str(args, "direction") ?: "down"
        return service.scroll(direction).fold(
            onSuccess = { msg ->
                ToolExecutionOutput(
                    toolName = "accessibility_scroll",
                    success = true,
                    summary = msg,
                    payload = buildJsonObject { put("success", true); put("message", msg) }
                )
            },
            onFailure = { err ->
                failureOutput("accessibility_scroll", err.localizedMessage ?: "Scroll failed.")
            }
        )
    }

    private suspend fun handleExecuteAutomation(args: Map<String, JsonElement>): ToolExecutionOutput {
        val appName = str(args, "app_name")
            ?: return failureOutput("execute_automation", "Missing 'app_name'.")
        val query = str(args, "search_query")
            ?: return failureOutput("execute_automation", "Missing 'search_query'.")
        val res = automationEngine.executeAppSearchWorkflow(appName, query)
        return ToolExecutionOutput(
            toolName = "execute_automation",
            success = res.success,
            summary = res.summary,
            payload = buildJsonObject {
                put("success", res.success)
                put("workflow", res.workflowName)
                put("completedSteps", res.completedSteps)
                put("totalSteps", res.totalSteps)
                put("summary", res.summary)
                put("details", buildJsonArray {
                    res.stepDetails.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            }
        )
    }

    private fun failureOutput(toolName: String, message: String): ToolExecutionOutput {
        return ToolExecutionOutput(
            toolName = toolName,
            success = false,
            summary = message,
            payload = buildJsonObject {
                put("success", false)
                put("error", message)
            }
        )
    }

    private fun str(args: Map<String, JsonElement>, key: String): String? =
        args[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun int(args: Map<String, JsonElement>, key: String): Int? =
        args[key]?.jsonPrimitive?.intOrNull ?: args[key]?.jsonPrimitive?.content?.toIntOrNull()

    private fun bool(args: Map<String, JsonElement>, key: String): Boolean? =
        args[key]?.jsonPrimitive?.booleanOrNull
            ?: args[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
}
