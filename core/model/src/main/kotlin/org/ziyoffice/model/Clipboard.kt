package org.ziyoffice.model

/**
 * What comes off the clipboard and what goes onto it.
 *
 * The clipboard is a plain string of text, but the apps that put things
 * there also put HTML beside it, and the HTML carries the formatting. A
 * document is built out of paragraphs and runs, so the work is turning one
 * into the other.
 *
 * The reading is deliberately small: the tags an office app actually puts
 * on the clipboard, and nothing else. A tag that is not understood is
 * stepped over and its text is kept, which is what a reader should do with
 * markup it does not recognise.
 */

/** The paragraphs a block of plain text becomes: one per line. */
fun textToParagraphs(text: String): List<Paragraph> =
    text.split("\n").map { line ->
        Paragraph(runs = listOf(Run(text = line.trimEnd('\r'))))
    }

/** The runs a piece of HTML becomes, with the formatting the HTML carries. */
fun htmlToRuns(html: String): List<Run> = htmlLines(html).flatten()

/** The paragraphs a piece of HTML becomes: one per line the HTML breaks at. */
fun htmlToParagraphs(html: String): List<Paragraph> =
    htmlLines(html).map { runs ->
        Paragraph(runs = runs.ifEmpty { listOf(Run(text = "")) })
    }

/** The text of a paragraph, for the clipboard. */
fun Paragraph.toClipboardText(): String = runs.joinToString("") { it.text }

/**
 * The text of the part of a paragraph between [start] and [end].
 *
 * A range that reaches past the end of the paragraph stops at its end, and
 * a range that starts before it starts at its beginning.
 */
fun Paragraph.toClipboardText(start: Int, end: Int): String {
    val from = start.coerceIn(0, textLength())
    val to = end.coerceIn(from, textLength())
    if (from == to) return ""
    val builder = StringBuilder()
    var offset = 0
    runs.forEach { run ->
        val runStart = offset
        val runEnd = offset + run.text.length
        offset = runEnd
        val overlapStart = maxOf(runStart, from)
        val overlapEnd = minOf(runEnd, to)
        if (overlapEnd > overlapStart) {
            builder.append(run.text.substring(overlapStart - runStart, overlapEnd - runStart))
        }
    }
    return builder.toString()
}

/**
 * The HTML of the part of a paragraph between [start] and [end].
 *
 * The formatting of the runs the range covers is kept, so a copied piece of
 * a sentence arrives with its bold and its italic still on it.
 */
fun Paragraph.toClipboardHtml(start: Int, end: Int): String {
    val from = start.coerceIn(0, textLength())
    val to = end.coerceIn(from, textLength())
    if (from == to) return "<p></p>"
    val body = StringBuilder()
    var offset = 0
    runs.forEach { run ->
        val runStart = offset
        val runEnd = offset + run.text.length
        offset = runEnd
        val overlapStart = maxOf(runStart, from)
        val overlapEnd = minOf(runEnd, to)
        if (overlapEnd > overlapStart) {
            val piece = run.copy(
                text = run.text.substring(overlapStart - runStart, overlapEnd - runStart),
            )
            body.append(piece.toHtml())
        }
    }
    return "<p>$body</p>"
}

/** How many characters a paragraph holds. */
fun Paragraph.textLength(): Int = runs.sumOf { it.text.length }

/** The HTML of a paragraph, for the clipboard. */
fun Paragraph.toClipboardHtml(): String {
    val body = runs.joinToString("") { run -> run.toHtml() }
    return "<p>$body</p>"
}

/** The text of a document, for the clipboard: one paragraph per line. */
fun Document.toClipboardText(): String = blocks.joinToString("\n") { block ->
    when (block) {
        is Paragraph -> block.toClipboardText()
        is Table -> block.rows.joinToString("\n") { row ->
            row.cells.joinToString("\t") { cell ->
                cell.blocks.filterIsInstance<Paragraph>().joinToString("\n") {
                    it.toClipboardText()
                }
            }
        }
    }
}

