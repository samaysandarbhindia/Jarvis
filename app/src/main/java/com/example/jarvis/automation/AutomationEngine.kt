package com.example.jarvis.automation

import com.example.jarvis.accessibility.AccessibilityBridge
import com.example.jarvis.apps.InstalledAppsManager
import com.example.jarvis.database.AutomationLogDao
import com.example.jarvis.database.AutomationLogEntity
import com.example.jarvis.security.InputValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

sealed interface AutomationStep {
    val label: String

    data class LaunchApp(val appName: String) : AutomationStep {
        override val label: String = "Launch app '$appName'"
    }

    data class WaitMs(val millis: Long) : AutomationStep {
        override val label: String = "Wait ${millis}ms"
    }

    data class WaitForPackage(val packageHint: String, val timeoutMs: Long = 6000L) : AutomationStep {
        override val label: String = "Wait for '$packageHint' window"
    }

    data class ClickElement(
        val query: String,
        val fallbackQueries: List<String> = emptyList(),
        val retries: Int = 3
    ) : AutomationStep {
        override val label: String = "Click '$query'"
    }

    data class EnterText(
        val targetQuery: String? = null,
        val text: String,
        val submit: Boolean = true,
        val retries: Int = 3
    ) : AutomationStep {
        override val label: String = "Enter text '${text.take(30)}'"
    }

    data class Scroll(val direction: String = "down") : AutomationStep {
        override val label: String = "Scroll $direction"
    }

    data class GlobalAction(val action: String) : AutomationStep {
        override val label: String = "Global action '$action'"
    }
}

data class AutomationResult(
    val success: Boolean,
    val workflowName: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val summary: String,
    val stepDetails: List<String>
)

