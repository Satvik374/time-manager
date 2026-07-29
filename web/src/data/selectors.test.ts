/*
 * Analytics are the part of the app a user is most likely to trust blindly, so the rules that
 * decide what counts as a scored day are pinned down here. The regression these were written
 * for: a week viewed on Wednesday reported the untouched Thursday-to-Sunday as 0% and told the
 * user their week was "Patchy" before most of it had happened.
 */

import { beforeEach, describe, expect, it } from 'vitest'
import { startOfWeek, todayEpochDay } from '../core/time'
import { createEmptyData } from './defaults'
import {
  agendaFor,
  categoryBreakdown,
  conflictsFor,
  hourBands,
  logKey,
  reportFor,
  weekdayProfile,
} from './selectors'
import { Recurrence, type KairoData, type Task } from './types'

const TODAY = todayEpochDay()

function task(id: number, overrides: Partial<Task> = {}): Task {
  return {
    id,
    title: `Block ${id}`,
    note: '',
    category: 'Work',
    colorHex: '#4C6FFF',
    priority: 1,
    startMinute: 9 * 60,
    durationMinutes: 60,
    recurrence: Recurrence.DAILY,
    daysMask: 0,
    specificDate: null,
    startEpochDay: TODAY - 60,
    endEpochDay: null,
    reminderMinutes: 0,
    archived: false,
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  }
}

let data: KairoData

beforeEach(() => {
  data = createEmptyData()
})

/** Marks a task done on a day, the way the completion mutation would. */
function complete(target: KairoData, taskId: number, epochDay: number) {
  target.taskLogs[logKey(taskId, epochDay)] = {
    taskId,
    epochDay,
    completed: true,
    skipped: false,
    completedAt: 0,
    focusMinutes: 0,
  }
}

describe('agenda', () => {
  it('orders a day by start time, then by id for a tie', () => {
    data.tasks = [
      task(1, { startMinute: 17 * 60 }),
      task(2, { startMinute: 9 * 60 }),
      task(3, { startMinute: 9 * 60 }),
    ]
    expect(agendaFor(data, TODAY).map((entry) => entry.task.id)).toEqual([2, 3, 1])
  })

  it('leaves archived tasks out of the day entirely', () => {
    data.tasks = [task(1), task(2, { archived: true })]
    expect(agendaFor(data, TODAY).map((entry) => entry.task.id)).toEqual([1])
  })

  it('reads completion from the log for that specific day', () => {
    data.tasks = [task(1)]
    complete(data, 1, TODAY)
    expect(agendaFor(data, TODAY)[0].completed).toBe(true)
    expect(agendaFor(data, TODAY - 1)[0].completed).toBe(false)
  })
})

describe('period report', () => {
  it('scores a partly-elapsed week on elapsed days only', () => {
    // Anchor to a week that is still running: today is inside it, later days are not.
    const monday = startOfWeek(TODAY)
    data.tasks = [task(1, { startEpochDay: monday })]
    for (let day = monday; day <= TODAY; day++) complete(data, 1, day)

    const report = reportFor(data, 'weekly', TODAY)

    // Every day the user has actually lived through was completed, so this is a perfect week
    // so far — not a week that is mostly zeroes because Sunday hasn't happened.
    expect(report.completionRate).toBe(1)
    expect(report.planned).toBe(report.completed)
    expect(report.elapsedDays.every((day) => day.epochDay <= TODAY)).toBe(true)
  })

  it('still reports the whole window for the chart', () => {
    const monday = startOfWeek(TODAY)
    data.tasks = [task(1, { startEpochDay: monday })]
    const report = reportFor(data, 'weekly', TODAY)
    // `days` spans Monday to Sunday so future days can be drawn as pale planned bars…
    expect(report.days).toHaveLength(7)
    // …while `elapsedDays` stops at today, which is what the rates are divided by.
    expect(report.elapsedDays).toHaveLength(TODAY - monday + 1)
  })

  it('never picks a day that has not happened as the weakest one', () => {
    const monday = startOfWeek(TODAY)
    if (TODAY === monday) return // a Monday has no earlier day in its week to compare against
    data.tasks = [task(1, { startEpochDay: monday })]
    for (let day = monday; day < TODAY; day++) complete(data, 1, day)

    const report = reportFor(data, 'weekly', TODAY)
    expect(report.worstDay?.epochDay).toBe(TODAY)
    expect(report.bestDay?.epochDay).toBeLessThan(TODAY)
  })

  it('judges a fully elapsed past week normally', () => {
    const monday = startOfWeek(TODAY - 7)
    data.tasks = [task(1, { startEpochDay: monday, endEpochDay: monday + 6 })]
    complete(data, 1, monday)
    complete(data, 1, monday + 1)

    const report = reportFor(data, 'weekly', monday)
    expect(report.days).toHaveLength(7)
    expect(report.elapsedDays).toHaveLength(7)
    expect(report.completed).toBe(2)
    expect(report.planned).toBe(7)
  })

  it('reports a rest day as zero rather than as a division by zero', () => {
    const report = reportFor(data, 'daily', TODAY)
    expect(report.completionRate).toBe(0)
    expect(report.consistency).toBe(0)
    expect(report.bestDay).toBeNull()
    expect(report.worstDay).toBeNull()
  })

  it('counts a day as perfect only when everything planned is done', () => {
    data.tasks = [task(1), task(2, { startMinute: 11 * 60 })]
    complete(data, 1, TODAY)
    expect(reportFor(data, 'daily', TODAY).perfectDays).toBe(0)
    complete(data, 2, TODAY)
    expect(reportFor(data, 'daily', TODAY).perfectDays).toBe(1)
  })

  it('measures consistency against days that had something scheduled', () => {
    // Two scheduled days, one of them touched — half consistent, regardless of the empty days.
    const from = TODAY - 1
    data.tasks = [task(1, { startEpochDay: from, endEpochDay: TODAY })]
    complete(data, 1, TODAY)
    const report = reportFor(data, 'weekly', TODAY)
    expect(report.activeDays).toBe(1)
    expect(report.consistency).toBeCloseTo(0.5, 5)
  })
})

