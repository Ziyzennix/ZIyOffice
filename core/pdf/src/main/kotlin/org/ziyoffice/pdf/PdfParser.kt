package org.ziyoffice.pdf

/**
 * Reads the objects out of a PDF file.
 *
 * The parser knows the syntax of the format and nothing about its meaning.
 * It works on the file's bytes and hands back the object tree, so the same
 * code can read a form, a page tree or anything else.
 *
 * Positions are kept as it goes. A file has to be written back byte for byte
 * everywhere the user did not touch it, so the code that changes something
 * has to know exactly where in the file that something lives.
 */
internal class PdfParser(private val bytes: ByteArray) {

    /** Where the parser is in the file. */
    var pos: Int = 0

    /** The bytes being read. */
    val source: ByteArray get() = bytes

    /**
     * The object at [offset], if there is one.
     *
     * Returns the object and where it ended, so a caller can read the next
     * thing after it.
     */
    fun parseAt(offset: Int): Pair<PdfObject, Int>? {
        if (offset < 0 || offset >= bytes.size) return null
        pos = offset
        skipWhitespace()
        val value = parseObject() ?: return null
        return value to pos
    }

    /**
     * The object that starts with an indirect object header (`12 0 obj`).
     *
     * [offset] points at the number, not at the body. The header is checked
     * so a stray `obj` in a stream's bytes cannot be mistaken for an object.
     */
    fun parseIndirectAt(offset: Int): PdfObject? {
        if (offset < 0 || offset >= bytes.size) return null
        pos = offset
        skipWhitespace()
        val number = parseIntegerToken() ?: return null
        skipWhitespace()
        val generation = parseIntegerToken() ?: return null
        skipWhitespace()
        if (!consumeKeyword("obj")) return null
        skipWhitespace()
        val value = parseObject() ?: return null
        if (value is PdfStream) {
            return value
        }
        return value
    }

    /** The object number the indirect object at [offset] declares. */
    fun numberAt(offset: Int): Int? {
        if (offset < 0 || offset >= bytes.size) return null
        pos = offset
        skipWhitespace()
        val number = parseIntegerToken() ?: return null
        skipWhitespace()
        parseIntegerToken() ?: return null
        skipWhitespace()
        if (!consumeKeyword("obj")) return null
        return number.toInt()
    }

    // ------------------------------------------------------------- the objects

    /** The object at the current position, or null when there is none. */
    fun parseObject(): PdfObject? {
        skipWhitespace()
        if (pos >= bytes.size) return null
        return when (val c = bytes[pos].toInt().and(0xFF)) {
            '/'.code -> parseName()
            '('.code -> parseLiteralString()
            '['.code -> parseArray()
            '<'.code -> if (peek(1) == '<'.code) parseDictionary() else parseHexString()
            ']'.code -> null
            '>'.code -> null
            '{'.code -> null
            '}'.code -> null
            else -> when {
                c == '+'.code || c == '-'.code || c == '.'.code ||
                    c in '0'.code..'9'.code -> parseNumber()
                else -> parseKeywordObject()
            }
        }
    }

    private fun parseDictionary(): PdfObject? {
        if (!consumeTwo('<')) return null
        val entries = LinkedHashMap<String, PdfObject>()
        while (true) {
            skipWhitespace()
            if (pos >= bytes.size) return null
            if (peek(1) == '>'.code) {
                pos += 2
                break
            }
            if (bytes[pos].toInt().and(0xFF) != '/'.code) {
                // A dictionary with a key that is not a name is broken. Skip
                // the value and keep going, so one bad entry does not cost us
                // the whole object.
                if (parseObject() == null) return null
                continue
            }
            val key = parseName() as? PdfName ?: return null
            val before = pos
            val value = parseObject()
            if (value == null) {
                // A value that cannot be read is stepped over rather than
                // losing the whole dictionary: one broken entry should not
                // cost us the object it sits in.
                if (!skipToNextKey()) return null
                continue
            }
            entries[key.value] = value
        }
        val dict = PdfDict(entries)
        skipWhitespace()
        if (isKeywordAhead("stream")) {
            // The keyword, then the end of line that has to follow it, then
            // the bytes of the stream itself.
            pos += 6
            if (peek(0) == '\r'.code) pos++
            if (peek(0) == '\n'.code) pos++
            val raw = readStreamBytes(dict)
            return PdfStream(dict, raw)
        }
        return dict
    }

