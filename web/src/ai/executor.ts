/*
 * Executes the model's tool calls against the real store. Every failure comes back to the model as
 * a structured {"ok": false, "error": …} payload rather than throwing, so a bad argument produces
 * a retry instead of killing the conversation.
 */

import { DayMask, formatIso, formatRange, formatTime, parseIsoDate, parseTimeToMinute, todayEpochDay, dayBit, weekdayName } from '../core/time'
import { Leveling, nextMilestone } from '../core/rules'
import {
  agendaFor,
  categories,
  conflictsFor,
  hourBands,
  reportFor,
  repeatLabel,
  taskById,
} from '../data/selectors'
import {
  addChecklistItems,
  addRecommendation,
  archiveTask,
  deleteTask,
  moveTask,
  removeChecklistItem,
  renameChecklistItem,
  replaceChecklist,
  setChecklistChecked,
  setTaskCompleted,
  upsertTask,
} from '../data/mutations'
import type { KairoData, Period, ScheduledTask, Task, ToolCall } from '../data/types'
import { Priority, Recurrence, priorityLabel } from '../data/types'
import { TOOL_NAMES, TOOLS_BY_NAME } from './tools'

export interface ToolOutcome {
  name: string
  resultJson: string
  succeeded: boolean
  mutatedSchedule: boolean
  summary: string
  data: KairoData
}

export function executeTool(data: KairoData, call: ToolCall): ToolOutcome {
  const spec = TOOLS_BY_NAME.get(call.name)
  if (!spec) return fail(data, call.name, `Unknown tool '${call.name}'.`, false)

  let args: Record<string, unknown> = {}
  try {
    const parsed = JSON.parse(call.argumentsJson || '{}')
    if (parsed && typeof parsed === 'object') args = parsed as Record<string, unknown>
  } catch {
    args = {}
  }

  try {
    switch (call.name) {
      case TOOL_NAMES.GET_SCHEDULE:
        return getSchedule(data, args)
      case TOOL_NAMES.GET_TASK:
        return getTask(data, args)
      case TOOL_NAMES.SEARCH_TASKS:
        return searchTasks(data, args)
      case TOOL_NAMES.CREATE_TASK:
        return createTask(data, args)
      case TOOL_NAMES.UPDATE_TASK:
        return updateTask(data, args)
      case TOOL_NAMES.DELETE_TASK:
        return removeTask(data, args)
      case TOOL_NAMES.MOVE_TASK:
        return relocateTask(data, args)
      case TOOL_NAMES.SET_TASK_COMPLETION:
        return setCompletion(data, args)
      case TOOL_NAMES.ADD_CHECKLIST_ITEMS:
        return addChecklist(data, args)
      case TOOL_NAMES.UPDATE_CHECKLIST_ITEM:
        return updateChecklistItem(data, args)
      case TOOL_NAMES.REMOVE_CHECKLIST_ITEM:
        return dropChecklistItem(data, args)
      case TOOL_NAMES.GET_ANALYTICS:
        return getAnalytics(data, args)
      case TOOL_NAMES.GET_PROGRESS:
        return getProgress(data)
      case TOOL_NAMES.LIST_REWARDS:
        return listRewards(data)
      case TOOL_NAMES.LIST_CATEGORIES:
        return listCategories(data)
      case TOOL_NAMES.SUGGEST_RECOMMENDATION:
        return suggestRecommendation(data, args)
      default:
        return fail(data, call.name, `Tool '${call.name}' is not implemented.`, spec.mutating)
    }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'tool failed'
    return fail(data, call.name, message, spec.mutating)
  }
}

// ---- readers -------------------------------------------------------------

