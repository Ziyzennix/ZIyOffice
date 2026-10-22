package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.ziyoffice.model.Slide
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.SlideTransition
import org.ziyoffice.model.SlideImage
import org.ziyoffice.model.SlideShape
import org.ziyoffice.model.SlideShapeKind

/** The PresentationML namespace, as defined by ECMA-376 / ISO 29500. */
const val PRESENTATIONML_NAMESPACE =
    "http://schemas.openxmlformats.org/presentationml/2006/main"

/** DrawingML, where the actual text lives. */
const val DRAWINGML_NAMESPACE = "http://schemas.openxmlformats.org/drawingml/2006/main"

/**
 * Reads a `.pptx` package into a [SlideDeck] using only platform APIs:
 * `java.util.zip` for the container and the XML pull parser for the parts.
 *
 * Read: every slide's text lines in shape order, speaker notes, and the slide
 * size. Not read: images, shape geometry, transitions and animation. Those
 * are preserved untouched by [PptxWriter], which patches text in place rather
 * than re-rendering the deck.
 */
class PptxReader(
    private val newParser: () -> XmlPullParser = {
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    },
) {

    fun read(input: InputStream): SlideDeck {
        val parts = readParts(input)
        val presentation = parts[PRESENTATION_PART]
            ?: throw java.io.IOException("$PRESENTATION_PART not found in the package")

        val size = parseSlideSize(presentation)
        val rels = parts[PRESENTATION_RELS_PART]?.let(::parseRelationships).orEmpty()
        val order = parseSlideOrder(presentation)
            .mapNotNull { relId -> rels[relId] }
            .filter { it in parts }

        // Fall back to scanning the package when presentation.xml lists
        // nothing: some generators write the list without relationships.
        val partsInOrder = if (order.isNotEmpty()) {
            order
        } else {
            parts.keys.filter { slideIndex(it) != null }.sortedBy { slideIndex(it) }
        }

        val slides = partsInOrder.map { partName ->
            val index = slideIndex(partName) ?: 0
            val lines = parseTextLines(parts[partName] ?: ByteArray(0))
            val notes = notesFor(partName, parts)?.let(::parseNotesText).orEmpty()
            val transition = parseTransition(parts[partName]?.decodeToString().orEmpty())
            val objects = parseSlideObjects(parts[partName] ?: ByteArray(0), parts, partName)
            Slide(
                number = index,
                lines = lines,
                notes = notes,
                transition = transition,
                images = objects.first,
                shapes = objects.second,
            )
        }
        return SlideDeck(
            slides = slides,
            widthEmu = size.first,
            heightEmu = size.second,
        )
    }

    private fun readParts(input: InputStream): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name in WANTED_PARTS ||
                    entry.name.startsWith(SLIDES_PREFIX) ||
                    entry.name.startsWith(NOTES_PREFIX) ||
                    entry.name.startsWith(SLIDE_RELS_PREFIX) ||
                    entry.name.startsWith(MEDIA_PREFIX)
                ) {
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw java.io.IOException(
                            "Package exceeds the ${MAX_TOTAL_BYTES / (1024 * 1024)} MB safety limit",
                        )
                    }
                    parts[entry.name] = bytes
                }
            }
        }
        return parts
    }

    /** `ppt/slides/slide7.xml` -> 7; anything else -> null. */
    private fun slideIndex(name: String): Int? {
        if (!name.startsWith(SLIDES_PREFIX) || !name.endsWith(".xml")) return null
        if (!name.removeSuffix(".xml").removePrefix(SLIDES_PREFIX).all { it.isDigit() }) return null
        return name.removePrefix(SLIDES_PREFIX).removeSuffix(".xml").toIntOrNull()
    }

    /** Slide width and height in EMU from `p:sldSz`, with 16:9 defaults. */
    private fun parseSlideSize(bytes: ByteArray): Pair<Int, Int> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sldSz") {
                val cx = parser.getAttributeValue(null, "cx")?.toIntOrNull()
                val cy = parser.getAttributeValue(null, "cy")?.toIntOrNull()
                if (cx != null && cy != null) return cx to cy
            }
            event = parser.next()
        }
        return SlideDeck.DEFAULT_WIDTH_EMU to SlideDeck.DEFAULT_HEIGHT_EMU
    }

    /** `p:sldIdLst` relationship ids, in presentation order. */
    private fun parseSlideOrder(bytes: ByteArray): List<String> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val ids = mutableListOf<String>()
        var inList = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "sldIdLst") inList = true
                    if (inList && parser.name == "sldId") {
                        parser.getAttributeValue(RELATIONSHIPS_NAMESPACE, "id")?.let(ids::add)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "sldIdLst") inList = false
            }
            event = parser.next()
        }
        return ids
    }

    /** Relationship id -> target part name, from a `.rels` part. */
    private fun parseRelationships(bytes: ByteArray): Map<String, String> {
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)
        val rels = mutableMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) rels[id] = resolvePart(target)
            }
            event = parser.next()
        }
        return rels
    }

    /** Resolves a relationship target against the package root. */
    private fun resolvePart(target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val segments = mutableListOf<String>()
        ("ppt/$target").split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    /** The notes part for a slide, if the slide's relationships name one. */
    private fun notesFor(slidePart: String, parts: Map<String, ByteArray>): ByteArray? {
        val relsName = slidePart.substringBeforeLast('/') + "/_rels/" +
            slidePart.substringAfterLast('/') + ".rels"
        val relsBytes = parts[relsName] ?: return null
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(relsBytes), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val type = parser.getAttributeValue(null, "Type").orEmpty()
                val target = parser.getAttributeValue(null, "Target") ?: continue
                if (type.endsWith("/notesSlide")) {
                    return parts[resolvePart("slides/$target")]
                }
            }
            event = parser.next()
        }
        return null
    }

    /**
     * Text of one slide part: every DrawingML paragraph, in document order.
     * Placeholder text on the master is not part of the slide part and is not
     * read.
     */
    private fun parseTextLines(bytes: ByteArray): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var inText = false
        var inParagraph = false
        var hasText = false
        var hasTextElement = false
        var preserveSpace = false
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)

        fun finishParagraph() {
            if (inParagraph && hasTextElement) lines += current.toString()
            current.setLength(0)
            hasText = false
            hasTextElement = false
            inParagraph = false
        }

        fun append(text: String) {
            if (text.isNotEmpty()) hasText = true
            current.append(text)
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name == "p" && parser.namespace == DRAWINGML_NAMESPACE -> {
                        finishParagraph()
                        inParagraph = true
                    }
                    parser.name == "t" && parser.namespace == DRAWINGML_NAMESPACE -> {
                        inText = true
                        hasTextElement = true
                        preserveSpace = parser.getAttributeValue(
                            "http://www.w3.org/XML/1998/namespace", "space",
                        ) == "preserve"
                    }
                    parser.name == "r" && parser.namespace == DRAWINGML_NAMESPACE -> {
                        preserveSpace = false
                    }
                    parser.name == "br" && parser.namespace == DRAWINGML_NAMESPACE -> append("\n")
                }
                XmlPullParser.TEXT -> if (inText) append(parser.text)
                XmlPullParser.END_TAG -> when {
                    parser.name == "t" && parser.namespace == DRAWINGML_NAMESPACE -> {
                        if (!preserveSpace && current.isNotEmpty() && current.last() == ' ') {
                            current.setLength(current.length - 1)
                        }
                        inText = false
                        preserveSpace = false
                    }
                    parser.name == "p" && parser.namespace == DRAWINGML_NAMESPACE -> finishParagraph()
                }
            }
            event = parser.next()
        }
        return lines
    }

    /**
     * The pictures and the shapes on a slide.
     *
     * A picture points at a part of the package through a relationship of
     * the slide's own. A shape that fills a placeholder is left out: it is
     * the slide's text, and the text is read on its own.
     */
    private fun parseSlideObjects(
        bytes: ByteArray,
        parts: Map<String, ByteArray>,
        slidePart: String,
    ): Pair<List<SlideImage>, List<SlideShape>> {
        val rels = parts[relsNameOf(slidePart)]?.let { parseRelationships(it) }.orEmpty()
        val parser = newParser()
        parser.setInput(ByteArrayInputStream(bytes), null)

        val images = mutableListOf<SlideImage>()
        val shapes = mutableListOf<SlideShape>()
        var inPicture = false
        var inShape = false
        var isPlaceholder = false
        var inSolidFill = false
        var relId: String? = null
        var name: String? = null
        var preset: String? = null
        var fill: String? = null
        var x = 0
        var y = 0
        var cx = 0
        var cy = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "pic" -> {
                        inPicture = true
                        relId = null
                        x = 0; y = 0; cx = 0; cy = 0
                    }
                    "sp" -> {
                        inShape = true
                        isPlaceholder = false
                        name = null
                        preset = null
                        fill = null
                        x = 0; y = 0; cx = 0; cy = 0
                    }
                    "ph" -> if (inShape) isPlaceholder = true
                    "cNvPr" -> if (inShape || inPicture) {
                        name = parser.getAttributeValue(null, "name")
                    }
                    "blip" -> if (inPicture) {
                        relId = parser.getAttributeValue(null, "embed")
                    }
                    "off" -> {
                        x = parser.getAttributeValue(null, "x")?.toIntOrNull() ?: x
                        y = parser.getAttributeValue(null, "y")?.toIntOrNull() ?: y
                    }
                    "ext" -> {
                        cx = parser.getAttributeValue(null, "cx")?.toIntOrNull() ?: cx
                        cy = parser.getAttributeValue(null, "cy")?.toIntOrNull() ?: cy
                    }
                    "prstGeom" -> if (inShape) {
                        preset = parser.getAttributeValue(null, "prst")
                    }
                    "solidFill" -> if (inShape) inSolidFill = true
                    "srgbClr" -> if (inShape && inSolidFill) {
                        fill = parser.getAttributeValue(null, "val")
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "solidFill" -> inSolidFill = false
                    "pic" -> {
                        if (inPicture) {
                            val target = relId?.let { rels[it] }
                            val bytes2 = target?.let { parts[it] }
                            if (bytes2 != null) {
                                images += SlideImage(
                                    partName = target,
                                    bytes = bytes2,
                                    xEmu = x,
                                    yEmu = y,
                                    widthEmu = cx,
                                    heightEmu = cy,
                                )
                            }
                        }
                        inPicture = false
                    }
                    "sp" -> {
                        if (inShape && !isPlaceholder) {
                            val kind = when (preset) {
                                "roundRect" -> SlideShapeKind.ROUNDED_RECTANGLE
                                "ellipse" -> SlideShapeKind.ELLIPSE
                                "rect", null -> SlideShapeKind.RECTANGLE
                                else -> null
                            }
                            if (kind != null) {
                                shapes += SlideShape(
                                    kind = kind,
                                    xEmu = x,
                                    yEmu = y,
                                    widthEmu = cx,
                                    heightEmu = cy,
                                    fillRgb = fill?.toIntOrNull(16)?.let {
                                        (it and 0xFFFFFF) or (0xFF shl 24)
                                    },
                                    name = name,
                                )
                            }
                        }
                        inShape = false
                    }
                }
            }
            event = parser.next()
        }
        return images to shapes
    }

    /** The name of the part that holds the relationships of [partName]. */
    private fun relsNameOf(partName: String): String =
        partName.substringBeforeLast('/') + "/_rels/" + partName.substringAfterLast('/') + ".rels"

    /**
     * The transition of a slide.
     *
     * The element names itself: `<p:fade/>` is a fade, `<p:wipe dir="d"/>` is
     * a wipe. A transition ZiyOffice does not know is read as none, so a
     * slide that had one keeps its text and loses only the effect.
     */
    private fun parseTransition(xml: String): SlideTransition {
        val at = xml.indexOf("<p:transition")
        if (at < 0) return SlideTransition.NONE
        // The effect is the child element, not the wrapper, so the whole
        // element is looked at rather than just its opening tag.
        val end = xml.indexOf("</p:transition>", at)
        val window = if (end < 0) {
            xml.substring(at, minOf(xml.length, at + 200))
        } else {
            xml.substring(at, end)
        }
        return when {
            window.contains("<p:fade") -> SlideTransition.FADE
            window.contains("<p:wipe") -> SlideTransition.WIPE
            window.contains("<p:push") -> SlideTransition.PUSH
            window.contains("<p:cover") -> SlideTransition.COVER
            window.contains("<p:dissolve") -> SlideTransition.DISSOLVE
            window.contains("<p:cut") -> SlideTransition.CUT
            window.contains("<p:split") -> SlideTransition.SPLIT
            window.contains("<p:zoom") -> SlideTransition.ZOOM
            else -> SlideTransition.NONE
        }
    }

    /** Speaker notes: the text of the notes part, joined into paragraphs. */    private fun parseNotesText(bytes: ByteArray): String =
        parseTextLines(bytes).joinToString("\n")

    private companion object {
        const val PRESENTATION_PART = "ppt/presentation.xml"
        const val PRESENTATION_RELS_PART = "ppt/_rels/presentation.xml.rels"
        const val RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val SLIDES_PREFIX = "ppt/slides/slide"
        const val SLIDE_RELS_PREFIX = "ppt/slides/_rels/"
        const val NOTES_PREFIX = "ppt/notesSlides/"
        val WANTED_PARTS = setOf(PRESENTATION_PART, PRESENTATION_RELS_PART)
        const val MEDIA_PREFIX = "ppt/media/"

        /** Same 128 MB ceiling the docx reader applies. */
        const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
    }
}
