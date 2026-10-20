package org.ziyoffice.model

import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * The spreadsheet function library.
 *
 * Every function takes the arguments the parser produced and returns a
 * [CellValue]. Ranges are read through the [CellResolver], so a formula that
 * reaches a cell with an error reports that error instead of a made-up number.
 *
 * The set follows the everyday spreadsheet vocabulary: text handling, maths,
 * conditional counting, lookup, statistics, dates and information tests.
 */
internal object FormulaFunctions {

    fun apply(name: String, args: List<Operand>, resolver: CellResolver): CellValue = when (name) {
        /* ----------------------------- aggregates ---------------------------- */
        "SUM" -> aggregate(args, resolver) { numbers -> CellValue.Number(numbers.sum()) }
        "AVERAGE" -> aggregate(args, resolver) { numbers ->
            if (numbers.isEmpty()) CellValue.Error("#DIV/0!") else CellValue.Number(numbers.average())
        }
        "MIN" -> aggregate(args, resolver) { numbers ->
            numbers.minOrNull()?.let { CellValue.Number(it) } ?: CellValue.Error("#VALUE!")
        }
        "MAX" -> aggregate(args, resolver) { numbers ->
            numbers.maxOrNull()?.let { CellValue.Number(it) } ?: CellValue.Error("#VALUE!")
        }
        "COUNT" -> countNumbers(args, resolver)
        "COUNTA" -> countFilled(args, resolver)
        "COUNTBLANK" -> countBlank(args, resolver)
        "MEDIAN" -> aggregate(args, resolver) { numbers ->
            if (numbers.isEmpty()) {
                CellValue.Error("#NUM!")
            } else {
                val sorted = numbers.sorted()
                val middle = sorted.size / 2
                val median = if (sorted.size % 2 == 1) {
                    sorted[middle]
                } else {
                    (sorted[middle - 1] + sorted[middle]) / 2.0
                }
                CellValue.Number(median)
            }
        }
        "LARGE" -> extreme(args, resolver, largest = true)
        "SMALL" -> extreme(args, resolver, largest = false)
        "SUMPRODUCT" -> sumProduct(args, resolver)

        /* ------------------------------ logical ------------------------------ */
        "IF" -> conditional(args, resolver)
        "IFERROR" -> ifError(args, resolver)
        "AND" -> logic(args, resolver, requireAll = true)
        "OR" -> logic(args, resolver, requireAll = false)
        "NOT" -> not(args, resolver)
        "NA" -> CellValue.Error("#N/A")

        /* ---------------------------- conditional ---------------------------- */
        "COUNTIF" -> countIf(args, resolver)
        "SUMIF" -> sumIf(args, resolver)
        "AVERAGEIF" -> averageIf(args, resolver)

        /* ------------------------------ lookup ------------------------------- */
        "VLOOKUP" -> vlookup(args, resolver)
        "INDEX" -> index(args, resolver)
        "MATCH" -> match(args, resolver)
        "CHOOSE" -> choose(args, resolver)

        /* -------------------------------- maths ------------------------------ */
        "ABS" -> singleNumber(args, resolver) { CellValue.Number(abs(it)) }
        "INT" -> singleNumber(args, resolver) { CellValue.Number(floor(it)) }
        "SIGN" -> singleNumber(args, resolver) { value ->
            CellValue.Number(if (value > 0) 1.0 else if (value < 0) -1.0 else 0.0)
        }
        "SQRT" -> singleNumber(args, resolver) { value ->
            if (value < 0) CellValue.Error("#NUM!") else CellValue.Number(sqrt(value))
        }
        "POWER" -> if (args.size != 2) {
            CellValue.Error("#VALUE!")
        } else {
            val base = numberOf(args[0], resolver)
            val exponent = numberOf(args[1], resolver)
            if (base == null || exponent == null) {
                CellValue.Error("#VALUE!")
            } else {
                CellValue.Number(base.pow(exponent))
            }
        }
        "ROUND" -> rounded(args, resolver, Rounding.ROUND)
        "ROUNDUP" -> rounded(args, resolver, Rounding.UP)
        "ROUNDDOWN" -> rounded(args, resolver, Rounding.DOWN)
        "MOD" -> modulus(args, resolver)
        "CEILING" -> stepped(args, resolver, up = true)
        "FLOOR" -> stepped(args, resolver, up = false)
        "PI" -> CellValue.Number(Math.PI)

        /* -------------------------------- text ------------------------------- */
        "LEN" -> singleText(args, resolver) { CellValue.Number(it.length.toDouble()) }
        "LEFT" -> edgeText(args, resolver, left = true)
        "RIGHT" -> edgeText(args, resolver, left = false)
        "MID" -> mid(args, resolver)
        "TRIM" -> singleText(args, resolver) { CellValue.Text(trimText(it)) }
        "UPPER" -> singleText(args, resolver) { CellValue.Text(it.uppercase()) }
        "LOWER" -> singleText(args, resolver) { CellValue.Text(it.lowercase()) }
        "PROPER" -> singleText(args, resolver) { CellValue.Text(properCase(it)) }
        "CONCATENATE" -> concatenate(args, resolver)
        "REPT" -> repeatText(args, resolver)
        "SUBSTITUTE" -> substitute(args, resolver)
        "REPLACE" -> replace(args, resolver)
        "FIND" -> findText(args, resolver, caseSensitive = true)
        "SEARCH" -> findText(args, resolver, caseSensitive = false)
        "EXACT" -> exact(args, resolver)
        "TEXT" -> text(args, resolver)
        "VALUE" -> value(args, resolver)

        /* -------------------------------- dates ------------------------------ */
        "TODAY" -> CellValue.Number(dateTimeToSerial(LocalDateTime.now().toLocalDate().atStartOfDay()))
        "NOW" -> CellValue.Number(dateTimeToSerial(LocalDateTime.now()))
        "YEAR" -> datePart(args, resolver) { it.year }
        "MONTH" -> datePart(args, resolver) { it.monthValue }
        "DAY" -> datePart(args, resolver) { it.dayOfMonth }
        "HOUR" -> datePart(args, resolver) { it.hour }
        "MINUTE" -> datePart(args, resolver) { it.minute }
        "SECOND" -> datePart(args, resolver) { it.second }
        "DATE" -> date(args, resolver)

        /* ----------------------------- information --------------------------- */
        "ISBLANK" -> information(args, resolver) { it is CellValue.Text && it.text.isEmpty() }
        "ISNUMBER" -> information(args, resolver) { it is CellValue.Number }
        "ISTEXT" -> information(args, resolver) { it is CellValue.Text }
        "ISERROR" -> information(args, resolver) { it is CellValue.Error }
        "ISLOGICAL" -> information(args, resolver) { it is CellValue.Bool }

        else -> CellValue.Error("#NAME?")
    }