function getSchedule(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const start = dateOrToday(args, 'date')
  const days = clamp(intArg(args, 'days') ?? 1, 1, 14)
  const payload = {
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    days: Array.from({ length: days }, (_, offset) => {
      const day = start + offset
      const agenda = agendaFor(data, day)
      return {
        date: formatIso(day),
        weekday: weekdayName(day).toLowerCase(),
        planned: agenda.length,
        completed: agenda.filter((entry) => entry.completed).length,
        blocks: agenda.map(scheduledToJson),
      }
    }),
  }
  const total = payload.days.reduce((sum, day) => sum + day.planned, 0)
  return ok(data, TOOL_NAMES.GET_SCHEDULE, payload, false, `Read ${days} day(s), ${total} block(s)`)
}

function getTask(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const id = intArg(args, 'task_id')
  if (id == null) return fail(data, TOOL_NAMES.GET_TASK, 'task_id is required', false)
  const day = dateOrToday(args, 'date')
  const scheduled = agendaFor(data, day).find((entry) => entry.task.id === id)
  if (scheduled) {
    return ok(data, TOOL_NAMES.GET_TASK, scheduledToJson(scheduled), false, `Read #${id}`)
  }
  const task = taskById(data, id)
  if (!task) return fail(data, TOOL_NAMES.GET_TASK, `No block with id ${id}`, false)
  return ok(data, TOOL_NAMES.GET_TASK, taskToJson(task), false, `Read #${id}`)
}

function searchTasks(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const query = (stringArg(args, 'query') ?? '').trim().toLowerCase()
  if (!query) return fail(data, TOOL_NAMES.SEARCH_TASKS, 'query is required', false)
  const matches = data.tasks
    .filter((task) => !task.archived)
    .filter(
      (task) =>
        task.title.toLowerCase().includes(query) ||
        task.note.toLowerCase().includes(query) ||
        task.category.toLowerCase().includes(query),
    )
    .slice(0, 25)
  return ok(
    data,
    TOOL_NAMES.SEARCH_TASKS,
    { query, count: matches.length, matches: matches.map(taskToJson) },
    false,
    `Found ${matches.length} match(es) for "${query}"`,
  )
}

function getAnalytics(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const raw = (stringArg(args, 'period') ?? 'weekly').toLowerCase()
  const period: Period = raw.startsWith('month')
    ? 'monthly'
    : raw.startsWith('da') || raw === 'today'
      ? 'daily'
      : 'weekly'
  const anchor = dateOrToday(args, 'date')
  const report = reportFor(data, period, anchor)
  const bands = hourBands(data, report.fromDay, report.toDay)
  const bandLabels = ['00-04', '04-08', '08-12', '12-16', '16-20', '20-24']

  const payload = {
    period,
    from: formatIso(report.fromDay),
    to: formatIso(report.toDay),
    planned_blocks: report.planned,
    completed_blocks: report.completed,
    completion_rate: Math.round(report.completionRate * 100),
    focused_minutes: report.focusMinutes,
    perfect_days: report.perfectDays,
    active_days: report.activeDays,
    consistency_percent: Math.round(report.consistency * 100),
    best_day: report.bestDay ? formatIso(report.bestDay.epochDay) : undefined,
    weakest_day: report.worstDay ? formatIso(report.worstDay.epochDay) : undefined,
    per_day: report.days.map((day) => ({
      date: formatIso(day.epochDay),
      weekday: weekdayName(day.epochDay).toLowerCase(),
      planned: day.planned,
      completed: day.completed,
      focused_minutes: day.completedMinutes,
    })),
    categories: report.categories.map((slice) => ({
      category: slice.category,
      completed: slice.completed,
      minutes: slice.minutes,
    })),
    completions_by_time_band: Object.fromEntries(
      bandLabels.map((label, index) => [label, bands[index] ?? 0]),
    ),
  }
  return ok(data, TOOL_NAMES.GET_ANALYTICS, payload, false, `Read ${period} analytics`)
}

