package com.example.jarvis.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.jarvis.database.JarvisDatabase
import com.example.jarvis.database.ReminderDao
import com.example.jarvis.database.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ReminderScheduler(
    private val context: Context,
    private val reminderDao: ReminderDao
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()

    init {
        ensureNotificationChannel(context)
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    suspend fun scheduleReminder(
        title: String,
        triggerAtMillis: Long,
        repeatInterval: String = ReminderEntity.REPEAT_NONE
    ): ScheduleResult {
        val cleanTitle = title.trim().ifEmpty { "JARVIS Reminder" }
        val safeTrigger = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 5_000L)
        val id = reminderDao.insertReminder(
            ReminderEntity(
                title = cleanTitle,
                triggerAtMillis = safeTrigger,
                repeatInterval = repeatInterval,
                isCompleted = false
            )
        )
        val exactUsed = schedulePlatformAlarm(id, cleanTitle, safeTrigger, repeatInterval)
        return ScheduleResult(
            reminderId = id,
            title = cleanTitle,
            triggerAtMillis = safeTrigger,
            exactScheduled = exactUsed
        )
    }

    suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder)
        cancelPlatformAlarm(reminder.id)
        if (!reminder.isCompleted && reminder.triggerAtMillis > System.currentTimeMillis()) {
            schedulePlatformAlarm(
                reminder.id,
                reminder.title,
                reminder.triggerAtMillis,
                reminder.repeatInterval
            )
        }
    }

    suspend fun toggleCompleted(reminder: ReminderEntity) {
        val nextCompleted = !reminder.isCompleted
        reminderDao.setCompleted(reminder.id, nextCompleted)
        if (nextCompleted) {
            cancelPlatformAlarm(reminder.id)
        } else if (reminder.triggerAtMillis > System.currentTimeMillis()) {
            schedulePlatformAlarm(
                reminder.id,
                reminder.title,
                reminder.triggerAtMillis,
                reminder.repeatInterval
            )
        }
    }

    suspend fun deleteReminder(id: Long) {
        cancelPlatformAlarm(id)
        reminderDao.deleteReminder(id)
    }

    fun schedulePlatformAlarm(
        id: Long,
        title: String,
        triggerAtMillis: Long,
        repeatInterval: String
    ): Boolean {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REPEAT_INTERVAL, repeatInterval)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            true
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            false
        }
    }

    private fun cancelPlatformAlarm(id: Long) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    companion object {
        const val CHANNEL_ID = "jarvis_reminders"
        const val ACTION_TRIGGER_REMINDER = "com.example.jarvis.ACTION_TRIGGER_REMINDER"
        const val ACTION_COMPLETE_REMINDER = "com.example.jarvis.ACTION_COMPLETE_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REPEAT_INTERVAL = "extra_repeat_interval"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.reminder_channel_description)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}

data class ScheduleResult(
    val reminderId: Long,
    val title: String,
    val triggerAtMillis: Long,
    val exactScheduled: Boolean
)

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "JARVIS Reminder"
        val repeatInterval = intent.getStringExtra(ReminderScheduler.EXTRA_REPEAT_INTERVAL)
            ?: ReminderEntity.REPEAT_NONE

        if (intent.action == ReminderScheduler.ACTION_COMPLETE_REMINDER && reminderId > 0) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(reminderId.toInt())
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    JarvisDatabase.getInstance(context).reminderDao().setCompleted(reminderId, true)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        ReminderScheduler.ensureNotificationChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_COMPLETE_REMINDER
            putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            (reminderId + 100_000).toInt(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("JARVIS Reminder")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(0, "Mark Done", donePending)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(reminderId.toInt().coerceAtLeast(1), notification)

        // Handle repeat interval scheduling if DAILY or WEEKLY
        if (reminderId > 0 && repeatInterval != ReminderEntity.REPEAT_NONE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = JarvisDatabase.getInstance(context).reminderDao()
                    val existing = dao.getReminderById(reminderId)
                    if (existing != null && !existing.isCompleted) {
                        val delta = when (repeatInterval) {
                            ReminderEntity.REPEAT_DAILY -> 24L * 60L * 60L * 1000L
                            ReminderEntity.REPEAT_WEEKLY -> 7L * 24L * 60L * 60L * 1000L
                            else -> 0L
                        }
                        if (delta > 0L) {
                            val nextTrigger = System.currentTimeMillis() + delta
                            dao.updateReminder(existing.copy(triggerAtMillis = nextTrigger))
                            ReminderScheduler(context, dao).schedulePlatformAlarm(
                                reminderId,
                                existing.title,
                                nextTrigger,
                                repeatInterval
                            )
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = JarvisDatabase.getInstance(context).reminderDao()
                val scheduler = ReminderScheduler(context, dao)
                val active = dao.getActiveFutureReminders(System.currentTimeMillis())
                active.forEach { reminder ->
                    scheduler.schedulePlatformAlarm(
                        reminder.id,
                        reminder.title,
                        reminder.triggerAtMillis,
                        reminder.repeatInterval
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
