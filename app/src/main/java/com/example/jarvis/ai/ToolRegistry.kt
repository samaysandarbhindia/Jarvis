package com.example.jarvis.ai

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema

object ToolRegistry {

    val specs: List<ToolSpec> = listOf(
        ToolSpec("open_app", "Launch an installed Android application by name or package name."),
        ToolSpec("list_installed_apps", "List launchable applications installed on the device."),
        ToolSpec("search_installed_apps", "Search installed applications by keyword."),
        ToolSpec("get_battery_status", "Read actual Android battery percentage, charging status, temperature, and health."),
        ToolSpec("get_device_status", "Read actual Android device info, RAM, storage, connectivity, and volume levels."),
        ToolSpec("get_volume", "Get current volume percentage for media, ring, alarm, or notification."),
        ToolSpec("set_volume", "Set Android volume percentage (0-100) for media, ring, alarm, or notification."),
        ToolSpec("toggle_flashlight", "Turn the device camera flashlight ON or OFF using CameraManager."),
        ToolSpec("open_settings", "Open a specific Android system settings screen (wifi, bluetooth, location, accessibility, notifications, battery, app)."),
        ToolSpec("open_website", "Open an HTTP/HTTPS URL in the Android browser."),
        ToolSpec("search_web", "Search the web via HTTPS and return real article summaries and source URLs."),
        ToolSpec("create_note", "Save a personal note to JARVIS local Room memory."),
        ToolSpec("retrieve_memory", "Retrieve saved user preferences, facts, or notes from Room memory."),
        ToolSpec("save_memory", "Save or update a user preference or fact in Room memory."),
        ToolSpec("set_reminder", "Schedule a real Android reminder notification at a specified time using AlarmManager."),
        ToolSpec("set_alarm", "Create an alarm in the Android Clock app for a given hour and minute."),
        ToolSpec("prepare_message", "Prepare a WhatsApp, SMS, or shared message for a recipient (requires user confirmation before opening composer).", requiresConfirmation = true),
        ToolSpec("dial_number", "Open the Android Phone dialer pre-filled with a phone number."),
        ToolSpec("read_notifications", "Read recent captured notifications when Notification Access is enabled.", permissionRequirement = ToolPermissionRequirement.NOTIFICATION_LISTENER),
        ToolSpec("accessibility_inspect", "Inspect visible UI elements on the active screen via AccessibilityService.", permissionRequirement = ToolPermissionRequirement.ACCESSIBILITY),
        ToolSpec("accessibility_click", "Click a visible button or UI element matching text/description via AccessibilityService.", permissionRequirement = ToolPermissionRequirement.ACCESSIBILITY),
        ToolSpec("accessibility_type", "Enter text into an accessible input field via AccessibilityService.", permissionRequirement = ToolPermissionRequirement.ACCESSIBILITY),
        ToolSpec("accessibility_scroll", "Scroll the active screen up or down via AccessibilityService.", permissionRequirement = ToolPermissionRequirement.ACCESSIBILITY),
        ToolSpec("execute_automation", "Execute a multi-step app launch and in-app search/interaction workflow via AccessibilityService.", permissionRequirement = ToolPermissionRequirement.ACCESSIBILITY)
    )

