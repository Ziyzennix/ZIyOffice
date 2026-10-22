package org.ziyoffice.model

/**
 * A presentation: its slides in presentation order. The model is deliberately flat (text
 * lines per slide) because that is what can be edited without a full DrawingML renderer.
 * The writer patches the text runs in the original package, so themes, layouts, images,
 * notes and animation survive an edit untouched.
 */
data class SlideDeck(
    val slides: List<Slide>,
    /** Slide width in EMU (914400 per inch); 16:9 by default. */
    val widthEmu: Int = DEFAULT_WIDTH_EMU,
    /** Slide height in EMU; 16:9 by default. */
    val heightEmu: Int = DEFAULT_HEIGHT_EMU,
) {
    val aspectRatio: Float get() = widthEmu.toFloat() / heightEmu.coerceAtLeast(1)

    companion object {
        /** 13.333 in, the PowerPoint widescreen default. */
        const val DEFAULT_WIDTH_EMU = 12_192_000
        const val DEFAULT_HEIGHT_EMU = 6_858_000

        fun blank(slideCount: Int = 1): SlideDeck = SlideDeck(
            slides = List(slideCount.coerceAtLeast(1)) { index ->
                Slide(number = index + 1, lines = emptyList())
            },
        )
    }
}

/**
 * One slide: its text lines in reading order, plus the index of its part in the package
 * (`ppt/slides/slide<number>.xml`). [number] is the *part* index, not the position in
 * the deck: reordering slides must not rename parts, and the writer needs the index to
 * patch the right file.
 */
data class Slide(
    val number: Int,
    val lines: List<String>,
    /** Speaker notes, read from the notes part; empty when the slide has none. */
    val notes: String = "",
    /** How the slide gives way to the next one. */
    val transition: SlideTransition = SlideTransition.NONE,
    /** The pictures on the slide. */
    val images: List<SlideImage> = emptyList(),
    /** The shapes on the slide. */
    val shapes: List<SlideShape> = emptyList(),
) {
    val title: String get() = lines.firstOrNull().orEmpty()
    val body: List<String> get() = lines.drop(1)
}

/**
 * A picture on a slide.
 *
 * The bytes stay as they are, in the part the file keeps them in, so a save
 * puts the same picture back rather than a new copy of it.
 */
class SlideImage(
    /** The part the picture's bytes live in, e.g. "ppt/media/image1.png". */
    val partName: String,
    val bytes: ByteArray,
    /** Where the picture sits, in EMU. */
    val xEmu: Int,
    val yEmu: Int,
    /** How wide the picture is, in EMU. */
    val widthEmu: Int,
    /** How tall the picture is, in EMU. */
    val heightEmu: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is SlideImage &&
            partName == other.partName &&
            bytes.contentEquals(other.bytes) &&
            xEmu == other.xEmu &&
            yEmu == other.yEmu &&
            widthEmu == other.widthEmu &&
            heightEmu == other.heightEmu

    override fun hashCode(): Int {
        var result = partName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + xEmu
        result = 31 * result + yEmu
        result = 31 * result + widthEmu
        result = 31 * result + heightEmu
        return result
    }

    /** The same picture, with [partName] changed. */
    fun copy(partName: String = this.partName): SlideImage =
        SlideImage(partName, bytes, xEmu, yEmu, widthEmu, heightEmu)
}

/** A shape on a slide. */
data class SlideShape(
    /** What the shape looks like. */
    val kind: SlideShapeKind,
    /** Where the shape sits, in EMU. */
    val xEmu: Int,
    val yEmu: Int,
    /** How wide the shape is, in EMU. */
    val widthEmu: Int,
    /** How tall the shape is, in EMU. */
    val heightEmu: Int,
    /** The colour of the shape, 0xRRGGBB; null when it has no fill. */
    val fillRgb: Int? = null,
    /**
     * The name the file gave the shape.
     *
     * A shape the file already had keeps its name, so a save knows it is the
     * same shape and leaves it alone.
     */
    val name: String? = null,
)

/** The shapes ZiyOffice draws. */
enum class SlideShapeKind {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    ELLIPSE,
}

/**
 * How a slide gives way to the next one.
 *
 * The names are the ones a presentation program shows.
 */
enum class SlideTransition(val label: String) {
    /** The slide changes with nothing in between. */
    NONE("None"),

    /** The old slide fades into the new one. */
    FADE("Fade"),

    /** The new slide wipes across the old one. */
    WIPE("Wipe"),

    /** The new slide pushes the old one out of the way. */
    PUSH("Push"),

    /** The new slide covers the old one. */
    COVER("Cover"),

    /** The two slides mix into each other. */
    DISSOLVE("Dissolve"),

    /** The old slide disappears at once. */
    CUT("Cut"),

    /** The new slide opens out from the middle. */
    SPLIT("Split"),

    /** The new slide grows into place. */
    ZOOM("Zoom"),
}

fun SlideDeck.withLines(index: Int, lines: List<String>): SlideDeck {
    if (index !in slides.indices) return this
    val updated = slides.toMutableList()
    updated[index] = updated[index].copy(lines = lines)
    return copy(slides = updated)
}

/** Inserts an empty slide after [index], reusing a free part number. */
fun SlideDeck.withSlideAdded(index: Int = slides.lastIndex): SlideDeck {
    val at = (index + 1).coerceIn(0, slides.size)
    val number = (slides.maxOfOrNull { it.number } ?: 0) + 1
    val updated = slides.toMutableList().apply { add(at, Slide(number, emptyList())) }
    return copy(slides = updated)
}

/** Removes the slide at [index]; a deck always keeps at least one slide. */
fun SlideDeck.withSlideRemoved(index: Int): SlideDeck {
    if (slides.size <= 1 || index !in slides.indices) return this
    return copy(slides = slides.filterIndexed { i, _ -> i != index })
}

/** Moves the slide at [index] by [delta] positions (negative = earlier). */
fun SlideDeck.withSlideMoved(index: Int, delta: Int): SlideDeck {
    val target = index + delta
    if (index !in slides.indices || target !in slides.indices) return this
    val updated = slides.toMutableList()
    updated.add(target, updated.removeAt(index))
    return copy(slides = updated)
}

/** The deck with [transition] on the slide at [index]. */
fun SlideDeck.withTransition(index: Int, transition: SlideTransition): SlideDeck {
    if (index !in slides.indices) return this
    val updated = slides.toMutableList()
    updated[index] = updated[index].copy(transition = transition)
    return copy(slides = updated)
}

/** The deck with [image] on the slide at [index]. */
fun SlideDeck.withImage(index: Int, image: SlideImage): SlideDeck {
    if (index !in slides.indices) return this
    val updated = slides.toMutableList()
    updated[index] = updated[index].copy(images = updated[index].images + image)
    return copy(slides = updated)
}

/** The deck with [shape] on the slide at [index]. */
fun SlideDeck.withShape(index: Int, shape: SlideShape): SlideDeck {
    if (index !in slides.indices) return this
    val updated = slides.toMutableList()
    updated[index] = updated[index].copy(shapes = updated[index].shapes + shape)
    return copy(slides = updated)
}
