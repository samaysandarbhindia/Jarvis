package com.example.jarvis.security

import java.net.URI

object InputValidator {

    private val packageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val phoneRegex = Regex("^[+0-9*#\\-()\\s]{3,24}$")

    fun validateHttpUrl(rawUrl: String): Result<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("URL cannot be empty."))
        }
        val normalized = if (!trimmed.contains("://") && !trimmed.contains(":")) {
            "https://$trimmed"
        } else {
            trimmed
        }
        val uri = runCatching { URI(normalized) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Malformed URL."))
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("Only HTTP and HTTPS URLs are permitted."))
        }
        if (uri.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("URL must include a valid host."))
        }
        return Result.success(normalized)
    }

    fun isValidPackageName(pkg: String): Boolean {
        return pkg.isNotBlank() && pkg.length <= 128 && packageNameRegex.matches(pkg.trim())
    }

    fun validateVolumePercent(percent: Int): Int = percent.coerceIn(0, 100)

    fun validatePhoneNumber(rawNumber: String): Result<String> {
        val trimmed = rawNumber.trim()
        if (!phoneRegex.matches(trimmed)) {
            return Result.failure(IllegalArgumentException("Invalid phone number format."))
        }
        return Result.success(trimmed)
    }

    fun sanitizeNonEmpty(value: String?, fieldName: String, maxLen: Int = 500): Result<String> {
        val cleaned = value?.trim().orEmpty()
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("$fieldName cannot be empty."))
        }
        return Result.success(cleaned.take(maxLen))
    }

    fun containsSensitiveAuthPattern(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("password") ||
            lower.contains("passcode") ||
            lower.contains("pin code") ||
            lower.contains("cvv") ||
            lower.contains("otp") ||
            lower.contains("captcha")
    }
}
