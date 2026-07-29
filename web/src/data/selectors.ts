/*
 * Read-side of the store: everything the screens and the AI tools need to know about the
 * schedule, derived from task definitions joined with the immutable per-day logs.
 */

import {
  DayMask,
  addMonths,
  endOfMonth,
  endOfWeek,
  formatDayLabel,
  formatDuration,
  formatMonth,
  fromEpochDay,
  rangeOf,
  startOfMonth,
  startOfWeek,
  todayEpochDay,
  dayOfWeek,
} from '../core/time'
import { occursOn } from '../core/rules'
import type {
  CategorySlice,
  ChecklistLine,
  DaySnapshot,
  KairoData,
  Period,
  PeriodReport,
  ScheduledTask,
  Task,
} from './types'
import { Recurrence, completionRate, isPerfect } from './types'

export function logKey(taskId: number, epochDay: number): string {
  return `${taskId}:${epochDay}`
}

export function taskById(data: KairoData, id: number): Task | undefined {
  return data.tasks.find((task) => task.id === id)
}

export function activeTasks(data: KairoData): Task[] {
  return data.tasks.filter((task) => !task.archived)
}

export function categories(data: KairoData): string[] {
  return Array.from(new Set(activeTasks(data).map((task) => task.category))).sort()
}

export function checklistFor(data: KairoData, taskId: number): ChecklistLine[] {
  return data.checklistItems
    .filter((item) => item.taskId === taskId)
    .sort((a, b) => a.position - b.position)
    .map((item) => ({ id: item.id, text: item.text, checked: false }))
}

/** The full ordered agenda for a day, with that day's completion and checklist state. */
export function agendaFor(data: KairoData, epochDay: number): ScheduledTask[] {
  return activeTasks(data)
    .filter((task) => occursOn(task, epochDay))
    .map((task) => {
      const log = data.taskLogs[logKey(task.id, epochDay)]
      const checklist = data.checklistItems
        .filter((item) => item.taskId === task.id)
        .sort((a, b) => a.position - b.position)
        .map((item) => ({
          id: item.id,
          text: item.text,
          checked: data.checklistLogs[logKey(item.id, epochDay)]?.checked ?? false,
        }))
      return {
        task,
        epochDay,
        completed: log?.completed ?? false,
        skipped: log?.skipped ?? false,
        focusMinutes: log?.focusMinutes ?? 0,
        checklist,
      }
    })
    .sort((a, b) => a.task.startMinute - b.task.startMinute || a.task.id - b.task.id)
}

export function snapshotFor(data: KairoData, epochDay: number): DaySnapshot {
  const agenda = agendaFor(data, epochDay)
  if (agenda.length === 0) return emptySnapshot(epochDay)
  const completed = agenda.filter((entry) => entry.completed)
  return {
    epochDay,
    planned: agenda.length,
    completed: completed.length,
    skipped: agenda.filter((entry) => entry.skipped && !entry.completed).length,
    plannedMinutes: agenda.reduce((total, entry) => total + entry.task.durationMinutes, 0),
    completedMinutes: completed.reduce((total, entry) => total + entry.task.durationMinutes, 0),
    checklistDone: agenda.reduce(
      (total, entry) => total + entry.checklist.filter((line) => line.checked).length,
      0,
    ),
    checklistTotal: agenda.reduce((total, entry) => total + entry.checklist.length, 0),
  }
}

export function emptySnapshot(epochDay: number): DaySnapshot {
  return {
    epochDay,
    planned: 0,
    completed: 0,
    skipped: 0,
    plannedMinutes: 0,
    completedMinutes: 0,
    checklistDone: 0,
    checklistTotal: 0,
  }
}

export function rangeForPeriod(period: Period, anchor: number): [number, number] {
  if (period === 'daily') return [anchor, anchor]
  if (period === 'weekly') return [startOfWeek(anchor), endOfWeek(anchor)]
  return [startOfMonth(anchor), endOfMonth(anchor)]
}

export function shiftPeriod(period: Period, anchor: number, delta: number): number {
  if (period === 'daily') return anchor + delta
  if (period === 'weekly') return anchor + delta * 7
  return addMonths(anchor, delta)
}

export function labelForPeriod(period: Period, anchor: number): string {
  if (period === 'daily') return formatDayLabel(anchor)
  if (period === 'monthly') return formatMonth(anchor)
  const from = fromEpochDay(startOfWeek(anchor))
  const to = fromEpochDay(endOfWeek(anchor))
  const sameMonth = from.getMonth() === to.getMonth()
  const fromLabel = from.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
  const toLabel = to.toLocaleDateString(
    undefined,
    sameMonth ? { day: 'numeric' } : { month: 'short', day: 'numeric' },
  )
  return `${fromLabel} – ${toLabel}`
}

