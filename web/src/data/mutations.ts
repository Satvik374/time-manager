/*
 * Write-side of the store. Every function takes the whole document and returns a new one, so
 * state updates stay a single reducer step and nothing mutates in place.
 */

import { dayBit, todayEpochDay } from '../core/time'
import {
  Economy,
  Leveling,
  evaluateStreak,
  type DayOutcome,
  type StreakState,
} from '../core/rules'
import { ACHIEVEMENT_KEYS } from './defaults'
import { agendaFor, logKey, snapshotFor, taskById } from './selectors'
import type { KairoData, Reward, Task } from './types'
import { completionRate, isPerfect } from './types'

/** Things worth celebrating in the UI right after an action. */
export type RewardEvent =
  | { kind: 'xp'; amount: number; reason: string }
  | { kind: 'coins'; amount: number; reason: string }
  | { kind: 'levelUp'; level: number; title: string }
  | { kind: 'unlocked'; title: string; emoji: string }
  | { kind: 'streak'; streak: number; bonusCoins: number }
  | { kind: 'frozen'; streak: number; tokensLeft: number }
  | { kind: 'streakLost'; previous: number }
  | { kind: 'perfectDay'; xp: number; coins: number }

export interface MutationResult {
  data: KairoData
  events: RewardEvent[]
}

function nextId(data: KairoData): [number, KairoData] {
  return [data.nextId, { ...data, nextId: data.nextId + 1 }]
}

// ---- tasks ---------------------------------------------------------------

export interface TaskDraft {
  id?: number
  title: string
  note?: string
  category?: string
  colorHex?: string
  priority?: number
  startMinute: number
  durationMinutes: number
  recurrence: number
  daysMask?: number
  specificDate?: number | null
  reminderMinutes?: number
}

export function upsertTask(
  data: KairoData,
  draft: TaskDraft,
  checklist?: string[],
): { data: KairoData; taskId: number } {
  const now = Date.now()
  const existing = draft.id != null ? taskById(data, draft.id) : undefined
  let working = data
  let id = draft.id ?? 0

  if (!existing) {
    const [freshId, advanced] = nextId(working)
    id = freshId
    working = advanced
  }

  const anchorDay = draft.specificDate ?? existing?.specificDate ?? todayEpochDay()
  const task: Task = {
    id,
    title: draft.title.trim(),
    note: draft.note ?? existing?.note ?? '',
    category: (draft.category ?? existing?.category ?? 'General').trim() || 'General',
    colorHex: draft.colorHex ?? existing?.colorHex ?? '#6C5CE7',
    priority: draft.priority ?? existing?.priority ?? 1,
    startMinute: draft.startMinute,
    durationMinutes: draft.durationMinutes,
    recurrence: draft.recurrence,
    daysMask: draft.daysMask ?? existing?.daysMask ?? 0,
    specificDate: draft.recurrence === 0 ? anchorDay : null,
    startEpochDay:
      draft.recurrence === 0 ? anchorDay : Math.min(existing?.startEpochDay ?? todayEpochDay(), todayEpochDay()),
    endEpochDay: existing?.endEpochDay ?? null,
    reminderMinutes: draft.reminderMinutes ?? existing?.reminderMinutes ?? -1,
    archived: existing?.archived ?? false,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  }

  working = {
    ...working,
    tasks: existing
      ? working.tasks.map((item) => (item.id === id ? task : item))
      : [...working.tasks, task],
  }

  if (checklist) working = replaceChecklist(working, id, checklist)
  return { data: working, taskId: id }
}

export function deleteTask(data: KairoData, taskId: number): KairoData {
  const itemIds = new Set(
    data.checklistItems.filter((item) => item.taskId === taskId).map((item) => item.id),
  )
  return {
    ...data,
    tasks: data.tasks.filter((task) => task.id !== taskId),
    checklistItems: data.checklistItems.filter((item) => item.taskId !== taskId),
    taskLogs: filterRecord(data.taskLogs, (log) => log.taskId !== taskId),
    checklistLogs: filterRecord(data.checklistLogs, (log) => !itemIds.has(log.itemId)),
  }
}