function getProgress(data: KairoData): ToolOutcome {
  const stats = data.stats
  const level = Leveling.infoFor(stats.xp)
  const payload = {
    current_streak_days: stats.currentStreak,
    longest_streak_days: stats.longestStreak,
    freeze_tokens: stats.freezeTokens,
    streak_alive_through: stats.lastStreakDay >= 0 ? formatIso(stats.lastStreakDay) : 'never',
    next_milestone_days: nextMilestone(stats.currentStreak),
    xp: stats.xp,
    level: level.level,
    level_title: level.title,
    xp_to_next_level: level.xpForNextLevel - level.xpIntoLevel,
    coins: stats.coins,
    total_blocks_completed: stats.totalTasksCompleted,
    total_focused_minutes: stats.totalFocusMinutes,
    perfect_days: stats.perfectDays,
    unlocked_achievements: data.achievements
      .filter((item) => item.unlockedAt != null)
      .map((item) => item.title),
  }
  return ok(data, TOOL_NAMES.GET_PROGRESS, payload, false, 'Read streak & rewards')
}

function listRewards(data: KairoData): ToolOutcome {
  const payload = {
    coins: data.stats.coins,
    rewards: data.rewards.map((reward) => ({
      id: reward.id,
      title: reward.title,
      cost: reward.cost,
      affordable: data.stats.coins >= reward.cost,
      times_redeemed: reward.timesRedeemed,
    })),
    note: 'Only the user can redeem rewards, from the Rewards tab.',
  }
  return ok(data, TOOL_NAMES.LIST_REWARDS, payload, false, 'Read reward shop')
}

function listCategories(data: KairoData): ToolOutcome {
  return ok(
    data,
    TOOL_NAMES.LIST_CATEGORIES,
    { categories: categories(data) },
    false,
    'Read categories',
  )
}

// ---- writers -------------------------------------------------------------

function createTask(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const title = (stringArg(args, 'title') ?? '').trim()
  if (!title) return fail(data, TOOL_NAMES.CREATE_TASK, 'title is required', true)

  const startMinute = parseTimeToMinute(stringArg(args, 'start_time'))
  if (startMinute == null) {
    return fail(data, TOOL_NAMES.CREATE_TASK, 'start_time must look like HH:MM', true)
  }
  const duration = intArg(args, 'duration_minutes')
  if (duration == null) {
    return fail(data, TOOL_NAMES.CREATE_TASK, 'duration_minutes is required', true)
  }
  if (duration < 5 || duration > 720) {
    return fail(data, TOOL_NAMES.CREATE_TASK, 'duration_minutes must be between 5 and 720', true)
  }

  const mask = DayMask.fromNames(stringListArg(args, 'days'))
  const specificDate = parseIsoDate(stringArg(args, 'date'))
  const today = todayEpochDay()
  const recurrence =
    mask === DayMask.ALL ? Recurrence.DAILY : mask !== DayMask.NONE ? Recurrence.WEEKLY : Recurrence.ONCE
  const onceDate = specificDate ?? today

  const draft = {
    title,
    note: stringArg(args, 'note') ?? '',
    category: stringArg(args, 'category') || 'General',
    colorHex: normalizeColor(stringArg(args, 'color')),
    priority: parsePriority(stringArg(args, 'priority')),
    startMinute,
    durationMinutes: duration,
    recurrence,
    daysMask: recurrence === Recurrence.WEEKLY ? mask : 0,
    specificDate: recurrence === Recurrence.ONCE ? onceDate : null,
    reminderMinutes: clampOrNull(intArg(args, 'reminder_minutes'), 0, 240) ?? -1,
  }

  const probeDay =
    recurrence === Recurrence.ONCE ? onceDate : nextMaskDay(mask, today)
  const probe: Task = {
    id: -1,
    ...draft,
    specificDate: draft.specificDate,
    startEpochDay: onceDate,
    endEpochDay: null,
    archived: false,
    createdAt: 0,
    updatedAt: 0,
  }
  const conflicts = conflictsFor(data, probe, probeDay)

  const created = upsertTask(data, draft)
  let working = created.data
  const checklist = stringListArg(args, 'checklist') ?? []
  if (checklist.length > 0) working = addChecklistItems(working, created.taskId, checklist)

  const payload: Record<string, unknown> = {
    ok: true,
    task_id: created.taskId,
    title,
    when: describeSchedule(recurrence, mask, onceDate, startMinute, duration),
    checklist_items_added: checklist.length,
  }
  if (conflicts.length > 0) {
    payload.overlaps_existing = conflicts.map(
      (entry) => `${entry.task.title} (${formatRange(entry.task.startMinute, entry.task.durationMinutes)})`,
    )
    payload.warning = 'This block overlaps existing blocks — tell the user and offer to move it.'
  }

  return ok(
    working,
    TOOL_NAMES.CREATE_TASK,
    payload,
    true,
    `Created "${title}" ${formatTime(startMinute)}`,
  )
}