    private fun parseArray(): PdfObject? {
        pos++ // '['
        val items = mutableListOf<PdfObject>()
        while (true) {
            skipWhitespace()
            if (pos >= bytes.size) return null
            if (bytes[pos].toInt().and(0xFF) == ']'.code) {
                pos++
                break
            }
            val value = parseObject() ?: return null
            items += value
        }
        return PdfArray(items)
    }

    private fun parseName(): PdfObject {
        pos++ // '/'
        val builder = StringBuilder()
        while (pos < bytes.size) {
            val c = bytes[pos].toInt().and(0xFF)
            if (c <= 0x20 || isDelimiter(c)) break
            if (c == '#'.code && pos + 2 < bytes.size) {
                val high = hexValue(bytes[pos + 1])
                val low = hexValue(bytes[pos + 2])
                if (high >= 0 && low >= 0) {
                    builder.append((high shl 4) or low)
                    pos += 3
                    continue
                }
            }
            builder.append(c.toChar())
            pos++
        }
        return PdfName(builder.toString())
    }

    private fun parseLiteralString(): PdfObject {
        pos++ // '('
        val out = java.io.ByteArrayOutputStream()
        var depth = 1
        while (pos < bytes.size) {
            val c = bytes[pos].toInt().and(0xFF)
            when (c) {
                BACKSLASH -> {
                    pos++
                    if (pos >= bytes.size) break
                    when (val escaped = bytes[pos].toInt().and(0xFF)) {
                        'n'.code -> { out.write('\n'.code); pos++ }
                        'r'.code -> { out.write('\r'.code); pos++ }
                        't'.code -> { out.write('\t'.code); pos++ }
                        'b'.code -> { out.write(0x08); pos++ }
                        'f'.code -> { out.write(0x0C); pos++ }
                        '('.code -> { out.write('('.code); pos++ }
                        ')'.code -> { out.write(')'.code); pos++ }
                        BACKSLASH -> { out.write(BACKSLASH); pos++ }
                        '\r'.code -> {
                            pos++
                            if (pos < bytes.size && bytes[pos].toInt().and(0xFF) == '\n'.code) pos++
                        }
                        '\n'.code -> pos++
                        else -> {
                            // Three octal digits, or one.
                            if (escaped in '0'.code..'7'.code) {
                                var value = escaped - '0'.code
                                var digits = 1
                                while (digits < 3 && pos + 1 < bytes.size) {
                                    val next = bytes[pos + 1].toInt().and(0xFF)
                                    if (next !in '0'.code..'7'.code) break
                                    value = value * 8 + (next - '0'.code)
                                    pos++
                                    digits++
                                }
                                out.write(value and 0xFF)
                                pos++
                            } else {
                                out.write(escaped)
                                pos++
                            }
                        }
                    }
                }
                '('.code -> { depth++; out.write(c); pos++ }
                ')'.code -> {
                    depth--
                    pos++
                    if (depth == 0) break
                    out.write(c)
                }
                else -> { out.write(c); pos++ }
            }
        }
        return PdfString(out.toByteArray())
    }

    private fun parseHexString(): PdfObject {
        pos++ // '<'
        val digits = StringBuilder()
        while (pos < bytes.size) {
            val c = bytes[pos].toInt().and(0xFF)
            pos++
            if (c == '>'.code) break
            if (hexValue(c.toByte()) >= 0) digits.append(c.toChar())
        }
        // An odd number of digits means the last one is followed by a zero.
        val padded = if (digits.length % 2 == 1) digits.toString() + "0" else digits.toString()
        val out = ByteArray(padded.length / 2)
        for (i in out.indices) {
            out[i] = ((hexValue(padded[i * 2].code.toByte()) shl 4) or
                hexValue(padded[i * 2 + 1].code.toByte())).toByte()
        }
        return PdfString(out)
    }

