package com.kairo.timemanager.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kairo.timemanager.MainActivity
import com.kairo.timemanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.channel_reminders_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COACH,
                context.getString(R.string.channel_coach_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.channel_coach_desc) }
        )
    }

    fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReminder(taskId: Long, title: String, timeLabel: String) {
        post(
            id = REMINDER_ID_BASE + taskId.toInt(),
            channel = CHANNEL_REMINDERS,
            title = title,
            body = "Starts at $timeLabel",
            route = "today"
        )
    }

    fun showCoachSuggestion(title: String, body: String) {
        post(
            id = COACH_ID,
            channel = CHANNEL_COACH,
            title = title,
            body = body,
            route = "coach"
        )
    }

    fun showStreakWarning(streak: Int, remaining: Int) {
        post(
            id = STREAK_ID,
            channel = CHANNEL_COACH,
            title = "🔥 $streak-day streak at risk",
            body = if (remaining > 0) "$remaining block(s) left today to keep it alive."
            else "Finish something today to keep it alive.",
            route = "today"
        )
    }

    private fun post(id: Int, channel: String, title: String, body: String, route: String) {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        const val CHANNEL_REMINDERS = "kairo_reminders"
        const val CHANNEL_COACH = "kairo_coach"
        private const val REMINDER_ID_BASE = 10_000
        private const val COACH_ID = 2
        private const val STREAK_ID = 3
    }
}
