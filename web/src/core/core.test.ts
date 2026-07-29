import { describe, expect, it } from 'vitest'
import {
  DayMask,
  dayBit,
  endOfMonth,
  endOfWeek,
  formatDuration,
  formatIso,
  formatTime,
  parseIsoDate,
  parseTimeToMinute,
  startOfMonth,
  startOfWeek,
  toEpochDay,
} from './time'
import {
  Economy,
  Leveling,
  MAX_REPLAY_DAYS,
  dayGrade,
  evaluateStreak,
  gradeFor,
  nextOccurrence,
  occursOn,
  type DayOutcome,
  type StreakState,
} from './rules'
import { Recurrence, type Task } from '../data/types'

/*
 * These mirror the Android app's unit tests. Both clients are meant to describe the same
 * schedule, so any divergence in these rules is a bug in one of them.
 */

describe('time parsing', () => {
  it('parses the time formats a language model actually emits', () => {
    expect(parseTimeToMinute('07:30')).toBe(450)
    expect(parseTimeToMinute('7:30')).toBe(450)
    expect(parseTimeToMinute('7:30 am')).toBe(450)
    expect(parseTimeToMinute('19:05')).toBe(1145)
    expect(parseTimeToMinute('7pm')).toBe(1140)
    expect(parseTimeToMinute('7:30 PM')).toBe(1170)
    expect(parseTimeToMinute('0730')).toBe(450)
    expect(parseTimeToMinute('12:00 am')).toBe(0)
    expect(parseTimeToMinute('12:00 pm')).toBe(720)
    expect(parseTimeToMinute(' 9 ')).toBe(540)
  })

  it('rejects nonsense rather than guessing', () => {
    expect(parseTimeToMinute(null)).toBeNull()
    expect(parseTimeToMinute('')).toBeNull()
    expect(parseTimeToMinute('later')).toBeNull()
    expect(parseTimeToMinute('25:00')).toBeNull()
    expect(parseTimeToMinute('10:75')).toBeNull()
  })

  it('round-trips ISO dates and rejects malformed ones', () => {
    const day = toEpochDay(new Date(2026, 2, 14))
    expect(parseIsoDate('2026-03-14')).toBe(day)
    expect(formatIso(day)).toBe('2026-03-14')
    expect(parseIsoDate('14/03/2026')).toBeNull()
    expect(parseIsoDate('2026-02-30')).toBeNull()
    expect(parseIsoDate(null)).toBeNull()
  })

  it('formats times and durations without redundant units', () => {
    expect(formatTime(450, true)).toBe('07:30')
    expect(formatTime(450)).toBe('7:30 AM')
    expect(formatTime(0, true)).toBe('00:00')
    expect(formatDuration(45)).toBe('45m')
    expect(formatDuration(60)).toBe('1h')
    expect(formatDuration(90)).toBe('1h 30m')
  })

  it('runs weeks Monday to Sunday and months end to end', () => {
    const wednesday = toEpochDay(new Date(2026, 2, 11))
    expect(startOfWeek(wednesday)).toBe(toEpochDay(new Date(2026, 2, 9)))
    expect(endOfWeek(wednesday)).toBe(toEpochDay(new Date(2026, 2, 15)))

    const midFeb = toEpochDay(new Date(2026, 1, 14))
    expect(startOfMonth(midFeb)).toBe(toEpochDay(new Date(2026, 1, 1)))
    expect(endOfMonth(midFeb)).toBe(toEpochDay(new Date(2026, 1, 28)))
  })

  it('maps Monday to bit zero', () => {
    const monday = toEpochDay(new Date(2026, 2, 9))
    expect(dayBit(monday)).toBe(1)
    expect(dayBit(monday + 6)).toBe(1 << 6)
  })
})

