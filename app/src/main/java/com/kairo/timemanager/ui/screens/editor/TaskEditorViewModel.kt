package com.kairo.timemanager.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.core.DayMask
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.Priority
import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.repo.ScheduledTask
import com.kairo.timemanager.data.repo.TaskRepository
import com.kairo.timemanager.ui.theme.TaskPalette
import com.kairo.timemanager.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val taskId: Long = 0,
    val epochDay: Long = TimeUtils.todayEpochDay(),
    val title: String = "",
    val note: String = "",
    val category: String = "General",
    val colorHex: String = TaskPalette.first(),
    val priority: Int = Priority.NORMAL,
    val startMinute: Int = 9 * 60,
    val durationMinutes: Int = 60,
    val recurrence: Int = Recurrence.ONCE,
    val daysMask: Int = 0,
    val reminderMinutes: Int = -1,
    val checklist: List<String> = emptyList(),
    val knownCategories: List<String> = emptyList(),
    val conflicts: List<ScheduledTask> = emptyList(),
    val use24h: Boolean = false,
    val loading: Boolean = true,
    val saved: Boolean = false,
    val deleted: Boolean = false
) {
    val isNew: Boolean get() = taskId == 0L
    val endMinute: Int get() = (startMinute + durationMinutes).coerceAtMost(24 * 60)
    val canSave: Boolean
        get() = title.isNotBlank() &&
            durationMinutes in 5..720 &&
            (recurrence != Recurrence.WEEKLY || daysMask != DayMask.NONE)
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    settings: com.kairo.timemanager.data.prefs.SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        val taskId = savedStateHandle.get<Long>("taskId") ?: -1L
        val day = savedStateHandle.get<Long>("day") ?: -1L
        val epochDay = if (day >= 0) day else TimeUtils.todayEpochDay()

        viewModelScope.launch {
            val use24h = settings.currentAppPrefs().use24HourClock
            val categories = taskRepository.categoriesSnapshot()
            if (taskId > 0) {
                val task = taskRepository.getTask(taskId)
                if (task != null) {
                    _state.value = EditorUiState(
                        taskId = task.id,
                        epochDay = epochDay,
                        title = task.title,
                        note = task.note,
                        category = task.category,
                        colorHex = task.colorHex,
                        priority = task.priority,
                        startMinute = task.startMinute,
                        durationMinutes = task.durationMinutes,
                        recurrence = task.recurrence,
                        daysMask = task.daysMask,
                        reminderMinutes = task.reminderMinutes,
                        checklist = taskRepository.checklistSnapshot(task.id).map { it.text },
                        knownCategories = categories,
                        use24h = use24h,
                        loading = false
                    )
                    refreshConflicts()
                    return@launch
                }
            }
            // New block: default to the next round half-hour so the common case needs no editing.
            val suggested = if (epochDay == TimeUtils.todayEpochDay()) {
                (((TimeUtils.nowMinuteOfDay() / 30) + 1) * 30).coerceIn(0, 23 * 60)
            } else 9 * 60
            _state.value = EditorUiState(
                epochDay = epochDay,
                startMinute = suggested,
                knownCategories = categories,
                category = categories.firstOrNull() ?: "General",
                use24h = use24h,
                loading = false
            )
        }
    }

    fun update(transform: (EditorUiState) -> EditorUiState) {
        _state.value = transform(_state.value)
        refreshConflicts()
    }

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setNote(value: String) = update { it.copy(note = value) }
    fun setCategory(value: String) = update { it.copy(category = value) }
    fun setColor(value: String) = update { it.copy(colorHex = value) }
    fun setPriority(value: Int) = update { it.copy(priority = value) }
    fun setStart(minute: Int) = update { it.copy(startMinute = minute.coerceIn(0, 23 * 60 + 55)) }
    fun setDuration(minutes: Int) = update { it.copy(durationMinutes = minutes.coerceIn(5, 720)) }
    fun setReminder(minutes: Int) = update { it.copy(reminderMinutes = minutes) }

    fun setRecurrence(recurrence: Int) = update { current ->
        current.copy(
            recurrence = recurrence,
            daysMask = when (recurrence) {
                Recurrence.WEEKLY -> if (current.daysMask == DayMask.NONE) {
                    TimeUtils.dayBit(current.epochDay)
                } else current.daysMask
                else -> current.daysMask
            }
        )
    }

    fun toggleDay(index: Int) = update { current ->
        current.copy(
            recurrence = Recurrence.WEEKLY,
            daysMask = DayMask.toggle(current.daysMask, index)
        )
    }

    fun setDaysMask(mask: Int) = update {
        it.copy(recurrence = Recurrence.WEEKLY, daysMask = mask)
    }

    fun addChecklistLine(text: String) {
        if (text.isBlank()) return
        update { it.copy(checklist = it.checklist + text.trim()) }
    }

    fun updateChecklistLine(index: Int, text: String) = update { current ->
        current.copy(
            checklist = current.checklist.toMutableList().also { it[index] = text }
        )
    }

    fun removeChecklistLine(index: Int) = update { current ->
        current.copy(checklist = current.checklist.filterIndexed { i, _ -> i != index })
    }

    fun moveChecklistLine(index: Int, delta: Int) = update { current ->
        val target = index + delta
        if (target !in current.checklist.indices) return@update current
        val mutable = current.checklist.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(target, moved)
        current.copy(checklist = mutable)
    }

    private fun refreshConflicts() {
        viewModelScope.launch {
            val current = _state.value
            if (current.title.isBlank()) return@launch
            val probe = toEntity(current)
            val day = when (current.recurrence) {
                Recurrence.WEEKLY -> nextMatchingDay(current.daysMask, current.epochDay)
                else -> current.epochDay
            }
            val conflicts = taskRepository.conflictsFor(probe, day)
            if (_state.value.conflicts != conflicts) {
                _state.value = _state.value.copy(conflicts = conflicts)
            }
        }
    }

    private fun nextMatchingDay(mask: Int, from: Long): Long {
        for (offset in 0..6) {
            val day = from + offset
            if (mask and TimeUtils.dayBit(day) != 0) return day
        }
        return from
    }

    private fun toEntity(state: EditorUiState): TaskEntity = TaskEntity(
        id = state.taskId,
        title = state.title.trim(),
        note = state.note.trim(),
        category = state.category.trim().ifBlank { "General" },
        colorHex = state.colorHex,
        priority = state.priority,
        startMinute = state.startMinute,
        durationMinutes = state.durationMinutes,
        recurrence = state.recurrence,
        daysMask = if (state.recurrence == Recurrence.WEEKLY) state.daysMask else 0,
        specificDate = if (state.recurrence == Recurrence.ONCE) state.epochDay else null,
        startEpochDay = if (state.recurrence == Recurrence.ONCE) state.epochDay
        else minOf(state.epochDay, TimeUtils.todayEpochDay()),
        reminderMinutes = state.reminderMinutes
    )

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val id = taskRepository.upsertTask(toEntity(current))
            taskRepository.replaceChecklist(id, current.checklist)
            reminderScheduler.rescheduleAll()
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun delete() {
        val id = _state.value.taskId
        if (id == 0L) {
            _state.value = _state.value.copy(deleted = true)
            return
        }
        viewModelScope.launch {
            taskRepository.getTask(id)?.let { reminderScheduler.cancel(it) }
            taskRepository.deleteTask(id)
            _state.value = _state.value.copy(deleted = true)
        }
    }

    fun archive() {
        val id = _state.value.taskId
        if (id == 0L) return
        viewModelScope.launch {
            taskRepository.archiveTask(id, archived = true)
            reminderScheduler.rescheduleAll()
            _state.value = _state.value.copy(deleted = true)
        }
    }
}
