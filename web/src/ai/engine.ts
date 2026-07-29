/*
 * Runs one coach turn end to end: builds the grounded system prompt, calls the provider, executes
 * whatever tools the model asks for, feeds the results back, and repeats until the model answers
 * in plain text or hits MAX_TOOL_ROUNDS.
 */

import { Leveling } from '../core/rules'
import { formatIso, formatTime, nowMinuteOfDay, todayEpochDay, weekdayName } from '../core/time'
import { appendChat, refreshAchievements } from '../data/mutations'
import { isToolEnabled, textDigest } from '../data/selectors'
import type { KairoData, ToolCall } from '../data/types'
import { complete, describeAiError, isConfigured, type AiMessage } from './client'
import { executeTool } from './executor'
import { RECOMMENDATION_PROMPT, runtimeContext } from './prompts'
import { ALL_TOOLS, RECOMMENDATION_TOOL_NAMES, TOOLS_BY_NAME, TOOL_NAMES } from './tools'

const MAX_TOOL_ROUNDS = 6

export type CoachProgress =
  | { kind: 'thinking' }
  | { kind: 'tool'; label: string }
  | { kind: 'toolDone'; summary: string; succeeded: boolean }
  | { kind: 'replied'; text: string; scheduleChanged: boolean }
  | { kind: 'failed'; message: string }

export interface TurnResult {
  data: KairoData
  scheduleChanged: boolean
}

/** The user's editable prompt plus live state, so the model never has to guess. */
export function buildSystemPrompt(data: KairoData): string {
  const today = todayEpochDay()
  return `${data.ai.systemPrompt.trim()}\n\n${runtimeContext({
    todayIso: formatIso(today),
    weekday: weekdayName(today),
    nowTime: formatTime(nowMinuteOfDay(), true),
    profileName: data.auth.profileName,
    streak: data.stats.currentStreak,
    level: Leveling.levelForXp(data.stats.xp),
    coins: data.stats.coins,
    digest: textDigest(data, today),
  })}`
}

/**
 * Rebuilds the provider-facing history. Tool results are only meaningful next to the assistant
 * turn that requested them, so the window is trimmed to a boundary where every retained tool
 * result still has its matching assistant call.
 */
export function buildHistory(data: KairoData, maxMessages: number): AiMessage[] {
  const recent = data.chat.slice(-Math.max(4, maxMessages))

  const pending = new Set<string>()
  let startIndex = 0
  recent.forEach((message, index) => {
    if (message.role === 'assistant') {
      for (const call of message.toolCalls ?? []) pending.add(call.id)
    } else if (message.role === 'tool') {
      // An orphaned tool result would confuse the provider — drop everything up to here.
      if (!message.toolCallId || !pending.has(message.toolCallId)) startIndex = index + 1
    }
  })

  return recent.slice(startIndex).map((message) => ({
    role: message.role === 'tool' ? 'tool' : message.role === 'assistant' ? 'assistant' : 'user',
    content: message.content,
    toolCalls: message.toolCalls,
    toolCallId: message.toolCallId,
    toolName: message.toolName,
  }))
}

function enabledTools(data: KairoData) {
  if (!data.ai.toolsEnabled) return []
  return ALL_TOOLS.filter((tool) => isToolEnabled(data, tool.name))
}

export async function sendMessage(
  data: KairoData,
  text: string,
  onProgress: (progress: CoachProgress) => void,
): Promise<TurnResult> {
  if (!data.ai.enabled) {
    onProgress({ kind: 'failed', message: 'The AI coach is switched off in Settings.' })
    return { data, scheduleChanged: false }
  }
  if (!isConfigured(data.ai)) {
    const message = describeAiError({ kind: 'notConfigured' })
    onProgress({ kind: 'failed', message })
    return { data, scheduleChanged: false }
  }

  let working = appendChat(data, { role: 'user', content: text })
  working = refreshAchievements(working, { coachTalk: true }).data
  return runTurn(working, onProgress)
}

export async function retryTurn(
  data: KairoData,
  onProgress: (progress: CoachProgress) => void,
): Promise<TurnResult> {
  if (!isConfigured(data.ai)) {
    onProgress({ kind: 'failed', message: describeAiError({ kind: 'notConfigured' }) })
    return { data, scheduleChanged: false }
  }
  return runTurn(data, onProgress)
}

