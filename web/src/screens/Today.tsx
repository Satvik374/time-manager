import { useState } from 'react'
import { maybeGenerateRecommendation } from '../ai/engine'
import { Leveling, dayGrade, dayGradeRate } from '../core/rules'
import {
  formatDuration,
  formatFullDate,
  formatDayLabel,
  greeting,
  nowMinuteOfDay,
  todayEpochDay,
} from '../core/time'
import { ProgressRing, gradeColor } from '../components/Charts'
import {
  IconCalendar,
  IconChevronLeft,
  IconChevronRight,
  IconClose,
  IconCoin,
  IconFire,
  IconPlus,
  IconSparkle,
} from '../components/Icons'
import { Markdown } from '../components/Markdown'
import { TaskRow } from '../components/TaskRow'
import { Card, EmptyState, ProgressBar } from '../components/Ui'
import { setChecklistChecked, setRecommendationStatus, setTaskCompleted } from '../data/mutations'
import { agendaFor, openRecommendations, snapshotFor } from '../data/selectors'
import { completionRate, isPerfect, remainingIn } from '../data/types'
import { useAppState } from '../state/AppState'

export function TodayScreen({
  selectedDay,
  onSelectDay,
  onEditTask,
  onCreateTask,
  onOpenCoach,
}: {
  selectedDay: number
  onSelectDay: (epochDay: number) => void
  onEditTask: (taskId: number) => void
  onCreateTask: () => void
  onOpenCoach: () => void
}) {
  const { data, update, updateWithEvents, pushToast } = useAppState()
  const [asking, setAsking] = useState(false)

  const agenda = agendaFor(data, selectedDay)
  const snapshot = snapshotFor(data, selectedDay)
  const rate = completionRate(snapshot)
  const level = Leveling.infoFor(data.stats.xp)
  const recommendations = openRecommendations(data)
  const isToday = selectedDay === todayEpochDay()
  const streakAtRisk = data.stats.currentStreak > 0 && data.stats.lastStreakDay < todayEpochDay()

  /*
   * The ring counts every block in the day, but the word beside it judges only the ones whose
   * time has passed. A day that still has an evening session ahead of it is not "Patchy" at
   * three in the afternoon.
   */
  const dueCount =
    selectedDay > todayEpochDay()
      ? 0
      : isToday
        ? agenda.filter(
            (entry) =>
              nowMinuteOfDay() >= entry.task.startMinute + entry.task.durationMinutes,
          ).length
        : agenda.length
  const dueCompleted = isToday
    ? agenda.filter(
        (entry) =>
          entry.completed &&
          nowMinuteOfDay() >= entry.task.startMinute + entry.task.durationMinutes,
      ).length
    : snapshot.completed
  const grade = dayGrade(dueCompleted, dueCount, snapshot.planned)
  const gradeRate = dayGradeRate(dueCompleted, dueCount, snapshot.planned)

  const upNext = isToday
    ? (agenda.find(
        (entry) =>
          nowMinuteOfDay() >= entry.task.startMinute &&
          nowMinuteOfDay() < entry.task.startMinute + entry.task.durationMinutes,
      ) ??
      agenda.find((entry) => !entry.completed && entry.task.startMinute >= nowMinuteOfDay()) ??
      agenda.find((entry) => !entry.completed))
    : agenda.find((entry) => !entry.completed)

  async function askCoach() {
    if (asking) return
    setAsking(true)
    try {
      const result = await maybeGenerateRecommendation(data, true)
      if (result.posted) {
        update(() => result.data)
        pushToast('New suggestion added above.', 'reward')
      } else {
        pushToast(
          "No suggestion this time — either the coach had nothing specific to add, or it isn't configured yet.",
        )
      }
    } finally {
      setAsking(false)
    }
  }

  return (
    <div className="page">
      <div className="hero">
        <div className="hero-row">
          <div>
            <div className="hero-eyebrow">
              {isToday
                ? `${greeting()}${data.auth.profileName ? `, ${data.auth.profileName}` : ''}`
                : formatDayLabel(selectedDay)}
            </div>
            <h1 className="hero-title">{formatFullDate(selectedDay)}</h1>
          </div>
          <div className="hero-pills">
            <span className={`hero-pill${streakAtRisk ? ' warn' : ''}`} title="Current streak">
              <IconFire size={16} />
              {data.stats.currentStreak} {data.stats.currentStreak === 1 ? 'day' : 'days'}
            </span>
            <span className="hero-pill" title="Coins earned">
              <IconCoin size={16} />
              {data.stats.coins}
            </span>
            <div className="day-stepper">
              <button
                type="button"
                className="hero-pill"
                onClick={() => onSelectDay(selectedDay - 1)}
                aria-label="Previous day"
              >
                <IconChevronLeft size={16} />
              </button>
              {!isToday ? (
                <button
                  type="button"
                  className="hero-pill"
                  onClick={() => onSelectDay(todayEpochDay())}
                >
                  Today
                </button>
              ) : null}
              <button
                type="button"
                className="hero-pill"
                onClick={() => onSelectDay(selectedDay + 1)}
                aria-label="Next day"
              >
                <IconChevronRight size={16} />
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="split" style={{ marginTop: 'var(--space-5)' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' }}>
          {recommendations.length > 0 ? (
            <div>
              <h2 className="section-title">From your coach</h2>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
                {recommendations.map((recommendation) => (
                  <div key={recommendation.id} className="card tinted suggestion">
                    <div className="suggestion-icon">
                      <IconSparkle size={17} />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <h3 className="card-title">{recommendation.title}</h3>
                      <Markdown text={recommendation.body} className="small-md" />
                      <div className="row" style={{ marginTop: 'var(--space-3)' }}>
                        <button
                          type="button"
                          className="btn primary"
                          onClick={() => {
                            update((current) =>
                              setRecommendationStatus(current, recommendation.id, 'accepted'),
                            )
                            onOpenCoach()
                          }}
                        >
                          Do it
                        </button>
                        <button
                          type="button"
                          className="btn ghost"
                          onClick={() =>
                            update((current) =>
                              setRecommendationStatus(current, recommendation.id, 'accepted'),
                            )
                          }
                        >
                          Keep for later
                        </button>
                      </div>
                    </div>
                    <button
                      type="button"
                      className="btn subtle icon-only"
                      aria-label="Dismiss suggestion"
                      onClick={() =>
                        update((current) =>
                          setRecommendationStatus(current, recommendation.id, 'dismissed'),
                        )
                      }
                    >
                      <IconClose size={16} />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          <div>
            <div className="row" style={{ marginBottom: 'var(--space-3)' }}>
              <h2 className="section-title" style={{ margin: 0 }}>
                {isToday ? "Today's blocks" : 'Blocks'}
              </h2>
              <span className="spacer" />
              <button type="button" className="btn ghost" onClick={onCreateTask}>
                <IconPlus size={16} /> New block
              </button>
            </div>

            {agenda.length === 0 ? (
              <Card>
                <EmptyState
                  icon={<IconCalendar size={26} />}
                  title="Nothing scheduled"
                  body="Add a block yourself, or ask the coach to build the day for you."
                  actionLabel="Add a block"
                  onAction={onCreateTask}
                />
              </Card>
            ) : (
              <div className="task-list">
                {agenda.map((entry) => (
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
              </div>
            )}
          </div>
        </div>

        <aside style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Card>
            <div className="ring-row">
              <ProgressRing progress={rate} size={124} color={gradeColor(gradeRate)}>
                <strong style={{ fontSize: 20, fontWeight: 700 }}>
                  {snapshot.completed}/{snapshot.planned}
                </strong>
                <span className="muted small">blocks</span>
              </ProgressRing>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 16, fontWeight: 650 }}>{grade}</div>
                <p className="muted small" style={{ marginTop: 2 }}>
                  {snapshot.planned === 0
                    ? 'No blocks scheduled — a clean slate.'
                    : isPerfect(snapshot)
                      ? "Every block done. That's a perfect day."
                      : remainingIn(snapshot) === 1
                        ? 'One block left.'
                        : `${remainingIn(snapshot)} blocks left · ${formatDuration(snapshot.completedMinutes)} focused`}
                </p>
              </div>
            </div>

            <div style={{ marginTop: 'var(--space-4)', display: 'grid', gap: 'var(--space-3)' }}>
              <ProgressBar
                label={`Level ${level.level} · ${level.title}`}
                trailing={`${level.xpIntoLevel}/${level.xpForNextLevel} XP`}
                progress={level.progress}
              />
              {snapshot.checklistTotal > 0 ? (
                <ProgressBar
                  label="Checklist steps"
                  trailing={`${snapshot.checklistDone}/${snapshot.checklistTotal}`}
                  progress={snapshot.checklistDone / snapshot.checklistTotal}
                  color="var(--secondary)"
                />
              ) : null}
            </div>
          </Card>

          {upNext ? (
            <Card title={isToday ? 'Up next' : 'First unfinished'}>
              <div className="up-next">
                <span className="task-spine" style={{ background: upNext.task.colorHex }} />
                <div>
                  <div style={{ fontWeight: 650 }}>{upNext.task.title}</div>
                  <div className="muted small">
                    {formatDuration(upNext.task.durationMinutes)} · {upNext.task.category}
                  </div>
                </div>
              </div>
            </Card>
          ) : null}

          <Card title="Want a second opinion?">
            <p className="muted small">
              The coach reads your real numbers and suggests one small change. Entirely optional.
            </p>
            <div className="row" style={{ marginTop: 'var(--space-3)' }}>
              <button type="button" className="btn primary" onClick={askCoach} disabled={asking}>
                {asking ? <span className="spinner" /> : <IconSparkle size={16} />}
                {asking ? 'Thinking…' : 'Ask for a suggestion'}
              </button>
              <button type="button" className="btn subtle" onClick={onOpenCoach}>
                Open Coach
              </button>
            </div>
          </Card>
        </aside>
      </div>
    </div>
  )
}