export function archiveTask(data: KairoData, taskId: number, archived = true): KairoData {
  return {
    ...data,
    tasks: data.tasks.map((task) =>
      task.id === taskId ? { ...task, archived, updatedAt: Date.now() } : task,
    ),
  }
}

export function moveTask(
  data: KairoData,
  taskId: number,
  startMinute?: number | null,
  durationMinutes?: number | null,
  epochDay?: number | null,
): KairoData {
  const task = taskById(data, taskId)
  if (!task) return data
  let updated: Task = {
    ...task,
    startMinute: startMinute ?? task.startMinute,
    durationMinutes: durationMinutes ?? task.durationMinutes,
    updatedAt: Date.now(),
  }
  if (epochDay != null) {
    if (task.recurrence === 0) {
      updated = { ...updated, specificDate: epochDay, startEpochDay: epochDay }
    } else if (task.recurrence === 2) {
      updated = { ...updated, daysMask: dayBit(epochDay) }
    }
  }
  return { ...data, tasks: data.tasks.map((item) => (item.id === taskId ? updated : item)) }
}

// ---- checklists ----------------------------------------------------------

export function replaceChecklist(data: KairoData, taskId: number, lines: string[]): KairoData {
  const removedIds = new Set(
    data.checklistItems.filter((item) => item.taskId === taskId).map((item) => item.id),
  )
  let cursor = data.nextId
  const items = lines
    .filter((line) => line.trim().length > 0)
    .map((line, index) => ({
      id: cursor++,
      taskId,
      text: line.trim(),
      position: index,
    }))
  return {
    ...data,
    nextId: cursor,
    checklistItems: [...data.checklistItems.filter((item) => item.taskId !== taskId), ...items],
    checklistLogs: filterRecord(data.checklistLogs, (log) => !removedIds.has(log.itemId)),
  }
}

export function addChecklistItems(data: KairoData, taskId: number, lines: string[]): KairoData {
  const existing = data.checklistItems.filter((item) => item.taskId === taskId)
  let position = existing.reduce((max, item) => Math.max(max, item.position), -1) + 1
  let cursor = data.nextId
  const items = lines
    .filter((line) => line.trim().length > 0)
    .map((line) => ({ id: cursor++, taskId, text: line.trim(), position: position++ }))
  return { ...data, nextId: cursor, checklistItems: [...data.checklistItems, ...items] }
}

export function renameChecklistItem(data: KairoData, itemId: number, text: string): KairoData {
  return {
    ...data,
    checklistItems: data.checklistItems.map((item) =>
      item.id === itemId ? { ...item, text: text.trim() } : item,
    ),
  }
}

export function removeChecklistItem(data: KairoData, itemId: number): KairoData {
  return {
    ...data,
    checklistItems: data.checklistItems.filter((item) => item.id !== itemId),
    checklistLogs: filterRecord(data.checklistLogs, (log) => log.itemId !== itemId),
  }
}

export function setChecklistChecked(
  data: KairoData,
  itemId: number,
  epochDay: number,
  checked: boolean,
): MutationResult {
  const item = data.checklistItems.find((entry) => entry.id === itemId)
  if (!item) return { data, events: [] }
  const withLog: KairoData = {
    ...data,
    checklistLogs: {
      ...data.checklistLogs,
      [logKey(itemId, epochDay)]: {
        itemId,
        taskId: item.taskId,
        epochDay,
        checked,
        checkedAt: checked ? Date.now() : null,
      },
    },
  }
  if (!checked) return { data: withLog, events: [] }
  return {
    data: { ...withLog, stats: { ...withLog.stats, xp: withLog.stats.xp + Economy.XP_CHECKLIST_ITEM } },
    events: [{ kind: 'xp', amount: Economy.XP_CHECKLIST_ITEM, reason: 'Checklist step' }],
  }
}

