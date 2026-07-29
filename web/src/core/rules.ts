/*
 * The rules that decide what a day is worth: recurrence, XP/coins, levels and the streak.
 * Ported from core/Gamification.kt, core/RecurrenceRules.kt and core/StreakEngine.kt so the
 * numbers on the web match the numbers on the phone exactly.
 */

import { dayBit } from './time'
import type { Task } from '../data/types'
import { Recurrence } from '../data/types'

// ---- recurrence ----------------------------------------------------------

export function occursOn(task: Task, epochDay: number): boolean {
  if (task.archived) return false
  if (epochDay < task.startEpochDay) return false
  if (task.endEpochDay != null && epochDay > task.endEpochDay) return false
  switch (task.recurrence) {
    case Recurrence.ONCE:
      return task.specificDate === epochDay
    case Recurrence.DAILY:
      return true
    case Recurrence.WEEKLY:
      return (task.daysMask & dayBit(epochDay)) !== 0
    default:
      return false
  }
}

export function nextOccurrence(task: Task, from: number, horizonDays = 14): number | null {
  for (let offset = 0; offset <= horizonDays; offset++) {
    if (occursOn(task, from + offset)) return from + offset
  }
  return null
}

// ---- economy -------------------------------------------------------------

export const Economy = {
  XP_TASK_LOW: 6,
  XP_TASK_NORMAL: 10,
  XP_TASK_HIGH: 16,
  XP_CHECKLIST_ITEM: 2,
  XP_PERFECT_DAY: 45,

  COINS_TASK_LOW: 3,
  COINS_TASK_NORMAL: 5,
  COINS_TASK_HIGH: 8,
  COINS_PERFECT_DAY: 25,

  /** A day only counts toward the streak once this share of planned work is done. */
  STREAK_COMPLETION_THRESHOLD: 0.6,

  MAX_FREEZE_TOKENS: 3,
  FREEZE_TOKEN_COST: 120,

  xpForTask(priority: number): number {
    if (priority === 0) return Economy.XP_TASK_LOW
    if (priority === 2) return Economy.XP_TASK_HIGH
    return Economy.XP_TASK_NORMAL
  },

  coinsForTask(priority: number): number {
    if (priority === 0) return Economy.COINS_TASK_LOW
    if (priority === 2) return Economy.COINS_TASK_HIGH
    return Economy.COINS_TASK_NORMAL
  },

  streakBonusCoins(streak: number): number {
    if (streak <= 0) return 0
    if (streak % 100 === 0) return 500
    if (streak % 30 === 0) return 200
    if (streak % 14 === 0) return 90
    if (streak % 7 === 0) return 40
    if (streak % 3 === 0) return 12
    return 0
  },

  grantsFreezeToken(streak: number): boolean {
    return streak > 0 && streak % 7 === 0
  },
}

// ---- levels --------------------------------------------------------------

const LEVEL_TITLES = [
  'Drifter',
  'Starter',
  'Planner',
  'Builder',
  'Focused',
  'Consistent',
  'Disciplined',
  'Momentum',
  'Relentless',
  'Architect',
  'Master of Hours',
]

export interface LevelInfo {
  level: number
  title: string
  xpIntoLevel: number
  xpForNextLevel: number
  progress: number
}

export const Leveling = {
  /** Total XP needed to reach a level: 30 * (L-1) * L — gently super-linear. */
  totalXpForLevel(level: number): number {
    if (level <= 1) return 0
    return 30 * (level - 1) * level
  },

  levelForXp(xp: number): number {
    if (xp <= 0) return 1
    const level = Math.floor((1 + Math.sqrt(1 + (4 * xp) / 30)) / 2)
    return Math.max(1, level)
  },

  titleFor(level: number): string {
    const index = Math.min(Math.max(level - 1, 0), LEVEL_TITLES.length - 1)
    return LEVEL_TITLES[index]
  },

  infoFor(xp: number): LevelInfo {
    const level = Leveling.levelForXp(xp)
    const floor = Leveling.totalXpForLevel(level)
    const ceiling = Leveling.totalXpForLevel(level + 1)
    const span = Math.max(1, ceiling - floor)
    const into = Math.max(0, xp - floor)
    return {
      level,
      title: Leveling.titleFor(level),
      xpIntoLevel: into,
      xpForNextLevel: span,
      progress: Math.min(1, Math.max(0, into / span)),
    }
  },
}

export function gradeFor(rate: number): string {
  if (rate >= 0.95) return 'Flawless'
  if (rate >= 0.8) return 'Strong'
  if (rate >= 0.6) return 'Solid'
  if (rate >= 0.35) return 'Patchy'
  if (rate > 0) return 'Rough'
  return 'Untouched'
}