export function reportFor(data: KairoData, period: Period, anchor: number): PeriodReport {
  const [from, to] = rangeForPeriod(period, anchor)
  const days = rangeOf(from, to).map((day) => snapshotFor(data, day))

  /*
   * Only days that have actually happened can be judged. A week viewed on Wednesday still
   * contains Thursday through Sunday, and counting those as 0% would report the rest of the
   * week as failure before it has begun. `days` keeps the full range for the bar chart —
   * a pale planned bar on a future day is useful — but every rate below is scored on
   * elapsed days alone.
   */
  const today = todayEpochDay()
  const elapsed = days.filter((day) => day.epochDay <= today)

  const planned = elapsed.reduce((total, day) => total + day.planned, 0)
  const completed = elapsed.reduce((total, day) => total + day.completed, 0)
  const focusMinutes = elapsed.reduce((total, day) => total + day.completedMinutes, 0)
  const plannedMinutes = elapsed.reduce((total, day) => total + day.plannedMinutes, 0)
  const scheduledDays = elapsed.filter((day) => day.planned > 0)
  const activeDays = elapsed.filter((day) => day.completed > 0).length

  return {
    label: labelForPeriod(period, anchor),
    fromDay: from,
    toDay: to,
    days,
    elapsedDays: elapsed,
    categories: categoryBreakdown(data, from, Math.min(to, today)),
    planned,
    completed,
    focusMinutes,
    plannedMinutes,
    completionRate: planned === 0 ? 0 : completed / planned,
    perfectDays: elapsed.filter(isPerfect).length,
    activeDays,
    consistency: scheduledDays.length === 0 ? 0 : activeDays / scheduledDays.length,
    bestDay:
      scheduledDays.length === 0
        ? null
        : scheduledDays.reduce((best, day) =>
            completionRate(day) > completionRate(best) ? day : best,
          ),
    worstDay:
      scheduledDays.length === 0
        ? null
        : scheduledDays.reduce((worst, day) =>
            completionRate(day) < completionRate(worst) ? day : worst,
          ),
  }
}

export function categoryBreakdown(data: KairoData, from: number, to: number): CategorySlice[] {
  const totals = new Map<string, CategorySlice>()
  for (let day = from; day <= to; day++) {
    for (const entry of agendaFor(data, day)) {
      if (!entry.completed) continue
      const existing = totals.get(entry.task.category)
      if (existing) {
        existing.completed += 1
        existing.minutes += entry.task.durationMinutes
      } else {
        totals.set(entry.task.category, {
          category: entry.task.category,
          colorHex: entry.task.colorHex,
          completed: 1,
          minutes: entry.task.durationMinutes,
        })
      }
    }
  }
  return Array.from(totals.values()).sort((a, b) => b.minutes - a.minutes)
}

/** Completed blocks bucketed into six 4-hour bands — the "when do you deliver" chart. */
export function hourBands(data: KairoData, from: number, to: number): number[] {
  const bands = [0, 0, 0, 0, 0, 0]
  const limit = Math.min(to, todayEpochDay())
  for (let day = from; day <= limit; day++) {
    for (const entry of agendaFor(data, day)) {
      if (!entry.completed) continue
      const index = Math.min(5, Math.max(0, Math.floor(entry.task.startMinute / 240)))
      bands[index] += 1
    }
  }
  return bands
}

/** Per-weekday completion rate over a window — powers "your Thursdays are weak". */
export function weekdayProfile(data: KairoData, from: number, to: number): Map<number, number> {
  const planned = new Map<number, number>()
  const done = new Map<number, number>()
  const limit = Math.min(to, todayEpochDay())
  for (let day = from; day <= limit; day++) {
    const snapshot = snapshotFor(data, day)
    if (snapshot.planned === 0) continue
    const dow = dayOfWeek(day)
    planned.set(dow, (planned.get(dow) ?? 0) + snapshot.planned)
    done.set(dow, (done.get(dow) ?? 0) + snapshot.completed)
  }
  const profile = new Map<number, number>()
  for (const [dow, total] of planned) {
    profile.set(dow, total === 0 ? 0 : (done.get(dow) ?? 0) / total)
  }
  return profile
}

export function checklistCompletedIn(data: KairoData, from: number, to: number): number {
  return Object.values(data.checklistLogs).filter(
    (log) => log.checked && log.epochDay >= from && log.epochDay <= to,
  ).length
}

/** Naive overlap check, used by the editor and by the AI before it schedules something. */
export function conflictsFor(data: KairoData, task: Task, epochDay: number): ScheduledTask[] {
  const start = task.startMinute
  const end = task.startMinute + task.durationMinutes
  return agendaFor(data, epochDay).filter(
    (entry) =>
      entry.task.id !== task.id &&
      entry.task.startMinute < end &&
      entry.task.startMinute + entry.task.durationMinutes > start,
  )
}

export function repeatLabel(task: Task): string {
  if (task.recurrence === Recurrence.DAILY) return 'Every day'
  if (task.recurrence === Recurrence.WEEKLY) return DayMask.describe(task.daysMask)
  return task.specificDate != null ? formatDayLabel(task.specificDate) : 'One-off'
}

export function summaryLine(report: PeriodReport): string {
  const pct = Math.round(report.completionRate * 100)
  return (
    `${report.label}: ${report.completed}/${report.planned} blocks done (${pct}%), ` +
    `${formatDuration(report.focusMinutes)} focused, ${report.perfectDays} perfect day(s), ` +
    `streak-eligible days ${report.activeDays}/${report.elapsedDays.length}.`
  )
}

/** Compact text digest handed to the AI so it reasons about real numbers. */
export function textDigest(data: KairoData, anchor = todayEpochDay()): string {
  const daily = reportFor(data, 'daily', anchor)
  const weekly = reportFor(data, 'weekly', anchor)
  const monthly = reportFor(data, 'monthly', anchor)
  const top =
    weekly.categories
      .slice(0, 3)
      .map((slice) => `${slice.category} ${formatDuration(slice.minutes)}`)
      .join(', ') || 'none yet'
  return [
    summaryLine(daily),
    summaryLine(weekly),
    summaryLine(monthly),
    `Top categories this week: ${top}`,
  ].join('\n')
}

export function isToolEnabled(data: KairoData, name: string): boolean {
  if (!data.ai.toolsEnabled) return false
  return data.ai.enabledTools.length === 0 || data.ai.enabledTools.includes(name)
}

export function openRecommendations(data: KairoData) {
  return data.recommendations
    .filter((item) => item.status === 'new')
    .sort((a, b) => b.createdAt - a.createdAt)
}