// ---- completion + rewards ------------------------------------------------

export function setTaskCompleted(
  data: KairoData,
  taskId: number,
  epochDay: number,
  completed: boolean,
): MutationResult {
  const task = taskById(data, taskId)
  if (!task) return { data, events: [] }

  const previous = data.taskLogs[logKey(taskId, epochDay)]
  const wasCompleted = previous?.completed ?? false

  let working: KairoData = {
    ...data,
    taskLogs: {
      ...data.taskLogs,
      [logKey(taskId, epochDay)]: {
        taskId,
        epochDay,
        completed,
        skipped: completed ? false : (previous?.skipped ?? false),
        completedAt: completed ? Date.now() : null,
        focusMinutes: completed ? task.durationMinutes : 0,
      },
    },
  }

  if (completed) {
    // Finishing the block implies its checklist is done, keeping the two views honest.
    const checklistLogs = { ...working.checklistLogs }
    for (const item of working.checklistItems.filter((entry) => entry.taskId === taskId)) {
      checklistLogs[logKey(item.id, epochDay)] = {
        itemId: item.id,
        taskId,
        epochDay,
        checked: true,
        checkedAt: Date.now(),
      }
    }
    working = { ...working, checklistLogs }
  }

  const events: RewardEvent[] = []
  if (completed && !wasCompleted) {
    const xp = Economy.xpForTask(task.priority)
    const coins = Economy.coinsForTask(task.priority)
    const beforeLevel = Leveling.levelForXp(working.stats.xp)

    working = {
      ...working,
      stats: {
        ...working.stats,
        xp: working.stats.xp + xp,
        coins: working.stats.coins + coins,
        totalTasksCompleted: working.stats.totalTasksCompleted + 1,
        totalFocusMinutes: working.stats.totalFocusMinutes + task.durationMinutes,
      },
    }
    events.push({ kind: 'xp', amount: xp, reason: 'Block completed' })
    events.push({ kind: 'coins', amount: coins, reason: 'Block completed' })

    if (isPerfect(snapshotFor(working, epochDay))) {
      working = {
        ...working,
        stats: {
          ...working.stats,
          xp: working.stats.xp + Economy.XP_PERFECT_DAY,
          coins: working.stats.coins + Economy.COINS_PERFECT_DAY,
          perfectDays: working.stats.perfectDays + 1,
        },
      }
      events.push({
        kind: 'perfectDay',
        xp: Economy.XP_PERFECT_DAY,
        coins: Economy.COINS_PERFECT_DAY,
      })
    }

    const afterLevel = Leveling.levelForXp(working.stats.xp)
    if (afterLevel > beforeLevel) {
      events.push({ kind: 'levelUp', level: afterLevel, title: Leveling.titleFor(afterLevel) })
    }
  }

  const streakResult = refreshStreak(working)
  working = streakResult.data
  events.push(...streakResult.events)

  const achievementResult = refreshAchievements(working, {
    earlyBird: completed && !wasCompleted && task.startMinute < 7 * 60,
  })
  working = achievementResult.data
  events.push(...achievementResult.events)

  return { data: working, events }
}

export function setTaskSkipped(
  data: KairoData,
  taskId: number,
  epochDay: number,
  skipped: boolean,
): KairoData {
  const previous = data.taskLogs[logKey(taskId, epochDay)]
  return {
    ...data,
    taskLogs: {
      ...data.taskLogs,
      [logKey(taskId, epochDay)]: {
        taskId,
        epochDay,
        completed: skipped ? false : (previous?.completed ?? false),
        skipped,
        completedAt: previous?.completedAt ?? null,
        focusMinutes: skipped ? 0 : (previous?.focusMinutes ?? 0),
      },
    },
  }
}

