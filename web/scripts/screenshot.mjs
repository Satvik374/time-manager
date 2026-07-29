/*
 * Drives the built app in a real browser and captures each screen.
 * Seeds a realistic week of data first, because empty states tell you nothing about layout.
 *
 *   node scripts/screenshot.mjs [outputDir]
 */

import { chromium } from 'playwright'
import { existsSync, mkdirSync } from 'node:fs'

const BASE = process.env.KAIRO_URL ?? 'http://127.0.0.1:4173'
const OUT = process.argv[2] ?? 'screenshots'
mkdirSync(OUT, { recursive: true })

const MS_PER_DAY = 86_400_000
const todayEpochDay = () => {
  const now = new Date()
  return Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / MS_PER_DAY)
}

function seed() {
  const today = todayEpochDay()
  const dow = (day) => {
    const d = new Date(day * 86_400_000)
    return ((d.getUTCDay() + 6) % 7) + 1
  }
  const bit = (day) => 1 << (dow(day) - 1)

  let nextId = 100
  const tasks = []
  const checklistItems = []

  const define = (t) => {
    const id = nextId++
    tasks.push({
      id,
      title: t.title,
      note: t.note ?? '',
      category: t.category,
      colorHex: t.color,
      priority: t.priority ?? 1,
      startMinute: t.start,
      durationMinutes: t.duration,
      recurrence: t.recurrence,
      daysMask: t.daysMask ?? 0,
      specificDate: t.recurrence === 0 ? t.date : null,
      startEpochDay: t.recurrence === 0 ? t.date : today - 40,
      endEpochDay: null,
      reminderMinutes: t.reminder ?? -1,
      archived: false,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    })
    for (const [index, step] of (t.checklist ?? []).entries()) {
      checklistItems.push({ id: nextId++, taskId: id, text: step, position: index })
    }
    return id
  }

  const WEEKDAYS = 0b0011111
  const MWF = 0b0010101

  define({
    title: 'Morning routine',
    category: 'Health',
    color: '#0E9F8E',
    start: 6 * 60 + 30,
    duration: 45,
    recurrence: 1,
    checklist: ['Stretch', 'Shower', 'Plan the day'],
  })
  define({
    title: 'Deep work',
    category: 'Work',
    color: '#6C5CE7',
    priority: 2,
    start: 9 * 60,
    duration: 120,
    recurrence: 2,
    daysMask: WEEKDAYS,
    reminder: 10,
    checklist: ['Close Slack', 'One task only', 'Note where I stopped'],
  })
  define({
    title: 'Standup',
    category: 'Work',
    color: '#3B82F6',
    start: 11 * 60 + 30,
    duration: 20,
    recurrence: 2,
    daysMask: WEEKDAYS,
  })
  define({
    title: 'Gym',
    category: 'Health',
    color: '#FFB020',
    start: 18 * 60,
    duration: 60,
    recurrence: 2,
    daysMask: MWF,
    checklist: ['Warm up', 'Main sets', 'Stretch'],
  })
  define({
    title: 'Chemistry revision',
    category: 'Study',
    color: '#FF6B81',
    priority: 2,
    start: 14 * 60,
    duration: 90,
    recurrence: 2,
    daysMask: 0b0101010,
    checklist: ['Read chapter 7', 'Drill 20 problems', 'Review mistakes'],
  })
  define({
    title: 'Inbox & admin',
    category: 'Work',
    color: '#64748B',
    priority: 0,
    start: 16 * 60 + 30,
    duration: 45,
    recurrence: 2,
    daysMask: WEEKDAYS,
  })
  define({
    title: 'Reading',
    category: 'Personal',
    color: '#A855F7',
    start: 21 * 60,
    duration: 40,
    recurrence: 1,
  })
  define({
    title: 'Dentist',
    category: 'Personal',
    color: '#EC4899',
    start: 15 * 60,
    duration: 60,
    recurrence: 0,
    date: today + 2,
  })

  // Backfill 40 days of plausible completion so the analytics have something to chew on.
  const taskLogs = {}
  const checklistLogs = {}
  let completedTotal = 0
  let focusMinutes = 0
  let perfectDays = 0

  const occurs = (task, day) => {
    if (day < task.startEpochDay) return false
    if (task.recurrence === 0) return task.specificDate === day
    if (task.recurrence === 1) return true
    return (task.daysMask & bit(day)) !== 0
  }

  for (let offset = 40; offset >= 0; offset--) {
    const day = today - offset
    const planned = tasks.filter((task) => occurs(task, day))
    if (planned.length === 0) continue
    // A gentle upward trend with realistic variance, and today only partly done.
    const base = 0.55 + (40 - offset) * 0.006
    const weekendPenalty = dow(day) >= 6 ? -0.18 : 0
    const noise = Math.sin(offset * 2.7) * 0.16
    let rate = Math.max(0, Math.min(1, base + weekendPenalty + noise))
    if (offset === 0) rate = 0.5
    let dayCompleted = 0
    for (const [index, task] of planned.entries()) {
      const done = index / planned.length < rate
      if (!done) continue
      dayCompleted++
      completedTotal++
      focusMinutes += task.durationMinutes
      taskLogs[`${task.id}:${day}`] = {
        taskId: task.id,
        epochDay: day,
        completed: true,
        skipped: false,
        completedAt: Date.now(),
        focusMinutes: task.durationMinutes,
      }
      for (const item of checklistItems.filter((i) => i.taskId === task.id)) {
        checklistLogs[`${item.id}:${day}`] = {
          itemId: item.id,
          taskId: task.id,
          epochDay: day,
          checked: true,
          checkedAt: Date.now(),
        }
      }
    }
    if (dayCompleted === planned.length) perfectDays++
  }

  return {
    tasks,
    checklistItems,
    taskLogs,
    checklistLogs,
    completedTotal,
    focusMinutes,
    perfectDays,
    nextId,
  }
}