/**
 * Puts [paragraphs] into the document at [path].
 *
 * The first one takes the place of the paragraph at [path] and the rest are
 * added after it. A paragraph that is empty and alone is left out, so
 * pasting a blank line does not add a blank one.
 */
fun Document.withPastedParagraphs(path: FocusPath, paragraphs: List<Paragraph>): Document {
    // A paste that is only blank does nothing. A blank line in the middle of
    // a paste is a real line, though, and is kept.
    if (paragraphs.isEmpty() || paragraphs.all { it.textLength() == 0 }) return this
    var result = mapParagraphAt(path) { paragraphs.first() }
    var current = path
    paragraphs.drop(1).forEach { paragraph ->
        result = result.withParagraphAddedAfter(current, paragraph)
        current = if (current.cellRow == null) {
            current.copy(blockIndex = current.blockIndex + 1)
        } else {
            current.copy(cellParagraph = current.cellParagraph + 1)
        }
    }
    return result
}

/** The HTML of a document, for the clipboard. */
fun Document.toClipboardHtml(): String = blocks.joinToString("") { block ->
    when (block) {
        is Paragraph -> block.toClipboardHtml()
        is Table -> block.rows.joinToString("") { row ->
            "<tr>" + row.cells.joinToString("") { cell ->
                "<td>" + cell.blocks.filterIsInstance<Paragraph>().joinToString("") {
                    it.toClipboardHtml()
                } + "</td>"
            } + "</tr>"
        }
    }
}

/** One run as HTML, with the tags that carry its formatting. */
private fun Run.toHtml(): String {
    var text = escapeHtml(this.text)
    if (strike) text = "<s>$text</s>"
    if (underline) text = "<u>$text</u>"
    if (italic) text = "<i>$text</i>"
    if (bold) text = "<b>$text</b>"
    return text
}

/** The characters that mean something in HTML, escaped. */
private fun escapeHtml(text: String): String = buildString {
    text.forEach { character ->
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(character)
        }
    }
}

// ------------------------------------------------------------------ reading

/** The formatting a run carries, as it is read from HTML. */
private data class Format(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sizePt: Float? = null,
    val colorRgb: Int? = null,
)

/**
 * The lines of an HTML fragment, each a list of runs.
 *
 * A line ends at a line break or at the end of a block, which is where a
 * paragraph begins in the document the HTML came from.
 */
private fun htmlLines(html: String): List<List<Run>> {
    val lines = mutableListOf<MutableList<Run>>()
    var current = mutableListOf<Run>()
    val buffer = StringBuilder()
    var format = Format()
    val stack = mutableListOf<Format>()

    fun flush() {
        if (buffer.isNotEmpty()) {
            current += Run(
                text = buffer.toString(),
                bold = format.bold,
                italic = format.italic,
                underline = format.underline,
                strike = format.strike,
                sizePt = format.sizePt,
                colorRgb = format.colorRgb,
            )
            buffer.clear()
        }
    }

    fun endLine() {
        flush()
        lines += current
        current = mutableListOf()
    }

    var at = 0
    while (at < html.length) {
        val character = html[at]
        if (character == '&') {
            // An entity stands for one character. One that is not known is
            // kept as it is, so nothing is lost.
            val end = html.indexOf(';', at)
            if (end in (at + 1)..(at + 12)) {
                val entity = html.substring(at + 1, end)
                val decoded = decodeEntity(entity)
                if (decoded.isNotEmpty()) {
                    buffer.append(decoded)
                    at = end + 1
                    continue
                }
            }
            buffer.append(character)
            at++
            continue
        }
        if (character != '<') {
            buffer.append(character)
            at++
            continue
        }
        val close = html.indexOf('>', at)
        if (close < 0) {
            // A tag that is never closed is text as far as the reader is
            // concerned.
            buffer.append(html.substring(at))
            break
        }
        val tag = html.substring(at + 1, close).trim()
        at = close + 1
        if (tag.startsWith("!") || tag.startsWith("?")) continue

        val closing = tag.startsWith("/")
        val name = tag.removePrefix("/").substringBefore(' ').substringBefore('/').lowercase()
        val attributes = tag.substringAfter(' ', "").takeIf { it.isNotBlank() } ?: ""

        when {
            closing -> {
                flush()
                // The stack holds the format each open tag brought, so
                // closing a tag puts back what was there before it.
                if (stack.isNotEmpty()) format = stack.removeAt(stack.lastIndex)
            }
            name == "br" -> endLine()
            name == "p" || name == "div" || name == "li" || name == "tr" -> {
                flush()
                if (current.isNotEmpty() || lines.isNotEmpty()) endLine()
                stack += format
                format = Format()
            }
            else -> {
                flush()
                stack += format
                format = format.forTag(name, attributes)
            }
        }
    }
    flush()
    if (current.isNotEmpty() || lines.isEmpty()) lines += current
    return lines
}

