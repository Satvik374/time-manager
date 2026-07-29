import { useEffect, type ReactNode } from 'react'
import { IconClose } from './Icons'

export function Card({
  children,
  title,
  hint,
  action,
  className = '',
}: {
  children: ReactNode
  title?: string
  hint?: string
  action?: ReactNode
  className?: string
}) {
  return (
    <section className={`card ${className}`.trim()}>
      {title || action ? (
        <header className="card-header">
          <div>
            <h3 className="card-title">{title}</h3>
            {hint ? <p className="card-hint">{hint}</p> : null}
          </div>
          {action}
        </header>
      ) : null}
      {children}
    </section>
  )
}

export function StatTile({
  label,
  value,
  caption,
  icon,
  accent,
}: {
  label: string
  value: string
  caption?: string
  icon?: ReactNode
  accent?: string
}) {
  return (
    <div className="stat-tile">
      <div className="stat-label" style={accent ? { color: accent } : undefined}>
        {icon}
        {label}
      </div>
      <div className="stat-value">{value}</div>
      {caption ? <div className="stat-caption">{caption}</div> : null}
    </div>
  )
}

export function Switch({
  checked,
  onChange,
  disabled,
  label,
}: {
  checked: boolean
  onChange: (value: boolean) => void
  disabled?: boolean
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      className={`switch${checked ? ' on' : ''}`}
      onClick={() => onChange(!checked)}
    />
  )
}

export function SwitchRow({
  title,
  subtitle,
  checked,
  onChange,
  disabled,
}: {
  title: string
  subtitle: string
  checked: boolean
  onChange: (value: boolean) => void
  disabled?: boolean
}) {
  return (
    <div className="switch-row">
      <div>
        <div className="switch-title">{title}</div>
        <div className="switch-sub">{subtitle}</div>
      </div>
      <Switch checked={checked} onChange={onChange} disabled={disabled} label={title} />
    </div>
  )
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[]
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="segmented" role="tablist">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={value === option.value}
          className={value === option.value ? 'active' : undefined}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

export function Chip({
  label,
  selected,
  onClick,
  title,
}: {
  label: ReactNode
  selected?: boolean
  onClick?: () => void
  title?: string
}) {
  if (!onClick) {
    return <span className={`chip${selected ? ' selected' : ''}`}>{label}</span>
  }
  return (
    <button
      type="button"
      className={`chip${selected ? ' selected' : ''}`}
      onClick={onClick}
      title={title}
    >
      {label}
    </button>
  )
}

export function ProgressBar({
  label,
  trailing,
  progress,
  color = 'var(--primary)',
}: {
  label: string
  trailing: string
  progress: number
  color?: string
}) {
  return (
    <div>
      <div className="progress-row">
        <span className="label">{label}</span>
        <span className="value">{trailing}</span>
      </div>
      <div className="bar">
        <span
          style={{ width: `${Math.min(100, Math.max(0, progress * 100))}%`, background: color }}
        />
      </div>
    </div>
  )
}

export function EmptyState({
  icon,
  title,
  body,
  actionLabel,
  onAction,
}: {
  icon: ReactNode
  title: string
  body: string
  actionLabel?: string
  onAction?: () => void
}) {
  return (
    <div className="empty">
      <div className="empty-icon">{icon}</div>
      <h3>{title}</h3>
      <p>{body}</p>
      {actionLabel && onAction ? (
        <button type="button" className="btn primary" onClick={onAction}>
          {actionLabel}
        </button>
      ) : null}
    </div>
  )
}

export function Modal({
  title,
  onClose,
  children,
  footer,
  wide,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  wide?: boolean
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div
      className="modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className={`modal${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-head">
          <h2 className="modal-title">{title}</h2>
          <button type="button" className="btn subtle icon-only" onClick={onClose} aria-label="Close">
            <IconClose size={18} />
          </button>
        </header>
        <div className="modal-body">{children}</div>
        {footer ? <footer className="modal-foot">{footer}</footer> : null}
      </div>
    </div>
  )
}

export function Field({
  label,
  hint,
  children,
}: {
  label: string
  hint?: string
  children: ReactNode
}) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  )
}