describe('day mask', () => {
  it('parses the vocabulary the AI is told to use', () => {
    expect(DayMask.fromNames(['daily'])).toBe(DayMask.ALL)
    expect(DayMask.fromNames(['weekdays'])).toBe(DayMask.WEEKDAYS)
    expect(DayMask.fromNames(['weekends'])).toBe(DayMask.WEEKENDS)
    expect(DayMask.fromNames([])).toBe(DayMask.NONE)
    expect(DayMask.fromNames(null)).toBe(DayMask.NONE)
    expect(DayMask.fromNames(['mon', 'wed', 'fri'])).toBe(0b0010101)
    expect(DayMask.fromNames(['Monday'])).toBe(0b0000001)
  })

  it('ignores names it cannot understand instead of failing', () => {
    expect(DayMask.fromNames(['mon', 'someday'])).toBe(0b0000001)
  })

  it('describes masks in human terms', () => {
    expect(DayMask.describe(DayMask.ALL)).toBe('Every day')
    expect(DayMask.describe(DayMask.WEEKDAYS)).toBe('Weekdays')
    expect(DayMask.describe(DayMask.fromNames(['mon', 'wed', 'fri']))).toBe('Mon, Wed, Fri')
  })

  it('toggles as its own inverse', () => {
    const start = DayMask.fromNames(['tue'])
    expect(DayMask.toggle(DayMask.toggle(start, 3), 3)).toBe(start)
  })
})

describe('recurrence', () => {
  const monday = toEpochDay(new Date(2026, 2, 9))
  const wednesday = monday + 2
  const saturday = monday + 5

  const task = (overrides: Partial<Task>): Task => ({
    id: 1,
    title: 'Block',
    note: '',
    category: 'General',
    colorHex: '#6C5CE7',
    priority: 1,
    startMinute: 540,
    durationMinutes: 60,
    recurrence: Recurrence.ONCE,
    daysMask: 0,
    specificDate: null,
    startEpochDay: monday,
    endEpochDay: null,
    reminderMinutes: -1,
    archived: false,
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  })

  it('places a one-off block only on its own date', () => {
    const subject = task({ specificDate: wednesday, startEpochDay: wednesday })
    expect(occursOn(subject, wednesday)).toBe(true)
    expect(occursOn(subject, wednesday + 1)).toBe(false)
    expect(occursOn(subject, wednesday - 1)).toBe(false)
  })

  it('places a daily block on every day from its start', () => {
    const subject = task({ recurrence: Recurrence.DAILY, startEpochDay: wednesday })
    expect(occursOn(subject, wednesday - 1)).toBe(false)
    expect(occursOn(subject, wednesday)).toBe(true)
    expect(occursOn(subject, wednesday + 30)).toBe(true)
  })

  it('honours a weekly day mask', () => {
    const subject = task({
      recurrence: Recurrence.WEEKLY,
      daysMask: DayMask.fromNames(['mon', 'wed', 'fri']),
    })
    expect(occursOn(subject, monday)).toBe(true)
    expect(occursOn(subject, monday + 1)).toBe(false)
    expect(occursOn(subject, wednesday)).toBe(true)
    expect(occursOn(subject, saturday)).toBe(false)
  })

  it('respects an end date and the archived flag', () => {
    expect(occursOn(task({ recurrence: Recurrence.DAILY, endEpochDay: wednesday }), wednesday)).toBe(true)
    expect(occursOn(task({ recurrence: Recurrence.DAILY, endEpochDay: wednesday }), wednesday + 1)).toBe(false)
    expect(occursOn(task({ recurrence: Recurrence.DAILY, archived: true }), monday)).toBe(false)
  })

  it('finds the next matching weekday', () => {
    const subject = task({ recurrence: Recurrence.WEEKLY, daysMask: DayMask.fromNames(['fri']) })
    expect(nextOccurrence(subject, monday)).toBe(monday + 4)
    expect(nextOccurrence(task({ recurrence: Recurrence.DAILY, endEpochDay: monday - 1 }), monday)).toBeNull()
  })
})