function updateTask(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const id = intArg(args, 'task_id')
  if (id == null) return fail(data, TOOL_NAMES.UPDATE_TASK, 'task_id is required', true)
  const existing = taskById(data, id)
  if (!existing) return fail(data, TOOL_NAMES.UPDATE_TASK, `No block with id ${id}`, true)

  let recurrence = existing.recurrence
  let daysMask = existing.daysMask
  let specificDate = existing.specificDate

  const days = stringListArg(args, 'days')
  if (days) {
    const mask = DayMask.fromNames(days)
    if (mask !== DayMask.NONE) {
      recurrence = mask === DayMask.ALL ? Recurrence.DAILY : Recurrence.WEEKLY
      daysMask = mask === DayMask.ALL ? 0 : mask
      specificDate = null
    }
  }
  const date = parseIsoDate(stringArg(args, 'date'))
  if (date != null) {
    recurrence = Recurrence.ONCE
    specificDate = date
    daysMask = 0
  }

  const duration = intArg(args, 'duration_minutes')
  if (duration != null && (duration < 5 || duration > 720)) {
    return fail(data, TOOL_NAMES.UPDATE_TASK, 'duration_minutes must be 5–720', true)
  }
  const startRaw = stringArg(args, 'start_time')
  let startMinute = existing.startMinute
  if (startRaw != null) {
    const parsed = parseTimeToMinute(startRaw)
    if (parsed == null) {
      return fail(data, TOOL_NAMES.UPDATE_TASK, 'start_time must look like HH:MM', true)
    }
    startMinute = parsed
  }

  const reminder = intArg(args, 'reminder_minutes')
  const archived = boolArg(args, 'archived')

  const result = upsertTask(data, {
    id,
    title: stringArg(args, 'title')?.trim() || existing.title,
    note: stringArg(args, 'note') ?? existing.note,
    category: stringArg(args, 'category')?.trim() || existing.category,
    colorHex: stringArg(args, 'color') ? normalizeColor(stringArg(args, 'color')) : existing.colorHex,
    priority: stringArg(args, 'priority') ? parsePriority(stringArg(args, 'priority')) : existing.priority,
    startMinute,
    durationMinutes: duration ?? existing.durationMinutes,
    recurrence,
    daysMask,
    specificDate,
    reminderMinutes: reminder != null ? (reminder < 0 ? -1 : Math.min(reminder, 240)) : existing.reminderMinutes,
  })

  let working = result.data
  if (archived != null) working = archiveTask(working, id, archived)

  const updated = taskById(working, id)!
  return ok(
    working,
    TOOL_NAMES.UPDATE_TASK,
    { ok: true, task_id: id, task: taskToJson(updated) },
    true,
    `Updated "${updated.title}"`,
  )
}

function removeTask(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const id = intArg(args, 'task_id')
  if (id == null) return fail(data, TOOL_NAMES.DELETE_TASK, 'task_id is required', true)
  const existing = taskById(data, id)
  if (!existing) return fail(data, TOOL_NAMES.DELETE_TASK, `No block with id ${id}`, true)
  return ok(
    deleteTask(data, id),
    TOOL_NAMES.DELETE_TASK,
    { ok: true, deleted_task_id: id, title: existing.title },
    true,
    `Deleted "${existing.title}"`,
  )
}

