package org.ziyoffice.model

/**
 * Formula evaluation for ZiyOffice Sheets. Pure Kotlin, no Android classes. Parsing is
 * recursive descent, and one [CellResolver] per evaluation owns cycle detection, so a
 * circular reference comes back as an error.
 */

data class FormulaResult(
    val value: CellValue,
    val errors: List<String> = emptyList(),
)

/**
 * Evaluates a formula string against [sheet].
 *
 * The [names] are the ones a formula on this sheet may use; a name stands
 * for a range of cells and is written in the formula like a cell reference.
 *
 * Errors come back in [FormulaResult.errors] rather than thrown, so callers
 * show them to the user.
 */
fun evaluateFormula(
    sheet: Sheet,
    formula: String,
    names: List<NamedRange> = emptyList(),
): FormulaResult {
    if (!formula.startsWith("=")) {
        return FormulaResult(CellValue.Text(formula))
    }
    return CellResolver(sheet, names).evaluate(formula.substring(1))
}

/** Column letters to 0-based index: "A" -> 0, "AA" -> 26. -1 when invalid. */
fun columnIndex(letters: String): Int {
    if (letters.isEmpty()) return -1
    var result = 0
    for (ch in letters.uppercase()) {
        if (ch !in 'A'..'Z') return -1
        result = result * 26 + (ch.code - 'A'.code + 1)
    }
    return result - 1
}

fun columnName(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.append(('A'.code + rem).toChar())
        n = (n - 1) / 26
    }
    return sb.reverse().toString()
}

/** A parsed operand: one scalar or a rectangular range of cells. */
internal sealed interface Operand {
    data class Scalar(val value: CellValue) : Operand
    data class Range(
        val topRow: Int,
        val leftCol: Int,
        val bottomRow: Int,
        val rightCol: Int,
    ) : Operand
}

/**
 * Deepest chain of nested evaluations allowed for one formula. Cycle detection already
 * stops self-referencing loops; this keeps a long acyclic chain from exhausting the
 * stack. Those cells report [#DEPTH!].
 */
private const val MAX_EVALUATION_DEPTH = 200

/** Resolves references against one sheet; owns cycle-detection state. */
internal class CellResolver(
    private val sheet: Sheet,
    /** The names a formula on this sheet can use. */
    private val names: List<NamedRange> = emptyList(),
) {
    private val byPosition: Map<Pair<Int, Int>, Cell> =
        sheet.cells.associateBy { it.row to it.column }
    private val evaluating = mutableSetOf<Pair<Int, Int>>()
    private val cycleErrors = mutableListOf<String>()

    /** The range the name stands for, or null when there is no such name. */
    fun rangeOf(name: String): CellRange? =
        names.firstOrNull { it.name.equals(name, ignoreCase = true) }?.range

    fun evaluate(body: String): FormulaResult {
        return try {
            val parser = Parser(tokenize(body), this)
            val operand = parser.parseWhole()
            FormulaResult(materialize(operand), cycleErrors.toList())
        } catch (e: FormulaException) {
            FormulaResult(CellValue.Error(e.message), listOf(e.message))
        }
    }

    fun cell(row: Int, column: Int): CellValue {
        if (row < 0 || column < 0 || row >= sheet.rowCount || column >= sheet.columnCount) {
            return CellValue.Error("#REF!")
        }
        val raw = byPosition[row to column]?.value ?: ""
        if (!raw.startsWith("=")) return parseLiteral(raw)

        val key = row to column
        if (!evaluating.add(key)) {
            if ("#CIRCULAR!" !in cycleErrors) cycleErrors += "#CIRCULAR!"
            return CellValue.Error("#CIRCULAR!")
        }
        if (evaluating.size > MAX_EVALUATION_DEPTH) {
            evaluating.remove(key)
            if ("#DEPTH!" !in cycleErrors) cycleErrors += "#DEPTH!"
            return CellValue.Error("#DEPTH!")
        }
        return try {
            val result = evaluate(raw.substring(1))
            if (result.errors.isNotEmpty()) CellValue.Error(result.errors.first()) else result.value
        } finally {
            evaluating.remove(key)
        }
    }

    /** Values of a rectangular range, row-major. */
    fun range(op: Operand.Range): List<CellValue> =
        (op.topRow..op.bottomRow).flatMap { r ->
            (op.leftCol..op.rightCol).map { c -> cell(r, c) }
        }

    /** Range collapsed to a scalar where one is required (top-left cell). */
    fun scalar(operand: Operand): CellValue = when (operand) {
        is Operand.Scalar -> operand.value
        is Operand.Range -> cell(operand.topRow, operand.leftCol)
    }

    fun numbers(operand: Operand): List<Double> = when (operand) {
        is Operand.Scalar -> asNumber(operand.value)?.let { listOf(it) } ?: emptyList()
        is Operand.Range -> range(operand).mapNotNull { asNumber(it) }
    }

    private fun materialize(operand: Operand): CellValue = when (operand) {
        is Operand.Scalar -> operand.value
        is Operand.Range -> cell(operand.topRow, operand.leftCol)
    }

    private fun parseLiteral(raw: String): CellValue {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return CellValue.Text("")
        return trimmed.toDoubleOrNull()?.let { CellValue.Number(it) } ?: CellValue.Text(trimmed)
    }
}

