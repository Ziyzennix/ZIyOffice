package org.ziyoffice.ooxml

import org.ziyoffice.model.Cell
import org.ziyoffice.model.CellRange
import org.ziyoffice.model.Sheet
import org.ziyoffice.model.columnName
import org.ziyoffice.ooxml.xml.XmlElement
import org.ziyoffice.ooxml.xml.XmlScanner

/**
 * Notes pinned to cells.
 *
 * A note in a spreadsheet is a short piece of text attached to a cell, shown
 * when the cell is pointed at. A file holds them in two parts: the comments
 * themselves, and a drawing that says where each one sits on the sheet.
 * The drawing is the old format a spreadsheet program still expects, so it
 * is written even though nothing in ZiyOffice draws it.
 */
internal object XlsxNotes {

    /** The relationship type of the part the notes live in. */
    const val COMMENTS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments"

    /** The relationship type of the part that says where the notes sit. */
    const val VML_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/vmlDrawing"

    /** The content type of the part the notes live in. */
    const val COMMENTS_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml"

    /** The content type of the part that says where the notes sit. */
    const val VML_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.vmlDrawing"

    /**
     * The part the notes of [sheetPart] live in, or null when the sheet has
     * none.
     */
    fun commentsPartOf(parts: Map<String, ByteArray>, sheetPart: String): String? {
        val rels = parts[relsNameOf(sheetPart)] ?: return null
        val root = XmlScanner.parse(rels.decodeToString()).root
        root.childrenNamed("Relationship").forEach { element ->
            if (element.attr("Type") != COMMENTS_REL) return@forEach
            val target = element.attr("Target") ?: return@forEach
            if (element.attr("TargetMode") == "External") return@forEach
            return resolveSheetTarget(sheetPart, target)
        }
        return null
    }

    /** The name of the part that holds the relationships of [partName]. */
    fun relsNameOf(partName: String): String =
        partName.substringBeforeLast('/') + "/_rels/" + partName.substringAfterLast('/') + ".rels"

