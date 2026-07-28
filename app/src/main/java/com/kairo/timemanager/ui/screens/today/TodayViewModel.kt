package com.kairo.timemanager.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.ai.CoachEngine
import com.kairo.timemanager.core.LevelInfo
import com.kairo.timemanager.core.Leveling
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.RecommendationEntity
import com.kairo.timemanager.data.prefs.AppPrefs
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.data.repo.DaySnapshot
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.data.repo.RecommendationRepository
import com.kairo.timemanager.data.repo.RewardEvent
import com.kairo.timemanager.data.repo.ScheduledTask
import com.kairo.timemanager.data.repo.StreakSnapshot
import com.kairo.timemanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val epochDay: Long = TimeUtils.todayEpochDay(),
    val agenda: List<ScheduledTask> = emptyList(),
    val snapshot: DaySnapshot = DaySnapshot.empty(TimeUtils.todayEpochDay()),
    val streak: StreakSnapshot = StreakSnapshot(0, 0, 0, -1, false, 3),
    val level: LevelInfo = Leveling.infoFor(0),
    val coins: Int = 0,
    val recommendations: List<RecommendationEntity> = emptyList(),
    val prefs: AppPrefs = AppPrefs(),
    val loading: Boolean = true
) {
    val isToday: Boolean get() = epochDay == TimeUtils.todayEpochDay()

    /** The block happening now, or the next one still to come. */
    fun upNext(minuteOfDay: Int): ScheduledTask? {
        if (!isToday) return agenda.firstOrNull { !it.completed }
        return agenda.firstOrNull { it.isNow(minuteOfDay) }
            ?: agenda.firstOrNull { !it.completed && it.startMinute >= minuteOfDay }
            ?: agenda.firstOrNull { !it.completed }
    }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val progressRepository: ProgressRepository,
    private val recommendationRepository: RecommendationRepository,
    private val settingsRepository: SettingsRepository,
    private val coachEngine: CoachEngine
) : ViewModel() {

    private val selectedDay = MutableStateFlow(TimeUtils.todayEpochDay())

    private val events = Channel<RewardEvent>(Channel.BUFFERED)
    val rewardEvents: Flow<RewardEvent> = events.receiveAsFlow()

    private val _requestingSuggestion = MutableStateFlow(false)
    val requestingSuggestion: StateFlow<Boolean> = _requestingSuggestion.asStateFlow()

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<TodayUiState> = combine(
        selectedDay.flatMapLatest { day -> taskRepository.observeDay(day) },
        selectedDay.flatMapLatest { day -> taskRepository.observeSnapshot(day) },
        progressRepository.observeStats(),
        recommendationRepository.observeOpen(),
        settingsRepository.appPrefs
    ) { agenda, snapshot, stats, recommendations, prefs ->
        TodayUiState(
            epochDay = snapshot.epochDay,
            agenda = agenda,
            snapshot = snapshot,
            streak = StreakSnapshot(
                current = stats.currentStreak,
                longest = stats.longestStreak,
                freezeTokens = stats.freezeTokens,
                lastStreakDay = stats.lastStreakDay,
                atRisk = stats.currentStreak > 0 && stats.lastStreakDay < TimeUtils.todayEpochDay(),
                nextMilestone = listOf(3, 7, 14, 30, 60, 100, 180, 365)
                    .firstOrNull { it > stats.currentStreak } ?: (stats.currentStreak + 100)
            ),
            level = Leveling.infoFor(stats.xp),
            coins = stats.coins,
            recommendations = recommendations,
            prefs = prefs,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        viewModelScope.launch {
            progressRepository.ensureSeeded()
            progressRepository.refreshStreak().forEach { events.send(it) }
        }
    }

    fun selectDay(epochDay: Long) {
        selectedDay.value = epochDay
    }

    fun shiftDay(delta: Long) {
        selectedDay.value += delta
    }

    fun goToToday() {
        selectedDay.value = TimeUtils.todayEpochDay()
    }

    fun toggleTask(task: ScheduledTask) {
        viewModelScope.launch {
            val nowCompleted = !task.completed
            val flipped = taskRepository.setCompleted(task.id, task.epochDay, nowCompleted)
            if (flipped) {
                progressRepository.onTaskCompleted(
                    priority = task.task.priority,
                    durationMinutes = task.task.durationMinutes,
                    startMinute = task.startMinute,
                    epochDay = task.epochDay
                ).forEach { events.send(it) }
            } else if (!nowCompleted) {
                progressRepository.refreshStreak()
            }
        }
    }

    fun toggleChecklistItem(task: ScheduledTask, itemId: Long, currentlyChecked: Boolean) {
        viewModelScope.launch {
            taskRepository.setChecklistChecked(itemId, task.epochDay, !currentlyChecked)
            if (!currentlyChecked) {
                progressRepository.onChecklistItemChecked().forEach { events.send(it) }
            }
        }
    }

    fun skipTask(task: ScheduledTask) {
        viewModelScope.launch {
            taskRepository.setSkipped(task.id, task.epochDay, !task.skipped)
        }
    }

    fun acceptRecommendation(id: Long) {
        viewModelScope.launch { recommendationRepository.accept(id) }
    }

    fun dismissRecommendation(id: Long) {
        viewModelScope.launch { recommendationRepository.dismiss(id) }
    }

    /** Manual "ask the coach for an idea" — the same path the periodic worker uses. */
    fun requestSuggestion(onResult: (Boolean) -> Unit) {
        if (_requestingSuggestion.value) return
        viewModelScope.launch {
            _requestingSuggestion.value = true
            val posted = runCatching { coachEngine.maybeGenerateRecommendation(force = true) }
                .getOrDefault(false)
            _requestingSuggestion.value = false
            onResult(posted)
        }
    }
}
