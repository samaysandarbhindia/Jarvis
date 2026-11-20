package com.example.jarvis.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?
)

class InstalledAppsManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun getLaunchableApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        resolveInfos
            .asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(packageManager).toString().trim()
                if (label.isEmpty()) return@mapNotNull null
                val icon = runCatching { info.loadIcon(packageManager) }.getOrNull()
                InstalledAppInfo(
                    appName = label,
                    packageName = pkg,
                    icon = icon
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    suspend fun searchApps(query: String): List<InstalledAppInfo> {
        val q = query.trim().lowercase()
        val apps = getLaunchableApps()
        if (q.isEmpty()) return apps
        return apps.filter {
            it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    suspend fun findBestMatch(appQuery: String): InstalledAppInfo? {
        val raw = appQuery.trim().lowercase()
        if (raw.isEmpty()) return null
        val apps = getLaunchableApps()

        // 1. Exact label or package match
        apps.firstOrNull {
            it.appName.equals(raw, ignoreCase = true) ||
                it.packageName.equals(raw, ignoreCase = true)
        }?.let { return it }

        // 2. Known package aliases if display label differs slightly
        val aliasPackages = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "chrome" to listOf("com.android.chrome"),
            "instagram" to listOf("com.instagram.android"),
            "settings" to listOf("com.android.settings"),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
            "calculator" to listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"),
            "maps" to listOf("com.google.android.apps.maps"),
            "gmail" to listOf("com.google.android.gm"),
            "spotify" to listOf("com.spotify.music"),
            "telegram" to listOf("org.telegram.messenger"),
            "phone" to listOf("com.google.android.dialer", "com.android.dialer"),
            "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock")
        )
        aliasPackages[raw]?.forEach { targetPkg ->
            apps.firstOrNull { it.packageName == targetPkg }?.let { return it }
        }

        // 3. Starts-with / contains match
        apps.firstOrNull { it.appName.lowercase().startsWith(raw) }?.let { return it }
        return apps.firstOrNull {
            it.appName.lowercase().contains(raw) || it.packageName.lowercase().contains(raw)
        }
    }

    suspend fun launchApp(appNameOrPackage: String): LaunchResult = withContext(Dispatchers.Main) {
        val target = findBestMatch(appNameOrPackage)
        val packageToLaunch = target?.packageName
            ?: if (appNameOrPackage.contains(".") && isPackageInstalled(appNameOrPackage.trim())) {
                appNameOrPackage.trim()
            } else {
                null
            }

        if (packageToLaunch == null) {
            return@withContext LaunchResult(
                success = false,
                appName = appNameOrPackage,
                packageName = null,
                message = "The application '$appNameOrPackage' is not installed on this device."
            )
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageToLaunch)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent == null) {
            return@withContext LaunchResult(
                success = false,
                appName = target?.appName ?: packageToLaunch,
                packageName = packageToLaunch,
                message = "Unable to resolve a launchable activity for '${target?.appName ?: packageToLaunch}'."
            )
        }

        return@withContext try {
            context.startActivity(launchIntent)
            LaunchResult(
                success = true,
                appName = target?.appName ?: packageToLaunch,
                packageName = packageToLaunch,
                message = "Launched ${target?.appName ?: packageToLaunch}."
            )
        } catch (e: Exception) {
            LaunchResult(
                success = false,
                appName = target?.appName ?: packageToLaunch,
                packageName = packageToLaunch,
                message = "Failed to launch ${target?.appName ?: packageToLaunch}: ${e.localizedMessage}"
            )
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

data class LaunchResult(
    val success: Boolean,
    val appName: String,
    val packageName: String?,
    val message: String
)
