import { useMemo, useState } from 'react'
import { DayMask, formatDuration, formatFullDate, formatIso, formatRange, formatTime, nowMinuteOfDay, parseIsoDate, parseTimeToMinute, todayEpochDay } from '../core/time'
import { IconPlus, IconTrash, IconWarning } from '../components/Icons'
import { Chip, Field, Modal } from '../components/Ui'
import { TASK_PALETTE } from '../data/defaults'
import { archiveTask, deleteTask, upsertTask } from '../data/mutations'
import { categories, checklistFor, conflictsFor, taskById } from '../data/selectors'
import type { Task } from '../data/types'
import { Priority, Recurrence, priorityLabel } from '../data/types'
import { useAppState } from '../state/AppState'

const DURATIONS = [15, 25, 30, 45, 60, 90, 120, 180]
const REMINDERS: { value: number; label: string }[] = [
  { value: -1, label: 'None' },
  { value: 0, label: 'At start' },
  { value: 5, label: '5m before' },
  { value: 15, label: '15m before' },
  { value: 30, label: '30m before' },
]

export function TaskEditorModal({
  taskId,
  epochDay,
  onClose,
}: {
  taskId: number | null
  epochDay: number
  onClose: () => void
}) {
  const { data, update, pushToast } = useAppState()
  const existing = taskId != null ? taskById(data, taskId) : undefined
  const knownCategories = categories(data)

  const [title, setTitle] = useState(existing?.title ?? '')
  const [note, setNote] = useState(existing?.note ?? '')
  const [category, setCategory] = useState(existing?.category ?? knownCategories[0] ?? 'General')
  const [colorHex, setColorHex] = useState(existing?.colorHex ?? TASK_PALETTE[0])
  const [priority, setPriority] = useState(existing?.priority ?? Priority.NORMAL)
  const [startMinute, setStartMinute] = useState(
    existing?.startMinute ??
      (epochDay === todayEpochDay()
        ? Math.min(23 * 60, (Math.floor(nowMinuteOfDay() / 30) + 1) * 30)
        : 9 * 60),
  )
  const [durationMinutes, setDurationMinutes] = useState(existing?.durationMinutes ?? 60)
  const [recurrence, setRecurrence] = useState(existing?.recurrence ?? Recurrence.ONCE)
  const [daysMask, setDaysMask] = useState(
    existing?.daysMask ?? 1 << ((new Date().getDay() + 6) % 7),
  )
  const [dateIso, setDateIso] = useState(formatIso(existing?.specificDate ?? epochDay))
  const [reminderMinutes, setReminderMinutes] = useState(existing?.reminderMinutes ?? -1)
  const [checklist, setChecklist] = useState<string[]>(
    existing ? checklistFor(data, existing.id).map((line) => line.text) : [],
  )
  const [newStep, setNewStep] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)

  const targetDay = parseIsoDate(dateIso) ?? epochDay
  const canSave =
    title.trim().length > 0 &&
    durationMinutes >= 5 &&
    durationMinutes <= 720 &&
    (recurrence !== Recurrence.WEEKLY || daysMask !== DayMask.NONE)

  const conflicts = useMemo(() => {
    if (!title.trim()) return []
    const probe: Task = {
      id: existing?.id ?? -1,
      title: title.trim(),
      note,
      category,
      colorHex,
      priority,
      startMinute,
      durationMinutes,
      recurrence,
      daysMask,
      specificDate: recurrence === Recurrence.ONCE ? targetDay : null,
      startEpochDay: targetDay,
      endEpochDay: null,
      reminderMinutes,
      archived: false,
      createdAt: 0,
      updatedAt: 0,
    }
    let probeDay = targetDay
    if (recurrence === Recurrence.WEEKLY) {
      for (let offset = 0; offset < 7; offset++) {
        const day = targetDay + offset
        if (DayMask.contains(daysMask, (new Date(day * 86_400_000).getUTCDay() + 6) % 7)) {
          probeDay = day
          break
        }
      }
    }
    return conflictsFor(data, probe, probeDay)
  }, [
    data,
    existing,
    title,
    note,
    category,
    colorHex,
    priority,
    startMinute,
    durationMinutes,
    recurrence,
    daysMask,
    targetDay,
    reminderMinutes,
  ])

  function save() {
    if (!canSave) return
    update((current) => {
      const result = upsertTask(
        current,
        {
          id: existing?.id,
          title,
          note,
          category,
          colorHex,
          priority,
          startMinute,
          durationMinutes,
          recurrence,
          daysMask: recurrence === Recurrence.WEEKLY ? daysMask : 0,
          specificDate: recurrence === Recurrence.ONCE ? targetDay : null,
          reminderMinutes,
        },
        checklist,
      )
      return result.data
    })
    pushToast(existing ? 'Block updated.' : 'Block added to your timetable.')
    onClose()
  }

  return (
    <Modal
      title={existing ? 'Edit block' : 'New block'}
      onClose={onClose}
      wide
      footer={
        <>
          {existing ? (
            <>
              <button
                type="button"
                className="btn subtle"
                onClick={() => {
                  update((current) => archiveTask(current, existing.id, true))
                  pushToast('Archived — past history is kept.')
                  onClose()
                }}
              >
                Archive
              </button>
              <button
                type="button"
                className="btn danger"
                onClick={() => setConfirmDelete(true)}
              >
                <IconTrash size={15} /> Delete
              </button>
            </>
          ) : null}
          <span className="spacer" />
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="btn primary" disabled={!canSave} onClick={save}>
            {existing ? 'Save changes' : 'Add to timetable'}
          </button>
        </>
      }
    >
      <Field label="What is this block?">
        <input
          className="input"
          value={title}
          autoFocus
          placeholder="Deep work, Gym, Chemistry revision…"
          onChange={(event) => setTitle(event.target.value)}
        />
      </Field>

      <div className="grid cols-2">
        <Field label="Starts">
          <input
            className="input"
            type="time"
            value={formatTime(startMinute, true)}
            onChange={(event) => {
              const parsed = parseTimeToMinute(event.target.value)
              if (parsed != null) setStartMinute(parsed)
            }}
          />
        </Field>
        <Field label={`Ends at ${formatTime((startMinute + durationMinutes) % (24 * 60))}`}>
          <div className="row wrap">
            {DURATIONS.map((minutes) => (
              <Chip
                key={minutes}
                label={formatDuration(minutes)}
                selected={durationMinutes === minutes}
                onClick={() => setDurationMinutes(minutes)}
              />
            ))}
          </div>
        </Field>
      </div>

      <Field label="Repeat">
        <div className="row wrap">
          <Chip
            label="Once"
            selected={recurrence === Recurrence.ONCE}
            onClick={() => setRecurrence(Recurrence.ONCE)}
          />
          <Chip
            label="Every day"
            selected={recurrence === Recurrence.DAILY}
            onClick={() => setRecurrence(Recurrence.DAILY)}
          />
          <Chip
            label="Certain days"
            selected={recurrence === Recurrence.WEEKLY}
            onClick={() => setRecurrence(Recurrence.WEEKLY)}
          />
        </div>
      </Field>

      {recurrence === Recurrence.ONCE ? (
        <Field label="On" hint={formatFullDate(targetDay)}>
          <input
            className="input"
            type="date"
            value={dateIso}
            onChange={(event) => setDateIso(event.target.value)}
          />
        </Field>
      ) : null}

      {recurrence === Recurrence.WEEKLY ? (
        <Field label="Days">
          <div className="day-picker">
            {DayMask.labels.map((label, index) => (
              <button
                key={index}
                type="button"
                className={`day-key${DayMask.contains(daysMask, index) ? ' on' : ''}`}
                onClick={() => setDaysMask(DayMask.toggle(daysMask, index))}
                aria-pressed={DayMask.contains(daysMask, index)}
                aria-label={DayMask.fullNames[index]}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="row wrap" style={{ marginTop: 'var(--space-2)' }}>
            <Chip
              label="Weekdays"
              selected={daysMask === DayMask.WEEKDAYS}
              onClick={() => setDaysMask(DayMask.WEEKDAYS)}
            />
            <Chip
              label="Weekends"
              selected={daysMask === DayMask.WEEKENDS}
              onClick={() => setDaysMask(DayMask.WEEKENDS)}
            />
          </div>
        </Field>
      ) : null}

      {conflicts.length > 0 ? (
        <div className="conflict-warning">
          <IconWarning size={17} />
          <div>
            <strong>Overlaps {conflicts.length} existing block(s)</strong>
            <div className="small">
              {conflicts
                .map(
                  (entry) =>
                    `${entry.task.title} (${formatRange(entry.task.startMinute, entry.task.durationMinutes)})`,
                )
                .join(', ')}
            </div>
          </div>
        </div>
      ) : null}

      <Field label="Checklist" hint="Break the block into steps you can tick off. The coach can add these too.">
        <div className="checklist-editor">
          {checklist.map((line, index) => (
            <div key={index} className="row">
              <input
                className="input"
                value={line}
                onChange={(event) =>
                  setChecklist((current) =>
                    current.map((item, itemIndex) =>
                      itemIndex === index ? event.target.value : item,
                    ),
                  )
                }
              />
              <button
                type="button"
                className="btn subtle icon-only"
                aria-label="Remove step"
                onClick={() =>
                  setChecklist((current) => current.filter((_, itemIndex) => itemIndex !== index))
                }
              >
                <IconTrash size={15} />
              </button>
            </div>
          ))}
          <div className="row">
            <input
              className="input"
              value={newStep}
              placeholder="Add a step…"
              onChange={(event) => setNewStep(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && newStep.trim()) {
                  event.preventDefault()
                  setChecklist((current) => [...current, newStep.trim()])
                  setNewStep('')
                }
              }}
            />
            <button
              type="button"
              className="btn ghost icon-only"
              disabled={!newStep.trim()}
              aria-label="Add step"
              onClick={() => {
                setChecklist((current) => [...current, newStep.trim()])
                setNewStep('')
              }}
            >
              <IconPlus size={16} />
            </button>
          </div>
        </div>
      </Field>

      <div className="grid cols-2">
        <Field label="Category">
          <input
            className="input"
            value={category}
            onChange={(event) => setCategory(event.target.value)}
            list="kairo-categories"
          />
          <datalist id="kairo-categories">
            {knownCategories.map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
        </Field>
        <Field label="Priority">
          <div className="row wrap">
            {[Priority.LOW, Priority.NORMAL, Priority.HIGH].map((value) => (
              <Chip
                key={value}
                label={priorityLabel(value)}
                selected={priority === value}
                onClick={() => setPriority(value)}
              />
            ))}
          </div>
        </Field>
      </div>

      <Field label="Colour">
        <div className="row wrap">
          {TASK_PALETTE.map((hex) => (
            <button
              key={hex}
              type="button"
              className={`swatch${colorHex.toLowerCase() === hex.toLowerCase() ? ' selected' : ''}`}
              style={{ background: hex }}
              onClick={() => setColorHex(hex)}
              aria-label={`Colour ${hex}`}
            />
          ))}
        </div>
      </Field>

      <Field label="Reminder" hint="The web app can't send notifications; this syncs the setting for the Android app.">
        <div className="row wrap">
          {REMINDERS.map((option) => (
            <Chip
              key={option.value}
              label={option.label}
              selected={reminderMinutes === option.value}
              onClick={() => setReminderMinutes(option.value)}
            />
          ))}
        </div>
      </Field>

      <Field label="Notes (optional)">
        <textarea
          className="textarea"
          value={note}
          onChange={(event) => setNote(event.target.value)}
        />
      </Field>

      {confirmDelete && existing ? (
        <Modal
          title="Delete this block?"
          onClose={() => setConfirmDelete(false)}
          footer={
            <>
              <button type="button" className="btn ghost" onClick={() => setConfirmDelete(false)}>
                Cancel
              </button>
              <button
                type="button"
                className="btn danger"
                onClick={() => {
                  update((current) => deleteTask(current, existing.id))
                  pushToast('Block deleted.')
                  setConfirmDelete(false)
                  onClose()
                }}
              >
                Delete permanently
              </button>
            </>
          }
        >
          <p>
            This removes the block and every completion it recorded. Archiving keeps the history and
            just takes it off your schedule.
          </p>
        </Modal>
      ) : null}
    </Modal>
  )
}
