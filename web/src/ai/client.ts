/*
 * Provider client. Speaks both the OpenAI-compatible /chat/completions dialect and the Anthropic
 * /messages dialect, including tool calls and tool results in each direction, so the user can
 * point Kairo at whatever endpoint they actually have.
 */

import type { AiConfig, ProviderFormat, ToolCall } from '../data/types'
import type { ToolSpec } from './tools'

export interface AiMessage {
  role: 'user' | 'assistant' | 'tool'
  content: string
  toolCalls?: ToolCall[]
  toolCallId?: string
  toolName?: string
}

export interface AiTurn {
  text: string
  toolCalls: ToolCall[]
  finishReason: string | null
}

export type AiError =
  | { kind: 'notConfigured' }
  | { kind: 'network'; message: string }
  | { kind: 'http'; status: number; body: string }
  | { kind: 'malformed'; message: string }

export function describeAiError(error: AiError): string {
  switch (error.kind) {
    case 'notConfigured':
      return 'Add a base URL, API key and model in Settings → AI provider first.'
    case 'network':
      return `Couldn't reach the provider: ${error.message}`
    case 'malformed':
      return `Couldn't read the provider's response: ${error.message}`
    case 'http':
      if (error.status === 401 || error.status === 403) {
        return `Provider rejected the API key (HTTP ${error.status}). Check the key in Settings.`
      }
      if (error.status === 404) {
        return 'Endpoint not found (HTTP 404). Check the base URL and wire format in Settings.'
      }
      if (error.status === 429) return 'Rate limited by the provider (HTTP 429). Try again in a moment.'
      if (error.status >= 500) return `Provider error (HTTP ${error.status}). Try again shortly.`
      return `Provider returned HTTP ${error.status}: ${error.body.slice(0, 300)}`
  }
}

export type AiResult = { ok: true; turn: AiTurn } | { ok: false; error: AiError }

const ANTHROPIC_VERSION = '2023-06-01'

export function endpointSuffix(format: ProviderFormat): string {
  return format === 'anthropic' ? 'messages' : 'chat/completions'
}

export function endpointFor(config: AiConfig): string {
  const base = config.baseUrl.trim().replace(/\/+$/, '')
  const suffix = endpointSuffix(config.format)
  return base.endsWith(suffix) ? base : `${base}/${suffix}`
}

export function isConfigured(config: AiConfig): boolean {
  return config.baseUrl.trim().length > 0 && config.model.trim().length > 0
}

export async function complete(
  config: AiConfig,
  messages: AiMessage[],
  tools: ToolSpec[],
): Promise<AiResult> {
  if (!isConfigured(config)) return { ok: false, error: { kind: 'notConfigured' } }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  }
  if (config.format === 'anthropic') {
    if (config.apiKey.trim()) headers['x-api-key'] = config.apiKey.trim()
    headers['anthropic-version'] = ANTHROPIC_VERSION
    // Browsers enforce CORS; providers that support direct browser calls need this opt-in.
    headers['anthropic-dangerous-direct-browser-access'] = 'true'
  } else if (config.apiKey.trim()) {
    headers['Authorization'] = `Bearer ${config.apiKey.trim()}`
  }

  const body =
    config.format === 'anthropic'
      ? buildAnthropicBody(config, messages, tools)
      : buildOpenAiBody(config, messages, tools)

  let response: Response
  try {
    response = await fetch(endpointFor(config), {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    })
  } catch (error) {
    const message = error instanceof Error ? error.message : 'connection failed'
    return {
      ok: false,
      error: {
        kind: 'network',
        // A CORS rejection surfaces as a generic TypeError, so name the likely cause.
        message: `${message}. If the provider doesn't allow browser requests, this will fail on CORS — use a gateway that does.`,
      },
    }
  }

  const raw = await response.text()
  if (!response.ok) {
    return { ok: false, error: { kind: 'http', status: response.status, body: raw } }
  }

  try {
    const parsed = JSON.parse(raw)
    const turn = config.format === 'anthropic' ? parseAnthropic(parsed) : parseOpenAi(parsed)
    return { ok: true, turn }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'unexpected shape'
    return { ok: false, error: { kind: 'malformed', message } }
  }
}

/** Lightweight round trip used by the "Test connection" button. */
export async function testConnection(config: AiConfig): Promise<AiResult> {
  return complete(
    { ...config, systemPrompt: 'You are a connectivity probe.', maxTokens: 32 },
    [{ role: 'user', content: 'Reply with exactly: OK' }],
    [],
  )
}

