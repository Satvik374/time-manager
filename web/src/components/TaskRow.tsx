import { useState } from 'react'
import { formatRange, nowMinuteOfDay, todayEpochDay } from '../core/time'
import type { ScheduledTask } from '../data/types'
import { Priority } from '../data/types'
import { IconCheck, IconChevronDown } from './Icons'

/**
 * One block on the agenda: a colour spine, the time, a tick target, and an expandable checklist.
 * The checklist is the point of the app, so it gets a real progress affordance rather than a count.
 */
export function TaskRow({
  entry,
  use24h,
  onToggle,
  onToggleChecklistItem,
  onOpen,
}: {
  entry: ScheduledTask
  use24h: boolean
  onToggle: () => void
  onToggleChecklistItem: (itemId: number, checked: boolean) => void
  onOpen: () => void
}) {
  const [expanded, setExpanded] = useState(false)
  const task = entry.task
  const minute = nowMinuteOfDay()
  const isToday = entry.epochDay === todayEpochDay()
  const endMinute = task.startMinute + task.durationMinutes
  const isNow = isToday && minute >= task.startMinute && minute < endMinute
  const overdue =
    !entry.completed &&
    !entry.skipped &&
    (entry.epochDay < todayEpochDay() || (isToday && minute >= endMinute))

  const done = entry.checklist.filter((line) => line.checked).length
  const total = entry.checklist.length

  return (
    <article
      className={`task-row${entry.completed ? ' done' : ''}${isNow ? ' now' : ''}`}
      data-testid="task-row"
    >
      <span className="task-spine" style={{ background: task.colorHex }} />
      <div className="task-body">
        <button type="button" className="task-open" onClick={onOpen} title="Edit this block">
          <span className="task-title">{task.title}</span>
          {task.priority === Priority.HIGH && !entry.completed ? (
            <span className="priority-star" title="High priority">
              ★
            </span>
          ) : null}
        </button>
        <div className="task-meta">
          <span className={isNow ? 'live' : overdue ? 'overdue' : undefined}>
            {formatRange(task.startMinute, task.durationMinutes, use24h)}
          </span>
          <span aria-hidden="true">·</span>
          <span>{task.category}</span>
          {isNow ? (
            <>
              <span className="dot pulse" style={{ background: 'var(--accent)' }} />
              <span className="live">now</span>
            </>
          ) : null}
          {entry.skipped && !entry.completed ? <span className="muted">· skipped</span> : null}
        </div>

        {total > 0 ? (
          <>
            <button
              type="button"
              className="checklist-toggle"
              onClick={() => setExpanded((value) => !value)}
              aria-expanded={expanded}
            >
              <span className="mini-bar">
                <span style={{ width: `${(done / total) * 100}%`, background: task.colorHex }} />
              </span>
              {done}/{total} steps
              <IconChevronDown
                size={14}
                className={expanded ? 'rotate' : undefined}
              />
            </button>
            {expanded ? (
              <div className="checklist">
                {entry.checklist.map((line) => (
                  <button
                    key={line.id}
                    type="button"
                    className={`checklist-item${line.checked ? ' checked' : ''}`}
                    onClick={() => onToggleChecklistItem(line.id, line.checked)}
                  >
                    <span
                      className={`checkbox${line.checked ? ' checked' : ''}`}
                      style={line.checked ? { background: task.colorHex, borderColor: task.colorHex } : undefined}
                    >
                      <IconCheck size={11} />
                    </span>
                    {line.text}
                  </button>
                ))}
              </div>
            ) : null}
          </>
        ) : null}
      </div>

      <button
        type="button"
        className={`tick${entry.completed ? ' checked' : ''}`}
        style={
          entry.completed ? { background: task.colorHex, borderColor: task.colorHex } : undefined
        }
        onClick={onToggle}
        aria-pressed={entry.completed}
        aria-label={entry.completed ? `Mark ${task.title} as not done` : `Mark ${task.title} as done`}
      >
        <IconCheck size={14} />
      </button>
    </article>
  )
}
