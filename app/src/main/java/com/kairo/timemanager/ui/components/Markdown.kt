package com.kairo.timemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/*
 * A deliberately small Markdown renderer for coach replies.
 *
 * Models emit Markdown whether or not you ask them to, so the choice is between rendering it and
 * showing the user raw asterisks. This covers what actually turns up in a chat reply — emphasis,
 * code, lists, headings, quotes, rules, links and simple tables — and renders anything it doesn't
 * recognise as plain text rather than dropping it. No third-party dependency for ~250 lines.
 */

// ---- block model --------------------------------------------------------

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class BulletItem(val text: String, val depth: Int) : MdBlock
    data class NumberedItem(val marker: String, val text: String, val depth: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeBlock(val code: String, val language: String?) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

private val headingRegex = Regex("^(#{1,6})\\s+(.*)$")
private val bulletRegex = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val numberedRegex = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val quoteRegex = Regex("^>\\s?(.*)$")
private val dividerRegex = Regex("^\\s*(-{3,}|_{3,}|\\*{3,})\\s*$")
private val tableSeparatorRegex = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

internal fun parseMarkdownBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.setLength(0)
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        // Fenced code block — everything inside is literal, including Markdown syntax.
        if (trimmed.startsWith("```")) {
            flushParagraph()
            val language = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val code = StringBuilder()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) {
                code.appendLine(lines[index])
                index++
            }
            index++ // consume the closing fence, if the model bothered to write one
            blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'), language)
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            index++
            continue
        }

        if (dividerRegex.matches(line)) {
            flushParagraph()
            blocks += MdBlock.Divider
            index++
            continue
        }

        // A pipe row followed by a dashed row is a table; a lone pipe row is just prose.
        if (trimmed.contains('|') && index + 1 < lines.size &&
            tableSeparatorRegex.matches(lines[index + 1]) && lines[index + 1].contains('-')
        ) {
            flushParagraph()
            val header = splitTableRow(line)
            val rows = mutableListOf<List<String>>()
            index += 2
            while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                rows += splitTableRow(lines[index])
                index++
            }
            blocks += MdBlock.Table(header, rows)
            continue
        }

        val heading = headingRegex.find(line)
        if (heading != null) {
            flushParagraph()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            index++
            continue
        }

        val numbered = numberedRegex.find(line)
        if (numbered != null) {
            flushParagraph()
            blocks += MdBlock.NumberedItem(
                marker = numbered.groupValues[2],
                text = numbered.groupValues[3].trim(),
                depth = numbered.groupValues[1].length / 2
            )
            index++
            continue
        }

        val bullet = bulletRegex.find(line)
        if (bullet != null) {
            flushParagraph()
            blocks += MdBlock.BulletItem(
                text = bullet.groupValues[2].trim(),
                depth = bullet.groupValues[1].length / 2
            )
            index++
            continue
        }

        val quote = quoteRegex.find(line)
        if (quote != null) {
            flushParagraph()
            blocks += MdBlock.Quote(quote.groupValues[1].trim())
            index++
            continue
        }

        // Plain prose: join wrapped lines into one paragraph.
        if (paragraph.isNotEmpty()) paragraph.append(' ')
        paragraph.append(trimmed)
        index++
    }
    flushParagraph()
    return blocks
}

private fun splitTableRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

// ---- inline spans -------------------------------------------------------

/**
 * Scans emphasis markers by hand rather than by regex so nesting works
 * (`**bold with *italic* inside**`) and code spans stay literal.
 */