/**
 * The verdict shown against a single day, judged only on the blocks the clock has already
 * asked for. An evening gym session is not a block you have failed at three in the afternoon,
 * so a day still running is graded on what is due — the same reason analytics only score
 * elapsed days. `due` equals `planned` for any day that is over.
 */
export function dayGrade(completed: number, due: number, planned: number): string {
  if (planned === 0) return 'Rest day'
  if (due === 0) return completed > 0 ? 'Ahead' : 'Not due yet'
  const grade = gradeFor(completed / due)
  // Say "so far" while blocks are still to come, so a verdict on half a day never reads as
  // a verdict on the whole one.
  return due < planned ? `${grade} so far` : grade
}

/** Rate to colour a day's ring by — pairs with {@link dayGrade}, not with raw progress. */
export function dayGradeRate(completed: number, due: number, planned: number): number {
  if (planned === 0 || due === 0) return 1
  return completed / due
}

// ---- streak --------------------------------------------------------------

export interface StreakState {
  streak: number
  longest: number
  freezeTokens: number
  lastStreakDay: number
}

export type StreakEvent =
  | { kind: 'extended'; streak: number; bonusCoins: number; grantedToken: boolean }
  | { kind: 'frozen'; streak: number; tokensLeft: number }
  | { kind: 'lost'; previous: number }

export interface DayOutcome {
  planned: number
  completionRate: number
}

export interface StreakOutcome {
  state: StreakState
  events: StreakEvent[]
  bonusCoins: number
}

/** Ceiling on how many days one pass will replay, in case the device clock jumps. */
export const MAX_REPLAY_DAYS = 400

/**
 * Pure streak arithmetic, in the same order of precedence as the Android engine:
 *  1. A day with nothing scheduled is neutral — a real rest day never breaks a streak.
 *  2. A day clearing the threshold extends the streak and may pay a milestone bonus.
 *  3. A missed past day burns a freeze token if one is available.
 *  4. Otherwise a missed past day resets the streak.
 *  5. Today is never judged as missed — it is still winnable.
 */
export function evaluateStreak(
  initial: StreakState,
  today: number,
  outcomeFor: (epochDay: number) => DayOutcome,
): StreakOutcome {
  let streak = initial.streak
  let longest = initial.longest
  let tokens = initial.freezeTokens
  let lastDay = initial.lastStreakDay < 0 ? today - 1 : initial.lastStreakDay
  if (lastDay > today) lastDay = today

  const events: StreakEvent[] = []
  let day = Math.max(lastDay + 1, today - MAX_REPLAY_DAYS)

  while (day <= today) {
    const outcome = outcomeFor(day)
    if (outcome.planned === 0) {
      // neutral rest day
    } else if (outcome.completionRate >= Economy.STREAK_COMPLETION_THRESHOLD) {
      streak += 1
      lastDay = day
      longest = Math.max(longest, streak)
      const bonusCoins = Economy.streakBonusCoins(streak)
      const grantedToken = Economy.grantsFreezeToken(streak)
      if (grantedToken) tokens = Math.min(tokens + 1, Economy.MAX_FREEZE_TOKENS)
      events.push({ kind: 'extended', streak, bonusCoins, grantedToken })
    } else if (day < today && streak > 0 && tokens > 0) {
      tokens -= 1
      lastDay = day
      events.push({ kind: 'frozen', streak, tokensLeft: tokens })
    } else if (day < today) {
      if (streak > 0) events.push({ kind: 'lost', previous: streak })
      streak = 0
      lastDay = day
    }
    day++
  }

  const bonusCoins = events.reduce(
    (total, event) => (event.kind === 'extended' ? total + event.bonusCoins : total),
    0,
  )
  return {
    state: { streak, longest, freezeTokens: tokens, lastStreakDay: lastDay },
    events,
    bonusCoins,
  }
}

export function nextMilestone(streak: number): number {
  return [3, 7, 14, 30, 60, 100, 180, 365].find((value) => value > streak) ?? streak + 100
}

// ---- PIN lockout ---------------------------------------------------------

export const ATTEMPTS_BEFORE_LOCKOUT = 5

export function lockoutMillisFor(failedAttempts: number): number {
  if (failedAttempts < ATTEMPTS_BEFORE_LOCKOUT) return 0
  if (failedAttempts < 7) return 30_000
  if (failedAttempts < 9) return 2 * 60_000
  if (failedAttempts < 12) return 5 * 60_000
  return 15 * 60_000
}

export function attemptsLeft(failedAttempts: number): number {
  return Math.max(0, ATTEMPTS_BEFORE_LOCKOUT - failedAttempts)
}
