package org.ziyoffice.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Excel-style number and date rendering for cell values.
 *
 * The patterns are the format codes found in a spreadsheet's numFmt entries:
 * "0.00", "#,##0", "0%", "m/d/yyyy" and friends. Anything this renderer does
 * not understand falls back to [generalNumber], which is what a spreadsheet
 * shows for an unformatted cell - never an error, never a raw serial number
 * pretending to be a date.
 */

/** Renders [value] the way a cell shows it without an explicit format. */
fun generalNumber(value: Double): String =
    if (value.isFinite() && value == floor(value) && abs(value) < 1e15) {
        value.toLong().toString()
    } else {
        value.toString()
    }

/** Renders [value] using an Excel format code; unknown codes fall back to General. */
fun formatNumber(value: Double, pattern: String?): String {
    if (pattern.isNullOrEmpty() || pattern == "@") return generalNumber(value)
    if (isDateFormat(pattern)) return formatDateValue(value, pattern)
    return formatNumericPattern(value, pattern) ?: generalNumber(value)
}

/** Renders any [CellValue] under the given format code. */
fun formatCellValue(value: CellValue, pattern: String?): String = when (value) {
    is CellValue.Number -> formatNumber(value.number, pattern)
    is CellValue.Text -> value.text
    is CellValue.Bool -> if (value.value) "TRUE" else "FALSE"
    is CellValue.Error -> value.message
}

/**
 * True when [pattern] describes a date and/or time. Bracket sections (colour and
 * condition codes) are ignored, so `[Red]0.00` stays numeric.
 */
fun isDateFormat(pattern: String?): Boolean {
    if (pattern.isNullOrEmpty()) return false
    val stripped = stripBracketSections(pattern)
    if (stripped.contains("AM/PM", ignoreCase = true)) return true
    return stripped.any { it.lowercaseChar() in "ymdhs" }
}

/**
 * Converts an Excel serial number to a date/time.
 *
 * Serial 1 is 1900-01-01. Serials 1..59 map directly; serial 60 is the phantom
 * 1900-02-29 that Lotus 1-2-3 introduced and every spreadsheet still carries, so
 * it is folded onto 1900-02-28. From 61 onwards the epoch is 1899-12-30.
 */
fun serialToDateTime(serial: Double): LocalDateTime {
    val wholeDays = floor(serial).toLong()
    val fraction = serial - wholeDays
    val date = when {
        wholeDays < 60 -> LocalDateTime.of(1899, 12, 31, 0, 0).plusDays(wholeDays)
        wholeDays == 60L -> LocalDateTime.of(1900, 2, 28, 0, 0)
        else -> LocalDateTime.of(1899, 12, 30, 0, 0).plusDays(wholeDays)
    }
    // Round to the nearest second so 0.999999 does not render as 23:59:59.
    val seconds = (fraction * 86_400).roundToLong()
    return date.plusSeconds(seconds)
}

/** Inverse of [serialToDateTime]; the time of day becomes the fractional part. */
fun dateTimeToSerial(dateTime: LocalDateTime): Double {
    val epoch = if (dateTime.toLocalDate().isBefore(java.time.LocalDate.of(1900, 3, 1))) {
        LocalDateTime.of(1899, 12, 31, 0, 0)
    } else {
        LocalDateTime.of(1899, 12, 30, 0, 0)
    }
    val seconds = java.time.temporal.ChronoUnit.SECONDS.between(epoch, dateTime)
    return seconds / 86_400.0
}

private fun formatDateValue(serial: Double, pattern: String): String {
    val dateTime = serialToDateTime(serial)
    val tokens = tokenizeDatePattern(pattern)
    if (tokens.isEmpty()) return generalNumber(serial)
    val hasAmPm = tokens.any { it is DateToken.AmPm }
    val resolved = resolveMinutes(tokens)
    val formatterPattern = buildString {
        resolved.forEach { token ->
            when (token) {
                is DateToken.Literal -> {
                    if (token.text.isNotEmpty()) {
                        append('\'')
                        append(token.text.replace("'", "''"))
                        append('\'')
                    }
                }

                is DateToken.Field -> append(
                    when (token.kind) {
                        DateKind.YEAR -> if (token.length <= 2) "uu" else "uuuu"
                        DateKind.MONTH -> when {
                            token.length >= 4 -> "MMMM"
                            token.length == 3 -> "MMM"
                            token.length == 2 -> "MM"
                            else -> "M"
                        }

                        DateKind.DAY -> when {
                            token.length >= 4 -> "EEEE"
                            token.length == 3 -> "EEE"
                            token.length == 2 -> "dd"
                            else -> "d"
                        }

                        DateKind.HOUR -> when {
                            hasAmPm -> if (token.length == 1) "h" else "hh"
                            else -> if (token.length == 1) "H" else "HH"
                        }

                        DateKind.MINUTE -> if (token.length == 1) "m" else "mm"
                        DateKind.SECOND -> if (token.length == 1) "s" else "ss"
                    },
                )

                DateToken.AmPm -> append('a')
            }
        }
    }
    return runCatching {
        DateTimeFormatter.ofPattern(formatterPattern, Locale.getDefault()).format(dateTime)
    }.getOrElse { generalNumber(serial) }
}

