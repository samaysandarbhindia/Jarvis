package com.example.jarvis.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.example.jarvis.security.InputValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder

data class BatteryTelemetry(
    val percentage: Int,
    val isCharging: Boolean,
    val chargingSource: String,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val healthLabel: String
)

data class DeviceTelemetry(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val networkType: String,
    val isConnected: Boolean,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val battery: BatteryTelemetry,
    val mediaVolumePercent: Int,
    val ringVolumePercent: Int,
    val alarmVolumePercent: Int,
    val isTorchOn: Boolean,
    val hasFlashlight: Boolean,
    val isIgnoringBatteryOptimizations: Boolean
)

class DeviceControlManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val torchCameraId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    init {
        runCatching {
            cameraManager.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == torchCameraId) {
                            _isTorchOn.value = enabled
                        }
                    }
                },
                null
            )
        }
    }

    fun getBatteryTelemetry(): BatteryTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "On Battery"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        return BatteryTelemetry(
            percentage = pct,
            isCharging = isCharging,
            chargingSource = chargingSource,
            temperatureCelsius = tempTenths / 10f,
            voltageMv = voltageMv,
            healthLabel = health
        )
    }

    fun getNetworkStatus(): Pair<Boolean, String> {
        val activeNetwork = connectivityManager.activeNetwork ?: return false to "Offline"
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false to "Offline"
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        return hasInternet to type
    }

    fun getDeviceTelemetry(): DeviceTelemetry {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val ramTotalMb = memInfo.totalMem / (1024L * 1024L)
        val ramAvailMb = memInfo.availMem / (1024L * 1024L)
        val ramUsedMb = (ramTotalMb - ramAvailMb).coerceAtLeast(0L)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = statFs.availableBytes
        val bytesTotal = statFs.totalBytes
        val gbDivisor = 1024.0 * 1024.0 * 1024.0

        val (isConnected, networkType) = getNetworkStatus()

        return DeviceTelemetry(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            networkType = networkType,
            isConnected = isConnected,
            ramUsedMb = ramUsedMb,
            ramTotalMb = ramTotalMb,
            storageFreeGb = bytesAvailable / gbDivisor,
            storageTotalGb = bytesTotal / gbDivisor,
            battery = getBatteryTelemetry(),
            mediaVolumePercent = getVolumePercent(AudioManager.STREAM_MUSIC),
            ringVolumePercent = getVolumePercent(AudioManager.STREAM_RING),
            alarmVolumePercent = getVolumePercent(AudioManager.STREAM_ALARM),
            isTorchOn = _isTorchOn.value,
            hasFlashlight = torchCameraId != null,
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
        )
    }

    fun getVolumePercent(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        return ((current * 100f) / max).toInt().coerceIn(0, 100)
    }

    fun setVolumePercent(percent: Int, streamName: String = "media"): Result<Int> {
        val clamped = InputValidator.validateVolumePercent(percent)
        val streamType = when (streamName.lowercase()) {
            "ring", "ringer" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        return try {
            val max = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val targetIndex = ((clamped / 100f) * max).toInt().coerceIn(0, max)
            audioManager.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)
            Result.success(getVolumePercent(streamType))
        } catch (e: SecurityException) {
            Result.failure(
                IllegalStateException(
                    "Android prevented changing $streamName volume (Do Not Disturb access may be required)."
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setFlashlight(enabled: Boolean? = null): Result<Boolean> {
        val cameraId = torchCameraId
            ?: return Result.failure(IllegalStateException("No camera flashlight hardware found on this device."))
        val target = enabled ?: !_isTorchOn.value
        return try {
            cameraManager.setTorchMode(cameraId, target)
            _isTorchOn.value = target
            Result.success(target)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed to toggle flashlight: ${e.localizedMessage}"))
        }
    }

    fun openSystemSettings(section: String): Result<String> {
        val action = when (section.lowercase().trim()) {
            "wifi", "wi-fi", "internet" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "notifications", "notification_access", "notification_listener" ->
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "battery", "battery_optimization" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            "exact_alarm", "alarms" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            } else {
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            }
            "app", "app_info", "permissions" -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && action == Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            ) {
                data = Uri.fromParts("package", context.packageName, null)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            Result.success("Opened Android $section settings.")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not open $section settings: ${e.localizedMessage}"))
        }
    }

    fun openCamera(): Result<String> {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success("Opened device camera.")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Unable to open camera: ${e.localizedMessage}"))
        }
    }

    fun openDialer(phoneNumber: String? = null): Result<String> {
        val intent = if (!phoneNumber.isNullOrBlank()) {
            val validated = InputValidator.validatePhoneNumber(phoneNumber).getOrElse {
                return Result.failure(it)
            }
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(validated)}"))
        } else {
            Intent(Intent.ACTION_DIAL)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success(
                if (phoneNumber.isNullOrBlank()) "Opened phone dialer."
                else "Opened dialer with number $phoneNumber."
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not open dialer: ${e.localizedMessage}"))
        }
    }

    fun openWebsite(url: String): Result<String> {
        val validUrl = InputValidator.validateHttpUrl(url).getOrElse {
            return Result.failure(it)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success("Opened $validUrl in browser.")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not open browser for $validUrl: ${e.localizedMessage}"))
        }
    }

    fun shareText(text: String, title: String = "Share via JARVIS"): Result<String> {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            Result.success("Opened share sheet.")
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed to open share sheet: ${e.localizedMessage}"))
        }
    }

    fun prepareMessageIntent(
        platform: String,
        recipient: String,
        messageText: String
    ): Result<String> {
        val cleanText = InputValidator.sanitizeNonEmpty(messageText, "Message", 2000).getOrElse {
            return Result.failure(it)
        }
        return try {
            val intent = when (platform.lowercase()) {
                "sms", "messages" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipient)}")).apply {
                    putExtra("sms_body", cleanText)
                }
                "whatsapp" -> {
                    val cleanDigits = recipient.filter { it.isDigit() || it == '+' }
                    if (cleanDigits.length >= 7) {
                        val encoded = URLEncoder.encode(cleanText, "UTF-8")
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${cleanDigits.removePrefix("+")}?text=$encoded"))
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            setPackage("com.whatsapp")
                            putExtra(Intent.EXTRA_TEXT, cleanText)
                        }
                    }
                }
                else -> Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, cleanText)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success("Prepared $platform message composer for $recipient. Please review and press Send in the app.")
        } catch (e: Exception) {
            // Fallback to generic share chooser if target app isn't installed
            shareText(cleanText, "Message for $recipient")
        }
    }

    fun createSystemAlarm(hour: Int, minute: Int, label: String): Result<String> {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, safeHour)
            putExtra(AlarmClock.EXTRA_MINUTES, safeMinute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "JARVIS Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success(
                String.format("Alarm set for %02d:%02d (%s).", safeHour, safeMinute, label.ifBlank { "JARVIS Alarm" })
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not open Clock app to set alarm: ${e.localizedMessage}"))
        }
    }
}
