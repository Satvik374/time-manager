package com.kairo.timemanager.data.repo

import com.kairo.timemanager.core.RecurrenceRules
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.dao.ChecklistDao
import com.kairo.timemanager.data.local.dao.TaskDao
import com.kairo.timemanager.data.local.dao.TaskLogDao
import com.kairo.timemanager.data.local.entity.ChecklistItemEntity
import com.kairo.timemanager.data.local.entity.ChecklistLogEntity
import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.local.entity.TaskLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: TaskLogDao,
    private val checklistDao: ChecklistDao
) {

    fun observeCategories(): Flow<List<String>> = taskDao.observeCategories()

    fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeActive()

    fun observeTask(id: Long): Flow<TaskEntity?> = taskDao.observeById(id)

    fun observeChecklist(taskId: Long): Flow<List<ChecklistItemEntity>> =
        checklistDao.observeForTask(taskId)

    suspend fun getTask(id: Long): TaskEntity? = taskDao.getById(id)

    suspend fun searchTasks(query: String): List<TaskEntity> = taskDao.search(query)

    /** True when [task]'s recurrence rule puts it on the given day. */
    fun occursOn(task: TaskEntity, epochDay: Long): Boolean =
        RecurrenceRules.occursOn(task, epochDay)

    /** The full ordered agenda for a day, joined with that day's logs and checklist ticks. */
    fun observeDay(epochDay: Long): Flow<List<ScheduledTask>> = combine(
        taskDao.observeActive(),
        logDao.observeForDay(epochDay),
        checklistDao.observeAll(),
        checklistDao.observeLogsForDay(epochDay)
    ) { tasks, logs, items, checkLogs ->
        buildDay(epochDay, tasks, logs, items, checkLogs)
    }

    suspend fun getDay(epochDay: Long): List<ScheduledTask> {
        val tasks = taskDao.getActive()
        val logs = logDao.getForDay(epochDay)
        val items = tasks.flatMap { checklistDao.getForTask(it.id) }
        val checkLogs = tasks.flatMap { checklistDao.getLogsForTaskDay(it.id, epochDay) }
        return buildDay(epochDay, tasks, logs, items, checkLogs)
    }

    private fun buildDay(
        epochDay: Long,
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
        items: List<ChecklistItemEntity>,
        checkLogs: List<ChecklistLogEntity>
    ): List<ScheduledTask> {
        val logByTask = logs.associateBy { it.taskId }
        val itemsByTask = items.groupBy { it.taskId }
        val checkedIds = checkLogs.filter { it.checked }.map { it.itemId }.toHashSet()
        return tasks.asSequence()
            .filter { occursOn(it, epochDay) }
            .map { task ->
                val log = logByTask[task.id]
                ScheduledTask(
                    task = task,
                    epochDay = epochDay,
                    completed = log?.completed == true,
                    skipped = log?.skipped == true,
                    focusMinutes = log?.focusMinutes ?: 0,
                    checklist = (itemsByTask[task.id] ?: emptyList())
                        .sortedBy { it.position }
                        .map { ChecklistLine(it, it.id in checkedIds) }
                )
            }
            .sortedWith(compareBy({ it.startMinute }, { it.task.id }))
            .toList()
    }

    suspend fun snapshotFor(epochDay: Long): DaySnapshot {
        val day = getDay(epochDay)
        if (day.isEmpty()) return DaySnapshot.empty(epochDay)
        return DaySnapshot(
            epochDay = epochDay,
            planned = day.size,
            completed = day.count { it.completed },
            skipped = day.count { it.skipped && !it.completed },
            plannedMinutes = day.sumOf { it.task.durationMinutes },
            completedMinutes = day.filter { it.completed }.sumOf { it.task.durationMinutes },
            checklistDone = day.sumOf { it.checklistDone },
            checklistTotal = day.sumOf { it.checklistTotal }
        )
    }

    fun observeSnapshot(epochDay: Long): Flow<DaySnapshot> = observeDay(epochDay).map { day ->
        if (day.isEmpty()) DaySnapshot.empty(epochDay)
        else DaySnapshot(
            epochDay = epochDay,
            planned = day.size,
            completed = day.count { it.completed },
            skipped = day.count { it.skipped && !it.completed },
            plannedMinutes = day.sumOf { it.task.durationMinutes },
            completedMinutes = day.filter { it.completed }.sumOf { it.task.durationMinutes },
            checklistDone = day.sumOf { it.checklistDone },
            checklistTotal = day.sumOf { it.checklistTotal }
        )
    }

    // ---- mutations -------------------------------------------------------

    suspend fun upsertTask(task: TaskEntity): Long =
        if (task.id == 0L) taskDao.insert(task)
        else {
            taskDao.update(task.copy(updatedAt = System.currentTimeMillis()))
            task.id
        }

    suspend fun deleteTask(id: Long) = taskDao.delete(id)

    suspend fun archiveTask(id: Long, archived: Boolean = true) = taskDao.setArchived(id, archived)

    suspend fun replaceChecklist(taskId: Long, lines: List<String>) {
        checklistDao.deleteForTask(taskId)
        if (lines.isEmpty()) return
        checklistDao.insertAll(
            lines.filter { it.isNotBlank() }.mapIndexed { index, text ->
                ChecklistItemEntity(taskId = taskId, text = text.trim(), position = index)
            }
        )
    }

    suspend fun addChecklistItems(taskId: Long, lines: List<String>): Int {
        var position = checklistDao.maxPosition(taskId) + 1
        var added = 0
        lines.filter { it.isNotBlank() }.forEach { text ->
            checklistDao.insert(ChecklistItemEntity(taskId = taskId, text = text.trim(), position = position++))
            added++
        }
        return added
    }

    suspend fun renameChecklistItem(itemId: Long, text: String): Boolean {
        val item = checklistDao.getItem(itemId) ?: return false
        checklistDao.update(item.copy(text = text.trim()))
        return true
    }

    suspend fun deleteChecklistItem(itemId: Long) = checklistDao.delete(itemId)

    suspend fun setChecklistChecked(itemId: Long, epochDay: Long, checked: Boolean): Boolean {
        val item = checklistDao.getItem(itemId) ?: return false
        checklistDao.upsertLog(
            ChecklistLogEntity(
                itemId = itemId,
                epochDay = epochDay,
                taskId = item.taskId,
                checked = checked,
                checkedAt = if (checked) System.currentTimeMillis() else null
            )
        )
        return true
    }

    suspend fun toggleChecklistItem(itemId: Long, epochDay: Long): Boolean {
        val current = checklistDao.getLog(itemId, epochDay)?.checked ?: false
        return setChecklistChecked(itemId, epochDay, !current)
    }

    /** Returns true when this call flipped the task from not-done to done (callers award XP on true). */
    suspend fun setCompleted(taskId: Long, epochDay: Long, completed: Boolean): Boolean {
        val previous = logDao.get(taskId, epochDay)
        val wasCompleted = previous?.completed == true
        val task = taskDao.getById(taskId)
        logDao.upsert(
            TaskLogEntity(
                taskId = taskId,
                epochDay = epochDay,
                completed = completed,
                skipped = if (completed) false else previous?.skipped ?: false,
                completedAt = if (completed) System.currentTimeMillis() else null,
                focusMinutes = if (completed) task?.durationMinutes ?: 0 else 0
            )
        )
        if (completed) {
            // Finishing the block implies its checklist is done, which keeps the two views honest.
            checklistDao.getForTask(taskId).forEach { setChecklistChecked(it.id, epochDay, true) }
        }
        return completed && !wasCompleted
    }

    suspend fun setSkipped(taskId: Long, epochDay: Long, skipped: Boolean) {
        val previous = logDao.get(taskId, epochDay)
        logDao.upsert(
            TaskLogEntity(
                taskId = taskId,
                epochDay = epochDay,
                completed = if (skipped) false else previous?.completed ?: false,
                skipped = skipped,
                completedAt = previous?.completedAt,
                focusMinutes = if (skipped) 0 else previous?.focusMinutes ?: 0
            )
        )
    }

    suspend fun logFocusMinutes(taskId: Long, epochDay: Long, minutes: Int) {
        val previous = logDao.get(taskId, epochDay)
        logDao.upsert(
            (previous ?: TaskLogEntity(taskId, epochDay))
                .copy(focusMinutes = (previous?.focusMinutes ?: 0) + minutes.coerceAtLeast(0))
        )
    }

    /** Moves a task to a new time and/or day. Returns false if the task is gone. */
    suspend fun moveTask(
        taskId: Long,
        newStartMinute: Int?,
        newDurationMinutes: Int?,
        newEpochDay: Long?
    ): Boolean {
        val task = taskDao.getById(taskId) ?: return false
        var updated = task.copy(
            startMinute = newStartMinute ?: task.startMinute,
            durationMinutes = newDurationMinutes ?: task.durationMinutes,
            updatedAt = System.currentTimeMillis()
        )
        if (newEpochDay != null) {
            updated = when (updated.recurrence) {
                Recurrence.ONCE -> updated.copy(specificDate = newEpochDay, startEpochDay = newEpochDay)
                Recurrence.WEEKLY -> updated.copy(daysMask = TimeUtils.dayBit(newEpochDay))
                else -> updated
            }
        }
        taskDao.update(updated)
        return true
    }

    /** Naive overlap check used by the editor and by the AI before it schedules something. */
    suspend fun conflictsFor(task: TaskEntity, epochDay: Long): List<ScheduledTask> {
        val day = getDay(epochDay)
        val start = task.startMinute
        val end = task.startMinute + task.durationMinutes
        return day.filter { it.id != task.id && it.startMinute < end && it.endMinute > start }
    }

    suspend fun activeTaskCount(): Int = taskDao.countActive()

    suspend fun allTasksSnapshot(): List<TaskEntity> = taskDao.getActive()

    suspend fun categoriesSnapshot(): List<String> =
        taskDao.getActive().map { it.category }.distinct().sorted()

    suspend fun checklistSnapshot(taskId: Long): List<ChecklistItemEntity> =
        checklistDao.getForTask(taskId)
}