/** Safe to call on every load; it replays only the days it has not scored yet. */
export function refreshStreak(data: KairoData, today = todayEpochDay()): MutationResult {
  const initial: StreakState = {
    streak: data.stats.currentStreak,
    longest: data.stats.longestStreak,
    freezeTokens: data.stats.freezeTokens,
    lastStreakDay: data.stats.lastStreakDay,
  }
  const outcomeFor = (day: number): DayOutcome => {
    const snapshot = snapshotFor(data, day)
    return { planned: snapshot.planned, completionRate: completionRate(snapshot) }
  }
  const result = evaluateStreak(initial, today, outcomeFor)

  const events: RewardEvent[] = result.events.map((event) => {
    if (event.kind === 'extended') {
      return { kind: 'streak', streak: event.streak, bonusCoins: event.bonusCoins }
    }
    if (event.kind === 'frozen') {
      return { kind: 'frozen', streak: event.streak, tokensLeft: event.tokensLeft }
    }
    return { kind: 'streakLost', previous: event.previous }
  })

  return {
    data: {
      ...data,
      stats: {
        ...data.stats,
        coins: data.stats.coins + result.bonusCoins,
        currentStreak: result.state.streak,
        longestStreak: result.state.longest,
        freezeTokens: result.state.freezeTokens,
        lastStreakDay: result.state.lastStreakDay,
        lastRolloverDay: today,
      },
    },
    events,
  }
}

export function refreshAchievements(
  data: KairoData,
  options: { earlyBird?: boolean; coachTalk?: boolean } = {},
): MutationResult {
  const stats = data.stats
  const level = Leveling.levelForXp(stats.xp)
  const completedCount = Object.values(data.taskLogs).filter((log) => log.completed).length

  const progressFor = (key: string): number => {
    switch (key) {
      case ACHIEVEMENT_KEYS.TASKS_10:
      case ACHIEVEMENT_KEYS.TASKS_50:
      case ACHIEVEMENT_KEYS.TASKS_250:
        return completedCount
      case ACHIEVEMENT_KEYS.STREAK_3:
      case ACHIEVEMENT_KEYS.STREAK_7:
      case ACHIEVEMENT_KEYS.STREAK_30:
      case ACHIEVEMENT_KEYS.STREAK_100:
        return stats.longestStreak
      case ACHIEVEMENT_KEYS.PERFECT_1:
      case ACHIEVEMENT_KEYS.PERFECT_10:
        return stats.perfectDays
      case ACHIEVEMENT_KEYS.FOCUS_600:
      case ACHIEVEMENT_KEYS.FOCUS_3000:
        return stats.totalFocusMinutes
      case ACHIEVEMENT_KEYS.LEVEL_5:
      case ACHIEVEMENT_KEYS.LEVEL_10:
        return level
      case ACHIEVEMENT_KEYS.EARLY_BIRD:
        return options.earlyBird ? 1 : 0
      case ACHIEVEMENT_KEYS.COACH_TALK:
        return options.coachTalk ? 1 : 0
      default:
        return -1
    }
  }

  const events: RewardEvent[] = []
  let bonusXp = 0
  let bonusCoins = 0

  const achievements = data.achievements.map((achievement) => {
    const computed = progressFor(achievement.key)
    if (computed < 0) return achievement
    const progress = Math.min(achievement.target, Math.max(achievement.progress, computed))
    const justUnlocked = achievement.unlockedAt == null && progress >= achievement.target
    if (justUnlocked) {
      bonusXp += achievement.rewardXp
      bonusCoins += achievement.rewardCoins
      events.push({ kind: 'unlocked', title: achievement.title, emoji: achievement.emoji })
      return { ...achievement, progress, unlockedAt: Date.now() }
    }
    return progress === achievement.progress ? achievement : { ...achievement, progress }
  })

  return {
    data: {
      ...data,
      achievements,
      stats: { ...data.stats, xp: data.stats.xp + bonusXp, coins: data.stats.coins + bonusCoins },
    },
    events,
  }
}

// ---- reward shop ---------------------------------------------------------

export type RedeemResult =
  | { ok: true; data: KairoData; message: string }
  | { ok: false; message: string }