    val functionDeclarations: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "open_app",
            description = "Launch an installed Android application by its display name (e.g. YouTube, WhatsApp, Chrome, Instagram, Settings, Camera, Calculator) or package name.",
            parameters = mapOf(
                "app_name" to Schema.string("The application name or package name to open.")
            )
        ),
        FunctionDeclaration(
            name = "list_installed_apps",
            description = "List launchable Android applications installed on this device.",
            parameters = mapOf(
                "limit" to Schema.integer("Maximum number of apps to return (default 25).")
            ),
            optionalParameters = listOf("limit")
        ),
        FunctionDeclaration(
            name = "search_installed_apps",
            description = "Search installed launchable Android applications matching a keyword.",
            parameters = mapOf(
                "query" to Schema.string("Search keyword for app name or package.")
            )
        ),
        FunctionDeclaration(
            name = "get_battery_status",
            description = "Read actual Android battery percentage, charging status, power source, temperature, and health.",
            parameters = emptyMap()
        ),
        FunctionDeclaration(
            name = "get_device_status",
            description = "Read actual device model, Android version, network connectivity, RAM usage, storage, flashlight state, and audio volumes.",
            parameters = emptyMap()
        ),
        FunctionDeclaration(
            name = "get_volume",
            description = "Read current Android volume level percentage for an audio stream.",
            parameters = mapOf(
                "stream" to Schema.enumeration(
                    listOf("media", "ring", "alarm", "notification"),
                    description = "Audio stream name."
                )
            ),
            optionalParameters = listOf("stream")
        ),
        FunctionDeclaration(
            name = "set_volume",
            description = "Adjust Android volume percentage (0 to 100) for a stream.",
            parameters = mapOf(
                "percent" to Schema.integer("Volume percentage between 0 and 100."),
                "stream" to Schema.enumeration(
                    listOf("media", "ring", "alarm", "notification"),
                    description = "Audio stream name (defaults to media)."
                )
            ),
            optionalParameters = listOf("stream")
        ),
        FunctionDeclaration(
            name = "toggle_flashlight",
            description = "Turn the device camera flashlight ON or OFF using Android CameraManager.",
            parameters = mapOf(
                "enabled" to Schema.boolean("True to turn flashlight on, false to turn off. Omit to toggle.")
            ),
            optionalParameters = listOf("enabled")
        ),
        FunctionDeclaration(
            name = "open_settings",
            description = "Open an Android system settings page (wifi, bluetooth, location, accessibility, notifications, battery, sound, display, app).",
            parameters = mapOf(
                "section" to Schema.string("Settings section name, e.g. wifi, bluetooth, location, accessibility, notifications, battery, app.")
            )
        ),
        FunctionDeclaration(
            name = "open_website",
            description = "Open a valid HTTP or HTTPS website URL in the default browser.",
            parameters = mapOf(
                "url" to Schema.string("The website URL to open.")
            )
        ),
        FunctionDeclaration(
            name = "search_web",
            description = "Search the internet for up-to-date facts, websites, or topics and return actual source links.",
            parameters = mapOf(
                "query" to Schema.string("The search query.")
            )
        ),
        FunctionDeclaration(
            name = "create_note",
            description = "Save a personal note into JARVIS local memory.",
            parameters = mapOf(
                "title" to Schema.string("Short title or key for the note."),
                "content" to Schema.string("Full note content.")
            )
        ),
        FunctionDeclaration(
            name = "retrieve_memory",
            description = "Retrieve saved user preferences, personal facts, or notes from JARVIS memory.",
            parameters = mapOf(
                "query" to Schema.string("Optional keyword to filter memories; empty returns recent memories.")
            ),
            optionalParameters = listOf("query")
        ),
        FunctionDeclaration(
            name = "save_memory",
            description = "Save or update a user preference or important personal fact in persistent Room memory (e.g. language preference, favorite topic).",
            parameters = mapOf(
                "key" to Schema.string("Short snake_case memory key, e.g. preferred_language."),
                "content" to Schema.string("Fact or preference to remember."),
                "category" to Schema.enumeration(
                    listOf("PREFERENCE", "FACT", "NOTE", "SUMMARY"),
                    description = "Category of memory."
                )
            ),
            optionalParameters = listOf("category")
        ),
        FunctionDeclaration(
            name = "set_reminder",
            description = "Schedule a real Android reminder notification at a future time. Provide either minutes_from_now OR hour_24 and minute (plus optional days_offset: 0 for today, 1 for tomorrow).",
            parameters = mapOf(
                "title" to Schema.string("What to remind the user about."),
                "minutes_from_now" to Schema.integer("Delay in minutes from now (if relative, e.g. in 15 minutes)."),
                "hour_24" to Schema.integer("Hour of day in 24-hour format (0-23), e.g. 19 for 7 PM."),
                "minute" to Schema.integer("Minute of hour (0-59)."),
                "days_offset" to Schema.integer("0 for today (or next occurrence), 1 for tomorrow."),
                "repeat" to Schema.enumeration(
                    listOf("NONE", "DAILY", "WEEKLY"),
                    description = "Repeat frequency."
                )
            ),
            optionalParameters = listOf("minutes_from_now", "hour_24", "minute", "days_offset", "repeat")
        ),
        FunctionDeclaration(
            name = "set_alarm",
            description = "Set a system alarm in the Android Clock app for a specific hour and minute.",
            parameters = mapOf(
                "hour_24" to Schema.integer("Hour in 24-hour format (0-23)."),
                "minute" to Schema.integer("Minute (0-59)."),
                "label" to Schema.string("Alarm label.")
            ),
            optionalParameters = listOf("label")
        ),
        FunctionDeclaration(
            name = "prepare_message",
            description = "Prepare a message for a recipient via WhatsApp, SMS, or Share Sheet. Never sends silently; queues explicit user confirmation.",
            parameters = mapOf(
                "platform" to Schema.enumeration(listOf("whatsapp", "sms", "share"), description = "Messaging platform."),
                "recipient" to Schema.string("Recipient name or phone number."),
                "message" to Schema.string("Message text to prepare.")
            )
        ),
        FunctionDeclaration(
            name = "dial_number",
            description = "Open the Android Phone dialer pre-filled with a phone number.",
            parameters = mapOf(
                "phone_number" to Schema.string("Phone number to dial.")
            )
        ),
        FunctionDeclaration(
            name = "read_notifications",
            description = "Read recent non-sensitive notifications captured while JARVIS Notification Access is enabled.",
            parameters = mapOf(
                "limit" to Schema.integer("Maximum number of notifications to retrieve.")
            ),
            optionalParameters = listOf("limit")
        ),
        FunctionDeclaration(
            name = "accessibility_inspect",
            description = "Inspect currently visible interactive UI elements on screen via AccessibilityService.",
            parameters = emptyMap()
        ),
        FunctionDeclaration(
            name = "accessibility_click",
            description = "Click a visible button or element matching a label/description on the active screen via AccessibilityService.",
            parameters = mapOf(
                "target" to Schema.string("Visible text or content description of the button/element to click.")
            )
        ),
        FunctionDeclaration(
            name = "accessibility_type",
            description = "Type text into an accessible text field on the active screen and optionally submit.",
            parameters = mapOf(
                "text" to Schema.string("Text to type into the field."),
                "target_field" to Schema.string("Optional hint or label of the target input field."),
                "submit" to Schema.boolean("Whether to press search/submit after typing.")
            ),
            optionalParameters = listOf("target_field", "submit")
        ),
        FunctionDeclaration(
            name = "accessibility_scroll",
            description = "Scroll the current screen up or down via AccessibilityService.",
            parameters = mapOf(
                "direction" to Schema.enumeration(listOf("down", "up"), description = "Scroll direction.")
            )
        ),
        FunctionDeclaration(
            name = "execute_automation",
            description = "Launch an app and perform a multi-step in-app search workflow (e.g., Open YouTube and search for Iron Man) using AccessibilityService.",
            parameters = mapOf(
                "app_name" to Schema.string("Target app name, e.g. YouTube or Chrome."),
                "search_query" to Schema.string("Query to search inside the app.")
            )
        )
    )

    fun findSpec(name: String): ToolSpec? = specs.firstOrNull { it.name == name }
}
