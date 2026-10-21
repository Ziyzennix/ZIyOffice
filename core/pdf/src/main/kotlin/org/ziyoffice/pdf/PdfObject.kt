package org.ziyoffice.pdf

/**
 * The values a PDF file is made of.
 *
 * A PDF is a tree of objects: dictionaries, arrays, names, numbers, strings,
 * streams and references. This is that tree, with no file positions and no
 * idea of what the objects mean. Reading a form and writing one back needs
 * both halves: the tree to say what a field is, and the positions to say
 * where in the file it sits.
 *
 * The model follows the file rather than the other way round. A number stays
 * an integer or a real, because a file that says `1` must not come back as
 * `1.0` after a round trip.
 */
sealed interface PdfObject

/** The `null` keyword. */
object PdfNull : PdfObject {
    override fun toString(): String = "null"
}

/** `true` or `false`. */
data class PdfBool(val value: Boolean) : PdfObject

/** A whole number. */
data class PdfInteger(val value: Long) : PdfObject

/** A number with a fraction. */
data class PdfReal(val value: Double) : PdfObject

/**
 * A name, written `/LikeThis` in the file.
 *
 * The leading slash is not part of the name. A `#` escape in the file is
 * decoded when the name is read, so `/A#20B` is the name `A B`.
 */
data class PdfName(val value: String) : PdfObject {
    /** The name as it is written in a file, with the characters that need escaping escaped. */
    fun toPdfName(): String = buildString {
        append('/')
        for (byte in value.toByteArray(Charsets.ISO_8859_1)) {
            val code = byte.toInt() and 0xFF
            val regular = code > 0x20 && code < 0x7F && code.toChar() !in NEEDS_ESCAPE
            if (regular) {
                append(code.toChar())
            } else {
                append('#')
                append(HEX[code shr 4])
                append(HEX[code and 0x0F])
            }
        }
    }

    private companion object {
        val HEX = "0123456789ABCDEF".toCharArray()

        /** The characters a name cannot hold without escaping them. */
        val NEEDS_ESCAPE = "#/()<>[]{}%".toSet()
    }
}

/**
 * A string, in either of the two forms a file can use.
 *
 * The bytes are kept as they are, because how they are encoded depends on
 * the font and the context. [text] makes a best guess: a byte order mark
 * means UTF-16BE, and anything else is read as PDFDocEncoding, which agrees
 * with Latin-1 for the characters that matter.
 */
class PdfString(val bytes: ByteArray) : PdfObject {

    /** A string holding [text], encoded the way the file would write it. */
    constructor(text: String) : this(encodeText(text))

    /** The string read as text. */
    val text: String by lazy { decodeText(bytes) }

    override fun equals(other: Any?): Boolean =
        other is PdfString && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "PdfString(${text})"

    companion object {
        /** The text of a string, as PDF spells it. */
        fun decodeText(bytes: ByteArray): String {
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            return String(bytes, Charsets.ISO_8859_1)
        }

        /** The bytes of a string holding [text], as UTF-16BE when it has to be. */
        fun encodeText(text: String): ByteArray {
            val latin = text.toByteArray(Charsets.ISO_8859_1)
            if (latin.size == text.length) return latin
            val utf16 = text.toByteArray(Charsets.UTF_16BE)
            return byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + utf16
        }
    }
}

/** An array of objects. */
data class PdfArray(val items: List<PdfObject>) : PdfObject

/**
 * A dictionary: the key/value pairs an object is made of.
 *
 * Keys are names without their slash, so `Type` is the key for `/Type`.
 */
data class PdfDict(val entries: Map<String, PdfObject>) : PdfObject {

    /** The value of [key], or null when the dictionary does not have it. */
    operator fun get(key: String): PdfObject? = entries[key]

    /** The dictionary at [key], or null. */
    fun dict(key: String): PdfDict? = entries[key] as? PdfDict

    /** The array at [key], or null. */
    fun array(key: String): PdfArray? = entries[key] as? PdfArray

    /** The name at [key] without its slash, or null. */
    fun name(key: String): String? = (entries[key] as? PdfName)?.value

    /** The whole number at [key], or null. */
    fun integer(key: String): Long? = (entries[key] as? PdfInteger)?.value

    /** The reference at [key], or null. */
    fun ref(key: String): PdfRef? = entries[key] as? PdfRef

    /** True when the dictionary has [key]. */
    operator fun contains(key: String): Boolean = key in entries

    /** This dictionary with [key] set to [value]. */
    fun with(key: String, value: PdfObject): PdfDict = copy(entries = entries + (key to value))

    /** This dictionary without [key]. */
    fun without(key: String): PdfDict = copy(entries = entries - key)

    companion object {
        /** An empty dictionary. */
        val EMPTY = PdfDict(emptyMap())
    }
}

/**
 * A stream: a dictionary plus the bytes that follow it.
 *
 * [raw] holds the bytes as they are in the file. Use
 * [org.ziyoffice.pdf.PdfFilters.decode] to get the content the filters
 * describe.
 */
class PdfStream(val dict: PdfDict, val raw: ByteArray) : PdfObject {

    /** The bytes the stream holds once its filters have been undone. */
    val decoded: ByteArray by lazy { PdfFilters.decode(dict, raw) }

    override fun toString(): String = "PdfStream(${dict.entries.keys})"
}

/**
 * A reference to an object elsewhere in the file.
 *
 * The generation number matters: a file may hold two objects with the same
 * number and different generations, and a reference names both.
 */
data class PdfRef(val number: Int, val generation: Int) : PdfObject {
    override fun toString(): String = "$number $generation R"
}