internal fun asNumber(value: CellValue): Double? = when (value) {
    is CellValue.Number -> value.number
    is CellValue.Text -> value.text.toDoubleOrNull()
    is CellValue.Bool -> if (value.value) 1.0 else 0.0
    is CellValue.Error -> null
}

/**
 * Numeric value for arithmetic operators only. A blank cell counts as 0, the way Excel and
 * LibreOffice do it, so `=A1+1` is 1 when A1 is empty. Function arguments keep using
 * [asNumber] instead, which skips blanks.
 */
private fun asNumberOrZero(value: CellValue): Double? = when (value) {
    is CellValue.Text -> if (value.text.isEmpty()) 0.0 else value.text.toDoubleOrNull()
    else -> asNumber(value)
}

internal fun asText(value: CellValue): String? = when (value) {
    is CellValue.Text -> value.text
    is CellValue.Number -> formatNumber(value.number)
    is CellValue.Bool -> if (value.value) "TRUE" else "FALSE"
    is CellValue.Error -> null
}

/** Renders a double without a trailing ".0" for whole values. */
internal fun formatNumber(number: Double): String {
    if (number.isFinite() && number == Math.floor(number) && Math.abs(number) < 1e15) {
        return number.toLong().toString()
    }
    return number.toString()
}

internal fun truthy(value: CellValue): Boolean = when (value) {
    is CellValue.Number -> value.number != 0.0
    is CellValue.Text -> value.text.isNotEmpty()
    is CellValue.Bool -> value.value
    is CellValue.Error -> false
}

private fun bool(value: Boolean): CellValue = CellValue.Bool(value)

private enum class TokenType {
    NUMBER,
    TEXT,
    NAME,
    OPERATOR,
    ERROR,
    LPAREN,
    RPAREN,
    COMMA,
    COLON,
    EOF,
}

private data class Token(val type: TokenType, val lexeme: String, val position: Int)

/** Error literals that may appear in a formula, including the ones this app emits. */
private val ERROR_LITERALS = listOf(
    "#REF!",
    "#DIV/0!",
    "#VALUE!",
    "#NAME?",
    "#N/A",
    "#NUM!",
    "#NULL!",
    "#CIRCULAR!",
    "#DEPTH!",
)

