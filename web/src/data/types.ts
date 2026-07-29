/*
 * The web client's data model. Field names deliberately mirror the Room entities in the Android
 * app: when sync eventually exists, the two sides should be describing the same rows rather than
 * needing a translation layer.
 */

export const Recurrence = {
  ONCE: 0,
  DAILY: 1,
  WEEKLY: 2,
} as const

export const Priority = {
  LOW: 0,
  NORMAL: 1,
  HIGH: 2,
} as const

export function priorityLabel(value: number): string {
  if (value === Priority.LOW) return 'Low'
  if (value === Priority.HIGH) return 'High'
  return 'Normal'
}

export interface Task {
  id: number
  title: string
  note: string
  category: string
  colorHex: string
  priority: number
  startMinute: number
  durationMinutes: number
  recurrence: number
  /** Only meaningful when recurrence is WEEKLY. bit0 = Monday. */
  daysMask: number
  /** Only meaningful when recurrence is ONCE. */
  specificDate: number | null
  startEpochDay: number
  endEpochDay: number | null
  reminderMinutes: number
  archived: boolean
  createdAt: number
  updatedAt: number
}

export interface ChecklistItem {
  id: number
  taskId: number
  text: string
  position: number
}

/** Per-day completion state, keyed by `${taskId}:${epochDay}`. */
export interface TaskLog {
  taskId: number
  epochDay: number
  completed: boolean
  skipped: boolean
  completedAt: number | null
  focusMinutes: number
}

/** Per-day tick state of a checklist item, keyed by `${itemId}:${epochDay}`. */
export interface ChecklistLog {
  itemId: number
  taskId: number
  epochDay: number
  checked: boolean
  checkedAt: number | null
}

export interface UserStats {
  xp: number
  coins: number
  currentStreak: number
  longestStreak: number
  lastStreakDay: number
  freezeTokens: number
  lastRolloverDay: number
  totalTasksCompleted: number
  totalFocusMinutes: number
  perfectDays: number
}

export interface Achievement {
  key: string
  title: string
  description: string
  emoji: string
  target: number
  progress: number
  unlockedAt: number | null
  rewardXp: number
  rewardCoins: number
  tier: number
}

export interface Reward {
  id: number
  title: string
  description: string
  emoji: string
  cost: number
  isCustom: boolean
  timesRedeemed: number
  lastRedeemedAt: number | null
}

export type ChatRole = 'user' | 'assistant' | 'tool' | 'system'

export interface ToolCall {
  id: string
  name: string
  argumentsJson: string
}

export interface ChatMessage {
  id: number
  role: ChatRole
  content: string
  toolCalls?: ToolCall[]
  toolCallId?: string
  toolName?: string
  isError?: boolean
  createdAt: number
}

export type RecommendationStatus = 'new' | 'accepted' | 'dismissed'

export interface Recommendation {
  id: number
  title: string
  body: string
  kind: string
  status: RecommendationStatus
  createdAt: number
  respondedAt: number | null
}

export type ProviderFormat = 'openai' | 'anthropic'

export interface AiConfig {
  enabled: boolean
  baseUrl: string
  apiKey: string
  model: string
  format: ProviderFormat
  systemPrompt: string
  temperature: number
  maxTokens: number
  toolsEnabled: boolean
  /** Empty means every tool is on. */
  enabledTools: string[]
  historyTurns: number
  recommendationsEnabled: boolean
  recommendationIntervalHours: number
}

export type ThemeMode = 'system' | 'light' | 'dark'

export interface AppPrefs {
  use24HourClock: boolean
  themeMode: ThemeMode
  dayStartMinute: number
  dayEndMinute: number
  lastRecommendationAt: number
}

export interface AuthState {
  profileName: string
  hasProfile: boolean
  /** Serialized PBKDF2 digest — never the PIN itself. */
  pinDigest: string | null
  lockEnabled: boolean
  autoLockMinutes: number
  failedAttempts: number
  lockedUntil: number
}

/** The whole persisted document. Versioned so a future migration has something to switch on. */
export interface KairoData {
  version: number
  tasks: Task[]
  checklistItems: ChecklistItem[]
  taskLogs: Record<string, TaskLog>
  checklistLogs: Record<string, ChecklistLog>
  stats: UserStats
  achievements: Achievement[]
  rewards: Reward[]
  chat: ChatMessage[]
  recommendations: Recommendation[]
  ai: AiConfig
  prefs: AppPrefs
  auth: AuthState
  nextId: number
}

// ---- derived view models -------------------------------------------------

export interface ChecklistLine {
  id: number
  text: string
  checked: boolean
}

/** A task resolved onto a concrete day, with that day's completion and checklist state. */
export interface ScheduledTask {
  task: Task
  epochDay: number
  completed: boolean
  skipped: boolean
  focusMinutes: number
  checklist: ChecklistLine[]
}

export interface DaySnapshot {
  epochDay: number
  planned: number
  completed: number
  skipped: number
  plannedMinutes: number
  completedMinutes: number
  checklistDone: number
  checklistTotal: number
}

export interface CategorySlice {
  category: string
  colorHex: string
  completed: number
  minutes: number
}

export interface PeriodReport {
  label: string
  fromDay: number
  toDay: number
  /** Every day in the window, including ones still to come. */
  days: DaySnapshot[]
  /** Only the days that have actually happened — what every rate below is scored on. */
  elapsedDays: DaySnapshot[]
  categories: CategorySlice[]
  planned: number
  completed: number
  focusMinutes: number
  plannedMinutes: number
  completionRate: number
  perfectDays: number
  activeDays: number
  consistency: number
  bestDay: DaySnapshot | null
  worstDay: DaySnapshot | null
}

export type Period = 'daily' | 'weekly' | 'monthly'

export function taskEndMinute(task: Task): number {
  return task.startMinute + task.durationMinutes
}

export function completionRate(snapshot: DaySnapshot): number {
  return snapshot.planned === 0 ? 0 : snapshot.completed / snapshot.planned
}

export function isPerfect(snapshot: DaySnapshot): boolean {
  return snapshot.planned > 0 && snapshot.completed === snapshot.planned
}

export function remainingIn(snapshot: DaySnapshot): number {
  return Math.max(0, snapshot.planned - snapshot.completed - snapshot.skipped)
}