private sealed interface DateToken {
    data class Literal(val text: String) : DateToken
    data class Field(val kind: DateKind, val length: Int) : DateToken
    data object AmPm : DateToken
}

private enum class DateKind { YEAR, MONTH, DAY, HOUR, MINUTE, SECOND }

private fun tokenizeDatePattern(pattern: String): List<DateToken> {
    val tokens = mutableListOf<DateToken>()
    val literal = StringBuilder()
    fun flush() {
        if (literal.isNotEmpty()) {
            tokens += DateToken.Literal(literal.toString())
            literal.clear()
        }
    }

    var i = 0
    while (i < pattern.length) {
        val ch = pattern[i]
        when {
            ch == '[' -> {
                // Colour / condition section: not part of the rendered text.
                val close = pattern.indexOf(']', i + 1)
                i = if (close > i) close + 1 else i + 1
            }

            ch == '\\' && i + 1 < pattern.length -> {
                literal.append(pattern[i + 1])
                i += 2
            }

            ch == '"' -> {
                val close = pattern.indexOf('"', i + 1)
                if (close > i) {
                    literal.append(pattern.substring(i + 1, close))
                    i = close + 1
                } else {
                    i++
                }
            }

            pattern.startsWith("AM/PM", i, ignoreCase = true) -> {
                flush()
                tokens += DateToken.AmPm
                i += 5
            }

            ch.lowercaseChar() in "ymdhs" -> {
                val lower = ch.lowercaseChar()
                var end = i
                while (end < pattern.length && pattern[end].lowercaseChar() == lower) end++
                flush()
                tokens += DateToken.Field(kindOf(lower), end - i)
                i = end
            }

            else -> {
                literal.append(ch)
                i++
            }
        }
    }
    flush()
    return tokens
}

private fun kindOf(letter: Char): DateKind = when (letter) {
    'y' -> DateKind.YEAR
    'm' -> DateKind.MONTH
    'd' -> DateKind.DAY
    'h' -> DateKind.HOUR
    else -> DateKind.SECOND
}

/**
 * Excel writes both month and minute as `m`; the surrounding tokens decide.
 * An `m` run is minutes when it follows an hour token or precedes a seconds token.
 */
private fun resolveMinutes(tokens: List<DateToken>): List<DateToken> {
    val significant = tokens.indices.filter { tokens[it] !is DateToken.Literal }
    return tokens.mapIndexed { index, token ->
        if (token !is DateToken.Field || token.kind != DateKind.MONTH) return@mapIndexed token
        val position = significant.indexOf(index)
        val previous = significant.getOrNull(position - 1)?.let { tokens[it] }
        val next = significant.getOrNull(position + 1)?.let { tokens[it] }
        val isMinute = (previous as? DateToken.Field)?.kind == DateKind.HOUR ||
            (next as? DateToken.Field)?.kind == DateKind.SECOND
        if (isMinute) DateToken.Field(DateKind.MINUTE, token.length) else token
    }
}

private fun stripBracketSections(pattern: String): String = buildString {
    var i = 0
    while (i < pattern.length) {
        if (pattern[i] == '[') {
            val close = pattern.indexOf(']', i + 1)
            i = if (close > i) close + 1 else i + 1
        } else {
            append(pattern[i])
            i++
        }
    }
}

/**
 * Renders the numeric subset of Excel format codes: `;`-separated sections,
 * thousands separators, fixed decimals, percentages and literal prefixes or
 * suffixes (currency symbols, parentheses for negatives).
 *
 * Returns null for codes it cannot render faithfully — scientific notation,
 * fractions and conditional sections — so the caller can fall back to General.
 */