describe('breakdowns', () => {
  it('totals completed minutes per category, heaviest first', () => {
    data.tasks = [
      task(1, { category: 'Work', durationMinutes: 60 }),
      task(2, { category: 'Health', durationMinutes: 90, startMinute: 7 * 60 }),
      task(3, { category: 'Work', durationMinutes: 30, startMinute: 14 * 60 }),
    ]
    complete(data, 1, TODAY)
    complete(data, 2, TODAY)
    complete(data, 3, TODAY)

    const slices = categoryBreakdown(data, TODAY, TODAY)
    expect(slices.map((slice) => slice.category)).toEqual(['Health', 'Work'])
    expect(slices[0].minutes).toBe(90)
    expect(slices[1].minutes).toBe(90)
    expect(slices[1].completed).toBe(2)
  })

  it('ignores incomplete blocks in the breakdown', () => {
    data.tasks = [task(1)]
    expect(categoryBreakdown(data, TODAY, TODAY)).toEqual([])
  })

  it('buckets completions into four-hour bands', () => {
    data.tasks = [
      task(1, { startMinute: 2 * 60 }), // band 0: 00:00–04:00
      task(2, { startMinute: 9 * 60 }), // band 2: 08:00–12:00
      task(3, { startMinute: 23 * 60 + 59 }), // band 5: 20:00–24:00
    ]
    complete(data, 1, TODAY)
    complete(data, 2, TODAY)
    complete(data, 3, TODAY)
    expect(hourBands(data, TODAY, TODAY)).toEqual([1, 0, 1, 0, 0, 1])
  })

  it('does not let future days drag a weekday average down', () => {
    // Scheduled every day, completed on every day so far. No weekday should read as weak
    // just because its next occurrence is still ahead.
    const monday = startOfWeek(TODAY)
    data.tasks = [task(1, { startEpochDay: monday })]
    for (let day = monday; day <= TODAY; day++) complete(data, 1, day)

    const profile = weekdayProfile(data, monday, monday + 6)
    expect(profile.size).toBe(TODAY - monday + 1)
    for (const rate of profile.values()) expect(rate).toBe(1)
  })
})

describe('conflicts', () => {
  it('finds an overlapping block but not an adjacent one', () => {
    data.tasks = [task(1, { startMinute: 9 * 60, durationMinutes: 60 })]

    const abutting = task(2, { startMinute: 10 * 60, durationMinutes: 30 })
    expect(conflictsFor(data, abutting, TODAY)).toEqual([])

    const overlapping = task(3, { startMinute: 9 * 60 + 30, durationMinutes: 60 })
    expect(conflictsFor(data, overlapping, TODAY).map((entry) => entry.task.id)).toEqual([1])
  })

  it('does not report a task as conflicting with itself', () => {
    data.tasks = [task(1)]
    expect(conflictsFor(data, data.tasks[0], TODAY)).toEqual([])
  })
})
