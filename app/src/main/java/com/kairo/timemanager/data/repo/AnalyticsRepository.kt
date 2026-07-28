package com.kairo.timemanager.data.repo

import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.dao.ChecklistDao
import com.kairo.timemanager.data.local.dao.TaskDao
import com.kairo.timemanager.data.local.dao.TaskLogDao
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.local.entity.TaskLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

enum class Period(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

/**
 * Builds the day/week/month reports. Everything is derived from the current task definitions
 * plus the immutable per-day logs, so a report always reflects the schedule as it stands today.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val logDao: TaskLogDao,
    private val checklistDao: ChecklistDao,
    private val taskRepository: TaskRepository
) {

    fun rangeFor(period: Period, anchorEpochDay: Long): LongRange = when (period) {
        Period.DAILY -> anchorEpochDay..anchorEpochDay
        Period.WEEKLY -> TimeUtils.startOfWeek(anchorEpochDay)..TimeUtils.endOfWeek(anchorEpochDay)
        Period.MONTHLY -> TimeUtils.startOfMonth(anchorEpochDay)..TimeUtils.endOfMonth(anchorEpochDay)
    }

    fun labelFor(period: Period, anchorEpochDay: Long): String = when (period) {
        Period.DAILY -> TimeUtils.formatDayLabel(anchorEpochDay)
        Period.WEEKLY -> {
            val from = TimeUtils.startOfWeek(anchorEpochDay)
            val to = TimeUtils.endOfWeek(anchorEpochDay)
            "${TimeUtils.date(from).month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} " +
                "${TimeUtils.date(from).dayOfMonth} – ${TimeUtils.date(to).dayOfMonth}"
        }
        Period.MONTHLY -> TimeUtils.formatMonth(anchorEpochDay)
    }

    fun observeReport(period: Period, anchorEpochDay: Long): Flow<PeriodReport> {
        val range = rangeFor(period, anchorEpochDay)
        return combine(
            taskDao.observeActive(),
            logDao.observeForRange(range.first, range.last),
            logDao.observeCategoryAggregates(range.first, range.last)
        ) { tasks, logs, categories ->
            PeriodReport(
                label = labelFor(period, anchorEpochDay),
                fromDay = range.first,
                toDay = range.last,
                days = buildDays(tasks, logs, range),
                categories = categories.map {
                    CategorySlice(it.category, it.colorHex, it.completed, it.minutes)
                }
            )
        }
    }

    suspend fun report(period: Period, anchorEpochDay: Long): PeriodReport {
        val range = rangeFor(period, anchorEpochDay)
        val tasks = taskDao.getActive()
        val logs = logDao.getForRange(range.first, range.last)
        val categories = buildCategories(tasks, logs)
        return PeriodReport(
            label = labelFor(period, anchorEpochDay),
            fromDay = range.first,
            toDay = range.last,
            days = buildDays(tasks, logs, range),
            categories = categories
        )
    }

    private fun buildCategories(tasks: List<TaskEntity>, logs: List<TaskLogEntity>): List<CategorySlice> {
        val byId = tasks.associateBy { it.id }
        return logs.filter { it.completed }
            .mapNotNull { byId[it.taskId] }
            .groupBy { it.category }
            .map { (category, group) ->
                CategorySlice(
                    category = category,
                    colorHex = group.first().colorHex,
                    completed = group.size,
                    minutes = group.sumOf { it.durationMinutes }
                )
            }
            .sortedByDescending { it.minutes }
    }

    private fun buildDays(
        tasks: List<TaskEntity>,
        logs: List<TaskLogEntity>,
        range: LongRange
    ): List<DaySnapshot> {
        val logsByDay = logs.groupBy { it.epochDay }
        val byId = tasks.associateBy { it.id }
        return range.map { day ->
            val planned = tasks.filter { taskRepository.occursOn(it, day) }
            val dayLogs = logsByDay[day].orEmpty()
            val completedLogs = dayLogs.filter { it.completed }
            DaySnapshot(
                epochDay = day,
                planned = planned.size,
                completed = completedLogs.count { byId.containsKey(it.taskId) },
                skipped = dayLogs.count { it.skipped && !it.completed },
                plannedMinutes = planned.sumOf { it.durationMinutes },
                completedMinutes = completedLogs.sumOf { byId[it.taskId]?.durationMinutes ?: 0 },
                checklistDone = 0,
                checklistTotal = 0
            )
        }
    }

    /** Completed blocks bucketed into six 4-hour bands — feeds the "when do you actually deliver" chart. */
    suspend fun hourBands(from: Long, to: Long): List<Int> {
        val tasks = taskDao.getActive().associateBy { it.id }
        val logs = logDao.getForRange(from, to).filter { it.completed }
        val bands = IntArray(6)
        logs.forEach { log ->
            val task = tasks[log.taskId] ?: return@forEach
            bands[(task.startMinute / 240).coerceIn(0, 5)]++
        }
        return bands.toList()
    }

    suspend fun checklistCompleted(from: Long, to: Long): Int = checklistDao.countChecked(from, to)

    /** Per-weekday completion rate over a window — powers "your Thursdays are weak". */
    suspend fun weekdayProfile(from: Long, to: Long): Map<Int, Float> {
        val tasks = taskDao.getActive()
        val logs = logDao.getForRange(from, to).filter { it.completed }.groupBy { it.epochDay }
        val planned = HashMap<Int, Int>()
        val done = HashMap<Int, Int>()
        for (day in from..to) {
            val dow = TimeUtils.dayOfWeek(day).value
            val p = tasks.count { taskRepository.occursOn(it, day) }
            if (p == 0) continue
            planned[dow] = (planned[dow] ?: 0) + p
            done[dow] = (done[dow] ?: 0) + (logs[day]?.size ?: 0)
        }
        return planned.mapValues { (dow, p) ->
            if (p == 0) 0f else (done[dow] ?: 0).toFloat() / p
        }
    }

    /** Compact text digest handed to the AI so it can reason about real numbers. */
    suspend fun textDigest(anchorEpochDay: Long = TimeUtils.todayEpochDay()): String {
        val daily = report(Period.DAILY, anchorEpochDay)
        val weekly = report(Period.WEEKLY, anchorEpochDay)
        val monthly = report(Period.MONTHLY, anchorEpochDay)
        val top = weekly.categories.take(3).joinToString(", ") {
            "${it.category} ${TimeUtils.formatDuration(it.minutes)}"
        }.ifBlank { "none yet" }
        return buildString {
            appendLine(daily.summaryLine())
            appendLine(weekly.summaryLine())
            appendLine(monthly.summaryLine())
            appendLine("Top categories this week: $top")
        }.trim()
    }
}
