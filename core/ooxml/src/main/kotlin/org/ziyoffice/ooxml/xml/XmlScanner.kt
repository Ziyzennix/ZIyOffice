package org.ziyoffice.ooxml.xml

/**
 * A minimal XML scanner that keeps the source offsets of everything it finds.
 *
 * The platform pull parser (`org.xmlpull`) reads a document but throws away the
 * byte offsets, and the OOXML preservation work needs them: a paragraph the
 * user did not touch must come out of a save byte-identical, and one they did
 * touch must keep every element the editor does not understand. With offsets
 * both are easy: copy the original source span verbatim, or splice a
 * replacement into it.
 *
 * The scanner is deliberately small. It reads the subset of XML that OOXML
 * parts use (elements, attributes, text, CDATA, comments, processing
 * instructions) and keeps the original text so nothing is normalised away.
 * It is not a validating parser: it never resolves namespaces beyond the
 * prefix and local name, and it does not check well-formedness beyond
 * matching tags. Malformed input produces a tree that stops at the problem
 * rather than throwing, so a damaged document still opens with whatever was
 * readable.
 */
internal class XmlElement(
    /** Qualified name exactly as written, e.g. `w:p`. */
    val qName: String,
    val attributes: List<XmlAttribute>,
    val children: List<XmlElement>,
    /** Offset of the `<` that opens the element. */
    val start: Int,
    /** Offset just past the `>` that closes the element. */
    val end: Int,
    /** Offset just past the `>` of the start tag. */
    val contentStart: Int,
    /** Offset of the `<` of the end tag (equal to [end] for a self-closing tag). */
    val contentEnd: Int,
    val selfClosing: Boolean,
    /**
     * Namespace bindings in scope for this element: prefix to URI, with the
     * default namespace under the empty prefix.
     */
    val namespaces: Map<String, String> = emptyMap(),
    /** The namespace this element's own name resolves to, or null when none. */
    val namespaceUri: String? = null,
) {
    val localName: String get() = qName.substringAfterLast(':')

    /** Namespace prefix, or an empty string when the element has none. */
    val prefix: String get() = qName.substringBefore(':', "")

    /** The original source of this element, tags included. */
    fun source(xml: String): String = xml.substring(start, end)

    /** The original source of this element's inner content. */
    fun innerSource(xml: String): String = xml.substring(contentStart, contentEnd)

    /**
     * The attribute with this local name, namespace prefix ignored. Attribute
     * names in OOXML are effectively unique by local name inside one element,
     * so this is the lookup most callers want.
     */
    fun attr(name: String): String? =
        attributes.firstOrNull { it.localName == name }?.value

    /**
     * The attribute with this local name in the given namespace, or, when the
     * namespace is null, one that has no namespace at all.
     *
     * This mirrors how a namespace-aware pull parser resolves attributes: an
     * unprefixed attribute is in no namespace, so `<x val="1"/>` is found by
     * `attr(null, "val")` but `<x r:val="1"/>` is not.
     */
    fun attr(namespaceUri: String?, name: String): String? {
        for (attribute in attributes) {
            if (attribute.localName != name) continue
            val uri = attributeNamespaceUri(attribute)
            if (uri == namespaceUri) return attribute.value
        }
        return null
    }

    /** The namespace an attribute belongs to, resolving its prefix. */
    fun attributeNamespaceUri(attribute: XmlAttribute): String? {
        if (!attribute.qName.contains(':')) return null
        val prefix = attribute.qName.substringBefore(':', "")
        return namespaces[prefix]
    }

    fun attrExact(qName: String): String? =
        attributes.firstOrNull { it.qName == qName }?.value

    /** Every descendant element, in document order. */
    fun descendants(): Sequence<XmlElement> = sequence {
        for (child in children) {
            yield(child)
            yieldAll(child.descendants())
        }
    }

    fun childrenNamed(name: String): List<XmlElement> = children.filter { it.localName == name }

    fun child(name: String): XmlElement? = children.firstOrNull { it.localName == name }

    /** Concatenated direct text of this element, with entities decoded. */
    val text: String get() = textBuilder().toString()

    private fun textBuilder(): StringBuilder {
        val builder = StringBuilder()
        collectText(builder)
        return builder
    }

    private fun collectText(builder: StringBuilder) {
        for (child in children) {
            if (child.isText) builder.append(child.textValue)
            else child.collectText(builder)
        }
    }

    /** True for the synthetic text nodes the scanner keeps between elements. */
    val isText: Boolean get() = qName == TEXT_MARKER

    /** The decoded text of a text node. */
    val textValue: String get() = attributes.firstOrNull()?.value.orEmpty()

    internal companion object {
        const val TEXT_MARKER = "#text"
    }
}

/**
 * One attribute of an element.
 *
 * [start] and [end] are the offsets the attribute occupies in the source, so
 * a writer can replace one attribute without touching the rest of the element.
 */