    /** A target in a sheet's relationships, as a path in the package. */
    fun resolveSheetTarget(sheetPart: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        var path = sheetPart.substringBeforeLast('/', "")
        target.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> path = path.substringBeforeLast('/', "")
                else -> path = if (path.isEmpty()) segment else "$path/$segment"
            }
        }
        return path
    }

    /**
     * The notes in a comments part, keyed by the cell they belong to.
     *
     * The text of a note may be split over several runs, so every piece is
     * taken. The phonetic hint a program leaves beside a note is skipped: it
     * is a reading of the text, not the text.
     */
    fun parseNotes(xml: String): Map<Pair<Int, Int>, String> {
        val root = XmlScanner.parse(xml).root
        val list = root.child("commentList") ?: return emptyMap()
        val notes = mutableMapOf<Pair<Int, Int>, String>()
        list.childrenNamed("comment").forEach { element ->
            val ref = element.attr("ref") ?: return@forEach
            val position = parseCellRef(ref) ?: return@forEach
            val text = element.child("text")?.let { collectNoteText(it) }.orEmpty()
            if (text.isNotEmpty()) notes[position] = text
        }
        return notes
    }

    /** The text of a note, without the phonetic hint beside it. */
    private fun collectNoteText(element: XmlElement): String {
        val builder = StringBuilder()
        fun walk(node: XmlElement) {
            if (node.localName == "rPh") return
            if (node.localName == "t") {
                builder.append(node.text)
                return
            }
            node.children.forEach { walk(it) }
        }
        walk(element)
        return builder.toString()
    }

    /** The cell a reference names, as a zero-based position. */
    fun parseCellRef(reference: String): Pair<Int, Int>? {
        val letters = reference.takeWhile { it.isLetter() }
        val digits = reference.dropWhile { it.isLetter() }
        if (letters.isEmpty() || digits.isEmpty()) return null
        val column = org.ziyoffice.model.columnIndex(letters)
        if (column < 0) return null
        val row = digits.toIntOrNull() ?: return null
        return (row - 1) to column
    }

    /** The notes of a sheet, keyed by the cell they belong to. */
    fun notesOf(sheet: Sheet): Map<Pair<Int, Int>, String> =
        sheet.cells.mapNotNull { cell ->
            if (cell.note.isEmpty()) null else (cell.row to cell.column) to cell.note
        }.toMap()

    /**
     * The part the notes live in.
     *
     * One author is enough: the notes ZiyOffice writes are the user's own.
     */
    fun commentsXml(notes: Map<Pair<Int, Int>, String>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<comments xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<authors><author>ZiyOffice</author></authors><commentList>")
        notes.forEach { (position, text) ->
            append("<comment ref=\"").append(referenceOf(position)).append("\" authorId=\"0\">")
            append("<text><r><rPr><sz val=\"9\"/><color indexed=\"81\"/>")
            append("<rFont val=\"Tahoma\"/><family val=\"2\"/></rPr>")
            append("<t xml:space=\"preserve\">").append(escapeXml(text)).append("</t></r></text>")
            append("</comment>")
        }
        append("</commentList></comments>")
    }

    /**
     * The part that says where the notes sit.
     *
     * A spreadsheet program expects one shape per note, each pointing at the
     * cell it belongs to. The shape is hidden; the program shows it when the
     * cell is pointed at.
     */
    fun vmlDrawingXml(notes: Map<Pair<Int, Int>, String>): String = buildString {
        append("<xml xmlns:v=\"urn:schemas-microsoft-com:vml\" ")
        append("xmlns:o=\"urn:schemas-microsoft-com:office:office\" ")
        append("xmlns:x=\"urn:schemas-microsoft-com:office:excel\">")
        append("<o:shapelayout v:ext=\"edit\"><o:idmap v:ext=\"edit\" data=\"1\"/></o:shapelayout>")
        append("<v:shapetype id=\"_x0000_t202\" coordsize=\"21600,21600\" o:spt=\"202\" ")
        append("path=\"m,l,21600r21600,l21600,xe\"><v:stroke joinstyle=\"miter\"/>")
        append("<v:path gradientshapeok=\"t\" o:connecttype=\"rect\"/></v:shapetype>")
        notes.keys.sortedWith(compareBy({ it.first }, { it.second })).forEachIndexed { index, position ->
            val id = 1025 + index
            append("<v:shape id=\"_x0000_s").append(id)
            append("\" type=\"#_x0000_t202\" style='position:absolute;")
            append("margin-left:59.25pt;margin-top:1.5pt;width:108pt;height:59.25pt;")
            append("z-index:").append(index + 1).append(";visibility:hidden' ")
            append("fillcolor=\"#ffffe1\" o:insetmode=\"auto\">")
            append("<v:fill color2=\"#ffffe1\"/><v:shadow on=\"t\" color=\"black\" obscured=\"t\"/>")
            append("<v:path o:connecttype=\"none\"/>")
            append("<v:textbox style='mso-direction-alt:auto'><div style='text-align:left'></div>")
            append("</v:textbox>")
            append("<x:ClientData ObjectType=\"Note\"><x:MoveWithCells/><x:SizeWithCells/>")
            append("<x:Anchor>").append(position.second + 1).append(", 15, ")
            append(position.first).append(", 2, ").append(position.second + 4).append(", 15, ")
            append(position.second + 4).append(", 16</x:Anchor>")
            append("<x:AutoFill>False</x:AutoFill>")
            append("<x:Row>").append(position.first).append("</x:Row>")
            append("<x:Column>").append(position.second).append("</x:Column>")
            append("</x:ClientData></v:shape>")
        }
        append("</xml>")
    }

    /** The reference a cell is written as. */
    fun referenceOf(position: Pair<Int, Int>): String =
        columnName(position.second) + (position.first + 1).toString()

    /** The text of a cell reference, such as "A1". */
    fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** The range a cell belongs to, for the record. */
    fun rangeOf(position: Pair<Int, Int>): CellRange =
        CellRange(position.first, position.second, position.first, position.second)
}
