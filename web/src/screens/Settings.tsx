import { useState } from 'react'
import { describeAiError, endpointSuffix, isConfigured, testConnection } from '../ai/client'
import { PROMPT_PRESETS, DEFAULT_SYSTEM_PROMPT } from '../ai/prompts'
import { ALL_TOOLS, ALL_TOOL_NAMES } from '../ai/tools'
import { formatTime } from '../core/time'
import { IconEye, IconEyeOff, IconLock, IconTrash } from '../components/Icons'
import { Card, Chip, Field, Modal, Segmented, SwitchRow } from '../components/Ui'
import { hashPin, isValidPin, parseDigest, serializeDigest, verifyPin, PIN_MAX_LENGTH, PIN_MIN_LENGTH } from '../data/crypto'
import { exportData } from '../data/storage'
import { isToolEnabled } from '../data/selectors'
import type { AiConfig, ProviderFormat, ThemeMode } from '../data/types'
import { useAppState } from '../state/AppState'

const NO_TOOLS = '__none__'

export function SettingsScreen() {
  const { data, update, pushToast, setLocked } = useAppState()
  const [keyVisible, setKeyVisible] = useState(false)
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<{ ok: boolean; message: string } | null>(null)
  const [promptDraft, setPromptDraft] = useState(data.ai.systemPrompt)
  const [pinFlow, setPinFlow] = useState<'set' | 'change' | 'remove' | null>(null)

  const ai = data.ai
  const updateAi = (patch: Partial<AiConfig>) =>
    update((current) => ({ ...current, ai: { ...current.ai, ...patch } }))

  async function runTest() {
    setTesting(true)
    setTestResult(null)
    const result = await testConnection(data.ai)
    setTesting(false)
    setTestResult(
      result.ok
        ? { ok: true, message: `Connected. Provider replied: "${result.turn.text.slice(0, 160)}"` }
        : { ok: false, message: describeAiError(result.error) },
    )
  }

  return (
    <div className="page settings-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Settings</h1>
          <p className="page-subtitle">
            Everything lives in this browser. Nothing is uploaded except what you send the coach.
          </p>
        </div>
      </header>

      <div className="settings-grid">
        <Card title="AI provider" hint="Point Kairo at whatever endpoint you actually have.">
          <SwitchRow
            title="AI coach enabled"
            subtitle="Turn off to use Kairo as a plain planner."
            checked={ai.enabled}
            onChange={(enabled) => updateAi({ enabled })}
          />

          <div className="field-group">
            <Field label="Wire format">
              <div className="row wrap">
                {(
                  [
                    { value: 'openai', label: 'OpenAI-compatible' },
                    { value: 'anthropic', label: 'Anthropic Messages' },
                  ] as { value: ProviderFormat; label: string }[]
                ).map((option) => (
                  <Chip
                    key={option.value}
                    label={option.label}
                    selected={ai.format === option.value}
                    onClick={() => updateAi({ format: option.value })}
                  />
                ))}
              </div>
            </Field>

            <Field label="Base URL" hint={`Kairo appends /${endpointSuffix(ai.format)} automatically.`}>
              <input
                className="input"
                value={ai.baseUrl}
                placeholder="https://your-provider.example/v1"
                onChange={(event) => updateAi({ baseUrl: event.target.value })}
              />
            </Field>

            <Field
              label="API key"
              hint="Stored in this browser only. Leave blank for a local endpoint that needs no key."
            >
              <div className="input-with-action">
                <input
                  className="input"
                  type={keyVisible ? 'text' : 'password'}
                  value={ai.apiKey}
                  onChange={(event) => updateAi({ apiKey: event.target.value })}
                />
                <button
                  type="button"
                  className="btn subtle icon-only"
                  onClick={() => setKeyVisible((value) => !value)}
                  aria-label={keyVisible ? 'Hide key' : 'Show key'}
                >
                  {keyVisible ? <IconEyeOff size={17} /> : <IconEye size={17} />}
                </button>
              </div>
            </Field>

            <Field label="Model">
              <input
                className="input"
                value={ai.model}
                placeholder="the model id your provider expects"
                onChange={(event) => updateAi({ model: event.target.value })}
              />
            </Field>

            <SliderField
              label="Creativity"
              value={ai.temperature}
              min={0}
              max={1.5}
              step={0.05}
              display={ai.temperature.toFixed(2)}
              onChange={(temperature) => updateAi({ temperature })}
            />
            <SliderField
              label="Max reply length"
              value={ai.maxTokens}
              min={256}
              max={4096}
              step={64}
              display={`${ai.maxTokens} tokens`}
              onChange={(maxTokens) => updateAi({ maxTokens })}
            />
            <SliderField
              label="Conversation memory"
              value={ai.historyTurns}
              min={4}
              max={60}
              step={1}
              display={`${ai.historyTurns} turns`}
              onChange={(historyTurns) => updateAi({ historyTurns })}
            />
          </div>

          <div className="row" style={{ marginTop: 'var(--space-4)' }}>
            <button
              type="button"
              className="btn primary"
              disabled={!isConfigured(ai) || testing}
              onClick={runTest}
            >
              {testing ? <span className="spinner" /> : null}
              Test connection
            </button>
            {testResult ? (
              <span className={`test-result ${testResult.ok ? 'ok' : 'bad'}`}>
                {testResult.message}
              </span>
            ) : null}
          </div>

          <p className="muted small" style={{ marginTop: 'var(--space-3)' }}>
            Browser requests are subject to CORS. A provider that refuses cross-origin calls will
            fail here even with a valid key — use a gateway that allows them, or the Android app.
          </p>
        </Card>

        <Card
          title="System prompt"
          hint="The coach's brief. Today's date, your streak and a performance summary are appended automatically."
        >
          <div className="row wrap" style={{ marginBottom: 'var(--space-3)' }}>
            {PROMPT_PRESETS.map((preset) => (
              <Chip
                key={preset.name}
                label={preset.name}
                selected={ai.systemPrompt.trim() === preset.prompt.trim()}
                onClick={() => {
                  setPromptDraft(preset.prompt)
                  updateAi({ systemPrompt: preset.prompt })
                }}
              />
            ))}
          </div>
          <textarea
            className="textarea mono"
            style={{ minHeight: 240 }}
            value={promptDraft}
            onChange={(event) => setPromptDraft(event.target.value)}
          />
          <div className="row" style={{ marginTop: 'var(--space-3)' }}>
            <button
              type="button"
              className="btn primary"
              disabled={promptDraft === ai.systemPrompt}
              onClick={() => {
                updateAi({ systemPrompt: promptDraft })
                pushToast('System prompt saved.')
              }}
            >
              {promptDraft === ai.systemPrompt ? 'Saved' : 'Save prompt'}
            </button>
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                setPromptDraft(DEFAULT_SYSTEM_PROMPT)
                updateAi({ systemPrompt: DEFAULT_SYSTEM_PROMPT })
              }}
            >
              Reset to default
            </button>
          </div>
        </Card>

        <Card title="Tools the coach may use" hint="Exactly what the AI is allowed to do.">
          <SwitchRow
            title="Let the coach edit your timetable"
            subtitle="Off means it can only talk — it can't read or change anything."
            checked={ai.toolsEnabled}
            onChange={(toolsEnabled) => updateAi({ toolsEnabled })}
          />
          {ai.toolsEnabled ? (
            <>
              <div className="row" style={{ margin: 'var(--space-3) 0' }}>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={() => updateAi({ enabledTools: [...ALL_TOOL_NAMES] })}
                >
                  Enable all
                </button>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={() => updateAi({ enabledTools: [NO_TOOLS] })}
                >
                  Disable all
                </button>
              </div>
              {[false, true].map((mutating) => (
                <div key={String(mutating)} className="tool-group">
                  <h4 className="section-title">
                    {mutating ? 'Can change things' : 'Read only'}
                  </h4>
                  {ALL_TOOLS.filter((tool) => tool.mutating === mutating).map((tool) => {
                    const enabled = isToolEnabled(data, tool.name)
                    return (
                      <div key={tool.name} className="tool-row">
                        <span
                          className="dot"
                          style={{
                            background: enabled ? 'var(--secondary)' : 'var(--border-strong)',
                          }}
                        />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 14 }}>{tool.humanLabel}</div>
                          <code className="tool-name">{tool.name}</code>
                        </div>
                        <button
                          type="button"
                          role="switch"
                          aria-checked={enabled}
                          aria-label={tool.humanLabel}
                          className={`switch${enabled ? ' on' : ''}`}
                          onClick={() => {
                            // Empty means "all on", so materialise the set before removing one.
                            const current =
                              ai.enabledTools.length === 0 ? [...ALL_TOOL_NAMES] : ai.enabledTools
                            const next = current.includes(tool.name)
                              ? current.filter((name) => name !== tool.name)
                              : [...current, tool.name]
                            updateAi({ enabledTools: next.length === 0 ? [NO_TOOLS] : next })
                          }}
                        />
                      </div>
                    )
                  })}
                </div>
              ))}
            </>
          ) : null}
        </Card>

        <Card title="Recommendations">
          <SwitchRow
            title="Occasional suggestions"
            subtitle="The coach posts at most one optional card at a time. You accept or dismiss it."
            checked={ai.recommendationsEnabled}
            onChange={(recommendationsEnabled) => updateAi({ recommendationsEnabled })}
          />
          {ai.recommendationsEnabled ? (
            <div style={{ marginTop: 'var(--space-3)' }}>
              <SliderField
                label="At most one every"
                value={ai.recommendationIntervalHours}
                min={4}
                max={72}
                step={1}
                display={`${ai.recommendationIntervalHours}h`}
                onChange={(recommendationIntervalHours) => updateAi({ recommendationIntervalHours })}
              />
            </div>
          ) : null}
        </Card>

        <Card title="Appearance">
          <Field label="Theme">
            <Segmented
              value={data.prefs.themeMode}
              onChange={(themeMode: ThemeMode) =>
                update((current) => ({ ...current, prefs: { ...current.prefs, themeMode } }))
              }
              options={[
                { value: 'system', label: 'System' },
                { value: 'light', label: 'Light' },
                { value: 'dark', label: 'Dark' },
              ]}
            />
          </Field>
          <SwitchRow
            title="24-hour clock"
            subtitle={`Currently showing ${formatTime(13 * 60 + 30, data.prefs.use24HourClock)}.`}
            checked={data.prefs.use24HourClock}
            onChange={(use24HourClock) =>
              update((current) => ({ ...current, prefs: { ...current.prefs, use24HourClock } }))
            }
          />
        </Card>

        <Card title="Profile & app lock" hint="A lock on this browser — there is no account.">
          <Field label="Your name">
            <input
              className="input"
              value={data.auth.profileName}
              placeholder="What should the coach call you?"
              onChange={(event) =>
                update((current) => ({
                  ...current,
                  auth: { ...current.auth, profileName: event.target.value.slice(0, 40) },
                }))
              }
            />
          </Field>

          <div className="switch-row">
            <div>
              <div className="switch-title">App lock</div>
              <div className="switch-sub">
                {data.auth.lockEnabled
                  ? 'A PIN is required when Kairo opens'
                  : 'Off — Kairo opens straight to your day'}
              </div>
            </div>
            <div className="row">
              {data.auth.lockEnabled ? (
                <>
                  <button type="button" className="btn ghost" onClick={() => setLocked(true)}>
                    <IconLock size={15} /> Lock now
                  </button>
                  <button type="button" className="btn ghost" onClick={() => setPinFlow('change')}>
                    Change PIN
                  </button>
                  <button type="button" className="btn danger" onClick={() => setPinFlow('remove')}>
                    Remove
                  </button>
                </>
              ) : (
                <button type="button" className="btn primary" onClick={() => setPinFlow('set')}>
                  Set a PIN
                </button>
              )}
            </div>
          </div>

          {data.auth.lockEnabled ? (
            <Field label="Lock again after">
              <div className="row wrap">
                {[0, 1, 5, 15, 60].map((minutes) => (
                  <Chip
                    key={minutes}
                    label={minutes === 0 ? 'Instantly' : minutes === 60 ? '1 hour' : `${minutes} min`}
                    selected={data.auth.autoLockMinutes === minutes}
                    onClick={() =>
                      update((current) => ({
                        ...current,
                        auth: { ...current.auth, autoLockMinutes: minutes },
                      }))
                    }
                  />
                ))}
              </div>
            </Field>
          ) : null}

          <p className="muted small" style={{ marginTop: 'var(--space-3)' }}>
            This gates the interface on this device. It is not encryption — anything with access to
            the browser profile can still read the underlying storage. There is no account, so a
            forgotten PIN can only be cleared by wiping the data below.
          </p>
        </Card>

        <Card title="Your data" hint="Everything is in this browser's local storage.">
          <div className="row wrap">
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                const blob = new Blob([exportData(data)], { type: 'application/json' })
                const url = URL.createObjectURL(blob)
                const link = document.createElement('a')
                link.href = url
                link.download = `kairo-export-${new Date().toISOString().slice(0, 10)}.json`
                link.click()
                URL.revokeObjectURL(url)
                pushToast('Exported. Your API key is deliberately left out of the file.')
              }}
            >
              Export as JSON
            </button>
            <button
              type="button"
              className="btn danger"
              onClick={() => {
                if (!window.confirm('Erase every block, log, streak and conversation on this device?')) return
                localStorage.clear()
                window.location.reload()
              }}
            >
              <IconTrash size={15} /> Erase everything
            </button>
          </div>
          <p className="muted small" style={{ marginTop: 'var(--space-3)' }}>
            Cross-device sync is not built yet — this browser and the Android app keep separate
            copies of your schedule.
          </p>
        </Card>
      </div>

      {pinFlow ? <PinFlowModal flow={pinFlow} onClose={() => setPinFlow(null)} /> : null}
    </div>
  )
}

