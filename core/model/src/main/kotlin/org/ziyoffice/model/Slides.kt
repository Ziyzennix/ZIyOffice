package org.ziyoffice.model

/**
 * A presentation: its slides in presentation order.
 *
 * The model is deliberately flat - text lines per slide - because that is what
 * can be edited without a full DrawingML renderer. The writer patches the text
 * runs in the original package, so themes, layouts, images, notes and animation
 * survive an edit untouched.
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
        /** 13.333 in — the PowerPoint widescreen default. */
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
 * One slide: its text lines in reading order, plus the index of its part in the
 * package (`ppt/slides/slide<number>.xml`).
 *
 * [number] is the *part* index, not the position in the deck: reordering slides
 * must not rename parts, and the writer needs the part index to patch the right
 * file.
 */
data class Slide(
    val number: Int,
    val lines: List<String>,
    /** Speaker notes, read from the notes part; empty when the slide has none. */
    val notes: String = "",
) {
    val title: String get() = lines.firstOrNull().orEmpty()
    val body: List<String> get() = lines.drop(1)
}

/** Replaces the text lines of the slide at [index]. */
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
