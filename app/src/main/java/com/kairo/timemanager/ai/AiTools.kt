package com.kairo.timemanager.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A tool the coach may call, in a provider-neutral shape. */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    /** Shown in Settings so the user can see and toggle exactly what the AI is allowed to do. */
    val humanLabel: String,
    val mutating: Boolean
)

private fun JsonObjectBuilder.stringProp(name: String, description: String, enum: List<String>? = null) {
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
        if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
    }
}

private fun JsonObjectBuilder.intProp(name: String, description: String) {
    putJsonObject(name) {
        put("type", "integer")
        put("description", description)
    }
}

private fun JsonObjectBuilder.boolProp(name: String, description: String) {
    putJsonObject(name) {
        put("type", "boolean")
        put("description", description)
    }
}

private fun JsonObjectBuilder.stringArrayProp(name: String, description: String) {
    putJsonObject(name) {
        put("type", "array")
        put("description", description)
        putJsonObject("items") { put("type", "string") }
    }
}

private fun schema(required: List<String> = emptyList(), props: JsonObjectBuilder.() -> Unit): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props() }
        putJsonArray("required") { required.forEach { add(it) } }
    }

/**
 * The full tool surface handed to the model. Names are stable identifiers — the settings screen
 * stores enabled tools by name, so renaming one silently disables it for existing users.
 */
object AiTools {

    const val GET_SCHEDULE = "get_schedule"
    const val GET_TASK = "get_task"
    const val SEARCH_TASKS = "search_tasks"
    const val CREATE_TASK = "create_task"
    const val UPDATE_TASK = "update_task"
    const val DELETE_TASK = "delete_task"
    const val MOVE_TASK = "move_task"
    const val SET_TASK_COMPLETION = "set_task_completion"
    const val ADD_CHECKLIST_ITEMS = "add_checklist_items"
    const val UPDATE_CHECKLIST_ITEM = "update_checklist_item"
    const val REMOVE_CHECKLIST_ITEM = "remove_checklist_item"
    const val GET_ANALYTICS = "get_analytics"
    const val GET_PROGRESS = "get_progress"
    const val LIST_REWARDS = "list_rewards"
    const val LIST_CATEGORIES = "list_categories"
    const val SUGGEST_RECOMMENDATION = "suggest_recommendation"