private fun formatNumericPattern(value: Double, pattern: String): String? {
    val sections = splitSections(pattern)
    if (sections.isEmpty()) return null
    if (sections.any { it.contains('E') || it.contains('/') && !it.contains("\"/\"") }) return null

    // A negative section renders its own magnitude; the sign lives in that section's
    // literal text, not in the number, so "-0.00;0.00" does not double the minus.
    val negativeSection = value < 0 && sections.size > 1
    val (section, magnitude) = when {
        negativeSection -> sections[1] to -value
        value == 0.0 && sections.size > 2 -> sections[2] to value
        // One section covers negatives too; the renderer adds the minus itself.
        else -> sections[0] to kotlin.math.abs(value)
    }

    val cleaned = stripBracketSections(section).replace("\\", "")
    val digits = cleaned.filter { it == '0' || it == '#' || it == '.' || it == ',' || it == '%' }
    if (digits.none { it == '0' || it == '#' }) return null

    val firstDigit = cleaned.indexOfFirst { it == '0' || it == '#' }
    val lastDigit = cleaned.indexOfLast { it == '0' || it == '#' }
    if (firstDigit < 0 || lastDigit < firstDigit) return null

    val prefix = unquoteLiterals(cleaned.substring(0, firstDigit))
    val core = cleaned.substring(firstDigit, lastDigit + 1)
    val suffix = unquoteLiterals(cleaned.substring(lastDigit + 1))

    val percent = cleaned.count { it == '%' }
    val scaled = magnitude * Math.pow(100.0, percent.toDouble())

    val dot = core.indexOf('.')
    val integerPart = if (dot >= 0) core.substring(0, dot) else core
    val fractionPart = if (dot >= 0) core.substring(dot + 1) else ""

    val decimals = fractionPart.count { it == '0' || it == '#' }
    val minIntegerDigits = integerPart.count { it == '0' }
    val grouping = core.contains(',')

    val rounded = String.format(Locale.US, "%.${decimals}f", scaled)
    val pieces = rounded.split('.')
    var integerText = pieces[0]
    if (minIntegerDigits > integerText.length) {
        integerText = integerText.padStart(minIntegerDigits, '0')
    }
    if (grouping) integerText = groupThousands(integerText)
    val fractionText = pieces.getOrNull(1).orEmpty()
    val numberText = if (decimals > 0) "$integerText.$fractionText" else integerText

    val sign = if (value < 0 && !negativeSection) "-" else ""
    return sign + prefix + numberText + suffix
}

private fun splitSections(pattern: String): List<String> {
    val sections = mutableListOf<String>()
    val current = StringBuilder()
    var inQuote = false
    var i = 0
    while (i < pattern.length) {
        val ch = pattern[i]
        when {
            ch == '"' -> {
                inQuote = !inQuote
                current.append(ch)
            }

            ch == ';' && !inQuote -> {
                sections += current.toString()
                current.clear()
            }

            else -> current.append(ch)
        }
        i++
    }
    sections += current.toString()
    return sections.filter { it.isNotEmpty() }
}

/** Drops the double quotes that delimit literal text in a format code. */
private fun unquoteLiterals(text: String): String = text.replace("\"", "")

private fun groupThousands(digits: String): String {
    val builder = StringBuilder()
    digits.forEachIndexed { index, ch ->
        if (index > 0 && (digits.length - index) % 3 == 0) builder.append(',')
        builder.append(ch)
    }
    return builder.toString()
}

private val DATE_INPUT_PATTERNS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US),
    DateTimeFormatter.ofPattern("d/M/yyyy", Locale.US),
    DateTimeFormatter.ofPattern("d-M-yyyy", Locale.US),
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US),
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US),
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US),
)

private val TIME_INPUT_PATTERNS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("H:mm", Locale.US),
    DateTimeFormatter.ofPattern("H:mm:ss", Locale.US),
    DateTimeFormatter.ofPattern("h:mm a", Locale.US),
    DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US),
)

private val DATE_TIME_INPUT_PATTERNS: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm", Locale.US),
    DateTimeFormatter.ofPattern("d/M/yyyy H:mm", Locale.US),
    DateTimeFormatter.ofPattern("d MMM yyyy H:mm", Locale.US),
)

/**
 * Parses text a user typed into a date- or time-formatted cell and returns the
 * serial number to store, or null when the text is not a date this app accepts.
 *
 * Only called for cells whose format is a date/time code: typing "3/4" in a
 * General cell must stay the text the user typed, not silently become a date.
 */
fun parseDateInput(text: String, reference: LocalDateTime = LocalDateTime.now()): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    DATE_TIME_INPUT_PATTERNS.forEach { formatter ->
        runCatching {
            val parsed = java.time.LocalDateTime.parse(trimmed, formatter)
            return dateTimeToSerial(parsed)
        }
    }
    TIME_INPUT_PATTERNS.forEach { formatter ->
        runCatching {
            val parsed = java.time.LocalTime.parse(trimmed, formatter)
            return dateTimeToSerial(reference.toLocalDate().atTime(parsed))
        }
    }
    DATE_INPUT_PATTERNS.forEach { formatter ->
        runCatching {
            val parsed = java.time.LocalDate.parse(trimmed, formatter)
            return dateTimeToSerial(parsed.atStartOfDay())
        }
    }
    return null
}