describe('levels and economy', () => {
  it('starts a new user at level one', () => {
    expect(Leveling.levelForXp(0)).toBe(1)
    expect(Leveling.levelForXp(-50)).toBe(1)
  })

  it('keeps thresholds and their inverse in agreement', () => {
    for (let level = 1; level <= 30; level++) {
      const floor = Leveling.totalXpForLevel(level)
      const ceiling = Leveling.totalXpForLevel(level + 1)
      expect(Leveling.levelForXp(floor)).toBe(level)
      expect(Leveling.levelForXp(ceiling - 1)).toBe(level)
      expect(Leveling.levelForXp(ceiling)).toBe(level + 1)
    }
  })

  it('normalises progress within a level', () => {
    for (let xp = 0; xp <= 5000; xp += 37) {
      const info = Leveling.infoFor(xp)
      expect(info.progress).toBeGreaterThanOrEqual(0)
      expect(info.progress).toBeLessThanOrEqual(1)
      expect(info.xpIntoLevel).toBeGreaterThanOrEqual(0)
      expect(info.xpIntoLevel).toBeLessThanOrEqual(info.xpForNextLevel)
    }
  })

  it('pays more for higher priority and only on milestone days', () => {
    expect(Economy.xpForTask(2)).toBeGreaterThan(Economy.xpForTask(1))
    expect(Economy.xpForTask(1)).toBeGreaterThan(Economy.xpForTask(0))
    expect(Economy.xpForTask(99)).toBe(Economy.xpForTask(1))
    expect(Economy.streakBonusCoins(1)).toBe(0)
    expect(Economy.streakBonusCoins(3)).toBeGreaterThan(0)
    expect(Economy.streakBonusCoins(7)).toBeGreaterThan(Economy.streakBonusCoins(3))
    expect(Economy.streakBonusCoins(8)).toBe(0)
    expect(Economy.grantsFreezeToken(7)).toBe(true)
    expect(Economy.grantsFreezeToken(8)).toBe(false)
  })
})

describe('day grade', () => {
  it('grades a finished day on everything it planned', () => {
    expect(dayGrade(6, 6, 6)).toBe(gradeFor(1))
    expect(dayGrade(3, 6, 6)).toBe(gradeFor(0.5))
    expect(dayGrade(0, 6, 6)).toBe('Untouched')
  })

  it('judges a running day only on the blocks that are due, and says so', () => {
    // Three of six blocks done at midday, and the other three are this evening: that is
    // everything the clock has asked for, not a half-failed day.
    expect(dayGrade(3, 3, 6)).toBe(`${gradeFor(1)} so far`)
    expect(dayGrade(1, 3, 6)).toBe(`${gradeFor(1 / 3)} so far`)
  })

  it('does not grade a day before anything is due', () => {
    expect(dayGrade(0, 0, 6)).toBe('Not due yet')
    expect(dayGrade(2, 0, 6)).toBe('Ahead')
  })

  it('calls an empty day a rest day rather than a failure', () => {
    expect(dayGrade(0, 0, 0)).toBe('Rest day')
  })
})

