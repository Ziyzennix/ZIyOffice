package org.ziyoffice.pdf

import java.io.ByteArrayOutputStream

/**
 * Writes values into a form and saves the file.
 *
 * The file is not rebuilt. The bytes that were there stay there, and what
 * changed is added at the end: the objects whose value changed, a new
 * cross-reference that points at them, and a trailer that points at the old
 * one. A viewer reads the last cross-reference and finds the new objects
 * there, and everything else it already had is exactly as it was.
 *
 * That is what keeps a form filling from taking the document apart: the
 * fonts, the images and the scripts stay byte for byte as they were.
 */
class PdfFormWriter(private val file: PdfFile) {

    /**
     * The file with [values] written into the fields they name.
     *
     * A field the file does not have is ignored, and a field whose value
     * cannot be written keeps the value it had.
     */
    fun write(values: Map<String, String>): ByteArray {
        val form = AcroForm.read(file) ?: return file.bytes
        val updates = LinkedHashMap<Int, PdfObject>()
        var nextNumber = (file.trailer.integer("Size")?.toInt() ?: 0) + 1

        form.fields.forEach { field ->
            val value = values[field.name] ?: return@forEach
            if (field.readOnly) return@forEach
            val number = field.objectNumber ?: return@forEach

            when {
                field.isToggle -> {
                    val widget = field.widgets.firstOrNull()
                    val onState = widget?.onState ?: "Yes"
                    val offState = widget?.offState ?: "Off"
                    // An empty value means the box is not ticked.
                    val state = if (value.isEmpty() || value == "Off") offState else onState
                    val updated = field.dict
                        .with("V", PdfName(state))
                        .with("AS", PdfName(state))
                    updates[number] = updated
                    field.widgets.forEach { widget ->
                        val widgetNumber = widget.objectNumber ?: return@forEach
                        if (widgetNumber == number) return@forEach
                        val widgetDict = file.resolveDict(PdfRef(widgetNumber, 0)) ?: return@forEach
                        updates[widgetNumber] = widgetDict.with("AS", PdfName(state))
                    }
                }
                field.kind == PdfFieldKind.DROPDOWN || field.kind == PdfFieldKind.LISTBOX -> {
                    updates[number] = field.dict.with("V", PdfString(value))
                }
                field.kind == PdfFieldKind.TEXT -> {
                    val widget = field.widgets.firstOrNull() ?: return@forEach
                    val content = PdfAppearance.textField(value, widget.rect, field.defaultAppearance)
                    val appearanceNumber = nextNumber++
                    val stream = PdfStream(
                        PdfAppearance.appearanceDict(widget.rect, content.size),
                        content,
                    )
                    updates[appearanceNumber] = stream
                    val appearances = field.dict.dict("AP")
                        ?.with("N", PdfRef(appearanceNumber, 0))
                        ?: PdfDict(mapOf("N" to PdfRef(appearanceNumber, 0)))
                    updates[number] = field.dict
                        .with("V", PdfString(value))
                        .with("AP", appearances)
                }
            }
        }

        if (updates.isEmpty()) return file.bytes

        // The form asks the viewer to redraw the fields, so a viewer that
        // ignores the appearance we wrote still shows the new value.
        val acroFormNumber = acroFormNumber()
        if (acroFormNumber != null) {
            val acroForm = file.resolveDict(PdfRef(acroFormNumber, 0))
            if (acroForm != null) {
                updates[acroFormNumber] = acroForm.with("NeedAppearances", PdfBool(true))
            }
        }

        return appendUpdates(updates, nextNumber)
    }

    /** The number of the object holding the form's own dictionary. */
    private fun acroFormNumber(): Int? {
        val catalog = file.catalog ?: return null
        val reference = catalog["AcroForm"] as? PdfRef ?: return null
        return reference.number
    }

    /**
     * The file with the changed objects added to the end of it.
     */
    private fun appendUpdates(updates: Map<Int, PdfObject>, nextNumber: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(file.bytes)

        val offsets = LinkedHashMap<Int, Int>()
        updates.forEach { (number, value) ->
            offsets[number] = out.size()
            out.write("$number 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(PdfSerializer.write(value))
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        val xrefOffset = out.size()
        out.write("xref\n".toByteArray(Charsets.ISO_8859_1))
        for (group in runs(offsets.keys.sorted())) {
            out.write("${group[0]} ${group.size}\n".toByteArray(Charsets.ISO_8859_1))
            group.forEach { number ->
                out.write("%010d 00000 n \n".format(offsets.getValue(number)).toByteArray(Charsets.ISO_8859_1))
            }
        }

        val size = maxOf(
            file.trailer.integer("Size")?.toInt() ?: 0,
            nextNumber,
        )
        out.write("trailer\n".toByteArray(Charsets.ISO_8859_1))
        out.write(
            (
                "<< /Size $size /Root ${rootReference()} " +
                    previousEntry() + identifierEntry() + " >>\n"
                ).toByteArray(Charsets.ISO_8859_1),
        )
        out.write("startxref\n$xrefOffset\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    /** The root, as the trailer names it. */
    private fun rootReference(): String {
        val root = file.trailer["Root"] as? PdfRef ?: return "1 0 R"
        return "${root.number} ${root.generation} R"
    }

    /** The pointer back to the cross-reference the file already had. */
    private fun previousEntry(): String =
        if (file.startXrefOffset < 0) "" else "/Prev ${file.startXrefOffset} "

    /** The file's own identifier, carried over so the file stays the same one. */
    private fun identifierEntry(): String {
        val id = file.trailer["ID"] as? PdfArray ?: return ""
        val parts = id.items.mapNotNull { (it as? PdfString)?.bytes }
        if (parts.size < 2) return ""
        val text = parts.joinToString("") { part ->
            part.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        }
        return "/ID [<$text> <$text>] "
    }

    /** The numbers that follow each other, grouped together. */
    private fun runs(numbers: List<Int>): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        numbers.forEach { number ->
            if (current.isNotEmpty() && number != current.last() + 1) {
                result += current
                current = mutableListOf()
            }
            current += number
        }
        if (current.isNotEmpty()) result += current
        return result
    }
}
