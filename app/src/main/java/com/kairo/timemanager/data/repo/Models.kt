package com.kairo.timemanager.data.repo

import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.ChecklistItemEntity
import com.kairo.timemanager.data.local.entity.TaskEntity

data class ChecklistLine(
    val item: ChecklistItemEntity,
    val checked: Boolean
) {
    val id: Long get() = item.id
    val text: String get() = item.text
}

/** A task resolved onto a concrete calendar day, with that day's completion + checklist state. */
data class ScheduledTask(
    val task: TaskEntity,
    val epochDay: Long,
    val completed: Boolean,
    val skipped: Boolean,
    val focusMinutes: Int,
    val checklist: List<ChecklistLine>
) {
    val id: Long get() = task.id
    val title: String get() = task.title
    val startMinute: Int get() = task.startMinute
    val endMinute: Int get() = task.startMinute + task.durationMinutes
    val checklistDone: Int get() = checklist.count { it.checked }
    val checklistTotal: Int get() = checklist.size
    val hasChecklist: Boolean get() = checklist.isNotEmpty()

    val checklistProgress: Float
        get() = if (checklist.isEmpty()) 0f else checklistDone.toFloat() / checklist.size

    val timeLabel: String get() = TimeUtils.formatRange(task.startMinute, task.durationMinutes)

    fun isNow(minuteOfDay: Int): Boolean =
        epochDay == TimeUtils.todayEpochDay() && minuteOfDay in startMinute until endMinute

    /** Unfinished and its window has passed — either an earlier day, or today's slot has ended. */
    fun isOverdue(minuteOfDay: Int): Boolean {
        if (completed || skipped) return false
        val today = TimeUtils.todayEpochDay()
        return epochDay < today || (epochDay == today && minuteOfDay >= endMinute)
    }
}

/** Everything a single day is worth, used by Today, Insights and the AI tools alike. */
data class DaySnapshot(
    val epochDay: Long,
    val planned: Int,
    val completed: Int,
    val skipped: Int,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val checklistDone: Int,
    val checklistTotal: Int
) {
    val completionRate: Float get() = if (planned == 0) 0f else completed.toFloat() / planned
    val isPerfect: Boolean get() = planned > 0 && completed == planned
    val remaining: Int get() = (planned - completed - skipped).coerceAtLeast(0)

    companion object {
        fun empty(epochDay: Long) = DaySnapshot(epochDay, 0, 0, 0, 0, 0, 0, 0)
    }
}

data class CategorySlice(
    val category: String,
    val colorHex: String,
    val completed: Int,
    val minutes: Int
)

data class PeriodReport(
    val label: String,
    val fromDay: Long,
    val toDay: Long,
    val days: List<DaySnapshot>,
    val categories: List<CategorySlice>
) {
    /**
     * Only days that have actually happened can be judged. A week viewed on Wednesday still
     * contains Thursday through Sunday, and scoring those as 0% would report the rest of the
     * week as failure before it has begun. [days] keeps the full range for the bar chart — a
     * pale planned bar on a future day is useful — but every rate below uses elapsed days only.
     */
    val elapsedDays: List<DaySnapshot>
        get() = days.filter { it.epochDay <= TimeUtils.todayEpochDay() }

    val planned: Int get() = elapsedDays.sumOf { it.planned }
    val completed: Int get() = elapsedDays.sumOf { it.completed }
    val focusMinutes: Int get() = elapsedDays.sumOf { it.completedMinutes }
    val plannedMinutes: Int get() = elapsedDays.sumOf { it.plannedMinutes }
    val completionRate: Float get() = if (planned == 0) 0f else completed.toFloat() / planned
    val perfectDays: Int get() = elapsedDays.count { it.isPerfect }
    val activeDays: Int get() = elapsedDays.count { it.completed > 0 }

    val bestDay: DaySnapshot?
        get() = elapsedDays.filter { it.planned > 0 }.maxByOrNull { it.completionRate }
    val worstDay: DaySnapshot?
        get() = elapsedDays.filter { it.planned > 0 }.minByOrNull { it.completionRate }

    /** How evenly effort is spread — high consistency means few zero days. */
    val consistency: Float
        get() {
            val scheduled = elapsedDays.count { it.planned > 0 }
            if (scheduled == 0) return 0f
            return elapsedDays.count { it.completed > 0 }.toFloat() / scheduled
        }

    val averageCompletedPerDay: Float
        get() = if (elapsedDays.isEmpty()) 0f else completed.toFloat() / elapsedDays.size

    /** Busiest hour band across the period, for the "you work best at…" insight. */
    fun summaryLine(): String {
        val pct = (completionRate * 100).toInt()
        return "$label: $completed/$planned blocks done ($pct%), " +
            "${TimeUtils.formatDuration(focusMinutes)} focused, " +
            "$perfectDays perfect day(s), streak-eligible days $activeDays/${elapsedDays.size}."
    }
}

data class StreakSnapshot(
    val current: Int,
    val longest: Int,
    val freezeTokens: Int,
    val lastStreakDay: Long,
    val atRisk: Boolean,
    val nextMilestone: Int
) {
    val daysToMilestone: Int get() = (nextMilestone - current).coerceAtLeast(0)
}