/** The format a tag brings, on top of the one that was there. */
private fun Format.forTag(name: String, attributes: String): Format = when (name) {
    "b", "strong" -> copy(bold = true)
    "i", "em" -> copy(italic = true)
    "u", "ins" -> copy(underline = true)
    "s", "strike", "del" -> copy(strike = true)
    "span", "font", "p", "div" -> {
        val style = attributes.styleOf()
        var result = this
        if (style.contains("font-weight:bold") || style.contains("font-weight: bold")) {
            result = result.copy(bold = true)
        }
        if (style.contains("font-style:italic") || style.contains("font-style: italic")) {
            result = result.copy(italic = true)
        }
        if (style.contains("text-decoration:underline") ||
            style.contains("text-decoration: underline")
        ) {
            result = result.copy(underline = true)
        }
        if (style.contains("text-decoration:line-through") ||
            style.contains("text-decoration: line-through")
        ) {
            result = result.copy(strike = true)
        }
        style.fontSize()?.let { result = result.copy(sizePt = it) }
        style.colour()?.let { result = result.copy(colorRgb = it) }
        result
    }
    else -> this
}

/** The style attribute of a tag, in lower case. */
private fun String.styleOf(): String {
    val at = indexOf("style=")
    if (at < 0) return ""
    val rest = substring(at + 6).trimStart()
    val quote = rest.firstOrNull()
    if (quote != '"' && quote != '\'') return ""
    val end = rest.indexOf(quote, 1)
    if (end < 0) return ""
    return rest.substring(1, end).lowercase()
}

/** The size a style asks for, in points. */
private fun String.fontSize(): Float? {
    val at = indexOf("font-size:")
    if (at < 0) return null
    val rest = substring(at + 10).trim().trimEnd(';').trim()
    return when {
        rest.endsWith("pt") -> rest.dropLast(2).trim().toFloatOrNull()
        rest.endsWith("px") -> rest.dropLast(2).trim().toFloatOrNull()?.times(0.75f)
        else -> null
    }
}

/** The colour a style asks for, as 0xRRGGBB. */
private fun String.colour(): Int? {
    val at = indexOf("color:")
    if (at < 0) return null
    val rest = substring(at + 6).trim().trimEnd(';').trim()
    if (!rest.startsWith("#")) return null
    val digits = rest.drop(1)
    return when (digits.length) {
        3 -> {
            val r = digits[0].digitToIntOrNull(16) ?: return null
            val g = digits[1].digitToIntOrNull(16) ?: return null
            val b = digits[2].digitToIntOrNull(16) ?: return null
            (r * 17 shl 16) or (g * 17 shl 8) or (b * 17)
        }
        6 -> digits.toIntOrNull(16)
        else -> null
    }
}

/** The characters of an HTML entity, as the character it stands for. */
internal fun decodeEntity(entity: String): String = when (entity.lowercase()) {
    "amp" -> "&"
    "lt" -> "<"
    "gt" -> ">"
    "quot" -> "\""
    "apos" -> "'"
    "nbsp" -> " "
    else -> {
        if (entity.startsWith("#")) {
            val code = if (entity.startsWith("#x") || entity.startsWith("#X")) {
                entity.drop(2).toIntOrNull(16)
            } else {
                entity.drop(1).toIntOrNull()
            }
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else ""
        } else {
            ""
        }
    }
}