    private const val DATE_HINT = "Date as YYYY-MM-DD. Omit for today."
    private const val TIME_HINT = "Start time as HH:MM in 24-hour local time, e.g. 07:30 or 19:05."

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            name = GET_SCHEDULE,
            humanLabel = "Read the timetable",
            mutating = false,
            description = "Read the user's timetable for one or more days, including each block's " +
                "id, time, category, completion state and checklist items with their ids. " +
                "Always call this before editing anything.",
            parameters = schema {
                stringProp("date", DATE_HINT)
                intProp("days", "How many consecutive days to return starting at `date`. Default 1, max 14.")
            }
        ),
        ToolSpec(
            name = GET_TASK,
            humanLabel = "Read one block",
            mutating = false,
            description = "Fetch a single block by id with its full definition and checklist.",
            parameters = schema(required = listOf("task_id")) {
                intProp("task_id", "The block's numeric id.")
            }
        ),
        ToolSpec(
            name = SEARCH_TASKS,
            humanLabel = "Search blocks",
            mutating = false,
            description = "Find blocks whose title, note or category matches a query string. " +
                "Use this when the user names a block but you do not know its id.",
            parameters = schema(required = listOf("query")) {
                stringProp("query", "Free text, e.g. 'gym' or 'chemistry'.")
            }
        ),
        ToolSpec(
            name = CREATE_TASK,
            humanLabel = "Create a block",
            mutating = true,
            description = "Add a new block to the timetable. Either give `date` for a one-off block " +
                "or `days` for a repeating one. Returns the new block's id.",
            parameters = schema(required = listOf("title", "start_time", "duration_minutes")) {
                stringProp("title", "Short name of the block, in the user's own words.")
                stringProp("start_time", TIME_HINT)
                intProp("duration_minutes", "Length in minutes, 5–720.")
                stringProp("date", "For a one-off block: YYYY-MM-DD.")
                stringArrayProp(
                    "days",
                    "For a repeating block: any of mon,tue,wed,thu,fri,sat,sun, or the shorthands " +
                        "'daily', 'weekdays', 'weekends'."
                )
                stringProp("category", "Grouping label such as Study, Work, Health, Personal.")
                stringProp("priority", "Block importance.", enum = listOf("low", "normal", "high"))
                stringProp("note", "Optional longer description.")
                stringArrayProp("checklist", "2–5 short imperative sub-steps to tick off.")
                intProp("reminder_minutes", "Notify this many minutes before the start. Omit for none.")
                stringProp("color", "Hex colour like #6C5CE7. Optional.")
            }
        ),
        ToolSpec(
            name = UPDATE_TASK,
            humanLabel = "Edit a block",
            mutating = true,
            description = "Change fields on an existing block. Only the fields you pass are modified.",
            parameters = schema(required = listOf("task_id")) {
                intProp("task_id", "The block's numeric id.")
                stringProp("title", "New title.")
                stringProp("start_time", TIME_HINT)
                intProp("duration_minutes", "New length in minutes.")
                stringArrayProp("days", "New repeat days, same vocabulary as create_task.")
                stringProp("date", "New one-off date, YYYY-MM-DD.")
                stringProp("category", "New category.")
                stringProp("priority", "New priority.", enum = listOf("low", "normal", "high"))
                stringProp("note", "New note text.")
                intProp("reminder_minutes", "New reminder lead time, or -1 to remove it.")
                stringProp("color", "New hex colour.")
                boolProp("archived", "Set true to hide the block without deleting its history.")
            }
        ),
        ToolSpec(
            name = DELETE_TASK,
            humanLabel = "Delete a block",
            mutating = true,
            description = "Permanently delete a block and its history. Prefer archiving via " +
                "update_task when the user only wants it off the schedule.",
            parameters = schema(required = listOf("task_id")) {
                intProp("task_id", "The block's numeric id.")
            }
        ),
        ToolSpec(
            name = MOVE_TASK,
            humanLabel = "Reschedule a block",
            mutating = true,
            description = "Shorthand for moving a block to a new time, length and/or day.",
            parameters = schema(required = listOf("task_id")) {
                intProp("task_id", "The block's numeric id.")
                stringProp("start_time", TIME_HINT)
                intProp("duration_minutes", "New length in minutes.")
                stringProp("date", "Move a one-off block to this date, YYYY-MM-DD.")
            }
        ),
        ToolSpec(
            name = SET_TASK_COMPLETION,
            humanLabel = "Tick a block off",
            mutating = true,
            description = "Mark a block done or not-done for a specific day. Only do this when the " +
                "user says they did or did not do it.",
            parameters = schema(required = listOf("task_id", "completed")) {
                intProp("task_id", "The block's numeric id.")
                boolProp("completed", "True to mark done, false to clear it.")
                stringProp("date", DATE_HINT)
            }
        ),
        ToolSpec(
            name = ADD_CHECKLIST_ITEMS,
            humanLabel = "Add checklist steps",
            mutating = true,
            description = "Append checklist steps to a block. Items are short and imperative.",
            parameters = schema(required = listOf("task_id", "items")) {
                intProp("task_id", "The block's numeric id.")
                stringArrayProp("items", "Steps to append, e.g. ['Warm up', 'Sets 1-3', 'Stretch'].")
                boolProp("replace", "True to replace the existing checklist instead of appending.")
            }
        ),
        ToolSpec(
            name = UPDATE_CHECKLIST_ITEM,
            humanLabel = "Edit or tick a checklist step",
            mutating = true,
            description = "Rename a checklist step and/or tick it for a given day. Item ids come " +
                "from get_schedule or get_task.",
            parameters = schema(required = listOf("item_id")) {
                intProp("item_id", "The checklist item's numeric id.")
                stringProp("text", "New text for the step.")
                boolProp("checked", "Tick or untick the step for `date`.")
                stringProp("date", DATE_HINT)
            }
        ),
        ToolSpec(
            name = REMOVE_CHECKLIST_ITEM,
            humanLabel = "Delete a checklist step",
            mutating = true,
            description = "Remove a checklist step from its block.",
            parameters = schema(required = listOf("item_id")) {
                intProp("item_id", "The checklist item's numeric id.")
            }
        ),
        ToolSpec(
            name = GET_ANALYTICS,
            humanLabel = "Read analytics",
            mutating = false,
            description = "Completion stats for a period: totals, per-day breakdown, category split, " +
                "best and worst days.",
            parameters = schema {
                stringProp("period", "Which window to report.", enum = listOf("daily", "weekly", "monthly"))
                stringProp("date", "Any date inside the window, YYYY-MM-DD. Omit for today.")
            }
        ),
        ToolSpec(
            name = GET_PROGRESS,
            humanLabel = "Read streak and rewards",
            mutating = false,
            description = "Current streak, longest streak, freeze tokens, XP, level and coin balance, " +
                "plus recently unlocked achievements.",
            parameters = schema { }
        ),
        ToolSpec(
            name = LIST_REWARDS,
            humanLabel = "List the reward shop",
            mutating = false,
            description = "The rewards the user can spend coins on, with costs and affordability. " +
                "You cannot redeem them — only the user can.",
            parameters = schema { }
        ),
        ToolSpec(
            name = LIST_CATEGORIES,
            humanLabel = "List categories",
            mutating = false,
            description = "Categories already in use, so new blocks reuse the user's own vocabulary.",
            parameters = schema { }
        ),
        ToolSpec(
            name = SUGGEST_RECOMMENDATION,
            humanLabel = "Post an optional suggestion card",
            mutating = true,
            description = "Post one optional suggestion to the user's Today screen for them to accept " +
                "or dismiss. Use at most once per conversation and only with evidence from analytics. " +
                "This does not change the schedule.",
            parameters = schema(required = listOf("title", "body")) {
                stringProp("title", "Under 60 characters, the headline of the suggestion.")
                stringProp("body", "One or two sentences: the observation and the concrete change.")
                stringProp(
                    "kind",
                    "Suggestion category.",
                    enum = listOf("schedule", "focus", "rest", "streak", "habit", "general")
                )
            }
        )
    )

    val byName: Map<String, ToolSpec> = all.associateBy { it.name }

    val defaultEnabled: Set<String> = all.map { it.name }.toSet()
}
