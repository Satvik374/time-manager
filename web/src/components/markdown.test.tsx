/*
 * The renderer's job is to make a model's answer readable without ever mangling the app's own
 * vocabulary — tool names like `get_schedule` and argument names like `task_id` are full of
 * underscores, and an over-eager emphasis rule turns them into italics. These mirror the
 * Android MarkdownTest so both clients keep formatting the same reply the same way.
 */

import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import { parseMarkdownBlocks, renderInline } from './Markdown'

/** Inline nodes are easier to assert on as markup than as a React tree. */
function inline(text: string): string {
  return renderToStaticMarkup(<>{renderInline(text)}</>)
}

describe('inline formatting', () => {
  it('renders bold, italic and strikethrough', () => {
    expect(inline('**deep work**')).toBe('<strong>deep work</strong>')
    expect(inline('*focus*')).toBe('<em>focus</em>')
    expect(inline('~~skipped~~')).toBe('<s>skipped</s>')
  })

  it('nests emphasis inside emphasis', () => {
    expect(inline('**bold with *italic* inside**')).toBe(
      '<strong>bold with <em>italic</em> inside</strong>',
    )
  })

  it('leaves underscores inside identifiers alone', () => {
    // The regression: `task_id ... get_schedule` used to italicise everything between the
    // first and last underscore, renaming the app's own tools mid-sentence.
    expect(inline('Call get_schedule with task_id set')).toBe(
      'Call get_schedule with task_id set',
    )
    expect(inline('add_task and complete_task both take task_id')).toBe(
      'add_task and complete_task both take task_id',
    )
  })

  it('still honours underscore emphasis at word boundaries', () => {
    expect(inline('_focus_ time')).toBe('<em>focus</em> time')
    expect(inline('__really__ done')).toBe('<strong>really</strong> done')
  })

  it('keeps code spans literal', () => {
    expect(inline('use `task_id` here')).toBe('use <code class="md-code">task_id</code> here')
    expect(inline('`**not bold**`')).toBe('<code class="md-code">**not bold**</code>')
  })

  it('passes unmatched markers through as text', () => {
    expect(inline('2 * 3 * 4')).toBe('2 * 3 * 4')
    expect(inline('a ** b')).toBe('a ** b')
    expect(inline('unclosed `code')).toBe('unclosed `code')
  })

  it('links only to schemes a browser should follow', () => {
    expect(inline('[docs](https://example.com)')).toContain('href="https://example.com"')
    expect(inline('[mail](mailto:a@b.c)')).toContain('href="mailto:a@b.c"')
    // A model that emits `javascript:` gets defanged rather than trusted.
    expect(inline('[bad](javascript:alert(1))')).toContain('href="#"')
  })

  it('leaves a bare bracket alone when it is not a link', () => {
    expect(inline('array[0] is first')).toBe('array[0] is first')
  })
})

describe('block parsing', () => {
  it('reads headings at every level it supports', () => {
    const blocks = parseMarkdownBlocks('# One\n## Two\n### Three')
    expect(blocks.map((block) => block.kind)).toEqual(['heading', 'heading', 'heading'])
    expect(blocks.map((block) => (block.kind === 'heading' ? block.level : 0))).toEqual([1, 2, 3])
  })

  it('reads each bullet as its own block, whichever marker is used', () => {
    const blocks = parseMarkdownBlocks('- one\n* two\n+ three')
    expect(blocks.map((block) => block.kind)).toEqual(['bullet', 'bullet', 'bullet'])
    expect(blocks.map((block) => (block.kind === 'bullet' ? block.text : ''))).toEqual([
      'one',
      'two',
      'three',
    ])
  })

  it('keeps a numbered list numbered rather than renumbering it', () => {
    const blocks = parseMarkdownBlocks('1. first\n2) second\n7. seventh')
    expect(blocks.map((block) => (block.kind === 'numbered' ? block.marker : ''))).toEqual([
      '1',
      '2',
      '7',
    ])
  })

  it('reads indentation as nesting depth', () => {
    const blocks = parseMarkdownBlocks('- outer\n  - inner\n    - deeper')
    expect(blocks.map((block) => (block.kind === 'bullet' ? block.depth : -1))).toEqual([0, 1, 2])
  })

  it('keeps a fenced block verbatim, markers and all', () => {
    const blocks = parseMarkdownBlocks('```json\n{"task_id": 1}\n```')
    expect(blocks).toHaveLength(1)
    expect(blocks[0].kind).toBe('code')
    if (blocks[0].kind === 'code') {
      expect(blocks[0].language).toBe('json')
      expect(blocks[0].code).toBe('{"task_id": 1}')
    }
  })

  it('does not leave an unterminated fence half-parsed', () => {
    const blocks = parseMarkdownBlocks('```\nstill open')
    expect(blocks[0].kind).toBe('code')
    if (blocks[0].kind === 'code') expect(blocks[0].code).toBe('still open')
  })

  it('reads a pipe table with its header row', () => {
    const blocks = parseMarkdownBlocks(
      '| Block | Time |\n| --- | --- |\n| Deep work | 09:00 |\n| Review | 17:00 |',
    )
    expect(blocks[0].kind).toBe('table')
    if (blocks[0].kind === 'table') {
      expect(blocks[0].header).toEqual(['Block', 'Time'])
      expect(blocks[0].rows).toEqual([
        ['Deep work', '09:00'],
        ['Review', '17:00'],
      ])
    }
  })

  it('reads quotes and dividers', () => {
    const blocks = parseMarkdownBlocks('> keep going\n\n---')
    expect(blocks.map((block) => block.kind)).toEqual(['quote', 'divider'])
  })

  it('does not mistake a lone pipe row for a table', () => {
    const blocks = parseMarkdownBlocks('Deep work | 09:00 tomorrow')
    expect(blocks.map((block) => block.kind)).toEqual(['paragraph'])
  })

  it('treats a blank line as a paragraph break', () => {
    const blocks = parseMarkdownBlocks('First thought.\n\nSecond thought.')
    expect(blocks.map((block) => block.kind)).toEqual(['paragraph', 'paragraph'])
  })

  it('returns nothing for empty input', () => {
    expect(parseMarkdownBlocks('')).toEqual([])
    expect(parseMarkdownBlocks('   \n  ')).toEqual([])
  })
})