function SliderField({
  label,
  value,
  min,
  max,
  step,
  display,
  onChange,
}: {
  label: string
  value: number
  min: number
  max: number
  step: number
  display: string
  onChange: (value: number) => void
}) {
  return (
    <div className="field">
      <div className="progress-row">
        <span className="label">{label}</span>
        <span className="value">{display}</span>
      </div>
      <input
        className="slider"
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        aria-label={label}
      />
    </div>
  )
}

/** One modal covering set / change / remove — all three are the same keypad, different steps. */
function PinFlowModal({
  flow,
  onClose,
}: {
  flow: 'set' | 'change' | 'remove'
  onClose: () => void
}) {
  const { data, update, pushToast, setLocked } = useAppState()
  const [step, setStep] = useState<'current' | 'new' | 'confirm'>(
    flow === 'set' ? 'new' : 'current',
  )
  const [currentPin, setCurrentPin] = useState('')
  const [newPin, setNewPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const active = step === 'current' ? currentPin : step === 'new' ? newPin : confirmPin
  const setActive = (value: string) => {
    setError(null)
    if (step === 'current') setCurrentPin(value)
    else if (step === 'new') setNewPin(value)
    else setConfirmPin(value)
  }

  const title =
    flow === 'remove'
      ? 'Enter your PIN to remove the lock'
      : step === 'current'
        ? 'Enter your current PIN'
        : step === 'new'
          ? flow === 'set'
            ? 'Choose a PIN'
            : 'Choose a new PIN'
          : 'Confirm your PIN'

  async function verifyCurrent(): Promise<boolean> {
    const digest = parseDigest(data.auth.pinDigest)
    if (!digest) return true
    const valid = await verifyPin(currentPin, digest)
    if (!valid) setError('That PIN is wrong.')
    return valid
  }

  async function advance() {
    if (busy) return
    setBusy(true)
    try {
      if (step === 'current') {
        if (!(await verifyCurrent())) return
        if (flow === 'remove') {
          update((current) => ({
            ...current,
            auth: { ...current.auth, pinDigest: null, lockEnabled: false, failedAttempts: 0 },
          }))
          setLocked(false)
          pushToast('App lock removed.')
          onClose()
          return
        }
        setStep('new')
        return
      }
      if (step === 'new') {
        if (!isValidPin(newPin)) {
          setError(`Use ${PIN_MIN_LENGTH}–${PIN_MAX_LENGTH} digits.`)
          return
        }
        setStep('confirm')
        return
      }
      if (newPin !== confirmPin) {
        setConfirmPin('')
        setError("Those didn't match.")
        return
      }
      const digest = await hashPin(newPin)
      update((current) => ({
        ...current,
        auth: {
          ...current.auth,
          pinDigest: serializeDigest(digest),
          lockEnabled: true,
          failedAttempts: 0,
          lockedUntil: 0,
        },
      }))
      pushToast('App lock updated.')
      onClose()
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title={title}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={active.length < PIN_MIN_LENGTH || busy}
            onClick={() => void advance()}
          >
            {flow === 'remove' ? 'Remove lock' : step === 'confirm' ? 'Save' : 'Next'}
          </button>
        </>
      }
    >
      {flow === 'remove' ? (
        <p className="muted small">
          Kairo will open without asking for anything. Your data stays in this browser.
        </p>
      ) : null}
      <PinPad value={active} onChange={setActive} error={error} />
    </Modal>
  )
}

export function PinPad({
  value,
  onChange,
  error,
  onSubmit,
}: {
  value: string
  onChange: (value: string) => void
  error?: string | null
  onSubmit?: () => void
}) {
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫']
  return (
    <div className="pinpad">
      <div className="pin-dots">
        {Array.from({ length: Math.max(6, value.length) }, (_, index) => (
          <span
            key={index}
            className={`pin-dot${index < value.length ? ' filled' : ''}${error ? ' error' : ''}`}
          />
        ))}
      </div>
      <div className="pin-message">{error ?? ' '}</div>
      <div className="pin-keys">
        {keys.map((key, index) =>
          key === '' ? (
            <span key={index} />
          ) : (
            <button
              key={index}
              type="button"
              className="pin-key"
              onClick={() => {
                if (key === '⌫') onChange(value.slice(0, -1))
                else if (value.length < PIN_MAX_LENGTH) onChange(value + key)
              }}
            >
              {key}
            </button>
          ),
        )}
      </div>
      {onSubmit ? (
        <button
          type="button"
          className="btn primary"
          style={{ width: '100%', marginTop: 'var(--space-4)' }}
          disabled={value.length < PIN_MIN_LENGTH}
          onClick={onSubmit}
        >
          Unlock
        </button>
      ) : null}
    </div>
  )
}