describe('streak engine', () => {
  const today = 20_000
  const state = (overrides: Partial<StreakState> = {}): StreakState => ({
    streak: 0,
    longest: 0,
    freezeTokens: 0,
    lastStreakDay: -1,
    ...overrides,
  })
  const outcomes =
    (map: Record<number, DayOutcome>) =>
    (day: number): DayOutcome =>
      map[day] ?? { planned: 0, completionRate: 0 }
  const done = (rate = 1, planned = 4): DayOutcome => ({ planned, completionRate: rate })
  const nothing: DayOutcome = { planned: 0, completionRate: 0 }

  it('starts a streak at one on a fully completed day', () => {
    const result = evaluateStreak(state(), today, outcomes({ [today]: done() }))
    expect(result.state.streak).toBe(1)
    expect(result.state.longest).toBe(1)
  })

  it('never breaks the streak on an unfinished today', () => {
    const result = evaluateStreak(
      state({ streak: 5, longest: 5, lastStreakDay: today - 1 }),
      today,
      outcomes({ [today]: done(0.1) }),
    )
    expect(result.state.streak).toBe(5)
    expect(result.events).toHaveLength(0)
  })

  it('resets on a missed past day', () => {
    const result = evaluateStreak(
      state({ streak: 9, longest: 9, lastStreakDay: today - 2 }),
      today,
      outcomes({ [today - 1]: done(0.25), [today]: nothing }),
    )
    expect(result.state.streak).toBe(0)
    expect(result.state.longest).toBe(9)
    expect(result.events).toEqual([{ kind: 'lost', previous: 9 }])
  })

  it('treats a day with nothing scheduled as neutral', () => {
    const result = evaluateStreak(
      state({ streak: 4, longest: 4, lastStreakDay: today - 3 }),
      today,
      outcomes({ [today - 2]: nothing, [today - 1]: nothing, [today]: done() }),
    )
    expect(result.state.streak).toBe(5)
    expect(result.events.some((event) => event.kind === 'lost')).toBe(false)
  })

  it('burns a freeze token to absorb one missed day', () => {
    const result = evaluateStreak(
      state({ streak: 6, longest: 6, freezeTokens: 1, lastStreakDay: today - 2 }),
      today,
      outcomes({ [today - 1]: done(0), [today]: nothing }),
    )
    expect(result.state.streak).toBe(6)
    expect(result.state.freezeTokens).toBe(0)
    expect(result.events).toEqual([{ kind: 'frozen', streak: 6, tokensLeft: 0 }])
  })

  it('still breaks after two misses with one token', () => {
    const result = evaluateStreak(
      state({ streak: 6, longest: 6, freezeTokens: 1, lastStreakDay: today - 3 }),
      today,
      outcomes({ [today - 2]: done(0), [today - 1]: done(0), [today]: nothing }),
    )
    expect(result.state.streak).toBe(0)
    expect(result.events.some((event) => event.kind === 'frozen')).toBe(true)
    expect(result.events.some((event) => event.kind === 'lost')).toBe(true)
  })

  it('pays a milestone bonus and hands back a token on day seven', () => {
    const result = evaluateStreak(
      state({ streak: 6, longest: 6, lastStreakDay: today - 1 }),
      today,
      outcomes({ [today]: done() }),
    )
    expect(result.state.streak).toBe(7)
    expect(result.state.freezeTokens).toBe(1)
    expect(result.bonusCoins).toBe(Economy.streakBonusCoins(7))
  })

  it('caps freeze tokens', () => {
    const result = evaluateStreak(
      state({
        streak: 6,
        longest: 6,
        freezeTokens: Economy.MAX_FREEZE_TOKENS,
        lastStreakDay: today - 1,
      }),
      today,
      outcomes({ [today]: done() }),
    )
    expect(result.state.freezeTokens).toBe(Economy.MAX_FREEZE_TOKENS)
  })

  it('is idempotent when re-run', () => {
    const first = evaluateStreak(
      state({ streak: 2, longest: 2, lastStreakDay: today - 1 }),
      today,
      outcomes({ [today]: done() }),
    )
    const second = evaluateStreak(first.state, today, outcomes({ [today]: done() }))
    expect(first.state.streak).toBe(3)
    expect(second.state).toEqual(first.state)
    expect(second.events).toHaveLength(0)
  })

  it('caps the replay window when the clock jumps', () => {
    let reads = 0
    const result = evaluateStreak(state({ streak: 1, longest: 1, lastStreakDay: 0 }), today, () => {
      reads++
      return nothing
    })
    expect(reads).toBeLessThanOrEqual(MAX_REPLAY_DAYS + 1)
    expect(result.state.streak).toBe(1)
  })

  it('does not punish a first run for the past', () => {
    const result = evaluateStreak(state(), today, outcomes({ [today]: done(0) }))
    expect(result.state.streak).toBe(0)
    expect(result.events).toHaveLength(0)
  })
})
