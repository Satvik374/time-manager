package com.kairo.timemanager

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.work.DailyWorker
import com.kairo.timemanager.work.Notifications
import com.kairo.timemanager.work.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KairoApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var progressRepository: ProgressRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var notifications: Notifications

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        appScope.launch {
            progressRepository.ensureSeeded()
            // Settle any days that passed while the app was closed before the UI reads stats.
            progressRepository.refreshStreak()
            reminderScheduler.rescheduleAll()
        }
        DailyWorker.enqueue(this)
    }
}