function relocateTask(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const id = intArg(args, 'task_id')
  if (id == null) return fail(data, TOOL_NAMES.MOVE_TASK, 'task_id is required', true)
  if (!taskById(data, id)) return fail(data, TOOL_NAMES.MOVE_TASK, `No block with id ${id}`, true)

  const startRaw = stringArg(args, 'start_time')
  let startMinute: number | null = null
  if (startRaw != null) {
    startMinute = parseTimeToMinute(startRaw)
    if (startMinute == null) {
      return fail(data, TOOL_NAMES.MOVE_TASK, 'start_time must look like HH:MM', true)
    }
  }
  const duration = intArg(args, 'duration_minutes')
  if (duration != null && (duration < 5 || duration > 720)) {
    return fail(data, TOOL_NAMES.MOVE_TASK, 'duration_minutes must be 5–720', true)
  }
  const date = parseIsoDate(stringArg(args, 'date'))

  const working = moveTask(data, id, startMinute, duration, date)
  const task = taskById(working, id)!
  return ok(
    working,
    TOOL_NAMES.MOVE_TASK,
    {
      ok: true,
      task_id: id,
      new_time: formatRange(task.startMinute, task.durationMinutes),
      new_date: date != null ? formatIso(date) : undefined,
    },
    true,
    `Moved "${task.title}"`,
  )
}

function setCompletion(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const id = intArg(args, 'task_id')
  if (id == null) return fail(data, TOOL_NAMES.SET_TASK_COMPLETION, 'task_id is required', true)
  const completed = boolArg(args, 'completed')
  if (completed == null) {
    return fail(data, TOOL_NAMES.SET_TASK_COMPLETION, 'completed is required', true)
  }
  const task = taskById(data, id)
  if (!task) return fail(data, TOOL_NAMES.SET_TASK_COMPLETION, `No block with id ${id}`, true)

  const day = dateOrToday(args, 'date')
  const result = setTaskCompleted(data, id, day, completed)
  return ok(
    result.data,
    TOOL_NAMES.SET_TASK_COMPLETION,
    {
      ok: true,
      task_id: id,
      date: formatIso(day),
      completed,
      current_streak_days: result.data.stats.currentStreak,
      coins: result.data.stats.coins,
      events: result.events.map(describeEvent),
    },
    true,
    completed ? `Ticked off "${task.title}"` : `Cleared "${task.title}"`,
  )
}

function addChecklist(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const id = intArg(args, 'task_id')
  if (id == null) return fail(data, TOOL_NAMES.ADD_CHECKLIST_ITEMS, 'task_id is required', true)
  if (!taskById(data, id)) {
    return fail(data, TOOL_NAMES.ADD_CHECKLIST_ITEMS, `No block with id ${id}`, true)
  }
  const items = (stringListArg(args, 'items') ?? []).filter((item) => item.trim())
  if (items.length === 0) {
    return fail(data, TOOL_NAMES.ADD_CHECKLIST_ITEMS, 'items must be a non-empty list', true)
  }
  const replace = boolArg(args, 'replace') ?? false
  const working = replace ? replaceChecklist(data, id, items) : addChecklistItems(data, id, items)
  const current = working.checklistItems
    .filter((item) => item.taskId === id)
    .sort((a, b) => a.position - b.position)

  return ok(
    working,
    TOOL_NAMES.ADD_CHECKLIST_ITEMS,
    {
      ok: true,
      task_id: id,
      replaced: replace,
      checklist: current.map((item) => ({ item_id: item.id, text: item.text })),
    },
    true,
    `${replace ? 'Replaced' : 'Added'} ${items.length} checklist step(s)`,
  )
}

