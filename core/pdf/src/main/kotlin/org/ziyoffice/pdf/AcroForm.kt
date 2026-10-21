package org.ziyoffice.pdf

/** What a form field is, as far as a person filling it in is concerned. */
enum class PdfFieldKind {
    /** A box to type in. */
    TEXT,

    /** A box to tick. */
    CHECKBOX,

    /** A circle to fill in, of which usually only one in a group can be. */
    RADIO,

    /** A list to pick one of. */
    DROPDOWN,

    /** A list to pick several of. */
    LISTBOX,

    /** A button that does something. ZiyOffice does not press them. */
    BUTTON,

    /** A place to sign. ZiyOffice draws a signature of its own instead. */
    SIGNATURE,
}

/**
 * A rectangle in the file's own units.
 *
 * The origin is the bottom left of the page and the units are points, one
 * seventy-second of an inch.
 */
data class PdfRect(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
) {
    /** How wide the rectangle is. */
    val width: Double get() = right - left

    /** How tall the rectangle is. */
    val height: Double get() = top - bottom

    /**
     * Where the rectangle sits on the page, as fractions of the page's size.
     *
     * The origin is the top left, the way a screen has it, and the page's
     * own rotation is taken into account, so the result can be laid straight
     * over the page as it was drawn.
     */
    fun fractionsOn(page: PdfPage): PdfFractionRect {
        val boxLeft = page.mediaBox[0]
        val boxBottom = page.mediaBox[1]
        val boxWidth = (page.mediaBox[2] - boxLeft).takeIf { it > 0 } ?: 1.0
        val boxHeight = (page.mediaBox[3] - boxBottom).takeIf { it > 0 } ?: 1.0

        val u0 = (left - boxLeft) / boxWidth
        val u1 = (right - boxLeft) / boxWidth
        val v0 = (bottom - boxBottom) / boxHeight
        val v1 = (top - boxBottom) / boxHeight

        return when (page.rotate) {
            90 -> PdfFractionRect(left = v0, top = u0, right = v1, bottom = u1)
            180 -> PdfFractionRect(left = 1.0 - u1, top = v0, right = 1.0 - u0, bottom = v1)
            270 -> PdfFractionRect(left = 1.0 - v1, top = 1.0 - u1, right = 1.0 - v0, bottom = 1.0 - u0)
            else -> PdfFractionRect(left = u0, top = 1.0 - v1, right = u1, bottom = 1.0 - v0)
        }
    }
}

/**
 * A rectangle as a fraction of the page it sits on.
 *
 * The origin is the top left, and the numbers run from zero to one. The
 * right and bottom may fall outside that range when a field hangs off the
 * edge of the page.
 */
data class PdfFractionRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    /** How wide the rectangle is, as a fraction of the page. */
    val width: Double get() = right - left

    /** How tall the rectangle is, as a fraction of the page. */
    val height: Double get() = bottom - top
}

/**
 * One box on the page.
 *
 * A field can have more than one: the same name on two pages, or a radio
 * group with a box for each choice.
 */
class PdfWidget(
    /** The page the box is on, counting from zero. */
    val pageIndex: Int,
    /** Where the box is. */
    val rect: PdfRect,
    /** The number of the object holding the box's dictionary, when it has one. */
    val objectNumber: Int?,
    /** The name of the state that means "on", for a box that can be ticked. */
    val onState: String?,
    /** The value the box gives back when it is on. */
    val exportValue: String?,
)

/**
 * A field of a form.
 *
 * Everything here is what the file says. What the user types into it lives
 * in [PdfFormValues], so the form can be read without being changed.
 */
class PdfFormField(
    /** The name of the field, as the file gives it. */
    val name: String,
    /** What the field is. */
    val kind: PdfFieldKind,
    /** The boxes the field is drawn as. */
    val widgets: List<PdfWidget>,
    /** What the field holds now. */
    val value: String,
    /** The choices a choice field offers. */
    val options: List<String>,
    /** True when the field cannot be changed. */
    val readOnly: Boolean,
    /** True when the field has to be filled in. */
    val required: Boolean,
    /** True when a text field takes more than one line. */
    val multiline: Boolean,
    /** How many characters a text field takes, when it says. */
    val maxLength: Int?,
    /** The dictionary of the field, for writing a new value back. */
    val dict: PdfDict,
    /** The number of the object holding [dict], when the field has one. */
    val objectNumber: Int?,
    /** The appearance the field's own text is drawn with. */
    val defaultAppearance: String?,
) {
    /** The page the field is on: the page of its first box. */
    val pageIndex: Int get() = widgets.firstOrNull()?.pageIndex ?: 0

    /** True when the field is a box that can be ticked. */
    val isToggle: Boolean get() = kind == PdfFieldKind.CHECKBOX || kind == PdfFieldKind.RADIO
}