// ---- OpenAI-compatible ---------------------------------------------------

export function buildOpenAiBody(config: AiConfig, messages: AiMessage[], tools: ToolSpec[]) {
  const payload: Record<string, unknown> = {
    model: config.model.trim(),
    temperature: config.temperature,
    max_tokens: config.maxTokens,
    stream: false,
    messages: [
      { role: 'system', content: config.systemPrompt },
      ...messages.map((message) => {
        if (message.role === 'tool') {
          return {
            role: 'tool',
            tool_call_id: message.toolCallId ?? '',
            content: message.content,
          }
        }
        const entry: Record<string, unknown> = { role: message.role, content: message.content }
        if (message.toolCalls?.length) {
          entry.tool_calls = message.toolCalls.map((call) => ({
            id: call.id,
            type: 'function',
            function: { name: call.name, arguments: call.argumentsJson },
          }))
        }
        return entry
      }),
    ],
  }
  if (tools.length > 0) {
    payload.tools = tools.map((tool) => ({
      type: 'function',
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.parameters,
      },
    }))
    payload.tool_choice = 'auto'
  }
  return payload
}

export function parseOpenAi(root: any): AiTurn {
  const choice = root?.choices?.[0]
  if (!choice) throw new Error('no choices in response')
  const message = choice.message
  if (!message) throw new Error('no message in choice')

  let text = ''
  if (typeof message.content === 'string') text = message.content
  else if (Array.isArray(message.content)) {
    text = message.content
      .map((part: any) => (typeof part?.text === 'string' ? part.text : ''))
      .filter(Boolean)
      .join('\n')
  }

  const toolCalls: ToolCall[] = (message.tool_calls ?? [])
    .map((call: any, index: number) => {
      const fn = call?.function
      if (!fn?.name) return null
      const args =
        typeof fn.arguments === 'string' ? fn.arguments : JSON.stringify(fn.arguments ?? {})
      return {
        id: call.id ?? `call_${index}`,
        name: fn.name,
        argumentsJson: args.trim() || '{}',
      }
    })
    .filter(Boolean) as ToolCall[]

  return { text: text.trim(), toolCalls, finishReason: choice.finish_reason ?? null }
}

// ---- Anthropic Messages --------------------------------------------------

export function buildAnthropicBody(config: AiConfig, messages: AiMessage[], tools: ToolSpec[]) {
  const converted: unknown[] = []
  let index = 0
  while (index < messages.length) {
    const message = messages[index]
    if (message.role === 'tool') {
      // Anthropic wants tool results inside a user turn, so consecutive results are merged.
      const batch: AiMessage[] = []
      while (index < messages.length && messages[index].role === 'tool') {
        batch.push(messages[index])
        index++
      }
      converted.push({
        role: 'user',
        content: batch.map((result) => ({
          type: 'tool_result',
          tool_use_id: result.toolCallId ?? '',
          content: result.content,
        })),
      })
      continue
    }

    const content: unknown[] = []
    if (message.content.trim()) content.push({ type: 'text', text: message.content })
    for (const call of message.toolCalls ?? []) {
      let input: unknown = {}
      try {
        input = JSON.parse(call.argumentsJson)
      } catch {
        input = {}
      }
      content.push({ type: 'tool_use', id: call.id, name: call.name, input })
    }
    converted.push({ role: message.role === 'assistant' ? 'assistant' : 'user', content })
    index++
  }

  const payload: Record<string, unknown> = {
    model: config.model.trim(),
    max_tokens: config.maxTokens,
    temperature: config.temperature,
    system: config.systemPrompt,
    messages: converted,
  }
  if (tools.length > 0) {
    payload.tools = tools.map((tool) => ({
      name: tool.name,
      description: tool.description,
      input_schema: tool.parameters,
    }))
  }
  return payload
}

export function parseAnthropic(root: any): AiTurn {
  const content = root?.content
  if (!Array.isArray(content)) throw new Error('no content in response')
  const textParts: string[] = []
  const toolCalls: ToolCall[] = []

  content.forEach((block: any, index: number) => {
    if (block?.type === 'text' && typeof block.text === 'string') textParts.push(block.text)
    else if (block?.type === 'tool_use' && block.name) {
      toolCalls.push({
        id: block.id ?? `call_${index}`,
        name: block.name,
        argumentsJson: JSON.stringify(block.input ?? {}),
      })
    }
  })

  return {
    text: textParts.join('\n').trim(),
    toolCalls,
    finishReason: root?.stop_reason ?? null,
  }
}