function updateChecklistItem(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const itemId = intArg(args, 'item_id')
  if (itemId == null) {
    return fail(data, TOOL_NAMES.UPDATE_CHECKLIST_ITEM, 'item_id is required', true)
  }
  if (!data.checklistItems.some((item) => item.id === itemId)) {
    return fail(data, TOOL_NAMES.UPDATE_CHECKLIST_ITEM, `No checklist item with id ${itemId}`, true)
  }

  let working = data
  let touched = false
  const text = stringArg(args, 'text')
  if (text && text.trim()) {
    working = renameChecklistItem(working, itemId, text)
    touched = true
  }
  const checked = boolArg(args, 'checked')
  if (checked != null) {
    working = setChecklistChecked(working, itemId, dateOrToday(args, 'date'), checked).data
    touched = true
  }
  if (!touched) {
    return fail(data, TOOL_NAMES.UPDATE_CHECKLIST_ITEM, 'Pass `text` and/or `checked`', true)
  }
  return ok(
    working,
    TOOL_NAMES.UPDATE_CHECKLIST_ITEM,
    { ok: true, item_id: itemId },
    true,
    'Updated checklist step',
  )
}

function dropChecklistItem(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const itemId = intArg(args, 'item_id')
  if (itemId == null) {
    return fail(data, TOOL_NAMES.REMOVE_CHECKLIST_ITEM, 'item_id is required', true)
  }
  return ok(
    removeChecklistItem(data, itemId),
    TOOL_NAMES.REMOVE_CHECKLIST_ITEM,
    { ok: true, removed_item_id: itemId },
    true,
    'Removed a checklist step',
  )
}

function suggestRecommendation(data: KairoData, args: Record<string, unknown>): ToolOutcome {
  const title = stringArg(args, 'title')?.trim()
  const body = stringArg(args, 'body')?.trim()
  if (!title || !body) {
    return fail(data, TOOL_NAMES.SUGGEST_RECOMMENDATION, 'title and body are required', true)
  }
  const result = addRecommendation(data, {
    title,
    body,
    kind: stringArg(args, 'kind')?.toLowerCase() ?? 'general',
  })
  return ok(
    result.data,
    TOOL_NAMES.SUGGEST_RECOMMENDATION,
    {
      ok: true,
      recommendation_id: result.id,
      note: "Posted to the user's Today screen. They can accept or dismiss it.",
    },
    false,
    `Posted a suggestion: ${title}`,
  )
}

// ---- helpers -------------------------------------------------------------

function ok(
  data: KairoData,
  name: string,
  payload: unknown,
  mutatedSchedule: boolean,
  summary: string,
): ToolOutcome {
  return {
    name,
    data,
    resultJson: JSON.stringify(payload),
    succeeded: true,
    mutatedSchedule,
    summary,
  }
}

function fail(data: KairoData, name: string, message: string, mutating: boolean): ToolOutcome {
  const payload: Record<string, unknown> = { ok: false, error: message }
  if (mutating) payload.hint = 'Nothing was changed. Fix the arguments and call the tool again.'
  // A failed call never counts as a schedule mutation, whatever the tool would have done.
  return {
    name,
    data,
    resultJson: JSON.stringify(payload),
    succeeded: false,
    mutatedSchedule: false,
    summary: message,
  }
}

function scheduledToJson(entry: ScheduledTask) {
  const task = entry.task
  return {
    task_id: task.id,
    title: task.title,
    category: task.category,
    priority: priorityLabel(task.priority).toLowerCase(),
    start_time: formatTime(task.startMinute, true),
    end_time: formatTime((task.startMinute + task.durationMinutes) % (24 * 60), true),
    duration_minutes: task.durationMinutes,
    repeat: repeatLabel(task).toLowerCase(),
    completed: entry.completed,
    skipped: entry.skipped,
    note: task.note || undefined,
    checklist: entry.checklist.map((line) => ({
      item_id: line.id,
      text: line.text,
      checked: line.checked,
    })),
  }
}

