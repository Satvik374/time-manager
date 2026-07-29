import { useEffect, useMemo, useRef, useState } from 'react'
import { isConfigured } from '../ai/client'
import { sendMessage, type CoachProgress } from '../ai/engine'
import { TOOLS_BY_NAME } from '../ai/tools'
import { clearChat } from '../data/mutations'
import { Markdown } from '../components/Markdown'
import { IconSparkle, IconTool, IconTrash, IconSend, IconWarning } from '../components/Icons'
import { Card, EmptyState } from '../components/Ui'
import type { ChatMessage } from '../data/types'
import { useAppState } from '../state/AppState'

const QUICK_PROMPTS = [
  'Plan my day around 3 hours of deep work',
  'What did I miss this week?',
  'Add a 45-minute workout on Mon, Wed, Fri at 7am',
  'Break my study block into steps',
  'Where am I losing time?',
]

/** A chat bubble as the screen wants it: tool traffic collapsed into one activity line. */
type ChatItem =
  | { kind: 'user'; id: number; text: string }
  | { kind: 'assistant'; id: number; text: string; isError: boolean }
  | { kind: 'tools'; id: number; labels: string[]; anyFailed: boolean }

function toItems(messages: ChatMessage[]): ChatItem[] {
  const items: ChatItem[] = []
  let pendingLabels: string[] = []
  let pendingFailed = false
  let pendingId = 0

  const flushTools = () => {
    if (pendingLabels.length > 0) {
      items.push({ kind: 'tools', id: pendingId, labels: pendingLabels, anyFailed: pendingFailed })
      pendingLabels = []
      pendingFailed = false
    }
  }

  for (const message of messages) {
    if (message.role === 'user') {
      flushTools()
      items.push({ kind: 'user', id: message.id, text: message.content })
    } else if (message.role === 'assistant') {
      if (message.toolCalls?.length) {
        if (pendingLabels.length === 0) pendingId = message.id
        pendingLabels.push(
          ...message.toolCalls.map((call) => TOOLS_BY_NAME.get(call.name)?.humanLabel ?? call.name),
        )
      }
      if (message.content.trim()) {
        flushTools()
        items.push({
          kind: 'assistant',
          id: message.id,
          text: message.content,
          isError: message.isError ?? false,
        })
      }
    } else if (message.role === 'tool') {
      if (message.isError) pendingFailed = true
    }
  }
  flushTools()
  return items
}

export function CoachScreen({ onOpenSettings }: { onOpenSettings: () => void }) {
  const { data, update, pushToast } = useAppState()
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [status, setStatus] = useState<string | null>(null)
  const [scheduleChanged, setScheduleChanged] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)

  const items = useMemo(() => toItems(data.chat), [data.chat])
  const configured = data.ai.enabled && isConfigured(data.ai)

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [items.length, status])

  async function send(text: string) {
    const message = text.trim()
    if (!message || busy) return
    setDraft('')
    setBusy(true)
    setScheduleChanged(false)
    setStatus('Thinking…')

    // The engine returns the whole updated document; progress only drives the status line.
    const onProgress = (progress: CoachProgress) => {
      switch (progress.kind) {
        case 'thinking':
          setStatus('Thinking…')
          break
        case 'tool':
          setStatus(`${progress.label}…`)
          break
        case 'toolDone':
          setStatus(progress.summary)
          break
        case 'replied':
          setStatus(null)
          if (progress.scheduleChanged) setScheduleChanged(true)
          break
        case 'failed':
          setStatus(null)
          pushToast(progress.message, 'error')
          break
      }
    }

    try {
      const result = await sendMessage(data, message, onProgress)
      update(() => result.data)
      if (result.scheduleChanged) setScheduleChanged(true)
    } finally {
      setBusy(false)
      setStatus(null)
    }
  }

  return (
    <div className="page coach-page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Coach</h1>
          <p className="page-subtitle">
            {configured
              ? 'Connected · it can read and edit your real timetable'
              : 'Not configured — add a provider in Settings'}
          </p>
        </div>
        <div className="page-actions">
          {data.chat.length > 0 ? (
            <button
              type="button"
              className="btn ghost"
              onClick={() => update((current) => clearChat(current))}
            >
              <IconTrash size={16} /> Clear conversation
            </button>
          ) : null}
          <button type="button" className="btn ghost" onClick={onOpenSettings}>
            AI settings
          </button>
        </div>
      </header>

      {scheduleChanged ? (
        <div className="banner">
          Your timetable was updated — check the Today and Planner tabs.
        </div>
      ) : null}

      <Card className="flush chat-card">
        <div className="chat-scroll" ref={scrollRef}>
          {items.length === 0 ? (
            <EmptyState
              icon={<IconSparkle size={26} />}
              title={configured ? 'Talk to your coach' : "Coach isn't connected yet"}
              body={
                configured
                  ? 'Ask it to plan, reshuffle or break down your timetable. It edits your real schedule, with a checklist for every block.'
                  : "Add your provider's base URL, API key and model in Settings, then come back."
              }
              actionLabel={configured ? undefined : 'Open settings'}
              onAction={configured ? undefined : onOpenSettings}
            />
          ) : (
            <div className="chat-thread">
              {items.map((item) => {
                if (item.kind === 'user') {
                  return (
                    <div key={`u${item.id}`} className="bubble user">
                      {item.text}
                    </div>
                  )
                }
                if (item.kind === 'assistant') {
                  return (
                    <div
                      key={`a${item.id}`}
                      className={`bubble assistant${item.isError ? ' error' : ''}`}
                    >
                      {item.isError ? <IconWarning size={15} /> : null}
                      <Markdown text={item.text} />
                    </div>
                  )
                }
                return (
                  <div key={`t${item.id}`} className="tool-trace">
                    <IconTool size={13} />
                    {Array.from(new Set(item.labels)).join(' · ')}
                    {item.anyFailed ? ' (one step failed)' : ''}
                  </div>
                )
              })}
              {status ? (
                <div className="tool-trace status">
                  <span className="spinner" />
                  {status}
                </div>
              ) : null}
            </div>
          )}
        </div>

        {items.length === 0 && configured ? (
          <div className="quick-prompts">
            {QUICK_PROMPTS.map((prompt) => (
              <button key={prompt} type="button" className="chip" onClick={() => send(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        ) : null}

        <div className="composer">
          <textarea
            ref={composerRef}
            className="textarea"
            value={draft}
            rows={1}
            placeholder="Ask, or tell it what to change…   (Enter to send, Shift+Enter for a new line)"
            disabled={busy}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                void send(draft)
              }
            }}
          />
          <button
            type="button"
            className="btn primary icon-only send"
            onClick={() => void send(draft)}
            disabled={busy || draft.trim().length === 0}
            aria-label="Send"
          >
            {busy ? <span className="spinner" /> : <IconSend size={17} />}
          </button>
        </div>
      </Card>
    </div>
  )
}