private fun tokenize(body: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < body.length) {
        val ch = body[i]
        val start = i
        when {
            ch.isWhitespace() -> i++

            ch.isDigit() || ch == '.' -> {
                var seenDot = false
                while (i < body.length && (body[i].isDigit() || (body[i] == '.' && !seenDot))) {
                    if (body[i] == '.') seenDot = true
                    i++
                }
                // Scientific notation: 1E3, 1.5E-2, 2e+10. Only when E/e directly
                // follows a complete number and is followed by an optional sign
                // plus at least one digit, so cell references like E1 stay names.
                if (i < body.length && (body[i] == 'E' || body[i] == 'e')) {
                    var j = i + 1
                    if (j < body.length && (body[j] == '+' || body[j] == '-')) j++
                    if (j < body.length && body[j].isDigit()) {
                        i = j
                        while (i < body.length && body[i].isDigit()) i++
                    }
                }
                tokens += Token(TokenType.NUMBER, body.substring(start, i), start)
            }

            ch == '"' -> {
                val sb = StringBuilder()
                i++
                while (i < body.length && body[i] != '"') {
                    sb.append(body[i])
                    i++
                }
                // A doubled quote inside a string is a literal quote: "he said ""hi""".
                while (i + 1 < body.length && body[i] == '"' && body[i + 1] == '"') {
                    sb.append('"')
                    i += 2
                    while (i < body.length && body[i] != '"') {
                        sb.append(body[i])
                        i++
                    }
                }
                if (i < body.length) i++ // closing quote
                tokens += Token(TokenType.TEXT, sb.toString(), start)
            }

            ch.isLetter() || ch == '_' || ch == '$' -> {
                while (i < body.length && (body[i].isLetterOrDigit() || body[i] == '_' || body[i] == '$')) i++
                tokens += Token(TokenType.NAME, body.substring(start, i), start)
            }

            ch == '(' -> { tokens += Token(TokenType.LPAREN, "(", start); i++ }
            ch == ')' -> { tokens += Token(TokenType.RPAREN, ")", start); i++ }
            ch == ',' -> { tokens += Token(TokenType.COMMA, ",", start); i++ }
            ch == ':' -> { tokens += Token(TokenType.COLON, ":", start); i++ }

            ch == '#' -> {
                val literal = ERROR_LITERALS.firstOrNull {
                    body.regionMatches(i, it, 0, it.length, ignoreCase = true)
                }
                if (literal == null) {
                    tokens += Token(TokenType.OPERATOR, "#", start)
                    i += 1
                } else {
                    tokens += Token(TokenType.ERROR, literal, start)
                    i += literal.length
                }
            }

            else -> {
                // Two-character operators first.
                val two = if (i + 1 < body.length) body.substring(i, i + 2) else ""
                if (two == "<>" || two == "<=" || two == ">=") {
                    tokens += Token(TokenType.OPERATOR, two, start)
                    i += 2
                } else {
                    tokens += Token(TokenType.OPERATOR, ch.toString(), start)
                    i++
                }
            }
        }
    }
    tokens += Token(TokenType.EOF, "", body.length)
    return tokens
}

/** Precedence, loosest to tightest: comparison, concatenation, additive, multiplicative,
 * power, unary, primary. */
