package com.example.jarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

data class AccessibleElementInfo(
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
)

object AccessibilityBridge {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _activePackage = MutableStateFlow<String?>(null)
    val activePackage: StateFlow<String?> = _activePackage.asStateFlow()

    @Volatile
    private var serviceRef: WeakReference<JarvisAccessibilityService>? = null

    fun attach(service: JarvisAccessibilityService) {
        serviceRef = WeakReference(service)
        _isConnected.value = true
    }

    fun detach() {
        serviceRef = null
        _isConnected.value = false
    }

    fun updateActivePackage(pkg: String?) {
        if (!pkg.isNullOrBlank()) {
            _activePackage.value = pkg
        }
    }

    fun getService(): JarvisAccessibilityService? = serviceRef?.get()

    fun isServiceEnabledInSystemSettings(context: Context): Boolean {
        val expected = ComponentName(context, JarvisAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any {
            it.equals(expected, ignoreCase = true) ||
                it.endsWith(JarvisAccessibilityService::class.java.simpleName)
        }
    }
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            AccessibilityBridge.updateActivePackage(pkg)
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        AccessibilityBridge.detach()
        super.onDestroy()
    }

    fun inspectScreen(maxNodes: Int = 60): List<AccessibleElementInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val collected = mutableListOf<AccessibleElementInfo>()
        traverse(root) { node ->
            if (collected.size >= maxNodes) return@traverse false
            // Never read password fields
            if (node.isPassword) return@traverse true

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName?.substringAfterLast("/")?.trim().orEmpty()
            val interesting = text.isNotEmpty() || desc.isNotEmpty() || node.isEditable || node.isClickable
            if (interesting) {
                collected.add(
                    AccessibleElementInfo(
                        text = text,
                        contentDescription = desc,
                        viewId = viewId,
                        className = node.className?.toString()?.substringAfterLast(".") ?: "",
                        isClickable = node.isClickable,
                        isEditable = node.isEditable,
                        isScrollable = node.isScrollable
                    )
                )
            }
            true
        }
        return collected
    }

    fun findAndClick(query: String): Result<String> {
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("No active window available to inspect."))
        val targetQuery = query.trim().lowercase()
        if (targetQuery.isEmpty()) {
            return Result.failure(IllegalArgumentException("Click target query cannot be empty."))
        }

        val candidates = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            if (node.isPassword) return@traverse true
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (text.contains(targetQuery) || desc.contains(targetQuery) || id.contains(targetQuery)) {
                candidates.add(node)
            }
            true
        }

        for (candidate in candidates) {
            val clickableNode = findClickableNodeOrAncestor(candidate)
            if (clickableNode != null && clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                val label = candidate.text?.toString()
                    ?: candidate.contentDescription?.toString()
                    ?: query
                return Result.success("Clicked '$label'.")
            }
        }
        return Result.failure(IllegalStateException("Could not find a clickable UI element matching '$query'."))
    }

    fun enterText(targetQuery: String?, text: String, submit: Boolean = false): Result<String> {
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("No active window available."))
        val queryLower = targetQuery?.trim()?.lowercase().orEmpty()

        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            if (node.isEditable && !node.isPassword) {
                editableNodes.add(node)
            }
            true
        }

        val targetNode = if (queryLower.isNotEmpty()) {
            editableNodes.firstOrNull { node ->
                val t = node.text?.toString()?.lowercase().orEmpty()
                val d = node.contentDescription?.toString()?.lowercase().orEmpty()
                val h = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.lowercase().orEmpty()
                } else ""
                val id = node.viewIdResourceName?.lowercase().orEmpty()
                t.contains(queryLower) || d.contains(queryLower) || h.contains(queryLower) || id.contains(queryLower)
            } ?: editableNodes.firstOrNull { it.isFocused } ?: editableNodes.firstOrNull()
        } else {
            editableNodes.firstOrNull { it.isFocused } ?: editableNodes.firstOrNull()
        }

        if (targetNode == null) {
            return Result.failure(IllegalStateException("No accessible text input field found on the current screen."))
        }

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setOk = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!setOk) {
            return Result.failure(IllegalStateException("Failed to enter text into the active input field."))
        }

        if (submit) {
            val imeSubmitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                targetNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            } else {
                false
            }
            if (!imeSubmitted) {
                // Attempt to click a visible search/submit/send button
                findAndClick("search").onFailure {
                    findAndClick("submit").onFailure {
                        findAndClick("go")
                    }
                }
            }
        }

        return Result.success("Entered '$text'${if (submit) " and submitted" else ""}.")
    }

    fun scroll(direction: String): Result<String> {
        val root = rootInActiveWindow
            ?: return Result.failure(IllegalStateException("No active window available."))
        val forward = !direction.lowercase().let { it == "up" || it == "backward" || it == "back" }

        var scrollable: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            if (node.isScrollable) {
                scrollable = node
                return@traverse false
            }
            true
        }
        val node = scrollable
            ?: return Result.failure(IllegalStateException("No scrollable container found on the current screen."))

        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val ok = node.performAction(action)
        return if (ok) {
            Result.success("Scrolled ${if (forward) "down" else "up"}.")
        } else {
            Result.failure(IllegalStateException("Unable to scroll further ${if (forward) "down" else "up"}."))
        }
    }

    fun performSystemAction(actionName: String): Result<String> {
        val code = when (actionName.lowercase().trim()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "overview" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            else -> return Result.failure(IllegalArgumentException("Unsupported global action: $actionName"))
        }
        return if (performGlobalAction(code)) {
            Result.success("Performed global action '$actionName'.")
        } else {
            Result.failure(IllegalStateException("Failed to perform global action '$actionName'."))
        }
    }

    private fun findClickableNodeOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun traverse(node: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Boolean) {
        if (!visitor(node)) return
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverse(child, visitor)
        }
    }
}