const shots = [
  { name: '01-today', tab: 'today' },
  { name: '02-planner', tab: 'planner' },
  { name: '03-coach', tab: 'coach' },
  { name: '04-insights', tab: 'insights' },
  { name: '05-rewards', tab: 'rewards' },
  { name: '06-settings', tab: 'settings' },
]


// The sandbox ships a pinned Chromium; point at it rather than downloading another.
const executablePath = process.env.KAIRO_CHROME ?? '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
const browser = await chromium.launch(
  existsSync(executablePath) ? { executablePath } : {},
)
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  deviceScaleFactor: 2,
  colorScheme: 'light',
})
const page = await context.newPage()
page.on('pageerror', (error) => console.error('PAGE ERROR:', error.message))
page.on('console', (message) => {
  if (message.type() === 'error') console.error('CONSOLE ERROR:', message.text())
})

const seeded = seed()

async function openSeeded(target) {
  await target.goto(BASE)
  await target.evaluate(applySeed, seeded)
  await target.reload()
  await target.waitForSelector('.sidebar', { timeout: 15_000 })
}

function applySeed(data) {
  const raw = localStorage.getItem('kairo.data.v1')
  const base = raw ? JSON.parse(raw) : {}
  localStorage.setItem(
    'kairo.data.v1',
    JSON.stringify({
      ...base,
      version: 1,
      tasks: data.tasks,
      checklistItems: data.checklistItems,
      taskLogs: data.taskLogs,
      checklistLogs: data.checklistLogs,
      nextId: data.nextId,
      stats: {
        xp: 1840,
        coins: 265,
        currentStreak: 12,
        longestStreak: 19,
        lastStreakDay: Math.floor(Date.now() / 86400000) - 1,
        freezeTokens: 2,
        lastRolloverDay: -1,
        totalTasksCompleted: data.completedTotal,
        totalFocusMinutes: data.focusMinutes,
        perfectDays: data.perfectDays,
      },
      auth: {
        profileName: 'Satvik',
        hasProfile: true,
        pinDigest: null,
        lockEnabled: false,
        autoLockMinutes: 15,
        failedAttempts: 0,
        lockedUntil: 0,
      },
      chat: [
        {
          id: 1,
          role: 'user',
          content: 'My afternoons keep slipping. What should I change?',
          createdAt: Date.now() - 400000,
        },
        {
          id: 2,
          role: 'assistant',
          content: '',
          toolCalls: [
            { id: 'c1', name: 'get_analytics', argumentsJson: '{"period":"weekly"}' },
            { id: 'c2', name: 'get_schedule', argumentsJson: '{"days":7}' },
          ],
          createdAt: Date.now() - 380000,
        },
        {
          id: 3,
          role: 'tool',
          content: '{"ok":true}',
          toolCallId: 'c1',
          toolName: 'get_analytics',
          createdAt: Date.now() - 370000,
        },
        {
          id: 4,
          role: 'tool',
          content: '{"ok":true}',
          toolCallId: 'c2',
          toolName: 'get_schedule',
          createdAt: Date.now() - 369000,
        },
        {
          id: 5,
          role: 'assistant',
          content:
            "Your afternoons aren't the problem — **Chemistry revision** is. It finishes 41% of the time, while everything before `14:00` sits above 80%.\n\nTwo things stand out:\n\n1. It lands right after lunch, your weakest band (`12:00–16:00`).\n2. It's 90 minutes with no break, the longest block you own.\n\n| Block | Rate | Slot |\n|---|---|---|\n| Deep work | 86% | 09:00 |\n| Gym | 74% | 18:00 |\n| Chemistry revision | 41% | 14:00 |\n\nI'd move it to `09:00` on Tuesdays and Thursdays and cut it to 60 minutes. Want me to do that?",
          createdAt: Date.now() - 360000,
        },
      ],
      recommendations: [
        {
          id: 20,
          title: 'Thursdays are your weakest day',
          body: 'You finish **41%** on Thursdays against 78% on Mondays. Try moving `Chemistry revision` to the morning on Thursdays only.',
          kind: 'schedule',
          status: 'new',
          createdAt: Date.now() - 7200000,
          respondedAt: null,
        },
      ],
      ai: {
        ...(base.ai ?? {}),
        baseUrl: 'https://api.example-gateway.dev/v1',
        apiKey: 'sk-demo-key-not-real',
        model: 'your-model-id',
        format: 'openai',
        enabled: true,
        toolsEnabled: true,
        enabledTools: [],
        temperature: 0.6,
        maxTokens: 1200,
        historyTurns: 24,
        recommendationsEnabled: true,
        recommendationIntervalHours: 12,
      },
    }),
  )
}