/**
 * A form read from a file.
 *
 * The fields are in the order the file lists them, which is the order a
 * person fills them in.
 */
class PdfForm(val fields: List<PdfFormField>) {

    /** True when the file has no fields to fill in. */
    val isEmpty: Boolean get() = fields.isEmpty()

    /** How many fields the form has. */
    val size: Int get() = fields.size

    /** The field with the given name, or null. */
    fun field(name: String): PdfFormField? = fields.firstOrNull { it.name == name }

    /** The fields that sit on the given page. */
    fun fieldsOn(pageIndex: Int): List<PdfFormField> =
        fields.filter { field -> field.widgets.any { it.pageIndex == pageIndex } }
}

/**
 * Reads the form out of a file.
 *
 * A PDF form is a tree: the catalogue points at an AcroForm dictionary,
 * which lists the fields, and each field may have children that are its
 * boxes on the page. Some children are fields of their own, which happens
 * with radio groups.
 */
object AcroForm {

    /** The form in [file], or null when the file has none. */
    fun read(file: PdfFile): PdfForm? {
        if (file.encrypted) return null
        val catalog = file.catalog ?: return null
        val acroForm = file.resolveDict(catalog["AcroForm"]) ?: return null
        val fields = acroForm.array("Fields") ?: return null
        val pages = widgetPages(file)

        val found = mutableListOf<PdfFormField>()
        val seen = HashSet<Int>()
        fields.items.forEach { entry ->
            readField(file, entry, pages, found, seen, depth = 0)
        }
        return PdfForm(found)
    }

    /**
     * The page each box is on, by the number of the object it lives in.
     *
     * The pages list their own boxes, which is the only place the file says
     * where a box is on the page. A box that is not an object of its own
     * cannot be found this way, and the field's own page is used instead.
     */
    private fun widgetPages(file: PdfFile): Map<Int, Int> {
        val pages = HashMap<Int, Int>()
        file.pages.forEachIndexed { index, page ->
            val annots = file.resolveArray(page.dict["Annots"]) ?: return@forEachIndexed
            annots.items.forEach { annot ->
                val reference = annot as? PdfRef ?: return@forEach
                pages[reference.number] = index
            }
        }
        return pages
    }

    private fun readField(
        file: PdfFile,
        entry: PdfObject,
        pages: Map<Int, Int>,
        found: MutableList<PdfFormField>,
        seen: MutableSet<Int>,
        depth: Int,
    ) {
        if (depth > 8) return
        val reference = entry as? PdfRef
        if (reference != null && !seen.add(reference.number)) return
        val dict = file.resolveDict(entry) ?: return

        val kids = file.resolveArray(dict["Kids"])
        val kidsAreFields = kids != null && kids.items.any { kid ->
            val kidDict = file.resolveDict(kid)
            kidDict != null && (kidDict.contains("T") || kidDict.contains("FT"))
        }

        if (kidsAreFields) {
            // A field whose children are fields: the children carry the
            // names, so each one is read on its own.
            kids.items.forEach { kid -> readField(file, kid, pages, found, seen, depth + 1) }
            return
        }

        val name = nameOf(dict, "T") ?: return
        val kind = kindOf(dict) ?: return
        val widgets = widgetsOf(file, dict, kids, pages)
        if (widgets.isEmpty()) return

        found += PdfFormField(
            name = name,
            kind = kind,
            widgets = widgets,
            value = valueOf(dict),
            options = optionsOf(dict),
            readOnly = flagsOf(dict) and FLAG_READ_ONLY != 0,
            required = flagsOf(dict) and FLAG_REQUIRED != 0,
            multiline = kind == PdfFieldKind.TEXT && flagsOf(dict) and FLAG_MULTILINE != 0,
            maxLength = dict.integer("MaxLen")?.toInt(),
            dict = dict,
            objectNumber = reference?.number,
            defaultAppearance = appearanceOf(dict),
        )
    }

    /** The boxes a field is drawn as. */
    private fun widgetsOf(
        file: PdfFile,
        field: PdfDict,
        kids: PdfArray?,
        pages: Map<Int, Int>,
    ): List<PdfWidget> {
        val boxes = mutableListOf<PdfWidget>()
        val candidates = kids?.items ?: listOf(field)
        candidates.forEach { candidate ->
            val widgetDict = file.resolveDict(candidate) ?: return@forEach
            val rect = rectOf(widgetDict) ?: return@forEach
            val reference = candidate as? PdfRef
            val pageIndex = reference?.let { pages[it.number] } ?: pageOf(file, widgetDict)
            boxes += PdfWidget(
                pageIndex = pageIndex,
                rect = rect,
                objectNumber = reference?.number,
                onState = onStateOf(widgetDict),
                exportValue = widgetDict.name("Opt"),
            )
        }
        return boxes
    }

