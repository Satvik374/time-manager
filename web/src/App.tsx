import { useEffect, useState } from 'react'
import { Leveling } from './core/rules'
import { todayEpochDay } from './core/time'
import {
  IconCoin,
  IconFire,
  IconInsights,
  IconCalendar,
  IconChevronLeft,
  IconChevronRight,
  IconSettings,
  IconSparkle,
  IconToday,
  IconTrophy,
} from './components/Icons'
import { LockScreen, SetupScreen } from './screens/Auth'
import { CoachScreen } from './screens/Coach'
import { InsightsScreen } from './screens/Insights'
import { PlannerScreen } from './screens/Planner'
import { RewardsScreen } from './screens/Rewards'
import { SettingsScreen } from './screens/Settings'
import { TaskEditorModal } from './screens/TaskEditor'
import { TodayScreen } from './screens/Today'
import { openRecommendations } from './data/selectors'
import { useAppState } from './state/AppState'

type Tab = 'today' | 'planner' | 'coach' | 'insights' | 'rewards' | 'settings'

const TABS: { id: Tab; label: string; icon: typeof IconToday; hint: string }[] = [
  { id: 'today', label: 'Today', icon: IconToday, hint: 'Your day, block by block' },
  { id: 'planner', label: 'Planner', icon: IconCalendar, hint: 'The whole week at a glance' },
  { id: 'coach', label: 'Coach', icon: IconSparkle, hint: 'Ask the AI to reshape your schedule' },
  { id: 'insights', label: 'Insights', icon: IconInsights, hint: 'Daily, weekly and monthly analysis' },
  { id: 'rewards', label: 'Rewards', icon: IconTrophy, hint: 'Streak, level and the coin shop' },
]

export function App() {
  const { data, locked, toasts, dismissToast } = useAppState()
  const [tab, setTab] = useState<Tab>('today')
  const [selectedDay, setSelectedDay] = useState(todayEpochDay())
  const [collapsed, setCollapsed] = useState(false)
  const [editor, setEditor] = useState<{ taskId: number | null; epochDay: number } | null>(null)

  // Number keys jump between tabs; a desktop app should be keyboard-navigable.
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      const target = event.target as HTMLElement | null
      if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return
      if (event.metaKey || event.ctrlKey || event.altKey) return
      const index = Number(event.key)
      if (index >= 1 && index <= TABS.length) setTab(TABS[index - 1].id)
      if (event.key === 'n' || event.key === 'N') {
        setEditor({ taskId: null, epochDay: selectedDay })
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [selectedDay])

  if (!data.auth.hasProfile) return <SetupScreen />
  if (locked && data.auth.lockEnabled) return <LockScreen />

  const level = Leveling.infoFor(data.stats.xp)
  const suggestions = openRecommendations(data).length

  return (
    <div className="app-shell">
      <nav className={`sidebar${collapsed ? ' collapsed' : ''}`} aria-label="Main">
        <div className="brand">
          <span className="brand-mark">
            <IconToday size={21} />
          </span>
          {!collapsed ? (
            <span className="brand-text">
              <span className="brand-name">Kairo</span>
              <span className="brand-tag">AI time manager</span>
            </span>
          ) : null}
        </div>

        <div className="nav">
          {TABS.map((item) => {
            const Icon = item.icon
            return (
              <button
                key={item.id}
                type="button"
                className={`nav-item${tab === item.id ? ' active' : ''}`}
                data-tab={item.id}
                onClick={() => setTab(item.id)}
                title={collapsed ? item.label : item.hint}
                aria-current={tab === item.id ? 'page' : undefined}
              >
                <span className="icon">
                  <Icon size={19} />
                </span>
                {!collapsed ? <span className="label">{item.label}</span> : null}
                {item.id === 'today' && suggestions > 0 && !collapsed ? (
                  <span className="nav-badge">{suggestions}</span>
                ) : null}
              </button>
            )
          })}
        </div>

        <div className="sidebar-footer">
          <div className="sidebar-stat" title={`${data.stats.currentStreak}-day streak`}>
            <IconFire size={16} />
            <span className="value">{data.stats.currentStreak}</span>
            {!collapsed ? <span className="label">day streak</span> : null}
          </div>
          <div className="sidebar-stat" title={`${data.stats.coins} coins`}>
            <IconCoin size={16} />
            <span className="value">{data.stats.coins}</span>
            {!collapsed ? <span className="label">coins</span> : null}
          </div>
          {!collapsed ? (
            <div className="sidebar-level">
              <div className="progress-row">
                <span className="label">
                  Lvl {level.level} · {level.title}
                </span>
              </div>
              <div className="bar">
                <span style={{ width: `${level.progress * 100}%` }} />
              </div>
            </div>
          ) : null}

          <button
            type="button"
            className={`nav-item${tab === 'settings' ? ' active' : ''}`}
            data-tab="settings"
            onClick={() => setTab('settings')}
          >
            <span className="icon">
              <IconSettings size={19} />
            </span>
            {!collapsed ? <span className="label">Settings</span> : null}
          </button>

          <button
            type="button"
            className="collapse-toggle"
            onClick={() => setCollapsed((value) => !value)}
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {collapsed ? <IconChevronRight size={16} /> : <IconChevronLeft size={16} />}
            {!collapsed ? <span>Collapse</span> : null}
          </button>
        </div>
      </nav>

      <main className="main">
        {tab === 'today' ? (
          <TodayScreen
            selectedDay={selectedDay}
            onSelectDay={setSelectedDay}
            onEditTask={(taskId) => setEditor({ taskId, epochDay: selectedDay })}
            onCreateTask={() => setEditor({ taskId: null, epochDay: selectedDay })}
            onOpenCoach={() => setTab('coach')}
          />
        ) : null}
        {tab === 'planner' ? (
          <PlannerScreen
            selectedDay={selectedDay}
            onSelectDay={setSelectedDay}
            onEditTask={(taskId) => setEditor({ taskId, epochDay: selectedDay })}
            onCreateTask={(epochDay) => setEditor({ taskId: null, epochDay })}
          />
        ) : null}
        {tab === 'coach' ? <CoachScreen onOpenSettings={() => setTab('settings')} /> : null}
        {tab === 'insights' ? (
          <InsightsScreen anchorDay={selectedDay} onAnchorChange={setSelectedDay} />
        ) : null}
        {tab === 'rewards' ? <RewardsScreen /> : null}
        {tab === 'settings' ? <SettingsScreen /> : null}
      </main>

      {editor ? (
        <TaskEditorModal
          taskId={editor.taskId}
          epochDay={editor.epochDay}
          onClose={() => setEditor(null)}
        />
      ) : null}

      <div className="toast-stack">
        {toasts.map((toast) => (
          <button
            key={toast.id}
            type="button"
            className={`toast ${toast.tone}`}
            onClick={() => dismissToast(toast.id)}
          >
            {toast.text}
          </button>
        ))}
      </div>
    </div>
  )
}