await openSeeded(page)

for (const shot of shots) {
  await page.locator(`.nav-item[data-tab="${shot.tab}"]`).click()
  await page.waitForTimeout(900)
  await page.screenshot({ path: `${OUT}/${shot.name}.png` })
  console.log(`captured ${shot.name}`)
}

// Dark mode on the busiest screen, plus the editor and a narrow window.
await context.close()
const darkContext = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  deviceScaleFactor: 2,
  colorScheme: 'dark',
})
const darkPage = await darkContext.newPage()
darkPage.on('pageerror', (error) => console.error('PAGE ERROR (dark):', error.message))
await openSeeded(darkPage)
await darkPage.locator('.nav-item[data-tab="insights"]').click()
await darkPage.waitForTimeout(900)
await darkPage.screenshot({ path: `${OUT}/07-insights-dark.png` })
console.log('captured 07-insights-dark')

await darkPage.locator('.nav-item[data-tab="today"]').click()
await darkPage.waitForTimeout(500)
await darkPage.getByRole('button', { name: 'New block' }).first().click()
await darkPage.waitForTimeout(700)
await darkPage.screenshot({ path: `${OUT}/08-editor-dark.png` })
console.log('captured 08-editor-dark')

await darkContext.close()
const narrow = await browser.newContext({ viewport: { width: 1100, height: 900 }, deviceScaleFactor: 2 })
const narrowPage = await narrow.newPage()
await openSeeded(narrowPage)
await narrowPage.locator('.nav-item[data-tab="planner"]').click()
await narrowPage.waitForTimeout(900)
await narrowPage.screenshot({ path: `${OUT}/09-planner-narrow.png` })
console.log('captured 09-planner-narrow')

await browser.close()
console.log('done')
