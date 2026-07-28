package com.kairo.timemanager.core

import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.data.local.entity.TaskEntity

/** Whether a block's recurrence rule places it on a given calendar day. */
object RecurrenceRules {

    fun occursOn(task: TaskEntity, epochDay: Long): Boolean {
        if (task.archived) return false
        if (epochDay < task.startEpochDay) return false
        task.endEpochDay?.let { if (epochDay > it) return false }
        return when (task.recurrence) {
            Recurrence.ONCE -> task.specificDate == epochDay
            Recurrence.DAILY -> true
            Recurrence.WEEKLY -> task.daysMask and TimeUtils.dayBit(epochDay) != 0
            else -> false
        }
    }

    /** The next day on or after [from] on which the block occurs, within a fortnight. */
    fun nextOccurrence(task: TaskEntity, from: Long, horizonDays: Int = 14): Long? =
        (0..horizonDays).map { from + it }.firstOrNull { occursOn(task, it) }
}
