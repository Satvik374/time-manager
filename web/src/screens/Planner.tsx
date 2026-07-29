import { useMemo, useState } from 'react'
import {
  DayMask,
  formatFullDate,
  formatMonth,
  formatRange,
  formatTime,
  fromEpochDay,
  nowMinuteOfDay,
  startOfWeek,
  todayEpochDay,
} from '../core/time'
import { IconCalendar, IconChevronLeft, IconChevronRight, IconList, IconPlus } from '../components/Icons'
import { TaskRow } from '../components/TaskRow'
import { Card, Chip, EmptyState, Segmented } from '../components/Ui'
import { setChecklistChecked, setTaskCompleted } from '../data/mutations'
import { agendaFor, categories, repeatLabel, snapshotFor } from '../data/selectors'
import { completionRate } from '../data/types'
import { useAppState } from '../state/AppState'

type PlannerView = 'week' | 'day' | 'list'

const HOUR_HEIGHT = 52

export function PlannerScreen({
  selectedDay,
  onSelectDay,
  onEditTask,
  onCreateTask,
}: {
  selectedDay: number
  onSelectDay: (epochDay: number) => void
  onEditTask: (taskId: number) => void
  onCreateTask: (epochDay: number) => void
}) {
  const { data, updateWithEvents } = useAppState()
  const [view, setView] = useState<PlannerView>('week')
  const [category, setCategory] = useState<string | null>(null)

  const weekStart = startOfWeek(selectedDay)
  const weekDays = useMemo(
    () => Array.from({ length: 7 }, (_, index) => weekStart + index),
    [weekStart],
  )
  const allCategories = categories(data)

  const matches = (name: string) => category == null || name === category

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Planner</h1>
          <p className="page-subtitle">
            {view === 'week'
              ? `${formatMonth(selectedDay)} · week of ${fromEpochDay(weekStart).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`
              : view === 'day'
                ? formatFullDate(selectedDay)
                : 'Every block you have defined'}
          </p>
        </div>
        <div className="page-actions">
          <Segmented
            value={view}
            onChange={setView}
            options={[
              { value: 'week', label: 'Week' },
              { value: 'day', label: 'Day' },
              { value: 'list', label: 'All blocks' },
            ]}
          />
          {view !== 'list' ? (
            <div className="row">
              <button
                type="button"
                className="btn ghost icon-only"
                aria-label="Previous"
                onClick={() => onSelectDay(selectedDay - (view === 'week' ? 7 : 1))}
              >
                <IconChevronLeft size={17} />
              </button>
              <button
                type="button"
                className="btn ghost"
                onClick={() => onSelectDay(todayEpochDay())}
              >
                Today
              </button>
              <button
                type="button"
                className="btn ghost icon-only"
                aria-label="Next"
                onClick={() => onSelectDay(selectedDay + (view === 'week' ? 7 : 1))}
              >
                <IconChevronRight size={17} />
              </button>
            </div>
          ) : null}
          <button type="button" className="btn primary" onClick={() => onCreateTask(selectedDay)}>
            <IconPlus size={16} /> New block
          </button>
        </div>
      </header>

      {allCategories.length > 1 ? (
        <div className="row wrap" style={{ marginBottom: 'var(--space-4)' }}>
          <Chip label="All" selected={category == null} onClick={() => setCategory(null)} />
          {allCategories.map((name) => (
            <Chip
              key={name}
              label={name}
              selected={category === name}
              onClick={() => setCategory(category === name ? null : name)}
            />
          ))}
        </div>
      ) : null}

      {view === 'week' ? (
        <WeekGrid
          days={weekDays}
          selectedDay={selectedDay}
          categoryFilter={category}
          onSelectDay={onSelectDay}
          onEditTask={onEditTask}
        />
      ) : null}

      {view === 'day' ? (
        <div className="split">
          <Card title={formatFullDate(selectedDay)}>
            <DayColumn
              epochDay={selectedDay}
              categoryFilter={category}
              onEditTask={onEditTask}
              tall
            />
          </Card>
          <div>
            <h2 className="section-title">Blocks on this day</h2>
            <div className="task-list">
              {agendaFor(data, selectedDay)
                .filter((entry) => matches(entry.task.category))
                .map((entry) => (
                  <TaskRow
                    key={entry.task.id}
                    entry={entry}
                    use24h={data.prefs.use24HourClock}
                    onOpen={() => onEditTask(entry.task.id)}
                    onToggle={() =>
                      updateWithEvents((current) =>
                        setTaskCompleted(current, entry.task.id, selectedDay, !entry.completed),
                      )
                    }
                    onToggleChecklistItem={(itemId, checked) =>
                      updateWithEvents((current) =>
                        setChecklistChecked(current, itemId, selectedDay, !checked),
                      )
                    }
                  />
                ))}
              {agendaFor(data, selectedDay).filter((entry) => matches(entry.task.category)).length ===
              0 ? (
                <Card>
                  <EmptyState
                    icon={<IconCalendar size={26} />}
                    title="This day is open"
                    body="Nothing is scheduled here yet."
                    actionLabel="Add a block"
                    onAction={() => onCreateTask(selectedDay)}
                  />
                </Card>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}

      {view === 'list' ? (
        <Card className="flush">
          {data.tasks.filter((task) => matches(task.category)).length === 0 ? (
            <EmptyState
              icon={<IconList size={26} />}
              title="No blocks yet"
              body="Blocks you create — one-off or recurring — all live here."
              actionLabel="Create your first"
              onAction={() => onCreateTask(selectedDay)}
            />
          ) : (
            <table className="data-table definitions">
              <thead>
                <tr>
                  <th>Block</th>
                  <th>Time</th>
                  <th>Repeats</th>
                  <th>Category</th>
                  <th className="num">Steps</th>
                </tr>
              </thead>
              <tbody>
                {data.tasks
                  .filter((task) => matches(task.category))
                  .slice()
                  .sort((a, b) => a.startMinute - b.startMinute || a.title.localeCompare(b.title))
                  .map((task) => (
                    <tr key={task.id} onClick={() => onEditTask(task.id)} className="clickable">
                      <td>
                        <span className="row">
                          <span className="dot" style={{ background: task.colorHex }} />
                          <strong>{task.title}</strong>
                          {task.archived ? <span className="chip">Archived</span> : null}
                        </span>
                      </td>
                      <td>{formatRange(task.startMinute, task.durationMinutes, data.prefs.use24HourClock)}</td>
                      <td>{repeatLabel(task)}</td>
                      <td>{task.category}</td>
                      <td className="num">
                        {data.checklistItems.filter((item) => item.taskId === task.id).length || '—'}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          )}
        </Card>
      ) : null}
    </div>
  )
}

/** The desktop payoff: a real seven-column week, with blocks drawn at their true position. */
function WeekGrid({
  days,
  selectedDay,
  categoryFilter,
  onSelectDay,
  onEditTask,
}: {
  days: number[]
  selectedDay: number
  categoryFilter: string | null
  onSelectDay: (epochDay: number) => void
  onEditTask: (taskId: number) => void
}) {
  const { data } = useAppState()
  const today = todayEpochDay()

  const entriesByDay = days.map((day) =>
    agendaFor(data, day).filter(
      (entry) => categoryFilter == null || entry.task.category === categoryFilter,
    ),
  )
  const all = entriesByDay.flat()

  const startHour = all.length === 0 ? 7 : Math.floor(Math.min(...all.map((e) => e.task.startMinute)) / 60)
  const endHour =
    all.length === 0
      ? 20
      : Math.min(
          24,
          Math.ceil(
            Math.max(...all.map((e) => e.task.startMinute + e.task.durationMinutes)) / 60,
          ),
        )
  const hours = Array.from({ length: Math.max(1, endHour - startHour) }, (_, i) => startHour + i)
  const gridHeight = hours.length * HOUR_HEIGHT

  return (
    <Card className="flush">
      <div className="week-head">
        <span className="week-gutter" />
        {days.map((day, index) => {
          const snapshot = snapshotFor(data, day)
          const rate = completionRate(snapshot)
          return (
            <button
              key={day}
              type="button"
              className={`week-day${day === selectedDay ? ' selected' : ''}${day === today ? ' today' : ''}`}
              onClick={() => onSelectDay(day)}
            >
              <span className="week-day-name">{DayMask.labels[index]}</span>
              <span className="week-day-num">{fromEpochDay(day).getDate()}</span>
              <span className="week-day-bar">
                <span
                  style={{
                    width: `${snapshot.planned === 0 ? 0 : Math.max(8, rate * 100)}%`,
                    background: snapshot.planned === 0 ? 'transparent' : 'var(--primary)',
                  }}
                />
              </span>
            </button>
          )
        })}
      </div>

      <div className="week-body" style={{ height: gridHeight }}>
        <div className="week-gutter hours">
          {hours.map((hour) => (
            <span key={hour} style={{ height: HOUR_HEIGHT }}>
              {formatTime(hour * 60, data.prefs.use24HourClock).replace(':00', '')}
            </span>
          ))}
        </div>
        {days.map((day, dayIndex) => (
          <div key={day} className={`week-col${day === today ? ' today' : ''}`}>
            {hours.map((hour) => (
              <div key={hour} className="hour-line" style={{ height: HOUR_HEIGHT }} />
            ))}
            {entriesByDay[dayIndex].map((entry) => {
              const top = ((entry.task.startMinute - startHour * 60) / 60) * HOUR_HEIGHT
              const height = Math.max(20, (entry.task.durationMinutes / 60) * HOUR_HEIGHT - 3)
              return (
                <button
                  key={entry.task.id}
                  type="button"
                  className={`week-block${entry.completed ? ' done' : ''}`}
                  style={{
                    top,
                    height,
                    background: entry.completed
                      ? 'color-mix(in srgb, ' + entry.task.colorHex + ' 22%, transparent)'
                      : entry.task.colorHex,
                    borderColor: entry.task.colorHex,
                  }}
                  onClick={() => onEditTask(entry.task.id)}
                  title={`${entry.task.title} · ${formatRange(entry.task.startMinute, entry.task.durationMinutes, data.prefs.use24HourClock)}`}
                >
                  <span className="week-block-title">{entry.task.title}</span>
                  {height > 38 ? (
                    <span className="week-block-time">
                      {formatTime(entry.task.startMinute, data.prefs.use24HourClock)}
                    </span>
                  ) : null}
                </button>
              )
            })}
            {day === today && nowMinuteOfDay() >= startHour * 60 && nowMinuteOfDay() <= endHour * 60 ? (
              <div
                className="now-line"
                style={{ top: ((nowMinuteOfDay() - startHour * 60) / 60) * HOUR_HEIGHT }}
              />
            ) : null}
          </div>
        ))}
      </div>

      {all.length === 0 ? (
        <div className="week-empty">
          Nothing scheduled this week — the grid shows a default 7am–8pm window.
        </div>
      ) : null}
    </Card>
  )
}

function DayColumn({
  epochDay,
  categoryFilter,
  onEditTask,
  tall,
}: {
  epochDay: number
  categoryFilter: string | null
  onEditTask: (taskId: number) => void
  tall?: boolean
}) {
  const { data } = useAppState()
  const entries = agendaFor(data, epochDay).filter(
    (entry) => categoryFilter == null || entry.task.category === categoryFilter,
  )
  if (entries.length === 0) {
    return <p className="muted small">No blocks scheduled.</p>
  }

  const startHour = Math.floor(Math.min(...entries.map((e) => e.task.startMinute)) / 60)
  const endHour = Math.min(
    24,
    Math.ceil(Math.max(...entries.map((e) => e.task.startMinute + e.task.durationMinutes)) / 60),
  )
  const rowHeight = tall ? 64 : 48
  const hours = Array.from({ length: Math.max(1, endHour - startHour) }, (_, i) => startHour + i)

  return (
    <div className="week-body single" style={{ height: hours.length * rowHeight }}>
      <div className="week-gutter hours">
        {hours.map((hour) => (
          <span key={hour} style={{ height: rowHeight }}>
            {formatTime(hour * 60, data.prefs.use24HourClock).replace(':00', '')}
          </span>
        ))}
      </div>
      <div className="week-col">
        {hours.map((hour) => (
          <div key={hour} className="hour-line" style={{ height: rowHeight }} />
        ))}
        {entries.map((entry) => (
          <button
            key={entry.task.id}
            type="button"
            className={`week-block${entry.completed ? ' done' : ''}`}
            style={{
              top: ((entry.task.startMinute - startHour * 60) / 60) * rowHeight,
              height: Math.max(24, (entry.task.durationMinutes / 60) * rowHeight - 3),
              background: entry.completed
                ? 'color-mix(in srgb, ' + entry.task.colorHex + ' 22%, transparent)'
                : entry.task.colorHex,
              borderColor: entry.task.colorHex,
            }}
            onClick={() => onEditTask(entry.task.id)}
          >
            <span className="week-block-title">{entry.task.title}</span>
            <span className="week-block-time">
              {formatRange(entry.task.startMinute, entry.task.durationMinutes, data.prefs.use24HourClock)}
            </span>
          </button>
        ))}
        {epochDay === todayEpochDay() ? (
          <div
            className="now-line"
            style={{ top: ((nowMinuteOfDay() - startHour * 60) / 60) * rowHeight }}
          />
        ) : null}
      </div>
    </div>
  )
}