export function redeemReward(data: KairoData, rewardId: number): RedeemResult {
  const reward = data.rewards.find((item) => item.id === rewardId)
  if (!reward) return { ok: false, message: 'That reward is no longer available.' }
  if (data.stats.coins < reward.cost) {
    return { ok: false, message: `${reward.cost - data.stats.coins} more coins needed for ${reward.title}.` }
  }
  return {
    ok: true,
    message: `${reward.emoji} ${reward.title} redeemed — enjoy it, you earned it.`,
    data: {
      ...data,
      stats: { ...data.stats, coins: data.stats.coins - reward.cost },
      rewards: data.rewards.map((item) =>
        item.id === rewardId
          ? { ...item, timesRedeemed: item.timesRedeemed + 1, lastRedeemedAt: Date.now() }
          : item,
      ),
    },
  }
}

export function buyFreezeToken(data: KairoData): RedeemResult {
  if (data.stats.freezeTokens >= Economy.MAX_FREEZE_TOKENS) {
    return { ok: false, message: "You're already carrying the maximum freezes." }
  }
  if (data.stats.coins < Economy.FREEZE_TOKEN_COST) {
    return {
      ok: false,
      message: `${Economy.FREEZE_TOKEN_COST - data.stats.coins} more coins needed for a streak freeze.`,
    }
  }
  return {
    ok: true,
    message: '🧊 Streak freeze bought. It covers one missed day automatically.',
    data: {
      ...data,
      stats: {
        ...data.stats,
        coins: data.stats.coins - Economy.FREEZE_TOKEN_COST,
        freezeTokens: data.stats.freezeTokens + 1,
      },
    },
  }
}

export function addCustomReward(
  data: KairoData,
  reward: Omit<Reward, 'id' | 'isCustom' | 'timesRedeemed' | 'lastRedeemedAt'>,
): KairoData {
  const [id, advanced] = nextId(data)
  return {
    ...advanced,
    rewards: [
      ...advanced.rewards,
      {
        ...reward,
        id,
        cost: Math.min(5000, Math.max(10, reward.cost)),
        isCustom: true,
        timesRedeemed: 0,
        lastRedeemedAt: null,
      },
    ],
  }
}

export function deleteReward(data: KairoData, rewardId: number): KairoData {
  return { ...data, rewards: data.rewards.filter((item) => item.id !== rewardId) }
}

// ---- chat + recommendations ---------------------------------------------

export function appendChat(
  data: KairoData,
  message: Omit<KairoData['chat'][number], 'id' | 'createdAt'>,
): KairoData {
  const [id, advanced] = nextId(data)
  return { ...advanced, chat: [...advanced.chat, { ...message, id, createdAt: Date.now() }] }
}

export function clearChat(data: KairoData): KairoData {
  return { ...data, chat: [] }
}

export function addRecommendation(
  data: KairoData,
  recommendation: { title: string; body: string; kind: string },
): { data: KairoData; id: number } {
  const [id, advanced] = nextId(data)
  return {
    id,
    data: {
      ...advanced,
      recommendations: [
        ...advanced.recommendations,
        {
          id,
          title: recommendation.title.slice(0, 80),
          body: recommendation.body.slice(0, 400),
          kind: recommendation.kind,
          status: 'new',
          createdAt: Date.now(),
          respondedAt: null,
        },
      ],
    },
  }
}

export function setRecommendationStatus(
  data: KairoData,
  id: number,
  status: 'accepted' | 'dismissed',
): KairoData {
  return {
    ...data,
    recommendations: data.recommendations.map((item) =>
      item.id === id ? { ...item, status, respondedAt: Date.now() } : item,
    ),
  }
}

// ---- helpers -------------------------------------------------------------

function filterRecord<T>(record: Record<string, T>, keep: (value: T) => boolean): Record<string, T> {
  const result: Record<string, T> = {}
  for (const [key, value] of Object.entries(record)) {
    if (keep(value)) result[key] = value
  }
  return result
}

export function agendaCount(data: KairoData, epochDay: number): number {
  return agendaFor(data, epochDay).length
}
