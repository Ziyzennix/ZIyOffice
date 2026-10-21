package org.ziyoffice.pdf

/**
 * A PDF file, opened for reading.
 *
 * The file is a set of numbered objects and a cross-reference that says
 * where each one is. This opens it, finds the objects, and hands them out
 * when they are asked for.
 *
 * Reading the cross-reference is the tidy way in, but not the only one. A
 * file with a damaged cross-reference is still a file, so every object in it
 * is also looked for by scanning the bytes for `12 0 obj`. The cross-
 * reference wins where the two disagree, because it knows which of two
 * objects with the same number is the live one.
 *
 * Objects held inside an object stream are unpacked as well: a modern file
 * keeps most of its small objects there rather than in the file body.
 */
class PdfFile private constructor(
    /** The bytes of the file. */
    val bytes: ByteArray,
    private val xrefOffsets: Map<Int, Long>,
    private val scannedOffsets: Map<Int, Int>,
    /** The trailer: the root, the info dictionary, the file identifier. */
    val trailer: PdfDict,
    /** Where the file's last cross-reference is, when it has one. */
    val startXrefOffset: Int,
) {

    private val cache = HashMap<Int, PdfObject?>()
    private val unpacked = HashMap<Int, PdfObject>()
    private var objectStreamsRead = false

    /** True when the file is encrypted and the bytes cannot be read. */
    val encrypted: Boolean get() = trailer.contains("Encrypt")

    /** The document catalogue, or null when the file has none. */
    val catalog: PdfDict? by lazy { resolve(trailer["Root"]) as? PdfDict }

    /** The pages of the file, in order. */
    val pages: List<PdfPage> by lazy { readPages() }

    /**
     * The object with the given number, or null when there is none.
     *
     * A reference is followed to the object it names.
     */
    fun objectAt(number: Int): PdfObject? {
        cache[number]?.let { return it }
        val found = readObject(number)
        cache[number] = found
        return found
    }

    /** The object a reference points at, or the object itself. */
    fun resolve(value: PdfObject?): PdfObject? = when (value) {
        is PdfRef -> objectAt(value.number)
        else -> value
    }

    /** The dictionary a reference points at, or the dictionary itself. */
    fun resolveDict(value: PdfObject?): PdfDict? = resolve(value) as? PdfDict

    /** The array a reference points at, or the array itself. */
    fun resolveArray(value: PdfObject?): PdfArray? = resolve(value) as? PdfArray

    private fun readObject(number: Int): PdfObject? {
        val parser = PdfParser(bytes)
        xrefOffsets[number]?.let { offset ->
            val parsed = parser.parseIndirectAt(offset.toInt())
            if (parsed != null && parser.numberAt(offset.toInt()) == number) return parsed
        }
        scannedOffsets[number]?.let { offset ->
            val parsed = parser.parseIndirectAt(offset)
            if (parsed != null && parser.numberAt(offset) == number) return parsed
        }
        readObjectStreams()
        return unpacked[number]
    }

    /**
     * Unpacks the object streams.
     *
     * An object stream holds a number of small objects and a table saying
     * where each one starts inside it. They are unpacked once, and only when
     * an object could not be found any other way.
     */
    private fun readObjectStreams() {
        if (objectStreamsRead) return
        objectStreamsRead = true
        val candidates = (xrefOffsets.keys + scannedOffsets.keys).sorted()
        for (number in candidates) {
            val parser = PdfParser(bytes)
            val offset = xrefOffsets[number]?.toInt() ?: scannedOffsets[number] ?: continue
            val parsed = parser.parseIndirectAt(offset) as? PdfStream ?: continue
            if (parsed.dict.name("Type") != "ObjStm") continue
            unpackObjectStream(parsed)
        }
    }

    private fun unpackObjectStream(stream: PdfStream) {
        val count = stream.dict.integer("N")?.toInt() ?: return
        val first = stream.dict.integer("First")?.toInt() ?: return
        val content = stream.decoded
        if (count <= 0 || first < 0 || first > content.size) return
        val parser = PdfParser(content)
        val pairs = ArrayList<Pair<Int, Int>>(count)
        var at = 0
        repeat(count) {
            val number = readInt(content, at) ?: return
            at = (readTokenEnd(content, at) ?: return) + 1
            val offset = readInt(content, at) ?: return
            at = (readTokenEnd(content, at) ?: return) + 1
            pairs += number to offset
        }
        for ((number, offset) in pairs) {
            val start = first + offset
            if (start < 0 || start >= content.size) continue
            val bodyParser = PdfParser(content)
            val parsed = bodyParser.parseAt(start) ?: continue
            unpacked.putIfAbsent(number, parsed.first)
        }
    }

    private fun readInt(data: ByteArray, from: Int): Int? {
        var at = from
        while (at < data.size && (data[at].toInt().and(0xFF) <= 0x20)) at++
        val start = at
        while (at < data.size && data[at].toInt().and(0xFF) in '0'.code..'9'.code) at++
        if (at == start) return null
        return String(data, start, at - start, Charsets.ISO_8859_1).toIntOrNull()
    }

    private fun readTokenEnd(data: ByteArray, from: Int): Int? {
        var at = from
        while (at < data.size && data[at].toInt().and(0xFF) <= 0x20) at++
        val start = at
        while (at < data.size && data[at].toInt().and(0xFF) in '0'.code..'9'.code) at++
        if (at == start) return null
        return at
    }

    // ----------------------------------------------------------------- pages

    private fun readPages(): List<PdfPage> {
        val root = catalog ?: return emptyList()
        val pagesNode = resolve(root["Pages"]) as? PdfDict ?: return emptyList()
        val found = mutableListOf<PdfPage>()
        val seen = HashSet<Int>()
        walkPages(pagesNode, inherited = emptyMap(), found = found, seen = seen)
        return found
    }

    private fun walkPages(
        node: PdfDict,
        inherited: Map<String, PdfObject>,
        found: MutableList<PdfPage>,
        seen: MutableSet<Int>,
    ) {
        val merged = inherited.toMutableMap()
        for (key in INHERITABLE) {
            node[key]?.let { merged[key] = it }
        }
        val kids = resolveArray(node["Kids"]) ?: return
        for (kid in kids.items) {
            val child = resolve(kid) as? PdfDict ?: continue
            val reference = kid as? PdfRef
            if (reference != null && !seen.add(reference.number)) continue
            when (child.name("Type")) {
                "Page" -> found += PdfPage(found.size, child, merged)
                "Pages" -> walkPages(child, merged, found, seen)
                else -> walkPages(child, merged, found, seen)
            }
        }
    }

    companion object {
        /** The page attributes a page inherits from the node above it. */
        private val INHERITABLE = listOf("MediaBox", "CropBox", "Rotate", "Resources")

        /**
         * Opens a PDF.
         *
         * Returns null when the bytes are not a PDF at all.
         */
        fun open(bytes: ByteArray): PdfFile? {
            if (!looksLikePdf(bytes)) return null
            val scanned = scanObjects(bytes)
            val xref = readXrefChain(bytes)
            val trailer = xref?.second ?: findTrailer(bytes, scanned)
            return PdfFile(
                bytes,
                xref?.first ?: emptyMap(),
                scanned,
                trailer,
                findStartXref(bytes) ?: -1,
            )
        }

        /** True when the bytes start with a PDF header. */
        fun looksLikePdf(bytes: ByteArray): Boolean {
            // The header is supposed to be at the very start, but some files
            // have bytes in front of it, so a short search is allowed.
            val limit = minOf(bytes.size, 1024)
            for (at in 0 until limit) {
                if (at + 5 <= bytes.size &&
                    String(bytes, at, 5, Charsets.ISO_8859_1) == "%PDF-"
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * Every object in the file, found by scanning for `12 0 obj`.
         *
         * The number is what the object declares, and the offset is where
         * its body starts.
         */
        fun scanObjects(bytes: ByteArray): Map<Int, Int> {
            val found = HashMap<Int, Int>()
            var at = 0
            val length = bytes.size
            while (at < length) {
                val c = bytes[at].toInt().and(0xFF)
                if (c !in '0'.code..'9'.code) {
                    at++
                    continue
                }
                // A number, a space, a number, a space, "obj".
                var cursor = at
                while (cursor < length && bytes[cursor].toInt().and(0xFF) in '0'.code..'9'.code) cursor++
                if (cursor >= length || !isSpace(bytes[cursor])) {
                    // Carry on from the next byte, not from where the number
                    // ended: the digits of the version in the header look
                    // like the start of an object.
                    at++
                    continue
                }
                cursor++
                val generationStart = cursor
                while (cursor < length && bytes[cursor].toInt().and(0xFF) in '0'.code..'9'.code) cursor++
                if (cursor == generationStart) {
                    at++
                    continue
                }
                while (cursor < length && isSpace(bytes[cursor])) cursor++
                if (cursor + 2 >= length || bytes[cursor].toInt().and(0xFF) != 'o'.code ||
                    bytes[cursor + 1].toInt().and(0xFF) != 'b'.code ||
                    bytes[cursor + 2].toInt().and(0xFF) != 'j'.code
                ) {
                    at++
                    continue
                }
                val number = String(bytes, at, generationStart - at - 1, Charsets.ISO_8859_1)
                    .toIntOrNull()
                if (number != null) {
                    // The offset points at the header, where the number is, so
                    // the parser can check that it really is that object.
                    found.putIfAbsent(number, at)
                }
                at = cursor + 3
            }
            return found
        }

        /**
         * The cross-reference chain, and the trailer it ends in.
         *
         * Returns the offsets it knows about and the trailer dictionary.
         */
        private fun readXrefChain(bytes: ByteArray): Pair<Map<Int, Long>, PdfDict>? {
            var start = findStartXref(bytes) ?: return null
            val offsets = HashMap<Int, Long>()
            var trailer: PdfDict? = null
            val visited = HashSet<Int>()
            while (start >= 0 && start < bytes.size && visited.add(start)) {
                val parser = PdfParser(bytes)
                parser.pos = start
                parser.skipWhitespace()
                val section = if (parser.isKeywordAhead("xref")) {
                    parser.pos += 4
                    readXrefTable(bytes, parser)
                } else {
                    val stream = parser.parseIndirectAt(start) as? PdfStream
                        ?: return null
                    readXrefStream(stream, parser)
                }
                // The chain is walked newest first, so what is already known
                // is the newer one and must not be overwritten by the older.
                section.first.forEach { (number, offset) ->
                    offsets.putIfAbsent(number, offset)
                }
                if (trailer == null) trailer = section.second
                start = section.third
            }
            return offsets to (trailer ?: PdfDict.EMPTY)
        }

        /** A classic cross-reference table, and the trailer that follows it. */
        private fun readXrefTable(
            bytes: ByteArray,
            parser: PdfParser,
        ): Triple<Map<Int, Long>, PdfDict?, Int> {
            val offsets = HashMap<Int, Long>()
            while (true) {
                parser.skipWhitespace()
                if (parser.isKeywordAhead("trailer")) {
                    parser.pos += 7
                    val trailer = parser.parseObject() as? PdfDict
                    val previous = trailer?.integer("Prev")?.toInt() ?: -1
                    return Triple(offsets, trailer, previous)
                }
                if (parser.isKeywordAhead("xref")) break
                val first = parser.readInteger()
                if (first == null) break
                parser.skipWhitespace()
                val count = parser.readInteger() ?: break
                for (i in 0 until count) {
                    parser.skipWhitespace()
                    val offset = parser.readLong() ?: break
                    parser.skipWhitespace()
                    parser.readLong()
                    parser.skipWhitespace()
                    val kind = parser.readByte()
                    if (kind == 'n'.code) offsets[(first + i).toInt()] = offset
                }
            }
            return Triple(offsets, null, -1)
        }

        /** A cross-reference stream, and the trailer dictionary inside it. */
        private fun readXrefStream(
            stream: PdfStream,
            parser: PdfParser,
        ): Triple<Map<Int, Long>, PdfDict?, Int> {
            val offsets = HashMap<Int, Long>()
            val dict = stream.dict
            val widths = dict.array("W")?.items?.mapNotNull { (it as? PdfInteger)?.value }
                ?: return Triple(offsets, null, -1)
            if (widths.size < 3) return Triple(offsets, null, -1)
            val size = dict.integer("Size")?.toInt() ?: 0
            val index = dict.array("Index")?.items?.mapNotNull { (it as? PdfInteger)?.value?.toInt() }
            val ranges = if (index != null && index.size >= 2) {
                index.chunked(2).map { it[0] to it[1] }
            } else {
                listOf(0 to size)
            }
            val data = stream.decoded
            val rowLength = widths.sum().toInt()
            var at = 0
            for ((start, count) in ranges) {
                repeat(count) {
                    if (at + rowLength > data.size) return Triple(offsets, dict, -1)
                    var field = at
                    val type = readField(data, field, widths[0].toInt())
                    field += widths[0].toInt()
                    val offset = readField(data, field, widths[1].toInt())
                    field += widths[1].toInt()
                    readField(data, field, widths[2].toInt())
                    at += rowLength
                    if (type == 1L && offset != null) offsets[start + it] = offset
                }
            }
            val previous = dict.integer("Prev")?.toInt() ?: -1
            return Triple(offsets, dict, previous)
        }

        private fun readField(data: ByteArray, from: Int, width: Int): Long {
            if (width == 0) return 0
            var value = 0L
            for (i in 0 until width) {
                val at = from + i
                if (at >= data.size) break
                value = value * 256 + (data[at].toInt().and(0xFF))
            }
            return value
        }

        /** The offset the file's last `startxref` points at. */
        private fun findStartXref(bytes: ByteArray): Int? {
            val needle = "startxref".toByteArray(Charsets.ISO_8859_1)
            val at = lastIndexOf(bytes, needle)
            if (at < 0) return null
            var cursor = at + needle.size
            while (cursor < bytes.size && isSpace(bytes[cursor])) cursor++
            val start = cursor
            while (cursor < bytes.size && bytes[cursor].toInt().and(0xFF) in '0'.code..'9'.code) {
                cursor++
            }
            if (cursor == start) return null
            return String(bytes, start, cursor - start, Charsets.ISO_8859_1).toIntOrNull()
        }

        /**
         * A trailer, found without a cross-reference.
         *
         * The last `trailer` keyword in the file wins; if there is none, the
         * catalogue is looked for directly.
         */
        private fun findTrailer(bytes: ByteArray, scanned: Map<Int, Int>): PdfDict {
            val needle = "trailer".toByteArray(Charsets.ISO_8859_1)
            val at = lastIndexOf(bytes, needle)
            if (at >= 0) {
                val parser = PdfParser(bytes)
                parser.pos = at + needle.size
                val dict = parser.parseObject() as? PdfDict
                if (dict != null) {
                    if (dict.contains("Root")) return dict
                    val root = findRoot(bytes, scanned)
                    return if (root != null) dict.with("Root", root) else dict
                }
            }
            val root = findRoot(bytes, scanned) ?: return PdfDict.EMPTY
            return PdfDict(mapOf("Root" to root))
        }

        /** The object that says it is the catalogue. */
        private fun findRoot(bytes: ByteArray, scanned: Map<Int, Int>): PdfRef? {
            for (number in scanned.keys.sorted()) {
                val parser = PdfParser(bytes)
                val parsed = parser.parseIndirectAt(scanned.getValue(number)) as? PdfDict ?: continue
                if (parsed.name("Type") == "Catalog") return PdfRef(number, 0)
            }
            return null
        }

        private fun isSpace(byte: Byte): Boolean = byte.toInt().and(0xFF) <= 0x20

        private fun lastIndexOf(bytes: ByteArray, needle: ByteArray): Int {
            outer@ for (at in bytes.size - needle.size downTo 0) {
                for (i in needle.indices) {
                    if (bytes[at + i] != needle[i]) continue@outer
                }
                return at
            }
            return -1
        }
    }
}

/**
 * One page of a PDF.
 *
 * The page dictionary and the attributes it inherits from the node above it
 * in the page tree are both here, so a page that does not name its own
 * media box still knows how big it is.
 */
class PdfPage(
    /** Where the page sits in the file, counting from zero. */
    val index: Int,
    /** The page dictionary. */
    val dict: PdfDict,
    /** The attributes the page inherits: media box, crop box, rotation. */
    val inherited: Map<String, PdfObject>,
) {
    /** The page's media box, as four numbers: left, bottom, right, top. */
    val mediaBox: DoubleArray by lazy {
        val raw = (dict["MediaBox"] ?: inherited["MediaBox"]) as? PdfArray
        val numbers = raw?.items?.mapNotNull { numberValue(it) } ?: emptyList()
        if (numbers.size >= 4) {
            doubleArrayOf(numbers[0], numbers[1], numbers[2], numbers[3])
        } else {
            // The default is US Letter, which is what a page without a media
            // box is taken to be.
            doubleArrayOf(0.0, 0.0, 612.0, 792.0)
        }
    }

    /** The page's rotation, in degrees, clockwise. */
    val rotate: Int by lazy {
        val raw = (dict["Rotate"] ?: inherited["Rotate"]) as? PdfInteger
        val value = raw?.value?.toInt() ?: 0
        ((value % 360) + 360) % 360
    }

    /** The page's width in points, taking the rotation into account. */
    val widthPoints: Double get() {
        val width = mediaBox[2] - mediaBox[0]
        val height = mediaBox[3] - mediaBox[1]
        // A page turned on its side is as wide as it was tall.
        return if (rotate == 90 || rotate == 270) height else width
    }

    /** The page's height in points, taking the rotation into account. */
    val heightPoints: Double get() {
        val width = mediaBox[2] - mediaBox[0]
        val height = mediaBox[3] - mediaBox[1]
        return if (rotate == 90 || rotate == 270) width else height
    }

    private fun numberValue(value: PdfObject): Double? = when (value) {
        is PdfInteger -> value.value.toDouble()
        is PdfReal -> value.value
        else -> null
    }
}
