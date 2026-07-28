package com.kairo.timemanager.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.data.repo.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one pending alarm per reminder-enabled block, always for its next occurrence.
 * Uses an inexact window so the app needs no exact-alarm permission; a reminder that lands
 * a couple of minutes late is fine, a permission prompt the user must hunt for is not.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository
) {

    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        val manager = alarmManager ?: return
        val enabled = settingsRepository.currentAppPrefs().remindersEnabled
        val tasks = taskRepository.allTasksSnapshot()

        tasks.forEach { task ->
            val pending = pendingIntentFor(task, create = true) ?: return@forEach
            manager.cancel(pending)
            if (!enabled || task.reminderMinutes < 0 || task.archived) return@forEach
            val triggerAt = nextTriggerMillis(task) ?: return@forEach
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                WINDOW_MILLIS,
                pending
            )
        }
    }

    fun cancel(task: TaskEntity) {
        val manager = alarmManager ?: return
        pendingIntentFor(task, create = false)?.let { manager.cancel(it) }
    }

    /** Next time this block starts minus its lead time, or null if it never occurs again soon. */
    private fun nextTriggerMillis(task: TaskEntity): Long? {
        val now = System.currentTimeMillis()
        val today = TimeUtils.todayEpochDay()
        for (offset in 0..14L) {
            val day = today + offset
            if (!taskRepository.occursOn(task, day)) continue
            val startMillis = TimeUtils.epochMillisOf(day, task.startMinute)
            val triggerAt = startMillis - task.reminderMinutes * 60_000L
            if (triggerAt > now + 30_000L) return triggerAt
        }
        return null
    }

    private fun pendingIntentFor(task: TaskEntity, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TITLE, task.title)
            putExtra(EXTRA_START_MINUTE, task.startMinute)
            // Unique data keeps one PendingIntent per task rather than one shared slot.
            data = android.net.Uri.parse("kairo://reminder/${task.id}")
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, task.id.toInt(), intent, flags)
    }

    companion object {
        const val ACTION_REMIND = "com.kairo.timemanager.REMIND"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START_MINUTE = "start_minute"
        private const val WINDOW_MILLIS = 5 * 60 * 1000L
    }
}