    private fun parseNumber(): PdfObject? {
        val start = pos
        if (peek(0) == '+'.code || peek(0) == '-'.code) pos++
        var isReal = false
        while (pos < bytes.size) {
            val c = bytes[pos].toInt().and(0xFF)
            if (c in '0'.code..'9'.code) {
                pos++
            } else if (c == '.'.code) {
                isReal = true
                pos++
            } else {
                break
            }
        }
        val text = String(bytes, start, pos - start, Charsets.ISO_8859_1)
        if (text.isEmpty() || text == "-" || text == "+") return null
        if (isReal) {
            return text.toDoubleOrNull()?.let { PdfReal(it) }
        }
        val value = text.toLongOrNull() ?: return null
        // `12 0 R` is a reference, not a number followed by two numbers.
        val reference = tryParseReference(value)
        return reference ?: PdfInteger(value)
    }

    /**
     * The reference `number generation R`, when the bytes after [number] are
     * one. Otherwise null, and the number stands on its own.
     */
    private fun tryParseReference(number: Long): PdfObject? {
        val saved = pos
        skipWhitespace()
        val generationStart = pos
        val generation = parseIntegerToken()
        if (generation == null) {
            pos = saved
            return null
        }
        skipWhitespace()
        if (pos < bytes.size && bytes[pos].toInt().and(0xFF) == 'R'.code) {
            val after = pos + 1
            if (after >= bytes.size || isDelimiter(bytes[after].toInt().and(0xFF)) ||
                bytes[after].toInt().and(0xFF) <= 0x20
            ) {
                pos = after
                return PdfRef(number.toInt(), generation.toInt())
            }
        }
        pos = if (generationStart != saved) saved else saved
        return null
    }

    private fun parseKeywordObject(): PdfObject? {
        val start = pos
        while (pos < bytes.size && !isDelimiter(bytes[pos].toInt().and(0xFF)) &&
            bytes[pos].toInt().and(0xFF) > 0x20
        ) {
            pos++
        }
        if (pos == start) {
            pos++
            return null
        }
        return when (String(bytes, start, pos - start, Charsets.ISO_8859_1)) {
            "true" -> PdfBool(true)
            "false" -> PdfBool(false)
            "null" -> PdfNull
            else -> null
        }
    }

    // ---------------------------------------------------------------- streams

    /**
     * The bytes of a stream.
     *
     * The length in the dictionary is used when it is there and it fits. When
     * it is an indirect reference, or it lies, the `endstream` keyword is
     * found instead, which is what every reader has to be able to do.
     */
    private fun readStreamBytes(dict: PdfDict): ByteArray {
        val declared = (dict["Length"] as? PdfInteger)?.value?.toInt()
        if (declared != null && declared >= 0 && pos + declared <= bytes.size) {
            val data = bytes.copyOfRange(pos, pos + declared)
            pos += declared
            return data
        }
        val end = indexOf("endstream".toByteArray(Charsets.ISO_8859_1), pos)
        if (end < 0) {
            val data = bytes.copyOfRange(pos, bytes.size)
            pos = bytes.size
            return data
        }
        var stop = end
        // The end of the line before the keyword belongs to the keyword.
        if (stop > pos && bytes[stop - 1].toInt().and(0xFF) == '\n'.code) stop--
        if (stop > pos && bytes[stop - 1].toInt().and(0xFF) == '\r'.code) stop--
        val data = bytes.copyOfRange(pos, stop)
        pos = end
        return data
    }

    // ----------------------------------------------------------------- tokens

