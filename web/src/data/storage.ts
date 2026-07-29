/*
 * Persistence. localStorage is plenty for a timetable, and keeping it behind this one module
 * means swapping in IndexedDB — or a sync endpoint — is a single-file change later.
 */

import { DATA_VERSION, createEmptyData } from './defaults'
import type { KairoData } from './types'

const STORAGE_KEY = 'kairo.data.v1'

export function loadData(): KairoData {
  if (typeof localStorage === 'undefined') return createEmptyData()
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return createEmptyData()
  try {
    const parsed = JSON.parse(raw) as Partial<KairoData>
    return migrate(parsed)
  } catch {
    // A corrupt blob shouldn't brick the app; start clean rather than crashing on boot.
    return createEmptyData()
  }
}

export function saveData(data: KairoData): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
  } catch {
    // Quota exceeded or private mode — the session keeps working, it just won't persist.
  }
}

export function clearData(): void {
  if (typeof localStorage === 'undefined') return
  localStorage.removeItem(STORAGE_KEY)
}

/** Fills in anything a newer build added, so an older saved document still loads. */
function migrate(parsed: Partial<KairoData>): KairoData {
  const empty = createEmptyData()
  const merged: KairoData = {
    ...empty,
    ...parsed,
    version: DATA_VERSION,
    stats: { ...empty.stats, ...(parsed.stats ?? {}) },
    ai: { ...empty.ai, ...(parsed.ai ?? {}) },
    prefs: { ...empty.prefs, ...(parsed.prefs ?? {}) },
    auth: { ...empty.auth, ...(parsed.auth ?? {}) },
    tasks: parsed.tasks ?? [],
    checklistItems: parsed.checklistItems ?? [],
    taskLogs: parsed.taskLogs ?? {},
    checklistLogs: parsed.checklistLogs ?? {},
    rewards: parsed.rewards ?? empty.rewards,
    chat: parsed.chat ?? [],
    recommendations: parsed.recommendations ?? [],
    nextId: parsed.nextId ?? empty.nextId,
  }

  // Achievements are a catalogue, not user data: keep progress but adopt new definitions.
  const savedByKey = new Map((parsed.achievements ?? []).map((item) => [item.key, item]))
  merged.achievements = empty.achievements.map((definition) => {
    const saved = savedByKey.get(definition.key)
    return saved
      ? { ...definition, progress: saved.progress ?? 0, unlockedAt: saved.unlockedAt ?? null }
      : definition
  })

  // Guard against an id counter that fell behind the rows it is meant to be ahead of.
  const highestId = Math.max(
    0,
    ...merged.tasks.map((task) => task.id),
    ...merged.checklistItems.map((item) => item.id),
    ...merged.rewards.map((reward) => reward.id),
    ...merged.chat.map((message) => message.id),
    ...merged.recommendations.map((item) => item.id),
  )
  merged.nextId = Math.max(merged.nextId, highestId + 1)

  return merged
}

export function exportData(data: KairoData): string {
  // The API key is a credential, not schedule data — it never travels in an export file.
  const { ai, ...rest } = data
  return JSON.stringify({ ...rest, ai: { ...ai, apiKey: '' } }, null, 2)
}