private class Parser(
    private val tokens: List<Token>,
    private val resolver: CellResolver,
) {
    private var pos = 0

    private fun peek(): Token = tokens[pos]
    private fun previous(): Token = tokens[pos - 1]

    private fun advance(): Token {
        val token = tokens[pos]
        if (pos < tokens.lastIndex) pos++
        return token
    }

    private fun matchOperator(vararg lexemes: String): Boolean {
        val current = peek()
        if (current.type != TokenType.OPERATOR) return false
        if (lexemes.none { it == current.lexeme }) return false
        advance()
        return true
    }

    fun parseWhole(): Operand {
        val operand = parseComparison()
        if (peek().type != TokenType.EOF) {
            throw FormulaException("Unexpected '${peek().lexeme}' at ${peek().position}")
        }
        return operand
    }

    private fun parseComparison(): Operand {
        var left = parseConcat()
        while (matchOperator("=", "<>", "<", ">", "<=", ">=")) {
            val op = previous().lexeme
            val right = parseConcat()
            val leftValue = resolver.scalar(left)
            val rightValue = resolver.scalar(right)
            left = when {
                leftValue is CellValue.Error -> Operand.Scalar(leftValue)
                rightValue is CellValue.Error -> Operand.Scalar(rightValue)
                else -> Operand.Scalar(bool(compare(op, leftValue, rightValue)))
            }
        }
        return left
    }

    private fun parseConcat(): Operand {
        var left = parseAdditive()
        while (matchOperator("&")) {
            val right = parseAdditive()
            val leftValue = resolver.scalar(left)
            val rightValue = resolver.scalar(right)
            val lt = asText(leftValue)
            val rt = asText(rightValue)
            left = when {
                leftValue is CellValue.Error -> Operand.Scalar(leftValue)
                rightValue is CellValue.Error -> Operand.Scalar(rightValue)
                lt == null || rt == null -> Operand.Scalar(CellValue.Error("#VALUE!"))
                else -> Operand.Scalar(CellValue.Text(lt + rt))
            }
        }
        return left
    }

    private fun parseAdditive(): Operand {
        var left = parseMultiplicative()
        while (matchOperator("+", "-")) {
            val op = previous().lexeme
            val right = parseMultiplicative()
            left = Operand.Scalar(arithmetic(op, resolver.scalar(left), resolver.scalar(right)))
        }
        return left
    }

    private fun parseMultiplicative(): Operand {
        var left = parsePower()
        while (matchOperator("*", "/")) {
            val op = previous().lexeme
            val right = parsePower()
            left = Operand.Scalar(arithmetic(op, resolver.scalar(left), resolver.scalar(right)))
        }
        return left
    }

    private fun parsePower(): Operand {
        val left = parseUnary()
        if (matchOperator("^")) {
            val right = parsePower() // right-associative
            return Operand.Scalar(arithmetic("^", resolver.scalar(left), resolver.scalar(right)))
        }
        return left
    }

    private fun parseUnary(): Operand {
        if (matchOperator("-")) {
            val operand = resolver.scalar(parseUnary())
            if (operand is CellValue.Error) return Operand.Scalar(operand)
            val number = asNumberOrZero(operand)
                ?: return Operand.Scalar(CellValue.Error("#VALUE!"))
            return Operand.Scalar(CellValue.Number(-number))
        }
        if (matchOperator("+")) return parseUnary()
        return parsePostfix()
    }

    /** Postfix percent: `50%` is 0.5, bound tighter than multiplication. */
    private fun parsePostfix(): Operand {
        var operand = parsePrimary()
        while (matchOperator("%")) {
            val value = resolver.scalar(operand)
            if (value is CellValue.Error) return Operand.Scalar(value)
            val number = asNumberOrZero(value)
                ?: return Operand.Scalar(CellValue.Error("#VALUE!"))
            operand = Operand.Scalar(CellValue.Number(number / 100.0))
        }
        return operand
    }

    private fun parsePrimary(): Operand {
        val token = peek()
        return when (token.type) {
            TokenType.NUMBER -> {
                advance()
                Operand.Scalar(CellValue.Number(token.lexeme.toDouble()))
            }
            TokenType.TEXT -> {
                advance()
                Operand.Scalar(CellValue.Text(token.lexeme))
            }
            TokenType.ERROR -> {
                advance()
                Operand.Scalar(CellValue.Error(token.lexeme))
            }
            TokenType.NAME -> {
                advance()
                when {
                    peek().type == TokenType.LPAREN -> parseCall(token)
                    token.lexeme.equals("TRUE", ignoreCase = true) ->
                        Operand.Scalar(CellValue.Bool(true))
                    token.lexeme.equals("FALSE", ignoreCase = true) ->
                        Operand.Scalar(CellValue.Bool(false))
                    else -> parseReferenceOperand(token)
                }
            }
            TokenType.LPAREN -> {
                advance()
                val inner = parseComparison()
                if (peek().type != TokenType.RPAREN) {
                    throw FormulaException("Expected ')' at ${peek().position}")
                }
                advance()
                inner
            }
            else -> throw FormulaException("Unexpected '${token.lexeme}' at ${token.position}")
        }
    }

    private fun parseCall(nameToken: Token): Operand {
        advance() // consume '('
        val args = mutableListOf<Operand>()
        if (peek().type != TokenType.RPAREN) {
            while (true) {
                args += parseComparison()
                if (peek().type != TokenType.COMMA) break
                advance()
            }
        }
        if (peek().type != TokenType.RPAREN) {
            throw FormulaException("Expected ')' after arguments at ${peek().position}")
        }
        advance()
        return Operand.Scalar(FormulaFunctions.apply(nameToken.lexeme.uppercase(), args, resolver))
    }

    private fun parseReferenceOperand(token: Token): Operand {
        // A name that is not a cell reference stands for a range.
        if (!looksLikeCellReference(token.lexeme)) {
            val named = resolver.rangeOf(token.lexeme)
                ?: throw FormulaException("Unknown name '${token.lexeme}'")
            return Operand.Range(
                topRow = named.top,
                leftCol = named.left,
                bottomRow = named.bottom,
                rightCol = named.right,
            )
        }
        val start = parseReference(token.lexeme)
        if (peek().type == TokenType.COLON) {
            advance()
            val endToken = peek()
            if (endToken.type != TokenType.NAME) {
                throw FormulaException("Expected cell reference at ${endToken.position}")
            }
            advance()
            val end = parseReference(endToken.lexeme)
            return Operand.Range(
                topRow = minOf(start.first, end.first),
                leftCol = minOf(start.second, end.second),
                bottomRow = maxOf(start.first, end.first),
                rightCol = maxOf(start.second, end.second),
            )
        }
        return Operand.Scalar(resolver.cell(start.first, start.second))
    }

    /** "B12" / "$B$12" -> 0-based (row, column). */
    /** True when the text is a cell reference such as "B12" or "$B$12". */
    private fun looksLikeCellReference(lexeme: String): Boolean {
        val clean = lexeme.replace("$", "")
        val letters = clean.takeWhile { it.isLetter() }
        val digits = clean.dropWhile { it.isLetter() }
        return letters.isNotEmpty() && digits.isNotEmpty() && digits.all { it.isDigit() }
    }

    private fun parseReference(lexeme: String): Pair<Int, Int> {
        val clean = lexeme.replace("$", "")
        val letters = clean.takeWhile { it.isLetter() }
        val digits = clean.dropWhile { it.isLetter() }
        if (letters.isEmpty() || digits.isEmpty() || !digits.all { it.isDigit() }) {
            throw FormulaException("Bad cell reference '$lexeme'")
        }
        val col = columnIndex(letters)
        val row = digits.toIntOrNull()
        // The same limits the rewriter uses, so a formula the evaluator accepts
        // is a formula a save can write back.
        if (col < 0 || col >= MAX_SHEET_COLUMNS || row == null || row !in 1..MAX_SHEET_ROWS) {
            throw FormulaException("Bad cell reference '$lexeme'")
        }
        return (row - 1) to col
    }

    private fun arithmetic(op: String, left: CellValue, right: CellValue): CellValue {
        // An error operand wins: `=#REF!+1` stays #REF! instead of becoming a
        // value the reader cannot explain.
        if (left is CellValue.Error) return left
        if (right is CellValue.Error) return right
        val l = asNumberOrZero(left) ?: return CellValue.Error("#VALUE!")
        val r = asNumberOrZero(right) ?: return CellValue.Error("#VALUE!")
        return when (op) {
            "+" -> CellValue.Number(l + r)
            "-" -> CellValue.Number(l - r)
            "*" -> CellValue.Number(l * r)
            "/" -> if (r == 0.0) CellValue.Error("#DIV/0!") else CellValue.Number(l / r)
            "^" -> CellValue.Number(Math.pow(l, r))
            else -> CellValue.Error("#VALUE!")
        }
    }

    private fun compare(op: String, left: CellValue, right: CellValue): Boolean {
        // Numeric comparison when both sides are numeric; text otherwise.
        val l = asNumber(left)
        val r = asNumber(right)
        if (l != null && r != null) {
            return when (op) {
                "=" -> l == r
                "<>" -> l != r
                "<" -> l < r
                ">" -> l > r
                "<=" -> l <= r
                ">=" -> l >= r
                else -> false
            }
        }
        val lt = asText(left) ?: return false
        val rt = asText(right) ?: return false
        val c = lt.compareTo(rt, ignoreCase = true)
        return when (op) {
            "=" -> c == 0
            "<>" -> c != 0
            "<" -> c < 0
            ">" -> c > 0
            "<=" -> c <= 0
            ">=" -> c >= 0
            else -> false
        }
    }

}

private class FormulaException(override val message: String) : Exception(message)
