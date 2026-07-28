package com.kairo.timemanager.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kairo.timemanager.core.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var notifications: Notifications
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val startMinute = intent.getIntExtra(ReminderScheduler.EXTRA_START_MINUTE, 0)
        if (taskId < 0 || title.isBlank()) return

        notifications.ensureChannels()
        notifications.showReminder(taskId, title, TimeUtils.formatTime(startMinute))

        // Re-arm this task for its next occurrence now that the alarm has fired.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyWorker.enqueue(context)
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