class AutomationEngine(
    private val appsManager: InstalledAppsManager,
    private val automationLogDao: AutomationLogDao
) {

    val recentLogs: Flow<List<AutomationLogEntity>> = automationLogDao.getRecentLogs()

    suspend fun logToolExecution(
        workflowName: String,
        description: String,
        success: Boolean,
        durationMs: Long = 0L
    ) {
        automationLogDao.insertLog(
            AutomationLogEntity(
                workflowName = workflowName,
                stepDescription = description,
                status = if (success) "SUCCESS" else "FAILED",
                durationMs = durationMs
            )
        )
    }

    suspend fun executeAppSearchWorkflow(appName: String, searchQuery: String): AutomationResult {
        if (InputValidator.containsSensitiveAuthPattern(searchQuery)) {
            return AutomationResult(
                success = false,
                workflowName = "$appName Search",
                completedSteps = 0,
                totalSteps = 1,
                summary = "Blocked: automation cannot be used on authentication, password, or CAPTCHA inputs.",
                stepDetails = listOf("Safety guardrail rejected sensitive input pattern.")
            )
        }

        val steps = listOf(
            AutomationStep.LaunchApp(appName),
            AutomationStep.WaitMs(1800L),
            AutomationStep.ClickElement(
                query = "search",
                fallbackQueries = listOf("search bar", "search_button", "find", "type", "query"),
                retries = 4
            ),
            AutomationStep.WaitMs(700L),
            AutomationStep.EnterText(
                targetQuery = "search",
                text = searchQuery,
                submit = true,
                retries = 3
            )
        )
        return executeWorkflow("$appName Search: $searchQuery", steps)
    }

    suspend fun executeWorkflow(
        workflowName: String,
        steps: List<AutomationStep>,
        overallTimeoutMs: Long = 25_000L
    ): AutomationResult {
        val startTime = System.currentTimeMillis()
        val details = mutableListOf<String>()
        var completed = 0

        val requiresAccessibility = steps.any {
            it !is AutomationStep.LaunchApp && it !is AutomationStep.WaitMs
        }
        if (requiresAccessibility && AccessibilityBridge.getService() == null) {
            val msg = "JARVIS Accessibility Service is not active. Enable it in Android Settings -> Accessibility -> JARVIS Automation Engine."
            automationLogDao.insertLog(
                AutomationLogEntity(
                    workflowName = workflowName,
                    stepDescription = msg,
                    status = "FAILED",
                    durationMs = System.currentTimeMillis() - startTime
                )
            )
            return AutomationResult(
                success = false,
                workflowName = workflowName,
                completedSteps = 0,
                totalSteps = steps.size,
                summary = msg,
                stepDetails = listOf(msg)
            )
        }

        return try {
            withTimeout(overallTimeoutMs) {
                for (step in steps) {
                    coroutineContext.ensureActive()
                    val stepResult = executeSingleStep(step)
                    if (stepResult.isSuccess) {
                        completed++
                        val msg = stepResult.getOrNull() ?: "${step.label} completed"
                        details.add("OK: $msg")
                    } else {
                        val err = stepResult.exceptionOrNull()?.localizedMessage ?: "${step.label} failed"
                        details.add("FAIL: $err")
                        val elapsed = System.currentTimeMillis() - startTime
                        automationLogDao.insertLog(
                            AutomationLogEntity(
                                workflowName = workflowName,
                                stepDescription = details.joinToString(" | "),
                                status = "FAILED",
                                durationMs = elapsed
                            )
                        )
                        return@withTimeout AutomationResult(
                            success = false,
                            workflowName = workflowName,
                            completedSteps = completed,
                            totalSteps = steps.size,
                            summary = "Stopped at step '${step.label}': $err",
                            stepDetails = details
                        )
                    }
                }

                val elapsed = System.currentTimeMillis() - startTime
                automationLogDao.insertLog(
                    AutomationLogEntity(
                        workflowName = workflowName,
                        stepDescription = details.joinToString(" | "),
                        status = "SUCCESS",
                        durationMs = elapsed
                    )
                )
                AutomationResult(
                    success = true,
                    workflowName = workflowName,
                    completedSteps = completed,
                    totalSteps = steps.size,
                    summary = "Completed $completed/${steps.size} automation steps for '$workflowName'.",
                    stepDetails = details
                )
            }
        } catch (e: CancellationException) {
            automationLogDao.insertLog(
                AutomationLogEntity(
                    workflowName = workflowName,
                    stepDescription = "Cancelled at step $completed/${steps.size}",
                    status = "CANCELLED",
                    durationMs = System.currentTimeMillis() - startTime
                )
            )
            throw e
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Automation timed out."
            automationLogDao.insertLog(
                AutomationLogEntity(
                    workflowName = workflowName,
                    stepDescription = msg,
                    status = "FAILED",
                    durationMs = System.currentTimeMillis() - startTime
                )
            )
            AutomationResult(
                success = false,
                workflowName = workflowName,
                completedSteps = completed,
                totalSteps = steps.size,
                summary = msg,
                stepDetails = details + "ERROR: $msg"
            )
        }
    }

    private suspend fun executeSingleStep(step: AutomationStep): Result<String> {
        return when (step) {
            is AutomationStep.LaunchApp -> {
                val res = appsManager.launchApp(step.appName)
                if (res.success) Result.success(res.message)
                else Result.failure(IllegalStateException(res.message))
            }

            is AutomationStep.WaitMs -> {
                delay(step.millis.coerceIn(50L, 10_000L))
                Result.success("Waited ${step.millis}ms")
            }

            is AutomationStep.WaitForPackage -> {
                val deadline = System.currentTimeMillis() + step.timeoutMs
                val hint = step.packageHint.lowercase()
                while (System.currentTimeMillis() < deadline) {
                    val active = AccessibilityBridge.activePackage.value?.lowercase().orEmpty()
                    if (active.contains(hint)) {
                        return Result.success("Active window matches '$hint' ($active)")
                    }
                    delay(250L)
                }
                Result.failure(IllegalStateException("Timed out waiting for '${step.packageHint}' window."))
            }

            is AutomationStep.ClickElement -> {
                val service = AccessibilityBridge.getService()
                    ?: return Result.failure(IllegalStateException("Accessibility Service unavailable."))
                val queries = listOf(step.query) + step.fallbackQueries
                var lastError: Throwable? = null
                repeat(step.retries.coerceAtLeast(1)) { attempt ->
                    for (q in queries) {
                        val res = service.findAndClick(q)
                        if (res.isSuccess) return res
                        lastError = res.exceptionOrNull()
                    }
                    if (attempt < step.retries - 1) delay(600L)
                }
                Result.failure(lastError ?: IllegalStateException("Element '${step.query}' not found."))
            }

            is AutomationStep.EnterText -> {
                val service = AccessibilityBridge.getService()
                    ?: return Result.failure(IllegalStateException("Accessibility Service unavailable."))
                var lastError: Throwable? = null
                repeat(step.retries.coerceAtLeast(1)) { attempt ->
                    val res = service.enterText(step.targetQuery, step.text, step.submit)
                    if (res.isSuccess) return res
                    lastError = res.exceptionOrNull()
                    if (attempt < step.retries - 1) delay(500L)
                }
                Result.failure(lastError ?: IllegalStateException("Failed to enter text."))
            }

            is AutomationStep.Scroll -> {
                val service = AccessibilityBridge.getService()
                    ?: return Result.failure(IllegalStateException("Accessibility Service unavailable."))
                service.scroll(step.direction)
            }

            is AutomationStep.GlobalAction -> {
                val service = AccessibilityBridge.getService()
                    ?: return Result.failure(IllegalStateException("Accessibility Service unavailable."))
                service.performSystemAction(step.action)
            }
        }
    }
}