internal data class XmlAttribute(
    val qName: String,
    val value: String,
    val start: Int = -1,
    val end: Int = -1,
) {
    val localName: String get() = qName.substringAfterLast(':')
}

/** The root element of a scanned document, plus the whole source. */
internal class XmlDocument(val xml: String, val root: XmlElement)

/**
 * Scans [xml] into a tree of [XmlElement]s. Text between elements becomes a
 * child element named `#text`, so the tree can reproduce the original source
 * exactly when it is spliced back.
 */
internal object XmlScanner {

    fun parse(xml: String): XmlDocument {
        val parser = Parser(xml)
        return XmlDocument(xml, parser.parseDocument())
    }

    private class Parser(private val xml: String) {
        private var pos = 0

        /** Namespace bindings in scope, prefix to URI ("" holds the default). */
        private var scope: Map<String, String> = emptyMap()

        fun parseDocument(): XmlElement {
            val children = parseChildren()
            val root = children.lastOrNull { !it.isText }
            return root ?: XmlElement(
                qName = "",
                attributes = emptyList(),
                children = children,
                start = 0,
                end = xml.length,
                contentStart = 0,
                contentEnd = xml.length,
                selfClosing = false,
            )
        }

    private fun parseChildren(): List<XmlElement> {
            val children = mutableListOf<XmlElement>()
            while (pos < xml.length) {
                val lt = xml.indexOf('<', pos)
                if (lt < 0) {
                    addText(children, pos, xml.length)
                    pos = xml.length
                    break
                }
                if (lt > pos) addText(children, pos, lt)
                pos = lt
                when {
                    xml.startsWith("<!--", pos) -> skipComment()
                    xml.startsWith("<![CDATA[", pos) -> addCdata(children)
                    xml.startsWith("<?", pos) -> skipUntil("?>")
                    xml.startsWith("<!", pos) -> skipDoctype()
                    xml.startsWith("</", pos) -> {
                        // A closing tag: the caller decides what to do with it.
                        return children
                    }
                    else -> {
                        val element = parseElement()
                        if (element != null) children += element
                    }
                }
            }
            return children
        }

        private fun addText(children: MutableList<XmlElement>, from: Int, to: Int) {
            if (to <= from) return
            val raw = xml.substring(from, to)
            if (raw.isEmpty()) return
            children += XmlElement(
                qName = XmlElement.TEXT_MARKER,
                attributes = listOf(XmlAttribute("value", decodeEntities(raw))),
                children = emptyList(),
                start = from,
                end = to,
                contentStart = from,
                contentEnd = to,
                selfClosing = false,
            )
        }

        private fun addCdata(children: MutableList<XmlElement>) {
            val start = pos
            val end = xml.indexOf("]]>", pos)
            val stop = if (end < 0) xml.length else end + 3
            val body = if (end < 0) xml.substring(start + 9) else xml.substring(start + 9, end)
            children += XmlElement(
                qName = XmlElement.TEXT_MARKER,
                attributes = listOf(XmlAttribute("value", body)),
                children = emptyList(),
                start = start,
                end = stop,
                contentStart = start + 9,
                contentEnd = if (end < 0) xml.length else end,
                selfClosing = false,
            )
            pos = stop
        }

        private fun skipComment() {
            val end = xml.indexOf("-->", pos)
            pos = if (end < 0) xml.length else end + 3
        }

        private fun skipUntil(marker: String) {
            val end = xml.indexOf(marker, pos)
            pos = if (end < 0) xml.length else end + marker.length
        }

        private fun skipDoctype() {
            // A DOCTYPE may carry an internal subset in square brackets.
            var index = pos + 2
            var inBracket = false
            while (index < xml.length) {
                val ch = xml[index]
                when {
                    ch == '[' -> inBracket = true
                    ch == ']' -> inBracket = false
                    ch == '>' && !inBracket -> {
                        index++
                        break
                    }
                }
                index++
            }
            pos = index
        }