    /** Skips whitespace and comments. */
    fun skipWhitespace() {
        while (pos < bytes.size) {
            val c = bytes[pos].toInt().and(0xFF)
            when {
                c <= 0x20 -> pos++
                c == '%'.code -> {
                    while (pos < bytes.size) {
                        val n = bytes[pos].toInt().and(0xFF)
                        if (n == '\n'.code || n == '\r'.code) break
                        pos++
                    }
                }
                else -> return
            }
        }
    }

    private fun parseIntegerToken(): Long? {
        skipWhitespace()
        val start = pos
        if (pos < bytes.size && (bytes[pos] == '+'.code.toByte() || bytes[pos] == '-'.code.toByte())) {
            pos++
        }
        while (pos < bytes.size && bytes[pos].toInt().and(0xFF) in '0'.code..'9'.code) pos++
        if (pos == start) {
            pos = start
            return null
        }
        val text = String(bytes, start, pos - start, Charsets.ISO_8859_1)
        return text.toLongOrNull()
    }

    /** The whole number at the current position, or null. */
    fun readLong(): Long? = parseIntegerToken()

    /** The whole number at the current position, or null. */
    fun readInteger(): Int? = parseIntegerToken()?.toInt()

    /** The byte at the current position, moved past. */
    fun readByte(): Int {
        if (pos >= bytes.size) return -1
        return bytes[pos++].toInt().and(0xFF)
    }

    private fun consumeKeyword(keyword: String): Boolean {
        if (!isKeywordAhead(keyword)) return false
        pos += keyword.length
        return true
    }

    /** True when the keyword [keyword] is at the current position. */
    fun isKeywordAhead(keyword: String): Boolean {
        if (pos + keyword.length > bytes.size) return false
        for (i in keyword.indices) {
            if (bytes[pos + i].toInt().and(0xFF) != keyword[i].code) return false
        }
        val after = pos + keyword.length
        if (after >= bytes.size) return true
        val c = bytes[after].toInt().and(0xFF)
        return c <= 0x20 || isDelimiter(c)
    }

    /**
     * Steps over whatever is left of a value that could not be read.
     *
     * The next key starts with a slash, so the search looks for one that is
     * followed by something that can be part of a name. The end of the
     * dictionary stops the search too.
     */
    private fun skipToNextKey(): Boolean {
        while (pos < bytes.size) {
            if (bytes[pos].toInt().and(0xFF) == '>'.code && peek(1) == '>'.code) return true
            if (bytes[pos].toInt().and(0xFF) == '/'.code) {
                val next = peek(1)
                if (next > 0x20 && !isDelimiter(next)) return true
            }
            pos++
        }
        return false
    }

    private fun consumeTwo(first: Char): Boolean {
        if (pos + 1 >= bytes.size) return false
        if (bytes[pos].toInt().and(0xFF) != first.code) return false
        if (bytes[pos + 1].toInt().and(0xFF) != first.code) return false
        pos += 2
        return true
    }

    private fun peek(offset: Int): Int {
        val at = pos + offset
        if (at < 0 || at >= bytes.size) return -1
        return bytes[at].toInt().and(0xFF)
    }

    private fun indexOf(needle: ByteArray, from: Int): Int {
        outer@ for (at in from..bytes.size - needle.size) {
            for (i in needle.indices) {
                if (bytes[at + i] != needle[i]) continue@outer
            }
            return at
        }
        return -1
    }

    private fun hexValue(byte: Byte): Int {
        val c = byte.toInt().and(0xFF)
        return when (c) {
            in '0'.code..'9'.code -> c - '0'.code
            in 'a'.code..'f'.code -> c - 'a'.code + 10
            in 'A'.code..'F'.code -> c - 'A'.code + 10
            else -> -1
        }
    }

    companion object {
        /** The byte that escapes the next one in a literal string. */
        private const val BACKSLASH = '\\'.code

        /** True when [c] ends a token. */
        fun isDelimiter(c: Int): Boolean = when (c) {
            '('.code, ')'.code, '<'.code, '>'.code, '['.code, ']'.code,
            '{'.code, '}'.code, '/'.code, '%'.code,
            -> true
            else -> false
        }
    }
}
