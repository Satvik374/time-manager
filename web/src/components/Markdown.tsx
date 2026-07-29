/*
 * The same small Markdown renderer as the Android client, in React.
 *
 * Models emit Markdown whether or not you ask them to, so the choice is between rendering it and
 * showing raw asterisks. Anything it doesn't recognise renders as literal text, so nothing is ever
 * lost to the parser. Notably, underscores inside words stay literal — an app whose own vocabulary
 * is `task_id` and `get_schedule` cannot afford to italicise half of it.
 */

import { Fragment, type ReactNode } from 'react'

export type MdBlock =
  | { kind: 'paragraph'; text: string }
  | { kind: 'heading'; level: number; text: string }
  | { kind: 'bullet'; text: string; depth: number }
  | { kind: 'numbered'; marker: string; text: string; depth: number }
  | { kind: 'quote'; text: string }
  | { kind: 'code'; code: string; language: string | null }
  | { kind: 'table'; header: string[]; rows: string[][] }
  | { kind: 'divider' }

const HEADING = /^(#{1,6})\s+(.*)$/
const BULLET = /^(\s*)[-*+]\s+(.*)$/
const NUMBERED = /^(\s*)(\d{1,3})[.)]\s+(.*)$/
const QUOTE = /^>\s?(.*)$/
const DIVIDER = /^\s*(-{3,}|_{3,}|\*{3,})\s*$/
const TABLE_SEPARATOR = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/

export function parseMarkdownBlocks(source: string): MdBlock[] {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const blocks: MdBlock[] = []
  let paragraph: string[] = []

  const flush = () => {
    const text = paragraph.join(' ').trim()
    if (text) blocks.push({ kind: 'paragraph', text })
    paragraph = []
  }

  let index = 0
  while (index < lines.length) {
    const line = lines[index]
    const trimmed = line.trim()

    // Fenced code — everything inside is literal, including Markdown syntax.
    if (trimmed.startsWith('```')) {
      flush()
      const language = trimmed.slice(3).trim() || null
      const code: string[] = []
      index++
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        code.push(lines[index])
        index++
      }
      index++ // consume the closing fence, if the model bothered to write one
      blocks.push({ kind: 'code', code: code.join('\n').replace(/\n+$/, ''), language })
      continue
    }

    if (!trimmed) {
      flush()
      index++
      continue
    }

    if (DIVIDER.test(line)) {
      flush()
      blocks.push({ kind: 'divider' })
      index++
      continue
    }

    // A pipe row followed by a dashed row is a table; a lone pipe row is just prose.
    if (
      trimmed.includes('|') &&
      index + 1 < lines.length &&
      TABLE_SEPARATOR.test(lines[index + 1]) &&
      lines[index + 1].includes('-')
    ) {
      flush()
      const header = splitTableRow(line)
      const rows: string[][] = []
      index += 2
      while (index < lines.length && lines[index].includes('|') && lines[index].trim()) {
        rows.push(splitTableRow(lines[index]))
        index++
      }
      blocks.push({ kind: 'table', header, rows })
      continue
    }

    const heading = HEADING.exec(line)
    if (heading) {
      flush()
      blocks.push({ kind: 'heading', level: heading[1].length, text: heading[2].trim() })
      index++
      continue
    }

    const numbered = NUMBERED.exec(line)
    if (numbered) {
      flush()
      blocks.push({
        kind: 'numbered',
        marker: numbered[2],
        text: numbered[3].trim(),
        depth: Math.floor(numbered[1].length / 2),
      })
      index++
      continue
    }

    const bullet = BULLET.exec(line)
    if (bullet) {
      flush()
      blocks.push({
        kind: 'bullet',
        text: bullet[2].trim(),
        depth: Math.floor(bullet[1].length / 2),
      })
      index++
      continue
    }

    const quote = QUOTE.exec(line)
    if (quote) {
      flush()
      blocks.push({ kind: 'quote', text: quote[1].trim() })
      index++
      continue
    }

    paragraph.push(trimmed)
    index++
  }

  flush()
  return blocks
}

function splitTableRow(line: string): string[] {
  return line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((cell) => cell.trim())
}

/**
 * Scans emphasis markers by hand rather than by regex so nesting works
 * (`**bold with *italic* inside**`) and code spans stay literal.
 */
