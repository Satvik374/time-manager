package com.kairo.timemanager.ui.screens.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.prefs.AppPrefs
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.data.repo.DaySnapshot
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.data.repo.ScheduledTask
import com.kairo.timemanager.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PlannerView { TIMELINE, LIST }

data class WeekDayCell(
    val epochDay: Long,
    val planned: Int,
    val completed: Int
) {
    val rate: Float get() = if (planned == 0) 0f else completed.toFloat() / planned
}

data class PlannerUiState(
    val selectedDay: Long = TimeUtils.todayEpochDay(),
    val weekStart: Long = TimeUtils.startOfWeek(TimeUtils.todayEpochDay()),
    val week: List<WeekDayCell> = emptyList(),
    val agenda: List<ScheduledTask> = emptyList(),
    val snapshot: DaySnapshot = DaySnapshot.empty(TimeUtils.todayEpochDay()),
    val allTasks: List<TaskEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val categoryFilter: String? = null,
    val view: PlannerView = PlannerView.TIMELINE,
    val prefs: AppPrefs = AppPrefs()
) {
    val filteredAgenda: List<ScheduledTask>
        get() = if (categoryFilter == null) agenda
        else agenda.filter { it.task.category == categoryFilter }

    val filteredTasks: List<TaskEntity>
        get() = if (categoryFilter == null) allTasks
        else allTasks.filter { it.category == categoryFilter }
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val progressRepository: ProgressRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val selectedDay = MutableStateFlow(TimeUtils.todayEpochDay())
    private val view = MutableStateFlow(PlannerView.TIMELINE)
    private val categoryFilter = MutableStateFlow<String?>(null)

    @Suppress("OPT_IN_USAGE")
    private val weekCells = selectedDay.flatMapLatest { day ->
        val start = TimeUtils.startOfWeek(day)
        // One combined flow per weekday keeps the strip live as blocks are ticked off.
        combine((0..6).map { offset -> taskRepository.observeSnapshot(start + offset) }) { snapshots ->
            snapshots.map { WeekDayCell(it.epochDay, it.planned, it.completed) }
        }
    }

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<PlannerUiState> = combine(
        combine(
            selectedDay.flatMapLatest { taskRepository.observeDay(it) },
            selectedDay.flatMapLatest { taskRepository.observeSnapshot(it) },
            weekCells
        ) { agenda, snapshot, week -> Triple(agenda, snapshot, week) },
        taskRepository.observeAllTasks(),
        taskRepository.observeCategories(),
        combine(view, categoryFilter) { v, filter -> v to filter },
        settingsRepository.appPrefs
    ) { dayBits, tasks, categories, viewBits, prefs ->
        val (agenda, snapshot, week) = dayBits
        PlannerUiState(
            selectedDay = snapshot.epochDay,
            weekStart = TimeUtils.startOfWeek(snapshot.epochDay),
            week = week,
            agenda = agenda,
            snapshot = snapshot,
            allTasks = tasks.sortedWith(compareBy({ it.startMinute }, { it.title })),
            categories = categories,
            categoryFilter = viewBits.second,
            view = viewBits.first,
            prefs = prefs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlannerUiState())

    val hasAnyTask: StateFlow<Boolean> = taskRepository.observeAllTasks()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun selectDay(epochDay: Long) {
        selectedDay.value = epochDay
    }

    fun shiftWeek(delta: Long) {
        selectedDay.value += delta * 7
    }

    fun goToToday() {
        selectedDay.value = TimeUtils.todayEpochDay()
    }

    fun setView(next: PlannerView) {
        view.value = next
    }

    fun setCategoryFilter(category: String?) {
        categoryFilter.value = if (categoryFilter.value == category) null else category
    }

    fun toggleTask(task: ScheduledTask) {
        viewModelScope.launch {
            val flipped = taskRepository.setCompleted(task.id, task.epochDay, !task.completed)
            if (flipped) {
                progressRepository.onTaskCompleted(
                    priority = task.task.priority,
                    durationMinutes = task.task.durationMinutes,
                    startMinute = task.startMinute,
                    epochDay = task.epochDay
                )
            }
        }
    }

    fun toggleChecklistItem(task: ScheduledTask, itemId: Long, currentlyChecked: Boolean) {
        viewModelScope.launch {
            taskRepository.setChecklistChecked(itemId, task.epochDay, !currentlyChecked)
            if (!currentlyChecked) progressRepository.onChecklistItemChecked()
        }
    }

    fun archiveTask(taskId: Long) {
        viewModelScope.launch { taskRepository.archiveTask(taskId, archived = true) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { taskRepository.deleteTask(taskId) }
    }
}
