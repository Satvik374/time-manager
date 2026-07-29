import { DEFAULT_SYSTEM_PROMPT } from '../ai/prompts'
import type { Achievement, KairoData, Reward } from './types'

export const TASK_PALETTE = [
  '#6C5CE7',
  '#0E9F8E',
  '#FFB020',
  '#FF6B81',
  '#3B82F6',
  '#22C55E',
  '#A855F7',
  '#F97316',
  '#EC4899',
  '#14B8A6',
  '#EF4444',
  '#64748B',
]

export const ACHIEVEMENT_KEYS = {
  TASKS_10: 'tasks_10',
  TASKS_50: 'tasks_50',
  TASKS_250: 'tasks_250',
  STREAK_3: 'streak_3',
  STREAK_7: 'streak_7',
  STREAK_30: 'streak_30',
  STREAK_100: 'streak_100',
  PERFECT_1: 'perfect_1',
  PERFECT_10: 'perfect_10',
  FOCUS_600: 'focus_600',
  FOCUS_3000: 'focus_3000',
  LEVEL_5: 'level_5',
  LEVEL_10: 'level_10',
  COACH_TALK: 'coach_talk',
  EARLY_BIRD: 'early_bird',
} as const

function achievement(
  key: string,
  title: string,
  description: string,
  emoji: string,
  target: number,
  rewardXp: number,
  rewardCoins: number,
  tier: number,
): Achievement {
  return {
    key,
    title,
    description,
    emoji,
    target,
    progress: 0,
    unlockedAt: null,
    rewardXp,
    rewardCoins,
    tier,
  }
}

export const ACHIEVEMENT_CATALOG: Achievement[] = [
  achievement(ACHIEVEMENT_KEYS.TASKS_10, 'First Ten', 'Complete 10 scheduled blocks', '✅', 10, 30, 20, 0),
  achievement(ACHIEVEMENT_KEYS.TASKS_50, 'Half Century', 'Complete 50 scheduled blocks', '🏅', 50, 90, 60, 1),
  achievement(ACHIEVEMENT_KEYS.TASKS_250, 'Two Fifty', 'Complete 250 scheduled blocks', '🏆', 250, 300, 200, 2),
  achievement(ACHIEVEMENT_KEYS.STREAK_3, 'Warming Up', 'Hold a 3-day streak', '🔥', 3, 25, 15, 0),
  achievement(ACHIEVEMENT_KEYS.STREAK_7, 'Week Solid', 'Hold a 7-day streak', '🔥', 7, 70, 50, 1),
  achievement(ACHIEVEMENT_KEYS.STREAK_30, 'Month Strong', 'Hold a 30-day streak', '💎', 30, 250, 180, 2),
  achievement(ACHIEVEMENT_KEYS.STREAK_100, 'Unbreakable', 'Hold a 100-day streak', '👑', 100, 800, 600, 3),
  achievement(ACHIEVEMENT_KEYS.PERFECT_1, 'Clean Sweep', 'Finish every block in a day', '🌟', 1, 40, 25, 0),
  achievement(ACHIEVEMENT_KEYS.PERFECT_10, 'Ten Clean Days', 'Finish every block on 10 days', '✨', 10, 200, 150, 2),
  achievement(ACHIEVEMENT_KEYS.FOCUS_600, 'Ten Hours Deep', 'Log 600 focused minutes', '⏳', 600, 80, 50, 1),
  achievement(ACHIEVEMENT_KEYS.FOCUS_3000, 'Fifty Hours Deep', 'Log 3000 focused minutes', '🧠', 3000, 350, 250, 3),
  achievement(ACHIEVEMENT_KEYS.LEVEL_5, 'Level Five', 'Reach level 5', '⭐', 5, 0, 60, 1),
  achievement(ACHIEVEMENT_KEYS.LEVEL_10, 'Level Ten', 'Reach level 10', '🌠', 10, 0, 200, 2),
  achievement(ACHIEVEMENT_KEYS.COACH_TALK, 'Ask the Coach', 'Have your first conversation with the AI coach', '🤖', 1, 20, 15, 0),
  achievement(ACHIEVEMENT_KEYS.EARLY_BIRD, 'Early Bird', 'Complete a block that starts before 7:00 AM', '🌅', 1, 30, 20, 0),
]

export function defaultRewards(startId: number): Reward[] {
  const seed = [
    { title: 'Coffee break', description: 'A proper unhurried one', emoji: '☕', cost: 40 },
    { title: 'One episode', description: 'Guilt-free screen time', emoji: '📺', cost: 90 },
    { title: 'Gaming hour', description: '60 minutes, no timer anxiety', emoji: '🎮', cost: 150 },
    { title: 'Takeout night', description: 'Someone else cooks', emoji: '🍜', cost: 260 },
    { title: 'Guilt-free lie-in', description: 'Skip the alarm tomorrow', emoji: '😴', cost: 320 },
    { title: 'Day off', description: 'A full planned rest day', emoji: '🏝️', cost: 600 },
  ]
  return seed.map((item, index) => ({
    id: startId + index,
    ...item,
    isCustom: false,
    timesRedeemed: 0,
    lastRedeemedAt: null,
  }))
}

export const DATA_VERSION = 1

export function createEmptyData(): KairoData {
  const rewards = defaultRewards(1)
  return {
    version: DATA_VERSION,
    tasks: [],
    checklistItems: [],
    taskLogs: {},
    checklistLogs: {},
    stats: {
      xp: 0,
      coins: 0,
      currentStreak: 0,
      longestStreak: 0,
      lastStreakDay: -1,
      freezeTokens: 1,
      lastRolloverDay: -1,
      totalTasksCompleted: 0,
      totalFocusMinutes: 0,
      perfectDays: 0,
    },
    achievements: ACHIEVEMENT_CATALOG.map((item) => ({ ...item })),
    rewards,
    chat: [],
    recommendations: [],
    ai: {
      enabled: true,
      baseUrl: '',
      apiKey: '',
      model: '',
      format: 'openai',
      systemPrompt: DEFAULT_SYSTEM_PROMPT,
      temperature: 0.6,
      maxTokens: 1200,
      toolsEnabled: true,
      enabledTools: [],
      historyTurns: 24,
      recommendationsEnabled: true,
      recommendationIntervalHours: 12,
    },
    prefs: {
      use24HourClock: false,
      themeMode: 'system',
      dayStartMinute: 6 * 60,
      dayEndMinute: 23 * 60,
      lastRecommendationAt: 0,
    },
    auth: {
      profileName: '',
      hasProfile: false,
      pinDigest: null,
      lockEnabled: false,
      autoLockMinutes: 15,
      failedAttempts: 0,
      lockedUntil: 0,
    },
    nextId: rewards.length + 1,
  }
}
