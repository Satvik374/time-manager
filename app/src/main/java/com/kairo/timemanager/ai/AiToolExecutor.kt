package com.kairo.timemanager.ai

import com.kairo.timemanager.core.DayMask
import com.kairo.timemanager.core.Leveling
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.Priority
import com.kairo.timemanager.data.local.entity.RecommendationEntity
import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.repo.AnalyticsRepository
import com.kairo.timemanager.data.repo.Period
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.data.repo.RecommendationRepository
import com.kairo.timemanager.data.repo.ScheduledTask
import com.kairo.timemanager.data.repo.TaskRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** What a tool call did, so the UI can show "Coach edited your timetable" affordances. */
data class ToolOutcome(
    val name: String,
    val resultJson: String,
    val succeeded: Boolean,
    val mutatedSchedule: Boolean,
    val summary: String
)

/**
 * Executes the model's tool calls against the real repositories.
 * Every failure is returned to the model as a structured `{"error": …}` payload rather than
 * thrown, so a bad argument produces a retry instead of killing the conversation.
 */
@Singleton
class AiToolExecutor @Inject constructor(
    private val taskRepository: TaskRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val progressRepository: ProgressRepository,
    private val recommendationRepository: RecommendationRepository,
    private val json: Json
) {

    suspend fun execute(call: ToolCall): ToolOutcome {
        val args = runCatching { json.parseToJsonElement(call.argumentsJson).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }
        val spec = AiTools.byName[call.name]
            ?: return failure(call.name, "Unknown tool '${call.name}'.", mutating = false)

        return runCatching {
            when (call.name) {
                AiTools.GET_SCHEDULE -> getSchedule(args)
                AiTools.GET_TASK -> getTask(args)
                AiTools.SEARCH_TASKS -> searchTasks(args)
                AiTools.CREATE_TASK -> createTask(args)
                AiTools.UPDATE_TASK -> updateTask(args)
                AiTools.DELETE_TASK -> deleteTask(args)
                AiTools.MOVE_TASK -> moveTask(args)
                AiTools.SET_TASK_COMPLETION -> setCompletion(args)
                AiTools.ADD_CHECKLIST_ITEMS -> addChecklistItems(args)
                AiTools.UPDATE_CHECKLIST_ITEM -> updateChecklistItem(args)
                AiTools.REMOVE_CHECKLIST_ITEM -> removeChecklistItem(args)
                AiTools.GET_ANALYTICS -> getAnalytics(args)
                AiTools.GET_PROGRESS -> getProgress()
                AiTools.LIST_REWARDS -> listRewards()
                AiTools.LIST_CATEGORIES -> listCategories()
                AiTools.SUGGEST_RECOMMENDATION -> suggestRecommendation(args)
                else -> failure(call.name, "Tool '${call.name}' is not implemented.", spec.mutating)
            }
        }.getOrElse { throwable ->
            failure(call.name, throwable.message ?: "tool failed", spec.mutating)
        }
    }

    // ---- readers ---------------------------------------------------------

    private suspend fun getSchedule(args: JsonObject): ToolOutcome {
        val start = args.dateOrToday("date")
        val days = args.intOrNull("days")?.coerceIn(1, 14) ?: 1
        // The JSON builders are not suspending, so every day is resolved up front.
        val agendas = (0 until days).map { offset ->
            val day = start + offset
            day to taskRepository.getDay(day)
        }
        val payload = buildJsonObject {
            put("timezone", TimeUtils.zone.id)
            putJsonArray("days") {
                agendas.forEach { (day, agenda) ->
                    addJsonObject {
                        put("date", TimeUtils.formatIso(day))
                        put("weekday", TimeUtils.dayOfWeek(day).name.lowercase())
                        put("planned", agenda.size)
                        put("completed", agenda.count { it.completed })
                        putJsonArray("blocks") { agenda.forEach { add(it.toJson()) } }
                    }
                }
            }
        }
        val total = agendas.sumOf { it.second.size }
        return ToolOutcome(
            AiTools.GET_SCHEDULE, payload.toString(), true, false,
            "Read $days day(s), $total block(s)"
        )
    }

    private suspend fun getTask(args: JsonObject): ToolOutcome {
        val id = args.longOrNull("task_id")
            ?: return failure(AiTools.GET_TASK, "task_id is required", false)
        val day = args.dateOrToday("date")
        val scheduled = taskRepository.getDay(day).firstOrNull { it.id == id }
        if (scheduled != null) {
            return ToolOutcome(AiTools.GET_TASK, scheduled.toJson().toString(), true, false, "Read #$id")
        }
        val task = taskRepository.getTask(id)
            ?: return failure(AiTools.GET_TASK, "No block with id $id", false)
        return ToolOutcome(AiTools.GET_TASK, task.toJson().toString(), true, false, "Read #$id")
    }

    private suspend fun searchTasks(args: JsonObject): ToolOutcome {
        val query = args.stringOrNull("query").orEmpty()
        if (query.isBlank()) return failure(AiTools.SEARCH_TASKS, "query is required", false)
        val matches = taskRepository.searchTasks(query)
        val payload = buildJsonObject {
            put("query", query)
            put("count", matches.size)
            putJsonArray("matches") { matches.forEach { add(it.toJson()) } }
        }
        return ToolOutcome(
            AiTools.SEARCH_TASKS, payload.toString(), true, false,
            "Found ${matches.size} match(es) for \"$query\""
        )
    }

    private suspend fun getAnalytics(args: JsonObject): ToolOutcome {
        val period = when (args.stringOrNull("period")?.lowercase()) {
            "monthly", "month" -> Period.MONTHLY
            "daily", "day", "today" -> Period.DAILY
            else -> Period.WEEKLY
        }
        val anchor = args.dateOrToday("date")
        val report = analyticsRepository.report(period, anchor)
        val bands = analyticsRepository.hourBands(report.fromDay, report.toDay)
        val payload = buildJsonObject {
            put("period", period.label.lowercase())
            put("from", TimeUtils.formatIso(report.fromDay))
            put("to", TimeUtils.formatIso(report.toDay))
            put("planned_blocks", report.planned)
            put("completed_blocks", report.completed)
            put("completion_rate", (report.completionRate * 100).toInt())
            put("focused_minutes", report.focusMinutes)
            put("perfect_days", report.perfectDays)
            put("active_days", report.activeDays)
            put("consistency_percent", (report.consistency * 100).toInt())
            report.bestDay?.let { put("best_day", TimeUtils.formatIso(it.epochDay)) }
            report.worstDay?.let { put("weakest_day", TimeUtils.formatIso(it.epochDay)) }
            putJsonArray("per_day") {
                report.days.forEach { day ->
                    addJsonObject {
                        put("date", TimeUtils.formatIso(day.epochDay))
                        put("weekday", TimeUtils.dayOfWeek(day.epochDay).name.lowercase())
                        put("planned", day.planned)
                        put("completed", day.completed)
                        put("focused_minutes", day.completedMinutes)
                    }
                }
            }
            putJsonArray("categories") {
                report.categories.forEach { slice ->
                    addJsonObject {
                        put("category", slice.category)
                        put("completed", slice.completed)
                        put("minutes", slice.minutes)
                    }
                }
            }
            putJsonObject("completions_by_time_band") {
                listOf("00-04", "04-08", "08-12", "12-16", "16-20", "20-24")
                    .forEachIndexed { index, label -> put(label, bands.getOrElse(index) { 0 }) }
            }
        }
        return ToolOutcome(
            AiTools.GET_ANALYTICS, payload.toString(), true, false,
            "Read ${period.label.lowercase()} analytics"
        )
    }

    private suspend fun getProgress(): ToolOutcome {
        val stats = progressRepository.stats()
        val level = Leveling.infoFor(stats.xp)
        val unlocked = progressRepository.unlockedAchievementTitles()
        val payload = buildJsonObject {
            put("current_streak_days", stats.currentStreak)
            put("longest_streak_days", stats.longestStreak)
            put("freeze_tokens", stats.freezeTokens)
            put("streak_alive_through", if (stats.lastStreakDay >= 0) TimeUtils.formatIso(stats.lastStreakDay) else "never")
            put("xp", stats.xp)
            put("level", level.level)
            put("level_title", level.title)
            put("xp_to_next_level", level.xpForNextLevel - level.xpIntoLevel)
            put("coins", stats.coins)
            put("total_blocks_completed", stats.totalTasksCompleted)
            put("total_focused_minutes", stats.totalFocusMinutes)
            put("perfect_days", stats.perfectDays)
            putJsonArray("unlocked_achievements") { unlocked.forEach { add(it) } }
        }
        return ToolOutcome(
            AiTools.GET_PROGRESS, payload.toString(), true, false,
            "Read streak & rewards"
        )
    }

    private suspend fun listRewards(): ToolOutcome {
        val stats = progressRepository.stats()
        val rewards = progressRepository.rewardsSnapshot()
        val payload = buildJsonObject {
            put("coins", stats.coins)
            putJsonArray("rewards") {
                rewards.forEach { reward ->
                    addJsonObject {
                        put("id", reward.id)
                        put("title", reward.title)
                        put("cost", reward.cost)
                        put("affordable", stats.coins >= reward.cost)
                        put("times_redeemed", reward.timesRedeemed)
                    }
                }
            }
            put("note", "Only the user can redeem rewards, from the Rewards tab.")
        }
        return ToolOutcome(AiTools.LIST_REWARDS, payload.toString(), true, false, "Read reward shop")
    }

    private suspend fun listCategories(): ToolOutcome {
        val categories = taskRepository.categoriesSnapshot()
        val payload = buildJsonObject {
            putJsonArray("categories") { categories.forEach { add(it) } }
        }
        return ToolOutcome(AiTools.LIST_CATEGORIES, payload.toString(), true, false, "Read categories")
    }

    // ---- writers ---------------------------------------------------------

    private suspend fun createTask(args: JsonObject): ToolOutcome {
        val title = args.stringOrNull("title")?.trim()
        if (title.isNullOrBlank()) return failure(AiTools.CREATE_TASK, "title is required", true)

        val startMinute = TimeUtils.parseTimeToMinuteOrNull(args.stringOrNull("start_time"))
            ?: return failure(AiTools.CREATE_TASK, "start_time must look like HH:MM", true)
        val duration = args.intOrNull("duration_minutes")
            ?: return failure(AiTools.CREATE_TASK, "duration_minutes is required", true)
        if (duration !in 5..720) {
            return failure(AiTools.CREATE_TASK, "duration_minutes must be between 5 and 720", true)
        }

        val dayNames = args.stringListOrNull("days")
        val mask = DayMask.fromNames(dayNames)
        val specificDate = TimeUtils.parseIsoDateOrNull(args.stringOrNull("date"))
        val today = TimeUtils.todayEpochDay()

        val recurrence = when {
            mask == DayMask.ALL -> Recurrence.DAILY
            mask != DayMask.NONE -> Recurrence.WEEKLY
            else -> Recurrence.ONCE
        }
        val onceDate = specificDate ?: today

        val task = TaskEntity(
            title = title,
            note = args.stringOrNull("note").orEmpty(),
            category = args.stringOrNull("category")?.takeIf { it.isNotBlank() } ?: "General",
            colorHex = normalizeColor(args.stringOrNull("color")),
            priority = parsePriority(args.stringOrNull("priority")),
            startMinute = startMinute,
            durationMinutes = duration,
            recurrence = recurrence,
            daysMask = if (recurrence == Recurrence.WEEKLY) mask else 0,
            specificDate = if (recurrence == Recurrence.ONCE) onceDate else null,
            startEpochDay = if (recurrence == Recurrence.ONCE) onceDate else today,
            reminderMinutes = args.intOrNull("reminder_minutes")?.coerceIn(0, 240) ?: -1
        )

        val conflicts = taskRepository.conflictsFor(
            task,
            if (recurrence == Recurrence.ONCE) onceDate else nextOccurrence(mask, recurrence, today)
        )
        val id = taskRepository.upsertTask(task)
        val checklist = args.stringListOrNull("checklist").orEmpty()
        if (checklist.isNotEmpty()) taskRepository.addChecklistItems(id, checklist)

        val payload = buildJsonObject {
            put("ok", true)
            put("task_id", id)
            put("title", title)
            put("when", describeSchedule(recurrence, mask, onceDate, startMinute, duration))
            put("checklist_items_added", checklist.size)
            if (conflicts.isNotEmpty()) {
                putJsonArray("overlaps_existing") {
                    conflicts.forEach { add("${it.title} (${it.timeLabel})") }
                }
                put("warning", "This block overlaps existing blocks — tell the user and offer to move it.")
            }
        }
        return ToolOutcome(
            AiTools.CREATE_TASK, payload.toString(), true, true,
            "Created \"$title\" ${TimeUtils.formatTime(startMinute)}"
        )
    }

    private suspend fun updateTask(args: JsonObject): ToolOutcome {
        val id = args.longOrNull("task_id")
            ?: return failure(AiTools.UPDATE_TASK, "task_id is required", true)
        val existing = taskRepository.getTask(id)
            ?: return failure(AiTools.UPDATE_TASK, "No block with id $id", true)

        var updated = existing
        args.stringOrNull("title")?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(title = it.trim()) }
        args.stringOrNull("note")?.let { updated = updated.copy(note = it) }
        args.stringOrNull("category")?.takeIf { it.isNotBlank() }?.let { updated = updated.copy(category = it.trim()) }
        args.stringOrNull("color")?.let { updated = updated.copy(colorHex = normalizeColor(it)) }
        args.stringOrNull("priority")?.let { updated = updated.copy(priority = parsePriority(it)) }
        args.intOrNull("reminder_minutes")?.let {
            updated = updated.copy(reminderMinutes = if (it < 0) -1 else it.coerceAtMost(240))
        }
        args.booleanOrNull("archived")?.let { updated = updated.copy(archived = it) }
        args.intOrNull("duration_minutes")?.let {
            if (it !in 5..720) return failure(AiTools.UPDATE_TASK, "duration_minutes must be 5–720", true)
            updated = updated.copy(durationMinutes = it)
        }
        args.stringOrNull("start_time")?.let { raw ->
            val minute = TimeUtils.parseTimeToMinuteOrNull(raw)
                ?: return failure(AiTools.UPDATE_TASK, "start_time must look like HH:MM", true)
            updated = updated.copy(startMinute = minute)
        }
        args.stringListOrNull("days")?.let { names ->
            val mask = DayMask.fromNames(names)
            if (mask != DayMask.NONE) {
                updated = if (mask == DayMask.ALL) {
                    updated.copy(recurrence = Recurrence.DAILY, daysMask = 0, specificDate = null)
                } else {
                    updated.copy(recurrence = Recurrence.WEEKLY, daysMask = mask, specificDate = null)
                }
            }
        }
        TimeUtils.parseIsoDateOrNull(args.stringOrNull("date"))?.let { date ->
            updated = updated.copy(
                recurrence = Recurrence.ONCE,
                specificDate = date,
                startEpochDay = date,
                daysMask = 0
            )
        }

        taskRepository.upsertTask(updated.copy(updatedAt = System.currentTimeMillis()))
        val payload = buildJsonObject {
            put("ok", true)
            put("task_id", id)
            put("task", updated.toJson())
        }
        return ToolOutcome(
            AiTools.UPDATE_TASK, payload.toString(), true, true,
            "Updated \"${updated.title}\""
        )
    }

    private suspend fun deleteTask(args: JsonObject): ToolOutcome {
        val id = args.longOrNull("task_id")
            ?: return failure(AiTools.DELETE_TASK, "task_id is required", true)
        val existing = taskRepository.getTask(id)
            ?: return failure(AiTools.DELETE_TASK, "No block with id $id", true)
        taskRepository.deleteTask(id)
        val payload = buildJsonObject {
            put("ok", true)
            put("deleted_task_id", id)
            put("title", existing.title)
        }
        return ToolOutcome(
            AiTools.DELETE_TASK, payload.toString(), true, true,
            "Deleted \"${existing.title}\""
        )
    }

    private suspend fun moveTask(args: JsonObject): ToolOutcome {
        val id = args.longOrNull("task_id")
            ?: return failure(AiTools.MOVE_TASK, "task_id is required", true)
        val startMinute = args.stringOrNull("start_time")?.let {
            TimeUtils.parseTimeToMinuteOrNull(it)
                ?: return failure(AiTools.MOVE_TASK, "start_time must look like HH:MM", true)
        }
        val duration = args.intOrNull("duration_minutes")?.also {
            if (it !in 5..720) return failure(AiTools.MOVE_TASK, "duration_minutes must be 5–720", true)
        }
        val date = TimeUtils.parseIsoDateOrNull(args.stringOrNull("date"))
        val moved = taskRepository.moveTask(id, startMinute, duration, date)
        if (!moved) return failure(AiTools.MOVE_TASK, "No block with id $id", true)
        val task = taskRepository.getTask(id)
        val payload = buildJsonObject {
            put("ok", true)
            put("task_id", id)
            put("new_time", task?.let { TimeUtils.formatRange(it.startMinute, it.durationMinutes) } ?: "")
            date?.let { put("new_date", TimeUtils.formatIso(it)) }
        }
        return ToolOutcome(
            AiTools.MOVE_TASK, payload.toString(), true, true,
            "Moved \"${task?.title.orEmpty()}\""
        )
    }

    private suspend fun setCompletion(args: JsonObject): ToolOutcome {
        val id = args.longOrNull("task_id")
            ?: return failure(AiTools.SET_TASK_COMPLETION, "task_id is required", true)
        val completed = args.booleanOrNull("completed")
            ?: return failure(AiTools.SET_TASK_COMPLETION, "completed is required", true)
        val day = args.dateOrToday("date")
        val task = taskRepository.getTask(id)
            ?: return failure(AiTools.SET_TASK_COMPLETION, "No block with id $id", true)

        val newlyCompleted = taskRepository.setCompleted(id, day, completed)
        val events = if (newlyCompleted) {
            progressRepository.onTaskCompleted(task.priority, task.durationMinutes, task.startMinute, day)
        } else {
            progressRepository.refreshStreak()
        }
        val stats = progressRepository.stats()
        val payload = buildJsonObject {
            put("ok", true)
            put("task_id", id)
            put("date", TimeUtils.formatIso(day))
            put("completed", completed)
            put("newly_completed", newlyCompleted)
            put("current_streak_days", stats.currentStreak)
            put("coins", stats.coins)
            putJsonArray("events") { events.forEach { add(describeEvent(it)) } }
        }
        return ToolOutcome(
            AiTools.SET_TASK_COMPLETION, payload.toString(), true, true,
            if (completed) "Ticked off \"${task.title}\"" else "Cleared \"${task.title}\""
        )
    }

    private suspend fun addChecklistItems(args: JsonObject): ToolOutcome {
        val id = args.longOrNull("task_id")
            ?: return failure(AiTools.ADD_CHECKLIST_ITEMS, "task_id is required", true)
        taskRepository.getTask(id)
            ?: return failure(AiTools.ADD_CHECKLIST_ITEMS, "No block with id $id", true)
        val items = args.stringListOrNull("items").orEmpty().filter { it.isNotBlank() }
        if (items.isEmpty()) return failure(AiTools.ADD_CHECKLIST_ITEMS, "items must be a non-empty list", true)

        val replace = args.booleanOrNull("replace") ?: false
        if (replace) taskRepository.replaceChecklist(id, items)
        else taskRepository.addChecklistItems(id, items)

        val current = taskRepository.checklistSnapshot(id)
        val payload = buildJsonObject {
            put("ok", true)
            put("task_id", id)
            put("replaced", replace)
            putJsonArray("checklist") {
                current.forEach { item ->
                    addJsonObject {
                        put("item_id", item.id)
                        put("text", item.text)
                    }
                }
            }
        }
        return ToolOutcome(
            AiTools.ADD_CHECKLIST_ITEMS, payload.toString(), true, true,
            "${if (replace) "Replaced" else "Added"} ${items.size} checklist step(s)"
        )
    }

    private suspend fun updateChecklistItem(args: JsonObject): ToolOutcome {
        val itemId = args.longOrNull("item_id")
            ?: return failure(AiTools.UPDATE_CHECKLIST_ITEM, "item_id is required", true)
        var touched = false
        args.stringOrNull("text")?.takeIf { it.isNotBlank() }?.let {
            if (!taskRepository.renameChecklistItem(itemId, it)) {
                return failure(AiTools.UPDATE_CHECKLIST_ITEM, "No checklist item with id $itemId", true)
            }
            touched = true
        }
        args.booleanOrNull("checked")?.let { checked ->
            val day = args.dateOrToday("date")
            if (!taskRepository.setChecklistChecked(itemId, day, checked)) {
                return failure(AiTools.UPDATE_CHECKLIST_ITEM, "No checklist item with id $itemId", true)
            }
            if (checked) progressRepository.onChecklistItemChecked()
            touched = true
        }
        if (!touched) {
            return failure(AiTools.UPDATE_CHECKLIST_ITEM, "Pass `text` and/or `checked`", true)
        }
        val payload = buildJsonObject {
            put("ok", true)
            put("item_id", itemId)
        }
        return ToolOutcome(
            AiTools.UPDATE_CHECKLIST_ITEM, payload.toString(), true, true,
            "Updated checklist step"
        )
    }

    private suspend fun removeChecklistItem(args: JsonObject): ToolOutcome {
        val itemId = args.longOrNull("item_id")
            ?: return failure(AiTools.REMOVE_CHECKLIST_ITEM, "item_id is required", true)
        taskRepository.deleteChecklistItem(itemId)
        val payload = buildJsonObject {
            put("ok", true)
            put("removed_item_id", itemId)
        }
        return ToolOutcome(
            AiTools.REMOVE_CHECKLIST_ITEM, payload.toString(), true, true,
            "Removed a checklist step"
        )
    }

    private suspend fun suggestRecommendation(args: JsonObject): ToolOutcome {
        val title = args.stringOrNull("title")?.trim()
        val body = args.stringOrNull("body")?.trim()
        if (title.isNullOrBlank() || body.isNullOrBlank()) {
            return failure(AiTools.SUGGEST_RECOMMENDATION, "title and body are required", true)
        }
        val id = recommendationRepository.add(
            RecommendationEntity(
                title = title.take(80),
                body = body.take(400),
                kind = args.stringOrNull("kind")?.lowercase() ?: "general"
            )
        )
        val payload = buildJsonObject {
            put("ok", true)
            put("recommendation_id", id)
            put("note", "Posted to the user's Today screen. They can accept or dismiss it.")
        }
        return ToolOutcome(
            AiTools.SUGGEST_RECOMMENDATION, payload.toString(), true, false,
            "Posted a suggestion: $title"
        )
    }

    // ---- helpers ---------------------------------------------------------

    private fun failure(tool: String, message: String, mutating: Boolean): ToolOutcome {
        val payload = buildJsonObject {
            put("ok", false)
            put("error", message)
            if (mutating) put("hint", "Nothing was changed. Fix the arguments and call the tool again.")
        }
        // A failed call never counts as a schedule mutation, whatever the tool would have done.
        return ToolOutcome(tool, payload.toString(), false, false, message)
    }

    private fun describeEvent(event: com.kairo.timemanager.data.repo.RewardEvent): String = when (event) {
        is com.kairo.timemanager.data.repo.RewardEvent.Xp -> "+${event.amount} XP (${event.reason})"
        is com.kairo.timemanager.data.repo.RewardEvent.Coins -> "+${event.amount} coins (${event.reason})"
        is com.kairo.timemanager.data.repo.RewardEvent.LevelUp -> "Level up to ${event.level} — ${event.title}"
        is com.kairo.timemanager.data.repo.RewardEvent.Unlocked -> "Unlocked ${event.achievement.title}"
        is com.kairo.timemanager.data.repo.RewardEvent.StreakExtended -> "Streak now ${event.streak} days"
        is com.kairo.timemanager.data.repo.RewardEvent.StreakFrozen -> "Streak freeze used, ${event.tokensLeft} left"
        is com.kairo.timemanager.data.repo.RewardEvent.StreakLost -> "Streak reset from ${event.previous}"
        is com.kairo.timemanager.data.repo.RewardEvent.PerfectDay -> "Perfect day bonus"
    }

    private fun nextOccurrence(mask: Int, recurrence: Int, from: Long): Long {
        if (recurrence != Recurrence.WEEKLY) return from
        for (offset in 0..6) {
            val day = from + offset
            if (mask and TimeUtils.dayBit(day) != 0) return day
        }
        return from
    }

    private fun describeSchedule(
        recurrence: Int,
        mask: Int,
        onceDate: Long,
        startMinute: Int,
        duration: Int
    ): String {
        val time = TimeUtils.formatRange(startMinute, duration)
        return when (recurrence) {
            Recurrence.DAILY -> "Every day, $time"
            Recurrence.WEEKLY -> "${DayMask.describe(mask)}, $time"
            else -> "${TimeUtils.formatIso(onceDate)}, $time"
        }
    }

    private fun parsePriority(raw: String?): Int = when (raw?.lowercase()?.trim()) {
        "low" -> Priority.LOW
        "high" -> Priority.HIGH
        else -> Priority.NORMAL
    }

    private fun normalizeColor(raw: String?): String {
        val value = raw?.trim().orEmpty()
        val withHash = if (value.startsWith("#")) value else "#$value"
        return if (Regex("^#[0-9a-fA-F]{6}$").matches(withHash)) withHash.uppercase() else "#6C5CE7"
    }

    private fun ScheduledTask.toJson(): JsonObject = buildJsonObject {
        put("task_id", id)
        put("title", title)
        put("category", task.category)
        put("priority", Priority.label(task.priority).lowercase())
        put("start_time", TimeUtils.formatTime(startMinute, use24h = true))
        put("end_time", TimeUtils.formatTime(endMinute % (24 * 60), use24h = true))
        put("duration_minutes", task.durationMinutes)
        put("repeat", repeatLabel(task.recurrence, task.daysMask, task.specificDate))
        put("completed", completed)
        put("skipped", skipped)
        if (task.note.isNotBlank()) put("note", task.note)
        putJsonArray("checklist") {
            checklist.forEach { line ->
                addJsonObject {
                    put("item_id", line.id)
                    put("text", line.text)
                    put("checked", line.checked)
                }
            }
        }
    }

    private fun TaskEntity.toJson(): JsonObject = buildJsonObject {
        put("task_id", id)
        put("title", title)
        put("category", category)
        put("priority", Priority.label(priority).lowercase())
        put("start_time", TimeUtils.formatTime(startMinute, use24h = true))
        put("duration_minutes", durationMinutes)
        put("repeat", repeatLabel(recurrence, daysMask, specificDate))
        put("archived", archived)
        if (note.isNotBlank()) put("note", note)
        if (reminderMinutes >= 0) put("reminder_minutes_before", reminderMinutes)
    }

    private fun repeatLabel(recurrence: Int, daysMask: Int, specificDate: Long?): String =
        when (recurrence) {
            Recurrence.DAILY -> "daily"
            Recurrence.WEEKLY -> DayMask.describe(daysMask).lowercase()
            else -> specificDate?.let { "once on ${TimeUtils.formatIso(it)}" } ?: "once"
        }
}

// ---- JSON argument accessors that tolerate the shapes models actually emit ----

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

private fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()

private fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong()

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    primitive.booleanOrNull?.let { return it }
    return when (primitive.contentOrNull?.lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> null
    }
}

/** Accepts a real array, or a comma-separated string, which smaller models tend to send. */
private fun JsonObject.stringListOrNull(key: String): List<String>? {
    val element = this[key] ?: return null
    return when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> element.contentOrNull
            ?.split(',', '\n')
            ?.map { it.trim().removePrefix("- ") }
            ?.filter { it.isNotEmpty() }
        else -> null
    }
}

private fun JsonObject.dateOrToday(key: String): Long =
    TimeUtils.parseIsoDateOrNull(stringOrNull(key)) ?: TimeUtils.todayEpochDay()
