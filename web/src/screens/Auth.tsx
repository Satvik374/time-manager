import { useState } from 'react'
import { attemptsLeft, lockoutMillisFor } from '../core/rules'
import { hashPin, isValidPin, parseDigest, serializeDigest, verifyPin, PIN_MAX_LENGTH, PIN_MIN_LENGTH } from '../data/crypto'
import { IconLock, IconToday } from '../components/Icons'
import { useAppState } from '../state/AppState'
import { PinPad } from './Settings'

/** First-run setup: a name, then an optional PIN. Skippable — this is a local app, not a service. */
export function SetupScreen() {
  const { data, update } = useAppState()
  const [step, setStep] = useState<'profile' | 'pin' | 'confirm'>('profile')
  const [name, setName] = useState(data.auth.profileName)
  const [pin, setPin] = useState('')
  const [confirmPin, setConfirmPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function finishWithoutPin() {
    update((current) => ({
      ...current,
      auth: { ...current.auth, profileName: name.trim(), hasProfile: true },
    }))
  }

  async function finishWithPin() {
    setBusy(true)
    try {
      const digest = await hashPin(pin)
      update((current) => ({
        ...current,
        auth: {
          ...current.auth,
          profileName: name.trim(),
          hasProfile: true,
          pinDigest: serializeDigest(digest),
          lockEnabled: true,
        },
      }))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-steps">
          {['profile', 'pin', 'confirm'].map((value, index) => (
            <span
              key={value}
              className={`auth-step${['profile', 'pin', 'confirm'].indexOf(step) >= index ? ' on' : ''}`}
            />
          ))}
        </div>

        <div className="auth-mark">
          <IconToday size={28} />
        </div>

        {step === 'profile' ? (
          <>
            <h1 className="auth-title">Welcome to Kairo</h1>
            <p className="auth-body">What should the coach call you?</p>
            <input
              className="input"
              value={name}
              autoFocus
              placeholder="Your name"
              onChange={(event) => setName(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && name.trim()) setStep('pin')
              }}
            />
            <button
              type="button"
              className="btn primary auth-action"
              disabled={!name.trim()}
              onClick={() => setStep('pin')}
            >
              Continue
            </button>
          </>
        ) : null}

        {step === 'pin' ? (
          <>
            <h1 className="auth-title">Set a PIN</h1>
            <p className="auth-body">
              Kairo will ask for this when you open it. {PIN_MIN_LENGTH} to {PIN_MAX_LENGTH} digits —
              or skip it entirely.
            </p>
            <PinPad value={pin} onChange={setPin} error={error} />
            <button
              type="button"
              className="btn primary auth-action"
              disabled={pin.length < PIN_MIN_LENGTH}
              onClick={() => {
                if (!isValidPin(pin)) {
                  setError(`Use ${PIN_MIN_LENGTH}–${PIN_MAX_LENGTH} digits.`)
                  return
                }
                setError(null)
                setStep('confirm')
              }}
            >
              Next
            </button>
            <button type="button" className="btn subtle auth-skip" onClick={finishWithoutPin}>
              Skip — don't lock the app
            </button>
          </>
        ) : null}

        {step === 'confirm' ? (
          <>
            <h1 className="auth-title">Confirm your PIN</h1>
            <p className="auth-body">Enter it once more so a typo can't lock you out.</p>
            <PinPad value={confirmPin} onChange={setConfirmPin} error={error} />
            <button
              type="button"
              className="btn primary auth-action"
              disabled={confirmPin.length < PIN_MIN_LENGTH || busy}
              onClick={() => {
                if (pin !== confirmPin) {
                  setConfirmPin('')
                  setError("Those didn't match. Try again.")
                  return
                }
                setError(null)
                void finishWithPin()
              }}
            >
              Finish setup
            </button>
            <button type="button" className="btn subtle auth-skip" onClick={() => setStep('pin')}>
              Back
            </button>
          </>
        ) : null}

        <p className="auth-note">
          Everything stays in this browser. There's no account to create and no server to sign in
          to — the PIN simply keeps your timetable private on this device.
        </p>
      </div>
    </div>
  )
}

export function LockScreen() {
  const { data, update, setLocked } = useAppState()
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const lockedUntil = data.auth.lockedUntil
  const lockedOut = Date.now() < lockedUntil
  const secondsLeft = Math.max(0, Math.ceil((lockedUntil - Date.now()) / 1000))

  async function submit() {
    if (busy || lockedOut) return
    const digest = parseDigest(data.auth.pinDigest)
    if (!digest) {
      // The lock was removed elsewhere; don't strand the user behind it.
      setLocked(false)
      return
    }
    setBusy(true)
    try {
      const valid = await verifyPin(pin, digest)
      if (valid) {
        update((current) => ({
          ...current,
          auth: { ...current.auth, failedAttempts: 0, lockedUntil: 0 },
        }))
        setPin('')
        setError(null)
        setLocked(false)
        return
      }
      const attempts = data.auth.failedAttempts + 1
      const cooldown = lockoutMillisFor(attempts)
      update((current) => ({
        ...current,
        auth: {
          ...current.auth,
          failedAttempts: attempts,
          lockedUntil: cooldown > 0 ? Date.now() + cooldown : 0,
        },
      }))
      setPin('')
      setError(
        cooldown > 0
          ? `Too many attempts. Try again in ${Math.ceil(cooldown / 1000)}s.`
          : `Wrong PIN. ${attemptsLeft(attempts)} attempt(s) before a cooldown.`,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-screen">
      <div className="auth-card">
        <div className="auth-mark lock">
          <IconLock size={26} />
        </div>
        <h1 className="auth-title">
          {data.auth.profileName ? `Welcome back, ${data.auth.profileName}` : 'Welcome back'}
        </h1>
        <p className="auth-body">Enter your PIN to open your timetable.</p>
        <PinPad
          value={pin}
          onChange={setPin}
          error={error ?? (lockedOut ? `Locked for ${secondsLeft}s after too many attempts.` : null)}
          onSubmit={() => void submit()}
        />
        <p className="auth-note">
          Your PIN unlocks this browser only. Kairo has no account and no server — a forgotten PIN
          can only be cleared by erasing the data from this browser.
        </p>
      </div>
    </div>
  )
}