async function runTurn(
  initial: KairoData,
  onProgress: (progress: CoachProgress) => void,
): Promise<TurnResult> {
  let working = initial
  let scheduleChanged = false
  const tools = enabledTools(working)

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    onProgress({ kind: 'thinking' })

    const grounded = { ...working.ai, systemPrompt: buildSystemPrompt(working) }
    const history = buildHistory(working, working.ai.historyTurns * 3)
    const result = await complete(grounded, history, tools)

    if (!result.ok) {
      const message = describeAiError(result.error)
      working = appendChat(working, { role: 'assistant', content: message, isError: true })
      onProgress({ kind: 'failed', message })
      return { data: working, scheduleChanged }
    }

    const turn = result.turn
    working = appendChat(working, {
      role: 'assistant',
      content: turn.text,
      toolCalls: turn.toolCalls.length > 0 ? turn.toolCalls : undefined,
    })

    if (turn.toolCalls.length === 0) {
      const text =
        turn.text ||
        "I didn't get a usable reply from the provider. Try rephrasing, or check the model name in Settings."
      onProgress({ kind: 'replied', text, scheduleChanged })
      return { data: working, scheduleChanged }
    }

    for (const call of turn.toolCalls) {
      const label = TOOLS_BY_NAME.get(call.name)?.humanLabel ?? call.name
      onProgress({ kind: 'tool', label })
      const outcome = executeTool(working, call)
      working = outcome.data
      if (outcome.mutatedSchedule) scheduleChanged = true
      working = appendChat(working, {
        role: 'tool',
        content: outcome.resultJson,
        toolCallId: call.id,
        toolName: call.name,
        isError: !outcome.succeeded,
      })
      onProgress({ kind: 'toolDone', summary: outcome.summary, succeeded: outcome.succeeded })
    }

    if (round === MAX_TOOL_ROUNDS - 1) {
      const message =
        "I ran out of tool steps for this request. Ask me to continue and I'll pick up where I left off."
      working = appendChat(working, { role: 'assistant', content: message, isError: true })
      onProgress({ kind: 'failed', message })
    }
  }

  return { data: working, scheduleChanged }
}

/**
 * Generates at most one recommendation card, respecting the user's interval setting.
 * Returns the (possibly unchanged) document and whether a card was posted.
 */
export async function maybeGenerateRecommendation(
  data: KairoData,
  force = false,
): Promise<{ data: KairoData; posted: boolean }> {
  const config = data.ai
  if (!config.enabled || !config.recommendationsEnabled || !isConfigured(config)) {
    return { data, posted: false }
  }
  if (!isToolEnabled(data, TOOL_NAMES.SUGGEST_RECOMMENDATION)) return { data, posted: false }

  const elapsed = Date.now() - data.prefs.lastRecommendationAt
  const intervalMillis = Math.max(1, config.recommendationIntervalHours) * 60 * 60 * 1000
  if (!force && elapsed < intervalMillis) return { data, posted: false }

  const grounded = { ...config, systemPrompt: buildSystemPrompt(data), maxTokens: 600 }
  const tools = ALL_TOOLS.filter(
    (tool) => RECOMMENDATION_TOOL_NAMES.includes(tool.name) && isToolEnabled(data, tool.name),
  )

  let working = data
  let conversation: AiMessage[] = [
    {
      role: 'user',
      content: `${RECOMMENDATION_PROMPT}\n\nSummary you already have:\n${textDigest(data)}`,
    },
  ]

  for (let round = 0; round < 3; round++) {
    const result = await complete(grounded, conversation, tools)
    if (!result.ok) return { data: working, posted: false }
    const turn = result.turn
    if (turn.toolCalls.length === 0) return { data: working, posted: false }

    conversation = [
      ...conversation,
      { role: 'assistant', content: turn.text, toolCalls: turn.toolCalls },
    ]

    let posted = false
    for (const call of turn.toolCalls) {
      const outcome = executeTool(working, call)
      working = outcome.data
      if (call.name === TOOL_NAMES.SUGGEST_RECOMMENDATION && outcome.succeeded) posted = true
      conversation = [
        ...conversation,
        { role: 'tool', content: outcome.resultJson, toolCallId: call.id, toolName: call.name },
      ]
    }

    if (posted) {
      return {
        data: { ...working, prefs: { ...working.prefs, lastRecommendationAt: Date.now() } },
        posted: true,
      }
    }
  }

  return { data: working, posted: false }
}

export function decodeToolCallLabels(calls: ToolCall[]): string[] {
  return calls.map((call) => TOOLS_BY_NAME.get(call.name)?.humanLabel ?? call.name)
}
