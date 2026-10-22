package org.ziyoffice.pdf

/**
 * Draws the text of a field.
 *
 * A viewer shows what a field holds by looking at the appearance the field
 * carries. ZiyOffice writes that appearance itself, so the text shows up in
 * any viewer, including the ones that redraw fields on their own.
 */
object PdfAppearance {

    /**
     * The content stream that draws [text] inside [rect].
     *
     * The text is clipped to the field, so a long value runs off the end of
     * the box instead of over the page.
     */
    fun textField(text: String, rect: PdfRect, appearance: String?): ByteArray {
        val style = DefaultAppearance.read(appearance)
        val size = style.fontSize
        val baseline = rect.bottom + kotlin.math.max(2.0, (rect.height - size) / 2)

        val builder = StringBuilder()
        builder.append("q\n")
        builder.append(
            "${number(rect.left)} ${number(rect.bottom)} " +
                "${number(rect.right)} ${number(rect.top)} re W n\n",
        )
        builder.append("BT\n")
        builder.append("/Helv ${number(size.toDouble())} Tf\n")
        builder.append("${style.red} ${style.green} ${style.blue} rg\n")
        builder.append("${number(rect.left + 2)} ${number(baseline)} Td\n")
        builder.append("(${escape(text)}) Tj\n")
        builder.append("ET\nQ\n")
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }

    /** The dictionary of the appearance stream, with the font it needs. */
    fun appearanceDict(rect: PdfRect, length: Int): PdfDict = PdfDict(
        mapOf(
            "Type" to PdfName("XObject"),
            "Subtype" to PdfName("Form"),
            "FormType" to PdfInteger(1),
            "BBox" to PdfArray(
                listOf(
                    PdfReal(rect.left),
                    PdfReal(rect.bottom),
                    PdfReal(rect.right),
                    PdfReal(rect.top),
                ),
            ),
            "Resources" to PdfDict(
                mapOf(
                    "Font" to PdfDict(
                        mapOf(
                            "Helv" to PdfDict(
                                mapOf(
                                    "Type" to PdfName("Font"),
                                    "Subtype" to PdfName("Type1"),
                                    "BaseFont" to PdfName("Helvetica"),
                                    "Encoding" to PdfName("WinAnsiEncoding"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            "Length" to PdfInteger(length.toLong()),
        ),
    )

    /** The text of a content stream, with the characters that need it escaped. */
    fun escape(text: String): String {
        val builder = StringBuilder()
        text.forEach { character ->
            when {
                character == '\\' -> builder.append("\\\\")
                character == '(' -> builder.append("\\(")
                character == ')' -> builder.append("\\)")
                character == '\r' -> builder.append("\\r")
                character == '\n' -> builder.append("\\n")
                character.code < 0x20 || character.code > 0x7E -> {
                    val code = character.code
                    builder.append('\\')
                    builder.append(HEX[code shr 4])
                    builder.append(HEX[code and 0x0F])
                }
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }

    private fun number(value: Double): String = PdfSerializer.formatReal(value)

    private val HEX = "0123456789ABCDEF".toCharArray()
}

/**
 * The appearance a field asks for: how big its text is and what colour it
 * is.
 *
 * The field's own default appearance says both, in a short line such as
 * `/Helv 10 Tf 0 g`.
 */
class DefaultAppearance(
    /** The size of the text, in points. */
    val fontSize: Float,
    /** How red the text is, from zero to one. */
    val red: Double,
    /** How green the text is, from zero to one. */
    val green: Double,
    /** How blue the text is, from zero to one. */
    val blue: Double,
) {
    companion object {
        /** The appearance a field has when it says nothing. */
        val DEFAULT = DefaultAppearance(10f, 0.0, 0.0, 0.0)

        /** Reads the appearance a field asks for, or the default. */
        fun read(appearance: String?): DefaultAppearance {
            if (appearance.isNullOrBlank()) return DEFAULT
            var size = DEFAULT.fontSize
            var red = 0.0
            var green = 0.0
            var blue = 0.0
            val tokens = appearance.trim().split(Regex("\\s+"))
            tokens.forEachIndexed { index, token ->
                when (token) {
                    "Tf" -> size = tokens.getOrNull(index - 1)?.toFloatOrNull() ?: size
                    "g" -> red = tokens.getOrNull(index - 1)?.toDoubleOrNull() ?: red
                    "rg" -> {
                        red = tokens.getOrNull(index - 3)?.toDoubleOrNull() ?: red
                        green = tokens.getOrNull(index - 2)?.toDoubleOrNull() ?: green
                        blue = tokens.getOrNull(index - 1)?.toDoubleOrNull() ?: blue
                    }
                    "k" -> {
                        // Four numbers for cyan, magenta, yellow and black.
                        val c = tokens.getOrNull(index - 4)?.toDoubleOrNull() ?: 0.0
                        val m = tokens.getOrNull(index - 3)?.toDoubleOrNull() ?: 0.0
                        val y = tokens.getOrNull(index - 2)?.toDoubleOrNull() ?: 0.0
                        val k = tokens.getOrNull(index - 1)?.toDoubleOrNull() ?: 0.0
                        red = (1.0 - c) * (1.0 - k)
                        green = (1.0 - m) * (1.0 - k)
                        blue = (1.0 - y) * (1.0 - k)
                    }
                }
            }
            return DefaultAppearance(size, red, green, blue)
        }
    }
}
