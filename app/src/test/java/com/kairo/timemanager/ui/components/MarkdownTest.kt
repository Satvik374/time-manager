package com.kairo.timemanager.ui.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockTest {

    @Test
    fun `plain prose becomes a single paragraph`() {
        val blocks = parseMarkdownBlocks("Moved Gym to 7pm.")
        assertEquals(1, blocks.size)
        assertEquals(MdBlock.Paragraph("Moved Gym to 7pm."), blocks.single())
    }

    @Test
    fun `wrapped lines join but blank lines split paragraphs`() {
        val blocks = parseMarkdownBlocks("one\ntwo\n\nthree")
        assertEquals(
            listOf(MdBlock.Paragraph("one two"), MdBlock.Paragraph("three")),
            blocks
        )
    }

    @Test
    fun `headings capture their level`() {
        val blocks = parseMarkdownBlocks("# Big\n## Medium\n### Small")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Big"),
                MdBlock.Heading(2, "Medium"),
                MdBlock.Heading(3, "Small")
            ),
            blocks
        )
    }

    @Test
    fun `bullet lists accept every common marker`() {
        val blocks = parseMarkdownBlocks("- one\n* two\n+ three")
        assertEquals(3, blocks.size)
        assertTrue(blocks.all { it is MdBlock.BulletItem })
        assertEquals("one", (blocks[0] as MdBlock.BulletItem).text)
        assertEquals("three", (blocks[2] as MdBlock.BulletItem).text)
    }

    @Test
    fun `indented bullets record their depth`() {
        val blocks = parseMarkdownBlocks("- top\n  - nested")
        assertEquals(0, (blocks[0] as MdBlock.BulletItem).depth)
        assertEquals(1, (blocks[1] as MdBlock.BulletItem).depth)
    }

    @Test
    fun `numbered lists keep the model's own numbering`() {
        val blocks = parseMarkdownBlocks("1. first\n2) second")
        assertEquals("1", (blocks[0] as MdBlock.NumberedItem).marker)
        assertEquals("second", (blocks[1] as MdBlock.NumberedItem).text)
    }

    @Test
    fun `fenced code keeps its content literal`() {
        val blocks = parseMarkdownBlocks("```kotlin\nval x = **not bold**\n```")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = **not bold**", code.code)
    }

    @Test
    fun `an unclosed code fence still terminates`() {
        val blocks = parseMarkdownBlocks("```\nline one\nline two")
        val code = blocks.single() as MdBlock.CodeBlock
        assertEquals("line one\nline two", code.code)
    }

    @Test
    fun `quotes and dividers are recognised`() {
        val blocks = parseMarkdownBlocks("> think about it\n\n---")
        assertEquals(MdBlock.Quote("think about it"), blocks[0])
        assertEquals(MdBlock.Divider, blocks[1])
    }

    @Test
    fun `a pipe table parses into header and rows`() {
        val blocks = parseMarkdownBlocks(
            """
            | Day | Done |
            |-----|------|
            | Mon | 3/4  |
            | Tue | 2/4  |
            """.trimIndent()
        )
        val table = blocks.single() as MdBlock.Table
        assertEquals(listOf("Day", "Done"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Tue", "2/4"), table.rows[1])
    }

    @Test
    fun `a lone pipe line is prose, not a table`() {
        val blocks = parseMarkdownBlocks("Use a | b to split")
        assertTrue(blocks.single() is MdBlock.Paragraph)
    }

    @Test
    fun `empty input produces no blocks rather than an empty paragraph`() {
        assertTrue(parseMarkdownBlocks("").isEmpty())
        assertTrue(parseMarkdownBlocks("   \n\n  ").isEmpty())
    }
}

class MarkdownInlineTest {

    private fun styles(text: String) = buildInlineMarkdown(text, SpanStyle()).spanStyles

    @Test
    fun `markers are stripped from the rendered text`() {
        assertEquals("bold", buildInlineMarkdown("**bold**").text)
        assertEquals("italic", buildInlineMarkdown("*italic*").text)
        assertEquals("code", buildInlineMarkdown("`code`").text)
        assertEquals("gone", buildInlineMarkdown("~~gone~~").text)
        assertEquals(
            "Moved Gym to 19:00 today",
            buildInlineMarkdown("Moved **Gym** to `19:00` today").text
        )
    }

    @Test
    fun `emphasis applies the matching style`() {
        assertTrue(styles("**bold**").any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(styles("*italic*").any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(styles("_italic_").any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(
            styles("~~gone~~").any { it.item.textDecoration == TextDecoration.LineThrough }
        )
    }

    @Test
    fun `emphasis nests`() {
        val result = buildInlineMarkdown("**bold with *italic* inside**")
        assertEquals("bold with italic inside", result.text)
        assertTrue(result.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(result.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }

    @Test
    fun `code spans suppress emphasis inside them`() {
        val result = buildInlineMarkdown("`**not bold**`")
        assertEquals("**not bold**", result.text)
        assertTrue(result.spanStyles.none { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `unmatched markers survive as literal characters`() {
        assertEquals("2 * 3 = 6", buildInlineMarkdown("2 * 3 = 6").text)
        assertEquals("a **dangling", buildInlineMarkdown("a **dangling").text)
        assertEquals("cost `50", buildInlineMarkdown("cost `50").text)
        assertEquals("snake_case_name", buildInlineMarkdown("snake_case_name").text)
    }

    @Test
    fun `underscores inside words stay literal but real italics still work`() {
        // The coach talks about get_schedule and task_id constantly; those must not go italic.
        assertEquals("call get_schedule first", buildInlineMarkdown("call get_schedule first").text)
        assertEquals("task_id and item_id", buildInlineMarkdown("task_id and item_id").text)
        assertTrue(styles("call get_schedule first").none { it.item.fontStyle == FontStyle.Italic })

        assertEquals("really", buildInlineMarkdown("_really_").text)
        assertTrue(styles("_really_").any { it.item.fontStyle == FontStyle.Italic })
        assertEquals("do this now", buildInlineMarkdown("do _this_ now").text)
    }

    @Test
    fun `double underscores follow the same word-boundary rule`() {
        assertEquals("SOME__CONSTANT__NAME", buildInlineMarkdown("SOME__CONSTANT__NAME").text)
        assertEquals("bold", buildInlineMarkdown("__bold__").text)
        assertTrue(styles("__bold__").any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `links render their label and register an annotation`() {
        val result = buildInlineMarkdown("see [the docs](https://example.com/x)")
        assertEquals("see the docs", result.text)
        assertEquals(1, result.getLinkAnnotations(0, result.length).size)
    }

    @Test
    fun `a malformed link degrades to plain text`() {
        assertEquals("[label] no url", buildInlineMarkdown("[label] no url").text)
        assertEquals("[unclosed", buildInlineMarkdown("[unclosed").text)
    }

    @Test
    fun `plain text passes through untouched`() {
        val plain = "Finish the report before 5pm, then take a break."
        assertEquals(plain, buildInlineMarkdown(plain).text)
    }
}
