import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { refreshAchievements, refreshStreak, type RewardEvent } from '../data/mutations'
import { loadData, saveData } from '../data/storage'
import type { KairoData, ThemeMode } from '../data/types'

interface Toast {
  id: number
  text: string
  tone: 'info' | 'reward' | 'error'
}

interface AppStateValue {
  data: KairoData
  /** Applies a pure update and persists it. */
  update: (updater: (data: KairoData) => KairoData) => void
  /** Same, but surfaces any reward events returned alongside the new document. */
  updateWithEvents: (
    updater: (data: KairoData) => { data: KairoData; events: RewardEvent[] },
  ) => void
  toasts: Toast[]
  pushToast: (text: string, tone?: Toast['tone']) => void
  dismissToast: (id: number) => void
  locked: boolean
  setLocked: (locked: boolean) => void
  resolvedTheme: 'light' | 'dark'
}

const AppStateContext = createContext<AppStateValue | null>(null)

export function AppStateProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<KairoData>(() => {
    const loaded = loadData()
    // Settle any days that passed while the tab was closed, then bring achievement progress
    // up to date — it is derived from stats, so an imported or restored document can arrive
    // with counters that no longer match reality.
    return refreshAchievements(refreshStreak(loaded).data).data
  })
  const [toasts, setToasts] = useState<Toast[]>([])
  const [locked, setLocked] = useState<boolean>(() => loadData().auth.lockEnabled)
  const toastId = useRef(1)

  useEffect(() => {
    saveData(data)
  }, [data])

  const update = useCallback((updater: (data: KairoData) => KairoData) => {
    setData((current) => updater(current))
  }, [])

  const pushToast = useCallback((text: string, tone: Toast['tone'] = 'info') => {
    const id = toastId.current++
    setToasts((current) => [...current, { id, text, tone }])
    window.setTimeout(() => {
      setToasts((current) => current.filter((toast) => toast.id !== id))
    }, 4200)
  }, [])

  const dismissToast = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const updateWithEvents = useCallback(
    (updater: (data: KairoData) => { data: KairoData; events: RewardEvent[] }) => {
      setData((current) => {
        const result = updater(current)
        // Toasts are queued outside the state update so React never sees a side effect mid-render.
        queueMicrotask(() => {
          for (const event of result.events) {
            const message = describeRewardEvent(event)
            if (message) pushToast(message, 'reward')
          }
        })
        return result.data
      })
    },
    [pushToast],
  )

  const resolvedTheme = useResolvedTheme(data.prefs.themeMode)

  useEffect(() => {
    document.documentElement.dataset.theme = resolvedTheme
    document.documentElement.style.colorScheme = resolvedTheme
  }, [resolvedTheme])

  // Re-lock when the tab has been hidden longer than the user's grace period.
  const hiddenAt = useRef<number>(0)
  useEffect(() => {
    function onVisibilityChange() {
      if (document.visibilityState === 'hidden') {
        hiddenAt.current = Date.now()
        return
      }
      if (!data.auth.lockEnabled || locked || hiddenAt.current === 0) return
      const awayMinutes = (Date.now() - hiddenAt.current) / 60_000
      hiddenAt.current = 0
      if (awayMinutes >= data.auth.autoLockMinutes) setLocked(true)
    }
    document.addEventListener('visibilitychange', onVisibilityChange)
    return () => document.removeEventListener('visibilitychange', onVisibilityChange)
  }, [data.auth.lockEnabled, data.auth.autoLockMinutes, locked])

  const value = useMemo<AppStateValue>(
    () => ({
      data,
      update,
      updateWithEvents,
      toasts,
      pushToast,
      dismissToast,
      locked,
      setLocked,
      resolvedTheme,
    }),
    [data, update, updateWithEvents, toasts, pushToast, dismissToast, locked, resolvedTheme],
  )

  return <AppStateContext.Provider value={value}>{children}</AppStateContext.Provider>
}

export function useAppState(): AppStateValue {
  const value = useContext(AppStateContext)
  if (!value) throw new Error('useAppState must be used inside AppStateProvider')
  return value
}

function useResolvedTheme(mode: ThemeMode): 'light' | 'dark' {
  const [systemDark, setSystemDark] = useState(
    () => window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false,
  )
  useEffect(() => {
    const query = window.matchMedia?.('(prefers-color-scheme: dark)')
    if (!query) return
    const listener = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    query.addEventListener('change', listener)
    return () => query.removeEventListener('change', listener)
  }, [])
  if (mode === 'light') return 'light'
  if (mode === 'dark') return 'dark'
  return systemDark ? 'dark' : 'light'
}

function describeRewardEvent(event: RewardEvent): string | null {
  switch (event.kind) {
    case 'xp':
      return null // too frequent to interrupt for on its own
    case 'coins':
      return `+${event.amount} 🪙`
    case 'levelUp':
      return `⭐ Level ${event.level} — ${event.title}`
    case 'unlocked':
      return `${event.emoji} ${event.title} unlocked`
    case 'streak':
      return event.bonusCoins > 0
        ? `🔥 ${event.streak}-day streak · +${event.bonusCoins} 🪙`
        : `🔥 ${event.streak}-day streak`
    case 'frozen':
      return `🧊 Streak freeze used — ${event.tokensLeft} left`
    case 'streakLost':
      return `Streak reset from ${event.previous}. Fresh start today.`
    case 'perfectDay':
      return `🌟 Perfect day! +${event.xp} XP, +${event.coins} 🪙`
  }
}