function taskToJson(task: Task) {
  return {
    task_id: task.id,
    title: task.title,
    category: task.category,
    priority: priorityLabel(task.priority).toLowerCase(),
    start_time: formatTime(task.startMinute, true),
    duration_minutes: task.durationMinutes,
    repeat: repeatLabel(task).toLowerCase(),
    archived: task.archived,
    note: task.note || undefined,
    reminder_minutes_before: task.reminderMinutes >= 0 ? task.reminderMinutes : undefined,
  }
}

function describeEvent(event: { kind: string; [key: string]: unknown }): string {
  switch (event.kind) {
    case 'xp':
      return `+${event.amount} XP (${event.reason})`
    case 'coins':
      return `+${event.amount} coins (${event.reason})`
    case 'levelUp':
      return `Level up to ${event.level} — ${event.title}`
    case 'unlocked':
      return `Unlocked ${event.title}`
    case 'streak':
      return `Streak now ${event.streak} days`
    case 'frozen':
      return `Streak freeze used, ${event.tokensLeft} left`
    case 'streakLost':
      return `Streak reset from ${event.previous}`
    case 'perfectDay':
      return 'Perfect day bonus'
    default:
      return event.kind
  }
}

function describeSchedule(
  recurrence: number,
  mask: number,
  onceDate: number,
  startMinute: number,
  duration: number,
): string {
  const time = formatRange(startMinute, duration)
  if (recurrence === Recurrence.DAILY) return `Every day, ${time}`
  if (recurrence === Recurrence.WEEKLY) return `${DayMask.describe(mask)}, ${time}`
  return `${formatIso(onceDate)}, ${time}`
}

function nextMaskDay(mask: number, from: number): number {
  for (let offset = 0; offset < 7; offset++) {
    if ((mask & dayBit(from + offset)) !== 0) return from + offset
  }
  return from
}

function parsePriority(raw: string | null | undefined): number {
  const value = raw?.toLowerCase().trim()
  if (value === 'low') return Priority.LOW
  if (value === 'high') return Priority.HIGH
  return Priority.NORMAL
}

function normalizeColor(raw: string | null | undefined): string {
  const value = (raw ?? '').trim()
  const withHash = value.startsWith('#') ? value : `#${value}`
  return /^#[0-9a-fA-F]{6}$/.test(withHash) ? withHash.toUpperCase() : '#6C5CE7'
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function clampOrNull(value: number | null, min: number, max: number): number | null {
  return value == null ? null : clamp(value, min, max)
}

// Argument readers that tolerate the shapes models actually emit.

function stringArg(args: Record<string, unknown>, key: string): string | null {
  const value = args[key]
  if (typeof value === 'string') return value === 'null' ? null : value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return null
}

function intArg(args: Record<string, unknown>, key: string): number | null {
  const value = args[key]
  if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value)
  if (typeof value === 'string') {
    const parsed = Number(value.trim())
    if (Number.isFinite(parsed)) return Math.trunc(parsed)
  }
  return null
}

function boolArg(args: Record<string, unknown>, key: string): boolean | null {
  const value = args[key]
  if (typeof value === 'boolean') return value
  if (typeof value === 'string') {
    const lower = value.toLowerCase().trim()
    if (['true', 'yes', '1'].includes(lower)) return true
    if (['false', 'no', '0'].includes(lower)) return false
  }
  return null
}

/** Accepts a real array, or a comma-separated string, which smaller models tend to send. */
function stringListArg(args: Record<string, unknown>, key: string): string[] | null {
  const value = args[key]
  if (Array.isArray(value)) {
    return value.filter((item) => typeof item === 'string' || typeof item === 'number').map(String)
  }
  if (typeof value === 'string') {
    return value
      .split(/[,\n]/)
      .map((item) => item.trim().replace(/^-\s*/, ''))
      .filter(Boolean)
  }
  return null
}

function dateOrToday(args: Record<string, unknown>, key: string): number {
  return parseIsoDate(stringArg(args, key)) ?? todayEpochDay()
}