    /* ------------------------------ helpers ------------------------------ */

    private enum class Rounding { ROUND, UP, DOWN }

    /** The first error among the arguments, if any. */
    private fun errorIn(args: List<Operand>, resolver: CellResolver): CellValue.Error? =
        args.firstNotNullOfOrNull { arg ->
            when (arg) {
                is Operand.Scalar -> arg.value as? CellValue.Error
                is Operand.Range -> resolver.range(arg).firstOrNull { it is CellValue.Error }
                    as? CellValue.Error
            }
        }

    private fun aggregate(
        args: List<Operand>,
        resolver: CellResolver,
        compute: (List<Double>) -> CellValue,
    ): CellValue {
        val error = errorIn(args, resolver)
        if (error != null) return error
        return compute(args.flatMap { resolver.numbers(it) })
    }

    private fun countNumbers(args: List<Operand>, resolver: CellResolver): CellValue {
        val error = errorIn(args, resolver)
        if (error != null) return error
        val count = args.sumOf { arg ->
            when (arg) {
                is Operand.Range -> resolver.range(arg).count { it is CellValue.Number }
                is Operand.Scalar -> if (arg.value is CellValue.Number) 1 else 0
            }
        }
        return CellValue.Number(count.toDouble())
    }

    private fun countFilled(args: List<Operand>, resolver: CellResolver): CellValue {
        val count = args.sumOf { arg ->
            when (arg) {
                is Operand.Range -> resolver.range(arg).count { isFilled(it) }
                is Operand.Scalar -> if (isFilled(arg.value)) 1 else 0
            }
        }
        return CellValue.Number(count.toDouble())
    }