private fun appendInline(
    builder: AnnotatedString.Builder,
    text: String,
    base: SpanStyle,
    codeColor: Color,
    codeBackground: Color
) {
    var index = 0
    val plain = StringBuilder()

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            builder.withStyle(base) { append(plain.toString()) }
            plain.setLength(0)
        }
    }

    fun closingIndex(marker: String, from: Int): Int {
        val at = text.indexOf(marker, from)
        return if (at > from) at else -1
    }

    /**
     * Underscore emphasis only counts at word boundaries. Without this, the app's own vocabulary —
     * `task_id`, `get_schedule`, `snake_case` — would silently render as italics with the
     * underscores eaten.
     */
    fun isWordCharacter(position: Int): Boolean =
        position in text.indices && (text[position].isLetterOrDigit() || text[position] == '_')

    fun underscoreOpensEmphasis(at: Int): Boolean = !isWordCharacter(at - 1)

    fun underscoreClosingIndex(marker: String, from: Int): Int {
        var search = from
        while (true) {
            val at = text.indexOf(marker, search)
            if (at <= from) return -1
            val after = at + marker.length
            if (!isWordCharacter(after)) return at
            search = at + 1
        }
    }

    while (index < text.length) {
        val rest = text.length - index
        when {
            text[index] == '`' -> {
                val close = closingIndex("`", index + 1)
                if (close < 0) {
                    plain.append(text[index]); index++
                } else {
                    flushPlain()
                    builder.withStyle(
                        base.copy(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeColor,
                            fontSize = if (base.fontSize.isSpecified) base.fontSize * 0.92f
                            else base.fontSize
                        )
                    ) { append(text.substring(index + 1, close)) }
                    index = close + 1
                }
            }

            rest >= 4 && (text.startsWith("**", index) || text.startsWith("__", index)) -> {
                val marker = text.substring(index, index + 2)
                val close = if (marker == "__") {
                    if (underscoreOpensEmphasis(index)) underscoreClosingIndex(marker, index + 2) else -1
                } else {
                    closingIndex(marker, index + 2)
                }
                if (close < 0) {
                    plain.append(text[index]); index++
                } else {
                    flushPlain()
                    appendInline(
                        builder,
                        text.substring(index + 2, close),
                        base.copy(fontWeight = FontWeight.Bold),
                        codeColor,
                        codeBackground
                    )
                    index = close + 2
                }
            }

            rest >= 4 && text.startsWith("~~", index) -> {
                val close = closingIndex("~~", index + 2)
                if (close < 0) {
                    plain.append(text[index]); index++
                } else {
                    flushPlain()
                    appendInline(
                        builder,
                        text.substring(index + 2, close),
                        base.copy(textDecoration = TextDecoration.LineThrough),
                        codeColor,
                        codeBackground
                    )
                    index = close + 2
                }
            }

            (text[index] == '*' || text[index] == '_') &&
                index + 1 < text.length && !text[index + 1].isWhitespace() -> {
                val marker = text[index].toString()
                val close = if (marker == "_") {
                    if (underscoreOpensEmphasis(index)) underscoreClosingIndex(marker, index + 1) else -1
                } else {
                    closingIndex(marker, index + 1)
                }
                if (close < 0) {
                    plain.append(text[index]); index++
                } else {
                    flushPlain()
                    appendInline(
                        builder,
                        text.substring(index + 1, close),
                        base.copy(fontStyle = FontStyle.Italic),
                        codeColor,
                        codeBackground
                    )
                    index = close + 1
                }
            }

            text[index] == '[' -> {
                val labelEnd = text.indexOf(']', index)
                val urlStart = if (labelEnd >= 0) labelEnd + 1 else -1
                if (labelEnd < 0 || urlStart >= text.length || text[urlStart] != '(') {
                    plain.append(text[index]); index++
                } else {
                    val urlEnd = text.indexOf(')', urlStart)
                    if (urlEnd < 0) {
                        plain.append(text[index]); index++
                    } else {
                        flushPlain()
                        val label = text.substring(index + 1, labelEnd)
                        val url = text.substring(urlStart + 1, urlEnd).trim()
                        builder.pushLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = base.copy(
                                        color = codeColor,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        )
                        builder.append(label.ifBlank { url })
                        builder.pop()
                        index = urlEnd + 1
                    }
                }
            }

            else -> {
                plain.append(text[index]); index++
            }
        }
    }
    flushPlain()
}

/** Inline-span entry point, separated so the emphasis rules can be tested directly. */
internal fun buildInlineMarkdown(
    text: String,
    base: SpanStyle = SpanStyle(),
    codeColor: Color = Color.Blue,
    codeBackground: Color = Color.LightGray
): AnnotatedString = buildAnnotatedString {
    appendInline(this, text, base, codeColor, codeBackground)
}

// ---- rendering ----------------------------------------------------------

/**
 * Renders [markdown] as formatted text. Falls back to rendering the literal characters for any
 * syntax it doesn't understand, so the user never loses content to the parser.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val baseSpan = style.toSpanStyle().copy(color = color)

    fun inline(text: String, spanStyle: SpanStyle = baseSpan): AnnotatedString =
        buildAnnotatedString {
            appendInline(this, text, spanStyle, accent, codeBackground)
        }

    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(if (block is MdBlock.Paragraph) 8.dp else 6.dp))
            when (block) {
                is MdBlock.Paragraph -> Text(inline(block.text), style = style, color = color)

                is MdBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    }
                    Text(
                        inline(block.text, headingStyle.toSpanStyle().copy(color = color)),
                        style = headingStyle,
                        color = color
                    )
                }

                is MdBlock.BulletItem -> Row(
                    Modifier.padding(start = (block.depth * 14).dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("•", style = style, color = accent)
                    Spacer(Modifier.width(8.dp))
                    Text(inline(block.text), style = style, color = color, modifier = Modifier.weight(1f))
                }

                is MdBlock.NumberedItem -> Row(
                    Modifier.padding(start = (block.depth * 14).dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "${block.marker}.",
                        style = style,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(inline(block.text), style = style, color = color, modifier = Modifier.weight(1f))
                }

                is MdBlock.Quote -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent.copy(alpha = 0.6f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inline(block.text, baseSpan.copy(color = muted, fontStyle = FontStyle.Italic)),
                        style = style,
                        color = muted
                    )
                }

                is MdBlock.CodeBlock -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBackground)
                ) {
                    // Code must never force the bubble wider than the screen.
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                        if (block.language != null) {
                            Text(
                                block.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = muted
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            block.code,
                            style = style.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            color = color
                        )
                    }
                }

                is MdBlock.Table -> Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(codeBackground.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        block.header.forEach { cell ->
                            Text(
                                cell,
                                style = MaterialTheme.typography.labelSmall,
                                color = muted,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    block.rows.forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            block.header.indices.forEach { column ->
                                Text(
                                    inline(row.getOrElse(column) { "" }),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = color,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                MdBlock.Divider -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}