    /** The page a box is on, when the box names it itself. */
    private fun pageOf(file: PdfFile, widget: PdfDict): Int {
        val pageRef = widget["P"] as? PdfRef ?: return 0
        val page = file.resolveDict(pageRef) ?: return 0
        val index = file.pages.indexOfFirst { it.dict === page }
        return if (index >= 0) index else 0
    }

    /** The rectangle of a box. */
    private fun rectOf(dict: PdfDict): PdfRect? {
        val array = dict.array("Rect") ?: return null
        val numbers = array.items.mapNotNull { numberOf(it) }
        if (numbers.size < 4) return null
        val left = minOf(numbers[0], numbers[2])
        val right = maxOf(numbers[0], numbers[2])
        val bottom = minOf(numbers[1], numbers[3])
        val top = maxOf(numbers[1], numbers[3])
        return PdfRect(left, bottom, right, top)
    }

    /** What the field is, from its type and its flags. */
    private fun kindOf(dict: PdfDict): PdfFieldKind? = when (dict.name("FT")) {
        "Tx" -> PdfFieldKind.TEXT
        "Ch" -> if (flagsOf(dict) and FLAG_COMBO != 0) {
            PdfFieldKind.DROPDOWN
        } else {
            PdfFieldKind.LISTBOX
        }
        "Sig" -> PdfFieldKind.SIGNATURE
        "Btn" -> {
            val flags = flagsOf(dict)
            when {
                flags and FLAG_PUSH_BUTTON != 0 -> PdfFieldKind.BUTTON
                flags and FLAG_RADIO != 0 -> PdfFieldKind.RADIO
                else -> PdfFieldKind.CHECKBOX
            }
        }
        else -> null
    }

    /** What the field holds now. */
    private fun valueOf(dict: PdfDict): String = when (val value = dict["V"]) {
        is PdfString -> value.text
        is PdfName -> value.value
        is PdfInteger -> value.value.toString()
        is PdfBool -> if (value.value) "Yes" else "Off"
        else -> ""
    }

    /** The choices a choice field offers. */
    private fun optionsOf(dict: PdfDict): List<String> {
        val options = dict.array("Opt") ?: return emptyList()
        return options.items.mapNotNull { option ->
            when (option) {
                is PdfString -> option.text
                // A pair: the value to store, and the text to show.
                is PdfArray -> (option.items.getOrNull(1) as? PdfString)?.text
                    ?: (option.items.getOrNull(0) as? PdfString)?.text
                else -> null
            }
        }
    }

    /**
     * The state that means "on" for a box that can be ticked.
     *
     * The appearances of a box are named after the states it can be in. The
     * one that is not "Off" is the one that means it is on.
     */
    private fun onStateOf(widget: PdfDict): String? {
        val appearances = widget.dict("AP") ?: return null
        val normal = appearances["N"] ?: return null
        val names = when (normal) {
            is PdfDict -> normal.entries.keys
            else -> return null
        }
        return names.firstOrNull { it != "Off" && it.isNotEmpty() }
    }

    /** The appearance a field's own text is drawn with. */
    private fun appearanceOf(dict: PdfDict): String? =
        (dict["DA"] as? PdfString)?.text

    /** The flags of a field. */
    private fun flagsOf(dict: PdfDict): Int = dict.integer("Ff")?.toInt() ?: 0

    /**
     * The name of a thing, however the file spells it.
     *
     * A field's name is usually a name, but it may also be a string, which
     * is how a name with a space or a slash in it is written.
     */
    private fun nameOf(dict: PdfDict, key: String): String? = when (val value = dict[key]) {
        is PdfName -> value.value
        is PdfString -> value.text
        else -> null
    }

    private fun numberOf(value: PdfObject): Double? = when (value) {
        is PdfInteger -> value.value.toDouble()
        is PdfReal -> value.value
        else -> null
    }

    /** The field cannot be changed. */
    const val FLAG_READ_ONLY = 1

    /** The field has to be filled in. */
    const val FLAG_REQUIRED = 2

    /** The field is a group of radio buttons. */
    const val FLAG_RADIO = 1 shl 15

    /** The field is a button that does something. */
    const val FLAG_PUSH_BUTTON = 1 shl 16

    /** The field is a list with a box to type in as well. */
    const val FLAG_COMBO = 1 shl 17

    /** The field takes more than one line of text. */
    const val FLAG_MULTILINE = 1 shl 22
}