export function renderInline(text: string, keyPrefix = 'i'): ReactNode[] {
  const nodes: ReactNode[] = []
  let plain = ''
  let index = 0
  let counter = 0

  const pushPlain = () => {
    if (plain) {
      nodes.push(<Fragment key={`${keyPrefix}-t${counter++}`}>{plain}</Fragment>)
      plain = ''
    }
  }

  const isWordChar = (position: number): boolean =>
    position >= 0 && position < text.length && /[\w]/.test(text[position])

  const closingIndex = (marker: string, from: number): number => {
    const at = text.indexOf(marker, from)
    return at > from ? at : -1
  }

  // Underscore emphasis only counts at word boundaries, so `task_id` survives intact.
  const underscoreClosing = (marker: string, from: number): number => {
    let search = from
    for (;;) {
      const at = text.indexOf(marker, search)
      if (at <= from) return -1
      if (!isWordChar(at + marker.length)) return at
      search = at + 1
    }
  }

  while (index < text.length) {
    const char = text[index]
    const rest = text.length - index

    if (char === '`') {
      const close = closingIndex('`', index + 1)
      if (close < 0) {
        plain += char
        index++
      } else {
        pushPlain()
        nodes.push(
          <code key={`${keyPrefix}-c${counter++}`} className="md-code">
            {text.slice(index + 1, close)}
          </code>,
        )
        index = close + 1
      }
      continue
    }

    if (rest >= 4 && (text.startsWith('**', index) || text.startsWith('__', index))) {
      const marker = text.slice(index, index + 2)
      const close =
        marker === '__'
          ? isWordChar(index - 1)
            ? -1
            : underscoreClosing(marker, index + 2)
          : closingIndex(marker, index + 2)
      if (close < 0) {
        plain += char
        index++
      } else {
        pushPlain()
        nodes.push(
          <strong key={`${keyPrefix}-b${counter++}`}>
            {renderInline(text.slice(index + 2, close), `${keyPrefix}-b${counter}`)}
          </strong>,
        )
        index = close + 2
      }
      continue
    }

    if (rest >= 4 && text.startsWith('~~', index)) {
      const close = closingIndex('~~', index + 2)
      if (close < 0) {
        plain += char
        index++
      } else {
        pushPlain()
        nodes.push(
          <s key={`${keyPrefix}-s${counter++}`}>
            {renderInline(text.slice(index + 2, close), `${keyPrefix}-s${counter}`)}
          </s>,
        )
        index = close + 2
      }
      continue
    }

    if ((char === '*' || char === '_') && index + 1 < text.length && !/\s/.test(text[index + 1])) {
      const close =
        char === '_'
          ? isWordChar(index - 1)
            ? -1
            : underscoreClosing('_', index + 1)
          : closingIndex('*', index + 1)
      if (close < 0) {
        plain += char
        index++
      } else {
        pushPlain()
        nodes.push(
          <em key={`${keyPrefix}-e${counter++}`}>
            {renderInline(text.slice(index + 1, close), `${keyPrefix}-e${counter}`)}
          </em>,
        )
        index = close + 1
      }
      continue
    }

    if (char === '[') {
      const labelEnd = text.indexOf(']', index)
      const urlStart = labelEnd >= 0 ? labelEnd + 1 : -1
      if (labelEnd < 0 || urlStart >= text.length || text[urlStart] !== '(') {
        plain += char
        index++
        continue
      }
      const urlEnd = text.indexOf(')', urlStart)
      if (urlEnd < 0) {
        plain += char
        index++
        continue
      }
      pushPlain()
      const label = text.slice(index + 1, labelEnd)
      const url = text.slice(urlStart + 1, urlEnd).trim()
      const safe = /^(https?:|mailto:)/i.test(url) ? url : '#'
      nodes.push(
        <a key={`${keyPrefix}-l${counter++}`} href={safe} target="_blank" rel="noreferrer noopener">
          {label || url}
        </a>,
      )
      index = urlEnd + 1
      continue
    }

    plain += char
    index++
  }

  pushPlain()
  return nodes
}

export function Markdown({ text, className }: { text: string; className?: string }) {
  const blocks = parseMarkdownBlocks(text)
  return (
    <div className={className ? `md ${className}` : 'md'}>
      {blocks.map((block, index) => {
        const key = `b${index}`
        switch (block.kind) {
          case 'paragraph':
            return <p key={key}>{renderInline(block.text, key)}</p>
          case 'heading': {
            const Tag = (block.level <= 1 ? 'h3' : block.level === 2 ? 'h4' : 'h5') as 'h3'
            return <Tag key={key}>{renderInline(block.text, key)}</Tag>
          }
          case 'bullet':
            return (
              <div key={key} className="md-item" style={{ paddingLeft: block.depth * 16 }}>
                <span className="md-marker">•</span>
                <span>{renderInline(block.text, key)}</span>
              </div>
            )
          case 'numbered':
            return (
              <div key={key} className="md-item" style={{ paddingLeft: block.depth * 16 }}>
                <span className="md-marker num">{block.marker}.</span>
                <span>{renderInline(block.text, key)}</span>
              </div>
            )
          case 'quote':
            return (
              <blockquote key={key}>{renderInline(block.text, key)}</blockquote>
            )
          case 'code':
            return (
              <pre key={key} className="md-pre">
                {block.language ? <span className="md-lang">{block.language}</span> : null}
                <code>{block.code}</code>
              </pre>
            )
          case 'table':
            return (
              <div key={key} className="md-table-wrap">
                <table className="md-table">
                  <thead>
                    <tr>
                      {block.header.map((cell, cellIndex) => (
                        <th key={cellIndex}>{cell}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {block.rows.map((row, rowIndex) => (
                      <tr key={rowIndex}>
                        {block.header.map((_, cellIndex) => (
                          <td key={cellIndex}>{renderInline(row[cellIndex] ?? '', `${key}-${rowIndex}-${cellIndex}`)}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          case 'divider':
            return <hr key={key} className="md-hr" />
        }
      })}
    </div>
  )
}