        /** Parses one element, leaving [pos] just past its end tag. */
        private fun parseElement(): XmlElement? {
            val start = pos
            pos++ // '<'
            val nameEnd = scanNameEnd()
            if (nameEnd < 0) {
                // Not an element after all; treat the '<' as text.
                pos = start + 1
                return null
            }
            val qName = xml.substring(start + 1, nameEnd)
            pos = nameEnd
            val attributes = mutableListOf<XmlAttribute>()
            var selfClosing = false
            while (pos < xml.length) {
                skipWhitespace()
                if (pos >= xml.length) break
                if (xml[pos] == '>') {
                    pos++
                    break
                }
                if (xml[pos] == '/') {
                    selfClosing = true
                    pos++
                    if (pos < xml.length && xml[pos] == '>') pos++
                    break
                }
                val attr = parseAttribute() ?: break
                attributes += attr
            }
            val outerScope = scope
            scope = extendScope(outerScope, attributes)
            val contentStart = pos
            if (selfClosing) {
                val element = XmlElement(
                    qName = qName,
                    attributes = attributes,
                    children = emptyList(),
                    start = start,
                    end = pos,
                    contentStart = pos,
                    contentEnd = pos,
                    selfClosing = true,
                    namespaces = scope,
                    namespaceUri = resolveNamespace(scope, qName),
                )
                scope = outerScope
                return element
            }
            val children = parseChildren()
            // The closing tag: skip it, tolerating a missing one.
            if (xml.startsWith("</", pos)) {
                val closeEnd = xml.indexOf('>', pos)
                pos = if (closeEnd < 0) xml.length else closeEnd + 1
            }
            val element = XmlElement(
                qName = qName,
                attributes = attributes,
                children = children,
                start = start,
                end = pos,
                contentStart = contentStart,
                contentEnd = if (pos > contentStart) findContentEnd(contentStart, pos) else contentStart,
                selfClosing = false,
                namespaces = scope,
                namespaceUri = resolveNamespace(scope, qName),
            )
            scope = outerScope
            return element
        }

        private fun findContentEnd(contentStart: Int, end: Int): Int {
            val close = xml.lastIndexOf("</", end)
            return if (close in contentStart..end) close else contentStart
        }

        /** Adds the element's own `xmlns` declarations to the inherited scope. */
        private fun extendScope(
            outer: Map<String, String>,
            attributes: List<XmlAttribute>,
        ): Map<String, String> {
            var result = outer
            for (attribute in attributes) {
                when {
                    attribute.qName == "xmlns" -> {
                        result = result + ("" to attribute.value)
                    }
                    attribute.qName.startsWith("xmlns:") -> {
                        result = result + (attribute.qName.removePrefix("xmlns:") to attribute.value)
                    }
                }
            }
            return result
        }

        private fun resolveNamespace(scope: Map<String, String>, qName: String): String? {
            if (!qName.contains(':')) return scope[""]
            return scope[qName.substringBefore(':', "")]
        }

        private fun scanNameEnd(): Int {
            var index = pos
            while (index < xml.length) {
                val ch = xml[index]
                if (ch.isWhitespace() || ch == '>' || ch == '/') break
                index++
            }
            return if (index > pos) index else -1
        }

        private fun parseAttribute(): XmlAttribute? {
            val start = pos
            var index = pos
            while (index < xml.length) {
                val ch = xml[index]
                if (ch.isWhitespace() || ch == '=' || ch == '>' || ch == '/') break
                index++
            }
            if (index == start) {
                pos++
                return null
            }
            val name = xml.substring(start, index)
            pos = index
            skipWhitespace()
            if (pos >= xml.length || xml[pos] != '=') {
                return XmlAttribute(name, "", start, pos)
            }
            pos++
            skipWhitespace()
            if (pos >= xml.length) return XmlAttribute(name, "", start, pos)
            val quote = xml[pos]
            if (quote != '"' && quote != '\'') {
                // Unquoted attribute value: read up to whitespace.
                val valueStart = pos
                var end = pos
                while (end < xml.length && !xml[end].isWhitespace() && xml[end] != '>' && xml[end] != '/') end++
                pos = end
                return XmlAttribute(name, decodeEntities(xml.substring(valueStart, end)), start, end)
            }
            pos++
            val valueStart = pos
            val valueEnd = xml.indexOf(quote, pos)
            if (valueEnd < 0) {
                pos = xml.length
                return XmlAttribute(name, decodeEntities(xml.substring(valueStart)), start, pos)
            }
            pos = valueEnd + 1
            return XmlAttribute(
                name,
                decodeEntities(xml.substring(valueStart, valueEnd)),
                start,
                pos,
            )
        }

        private fun skipWhitespace() {
            while (pos < xml.length && xml[pos].isWhitespace()) pos++
        }
    }
}

/** Decodes the five predefined entities plus numeric character references. */
internal fun decodeEntities(value: String): String {
    if ('&' !in value) return value
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch != '&') {
            out.append(ch)
            index++
            continue
        }
        val end = value.indexOf(';', index + 1)
        if (end < 0 || end - index > 12) {
            out.append(ch)
            index++
            continue
        }
        val body = value.substring(index + 1, end)
        val decoded = when {
            body == "amp" -> "&"
            body == "lt" -> "<"
            body == "gt" -> ">"
            body == "quot" -> "\""
            body == "apos" -> "'"
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.toChar()?.toString()
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.toChar()?.toString()
            else -> null
        }
        if (decoded == null) {
            out.append(ch)
            index++
        } else {
            out.append(decoded)
            index = end + 1
        }
    }
    return out.toString()
}

/** Escapes text for use inside an XML element body. */
internal fun escapeXmlText(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            else -> append(ch)
        }
    }
}

/** Escapes text for use inside an XML attribute value. */
internal fun escapeXmlAttribute(value: String): String = buildString(value.length) {
    value.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }
}
