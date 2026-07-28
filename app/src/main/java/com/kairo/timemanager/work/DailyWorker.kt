package com.kairo.timemanager.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kairo.timemanager.ai.CoachEngine
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.data.repo.RecommendationRepository
import com.kairo.timemanager.data.repo.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic housekeeping: settle the streak for finished days, re-arm reminders, warn when a live
 * streak is about to lapse, and — only if the user opted in — ask the coach for one suggestion.
 */
@HiltWorker
class DailyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val progressRepository: ProgressRepository,
    private val taskRepository: TaskRepository,
    private val recommendationRepository: RecommendationRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val coachEngine: CoachEngine,
    private val notifications: Notifications
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            notifications.ensureChannels()
            progressRepository.ensureSeeded()
            progressRepository.refreshStreak()
            reminderScheduler.rescheduleAll()
            recommendationRepository.prune()
        }

        val config = runCatching { settingsRepository.currentAiConfig() }.getOrNull()
        if (config?.recommendationsEnabled == true) {
            val posted = runCatching { coachEngine.maybeGenerateRecommendation() }.getOrDefault(false)
            if (posted && config.proactiveNotifications) {
                recommendationRepository.observeOpen().first().firstOrNull()?.let {
                    notifications.showCoachSuggestion(it.title, it.body)
                }
            }
        }

        runCatching { warnIfStreakAtRisk() }
        return Result.success()
    }

    private suspend fun warnIfStreakAtRisk() {
        if (!settingsRepository.currentAppPrefs().remindersEnabled) return
        val stats = progressRepository.stats()
        if (stats.currentStreak <= 0) return
        val today = TimeUtils.todayEpochDay()
        if (stats.lastStreakDay >= today) return
        // Only nag in the evening, once the day is genuinely running out.
        if (TimeUtils.nowMinuteOfDay() < EVENING_MINUTE) return
        val snapshot = taskRepository.snapshotFor(today)
        if (snapshot.planned == 0) return
        notifications.showStreakWarning(stats.currentStreak, snapshot.remaining)
    }

    companion object {
        private const val UNIQUE_NAME = "kairo_daily"
        private const val EVENING_MINUTE = 19 * 60

        fun enqueue(context: Context) {
            // No network constraint: settling the streak, re-arming reminders and pruning all
            // work offline. The optional coach call inside simply no-ops when it can't connect.
            val request = PeriodicWorkRequestBuilder<DailyWorker>(4, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