    private fun countBlank(args: List<Operand>, resolver: CellResolver): CellValue {
        val count = args.sumOf { arg ->
            when (arg) {
                is Operand.Range -> resolver.range(arg).count { it is CellValue.Text && it.text.isEmpty() }
                is Operand.Scalar -> if (arg.value is CellValue.Text && arg.value.text.isEmpty()) 1 else 0
            }
        }
        return CellValue.Number(count.toDouble())
    }

    private fun isFilled(value: CellValue): Boolean = when (value) {
        is CellValue.Text -> value.text.isNotEmpty()
        else -> true
    }

    private fun extreme(
        args: List<Operand>,
        resolver: CellResolver,
        largest: Boolean,
    ): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val error = errorIn(args, resolver)
        if (error != null) return error
        val numbers = resolver.numbers(args[0])
        val rank = numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        if (rank < 1 || rank > numbers.size) return CellValue.Error("#NUM!")
        val sorted = numbers.sorted()
        val picked = if (largest) sorted[sorted.size - rank] else sorted[rank - 1]
        return CellValue.Number(picked)
    }

    private fun sumProduct(args: List<Operand>, resolver: CellResolver): CellValue {
        val error = errorIn(args, resolver)
        if (error != null) return error
        if (args.isEmpty()) return CellValue.Error("#VALUE!")
        val columns = args.map { arg ->
            when (arg) {
                is Operand.Range -> resolver.range(arg).map { asNumber(it) ?: 0.0 }
                is Operand.Scalar -> listOf(asNumber(arg.value) ?: 0.0)
            }
        }
        val size = columns.first().size
        if (columns.any { it.size != size }) return CellValue.Error("#VALUE!")
        var total = 0.0
        for (index in 0 until size) {
            var product = 1.0
            columns.forEach { column -> product *= column[index] }
            total += product
        }
        return CellValue.Number(total)
    }

    private fun conditional(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 2 || args.size > 3) return CellValue.Error("#VALUE!")
        val condition = resolver.scalar(args[0])
        return when {
            condition is CellValue.Error -> condition
            truthy(condition) -> resolver.scalar(args[1])
            args.size == 3 -> resolver.scalar(args[2])
            else -> CellValue.Bool(false)
        }
    }

    private fun ifError(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val value = resolver.scalar(args[0])
        return if (value is CellValue.Error) resolver.scalar(args[1]) else value
    }

    private fun logic(
        args: List<Operand>,
        resolver: CellResolver,
        requireAll: Boolean,
    ): CellValue {
        val error = errorIn(args, resolver)
        if (error != null) return error
        val numbers = args.flatMap { resolver.numbers(it) }
        if (numbers.isEmpty()) return CellValue.Error("#VALUE!")
        val result = if (requireAll) numbers.all { it != 0.0 } else numbers.any { it != 0.0 }
        return CellValue.Bool(result)
    }

    private fun not(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 1) return CellValue.Error("#VALUE!")
        val number = numberOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        return CellValue.Bool(number == 0.0)
    }

    private fun countIf(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val range = args[0] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        val criterion = resolver.scalar(args[1])
        val matches = resolver.range(range).count { matchesCriterion(it, criterion) }
        return CellValue.Number(matches.toDouble())
    }

    private fun sumIf(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 2 || args.size > 3) return CellValue.Error("#VALUE!")
        val range = args[0] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        val criterion = resolver.scalar(args[1])
        val sumRange = if (args.size == 3) {
            args[2] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        } else {
            range
        }
        val values = resolver.range(range)
        val sums = resolver.range(sumRange)
        if (values.size != sums.size) return CellValue.Error("#VALUE!")
        var total = 0.0
        values.forEachIndexed { index, value ->
            if (matchesCriterion(value, criterion)) total += asNumber(sums[index]) ?: 0.0
        }
        return CellValue.Number(total)
    }

    private fun averageIf(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 2 || args.size > 3) return CellValue.Error("#VALUE!")
        val range = args[0] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        val criterion = resolver.scalar(args[1])
        val averageRange = if (args.size == 3) {
            args[2] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        } else {
            range
        }
        val values = resolver.range(range)
        val averages = resolver.range(averageRange)
        if (values.size != averages.size) return CellValue.Error("#VALUE!")
        val picked = averages.filterIndexed { index, _ ->
            matchesCriterion(values[index], criterion)
        }.mapNotNull { asNumber(it) }
        if (picked.isEmpty()) return CellValue.Error("#DIV/0!")
        return CellValue.Number(picked.average())
    }

    private fun vlookup(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 3 || args.size > 4) return CellValue.Error("#VALUE!")
        val lookup = resolver.scalar(args[0])
        if (lookup is CellValue.Error) return lookup
        val table = args[1] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        val columnOffset = numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val approximate = if (args.size == 4) truthy(resolver.scalar(args[3])) else true

        val width = table.rightCol - table.leftCol + 1
        if (columnOffset < 1 || columnOffset > width) return CellValue.Error("#REF!")

        var foundRow: Int? = null
        for (row in table.topRow..table.bottomRow) {
            val candidate = resolver.cell(row, table.leftCol)
            if (equalValues(lookup, candidate)) {
                foundRow = row
                break
            }
            if (!approximate) continue
            // Approximate lookup assumes an ascending first column.
            val c = asNumber(candidate)
            val l = asNumber(lookup)
            if (c != null && l != null && c > l) break
        }
        if (approximate && foundRow == null) {
            // Last row whose key does not exceed the lookup value.
            for (row in table.bottomRow downTo table.topRow) {
                val c = asNumber(resolver.cell(row, table.leftCol)) ?: continue
                val l = asNumber(lookup) ?: return CellValue.Error("#VALUE!")
                if (c <= l) {
                    foundRow = row
                    break
                }
            }
        }
        val row = foundRow ?: return CellValue.Error("#N/A")
        return resolver.cell(row, table.leftCol + columnOffset - 1)
    }

    private fun index(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 2 || args.size > 3) return CellValue.Error("#VALUE!")
        val range = args[0] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        val rows = range.bottomRow - range.topRow + 1
        val columns = range.rightCol - range.leftCol + 1
        val first = numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val second = if (args.size == 3) {
            numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        } else {
            null
        }
        val (rowOffset, columnOffset) = if (second == null) {
            when {
                rows == 1 -> 0 to first - 1
                columns == 1 -> first - 1 to 0
                else -> return CellValue.Error("#VALUE!")
            }
        } else {
            first - 1 to second - 1
        }
        if (rowOffset < 0 || columnOffset < 0) return CellValue.Error("#VALUE!")
        if (rowOffset >= rows || columnOffset >= columns) return CellValue.Error("#REF!")
        return resolver.cell(range.topRow + rowOffset, range.leftCol + columnOffset)
    }

    private fun match(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 2 || args.size > 3) return CellValue.Error("#VALUE!")
        val lookup = resolver.scalar(args[0])
        if (lookup is CellValue.Error) return lookup
        val range = args[1] as? Operand.Range ?: return CellValue.Error("#VALUE!")
        val type = if (args.size == 3) {
            numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        } else {
            0
        }
        val values = resolver.range(range)
        if (type == 0) {
            val index = values.indexOfFirst { equalValues(lookup, it) }
            if (index < 0) return CellValue.Error("#N/A")
            return CellValue.Number((index + 1).toDouble())
        }
        val target = asNumber(lookup) ?: return CellValue.Error("#N/A")
        var best: Int? = null
        values.forEachIndexed { index, value ->
            val number = asNumber(value) ?: return@forEachIndexed
            val fits = if (type > 0) number <= target else number >= target
            if (fits) {
                val current = best
                if (current == null) {
                    best = index
                } else {
                    val previous = asNumber(values[current])
                    if (previous != null && (if (type > 0) number > previous else number < previous)) {
                        best = index
                    }
                }
            }
        }
        val position = best ?: return CellValue.Error("#N/A")
        return CellValue.Number((position + 1).toDouble())
    }

    private fun choose(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 2) return CellValue.Error("#VALUE!")
        val index = numberOf(args[0], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        if (index < 1 || index >= args.size) return CellValue.Error("#VALUE!")
        return resolver.scalar(args[index])
    }

    private fun singleNumber(
        args: List<Operand>,
        resolver: CellResolver,
        compute: (Double) -> CellValue,
    ): CellValue {
        if (args.size != 1) return CellValue.Error("#VALUE!")
        val value = resolver.scalar(args[0])
        if (value is CellValue.Error) return value
        val number = asNumber(value) ?: return CellValue.Error("#VALUE!")
        return compute(number)
    }

    private fun singleText(
        args: List<Operand>,
        resolver: CellResolver,
        compute: (String) -> CellValue,
    ): CellValue {
        if (args.size != 1) return CellValue.Error("#VALUE!")
        val value = resolver.scalar(args[0])
        if (value is CellValue.Error) return value
        val text = asText(value) ?: return CellValue.Error("#VALUE!")
        return compute(text)
    }

    private fun numberOf(arg: Operand, resolver: CellResolver): Double? =
        asNumber(resolver.scalar(arg))

    private fun textOf(arg: Operand, resolver: CellResolver): String? =
        asText(resolver.scalar(arg))

    private fun rounded(
        args: List<Operand>,
        resolver: CellResolver,
        mode: Rounding,
    ): CellValue {
        if (args.size < 1 || args.size > 2) return CellValue.Error("#VALUE!")
        val value = numberOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val digits = if (args.size == 2) {
            numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        } else {
            0
        }
        return CellValue.Number(roundValue(value, digits, mode))
    }

    private fun roundValue(value: Double, digits: Int, mode: Rounding): Double {
        val factor = 10.0.pow(digits)
        val scaled = value * factor
        // Excel rounds halfway cases away from zero, not towards positive
        // infinity, so -2.5 becomes -3.
        val rounded: Double = when (mode) {
            Rounding.ROUND ->
                if (scaled < 0) -(-scaled).roundToLong().toDouble() else scaled.roundToLong().toDouble()
            Rounding.UP -> if (scaled < 0) -ceil(-scaled) else ceil(scaled)
            Rounding.DOWN -> if (scaled < 0) -floor(-scaled) else floor(scaled)
        }
        return rounded / factor
    }

    private fun modulus(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val dividend = numberOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val divisor = numberOf(args[1], resolver) ?: return CellValue.Error("#VALUE!")
        if (divisor == 0.0) return CellValue.Error("#DIV/0!")
        return CellValue.Number(dividend - divisor * floor(dividend / divisor))
    }

    private fun stepped(
        args: List<Operand>,
        resolver: CellResolver,
        up: Boolean,
    ): CellValue {
        if (args.size < 1 || args.size > 2) return CellValue.Error("#VALUE!")
        val value = numberOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val step = if (args.size == 2) {
            numberOf(args[1], resolver) ?: return CellValue.Error("#VALUE!")
        } else {
            1.0
        }
        if (step == 0.0) return CellValue.Error("#DIV/0!")
        val magnitude = abs(value)
        val stepped = if (up) ceil(magnitude / abs(step)) * abs(step) else floor(magnitude / abs(step)) * abs(step)
        return CellValue.Number(if (value < 0) -stepped else stepped)
    }

    private fun edgeText(
        args: List<Operand>,
        resolver: CellResolver,
        left: Boolean,
    ): CellValue {
        if (args.size < 1 || args.size > 2) return CellValue.Error("#VALUE!")
        val text = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val count = if (args.size == 2) {
            numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        } else {
            1
        }
        if (count < 0) return CellValue.Error("#VALUE!")
        val taken = minOf(count, text.length)
        return CellValue.Text(if (left) text.substring(0, taken) else text.substring(text.length - taken))
    }

    private fun mid(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 3) return CellValue.Error("#VALUE!")
        val text = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val start = numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val length = numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        if (start < 1 || length < 0) return CellValue.Error("#VALUE!")
        if (start > text.length) return CellValue.Text("")
        val end = minOf(start + length, text.length + 1)
        return CellValue.Text(text.substring(start - 1, end - 1))
    }

    private fun concatenate(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.isEmpty()) return CellValue.Error("#VALUE!")
        val parts = args.mapNotNull { textOf(it, resolver) }
        if (parts.size != args.size) return CellValue.Error("#VALUE!")
        return CellValue.Text(parts.joinToString(""))
    }

    private fun repeatText(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val text = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val count = numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        if (count < 0) return CellValue.Error("#VALUE!")
        return CellValue.Text(text.repeat(count))
    }

    private fun substitute(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size < 3 || args.size > 4) return CellValue.Error("#VALUE!")
        val text = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val target = textOf(args[1], resolver) ?: return CellValue.Error("#VALUE!")
        val replacement = textOf(args[2], resolver) ?: return CellValue.Error("#VALUE!")
        if (target.isEmpty()) return CellValue.Text(text)
        if (args.size == 3) return CellValue.Text(text.replace(target, replacement))
        val occurrence = numberOf(args[3], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        if (occurrence < 1) return CellValue.Error("#VALUE!")
        var seen = 0
        var index = text.indexOf(target)
        while (index >= 0) {
            seen += 1
            if (seen == occurrence) {
                return CellValue.Text(
                    text.substring(0, index) + replacement + text.substring(index + target.length),
                )
            }
            index = text.indexOf(target, index + target.length)
        }
        return CellValue.Text(text)
    }

    private fun replace(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 4) return CellValue.Error("#VALUE!")
        val text = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val start = numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val length = numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val replacement = textOf(args[3], resolver) ?: return CellValue.Error("#VALUE!")
        if (start < 1 || length < 0) return CellValue.Error("#VALUE!")
        if (start > text.length) return CellValue.Error("#VALUE!")
        val head = text.substring(0, start - 1)
        val tail = text.substring(minOf(start - 1 + length, text.length))
        return CellValue.Text(head + replacement + tail)
    }

    private fun findText(
        args: List<Operand>,
        resolver: CellResolver,
        caseSensitive: Boolean,
    ): CellValue {
        if (args.size < 2 || args.size > 3) return CellValue.Error("#VALUE!")
        val needle = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val haystack = textOf(args[1], resolver) ?: return CellValue.Error("#VALUE!")
        val start = if (args.size == 3) {
            numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        } else {
            1
        }
        if (start < 1) return CellValue.Error("#VALUE!")
        if (needle.isEmpty()) return CellValue.Number(start.toDouble())
        val index = if (caseSensitive) {
            haystack.indexOf(needle, start - 1)
        } else {
            haystack.indexOf(needle, start - 1, ignoreCase = true)
        }
        if (index < 0) return CellValue.Error("#VALUE!")
        return CellValue.Number((index + 1).toDouble())
    }

    private fun exact(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val left = textOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        val right = textOf(args[1], resolver) ?: return CellValue.Error("#VALUE!")
        return CellValue.Bool(left == right)
    }

    private fun text(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 2) return CellValue.Error("#VALUE!")
        val value = resolver.scalar(args[0])
        if (value is CellValue.Error) return value
        val pattern = textOf(args[1], resolver) ?: return CellValue.Error("#VALUE!")
        return when (value) {
            is CellValue.Number -> CellValue.Text(formatNumber(value.number, pattern))
            is CellValue.Text -> CellValue.Text(value.text)
            is CellValue.Bool -> CellValue.Text(if (value.value) "TRUE" else "FALSE")
            is CellValue.Error -> value
        }
    }

    private fun value(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 1) return CellValue.Error("#VALUE!")
        val raw = resolver.scalar(args[0])
        if (raw is CellValue.Error) return raw
        return when (raw) {
            is CellValue.Number -> raw
            is CellValue.Text -> raw.text.trim().toDoubleOrNull()
                ?.let { CellValue.Number(it) }
                ?: CellValue.Error("#VALUE!")
            else -> CellValue.Error("#VALUE!")
        }
    }

    private fun datePart(
        args: List<Operand>,
        resolver: CellResolver,
        part: (LocalDateTime) -> Int,
    ): CellValue {
        if (args.size != 1) return CellValue.Error("#VALUE!")
        val serial = numberOf(args[0], resolver) ?: return CellValue.Error("#VALUE!")
        if (serial < 0) return CellValue.Error("#VALUE!")
        return CellValue.Number(part(serialToDateTime(serial)).toDouble())
    }

    private fun date(args: List<Operand>, resolver: CellResolver): CellValue {
        if (args.size != 3) return CellValue.Error("#VALUE!")
        val year = numberOf(args[0], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val month = numberOf(args[1], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        val day = numberOf(args[2], resolver)?.toInt() ?: return CellValue.Error("#VALUE!")
        if (year < 1900 || month !in 1..12 || day !in 1..31) {
            return CellValue.Error("#VALUE!")
        }
        val dateTime = try {
            LocalDateTime.of(year, month, day, 0, 0)
        } catch (error: java.time.DateTimeException) {
            return CellValue.Error("#VALUE!")
        }
        return CellValue.Number(dateTimeToSerial(dateTime))
    }

    private fun information(
        args: List<Operand>,
        resolver: CellResolver,
        test: (CellValue) -> Boolean,
    ): CellValue {
        if (args.size != 1) return CellValue.Error("#VALUE!")
        return CellValue.Bool(test(resolver.scalar(args[0])))
    }

    /* ----------------------------- comparisons ---------------------------- */

    private fun equalValues(left: CellValue, right: CellValue): Boolean {
        val l = asNumber(left)
        val r = asNumber(right)
        if (l != null && r != null) return l == r
        return asText(left)?.equals(asText(right), ignoreCase = true) == true
    }

    /**
     * True when a cell matches a criterion such as `">10"`, `"<>done"` or
     * `"Paper"`. Text comparisons ignore case, like every spreadsheet.
     */
    private fun matchesCriterion(value: CellValue, criterion: CellValue): Boolean {
        val text = asText(criterion) ?: return equalValues(value, criterion)
        val operator = when {
            text.startsWith(">=") -> ">="
            text.startsWith("<=") -> "<="
            text.startsWith("<>") -> "<>"
            text.startsWith(">") -> ">"
            text.startsWith("<") -> "<"
            text.startsWith("=") -> "="
            else -> null
        }
        if (operator == null) return equalValues(value, criterion)

        val rest = text.substring(operator.length).trim()
        val number = rest.toDoubleOrNull()
        val valueNumber = asNumber(value)
        if (number != null && valueNumber != null) {
            return when (operator) {
                ">=" -> valueNumber >= number
                "<=" -> valueNumber <= number
                "<>" -> valueNumber != number
                ">" -> valueNumber > number
                "<" -> valueNumber < number
                else -> valueNumber == number
            }
        }
        val comparison = (asText(value) ?: "").compareTo(rest, ignoreCase = true)
        return when (operator) {
            ">=" -> comparison >= 0
            "<=" -> comparison <= 0
            "<>" -> comparison != 0
            ">" -> comparison > 0
            "<" -> comparison < 0
            else -> comparison == 0
        }
    }

    private fun trimText(text: String): String =
        text.split(Regex("\\s+")).joinToString(" ") { it }.trim()

    private fun properCase(text: String): String {
        val result = StringBuilder(text.length)
        var startOfWord = true
        text.forEach { character ->
            if (character.isLetter()) {
                result.append(if (startOfWord) character.uppercaseChar() else character.lowercaseChar())
                startOfWord = false
            } else {
                result.append(character)
                startOfWord = true
            }
        }
        return result.toString()
    }
}
