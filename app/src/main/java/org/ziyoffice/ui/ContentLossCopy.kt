package org.ziyoffice.ui

import org.ziyoffice.ooxml.DroppedFeature

/**
 * Copy for the "saving this file will drop content" warning.
 *
 * The writers rebuild each package from ZIyOffice's model, so anything the
 * readers skip is gone after a save. That is an acceptable limitation for an
 * honest subset implementation; doing it silently is not. This text is shared by
 * Docs and Sheets and is unit-tested so the wording cannot drift into vagueness.
 */
internal object ContentLossCopy {

    /** "Comments, Tracked changes and 2 more" — the first few, counted honestly. */
    fun summary(features: List<DroppedFeature>, limit: Int = 3): String {
        val named = features.take(limit).map { it.label }
        val rest = features.size - limit
        return when {
            named.isEmpty() -> ""
            rest > 0 -> named.joinToString(", ") + " and $rest more"
            named.size == 1 -> named.single()
            else -> named.dropLast(1).joinToString(", ") + " and " + named.last()
        }
    }

    /**
     * The full warning. [formatName] names the format, e.g. ".docx", so the user
     * understands the loss happens in the file, not just on screen.
     */
    fun body(features: List<DroppedFeature>, formatName: String): String = buildString {
        append("Saving as $formatName removes content ZIyOffice cannot write back:\n")
        // One item per line: a phone dialog that wraps a comma-separated list into
        // a paragraph is not read, and this list is the point of the warning.
        features.forEach { append("\n\u2022  ").append(it.label) }
        append("\n\nThis copy keeps only what ZIyOffice can edit. To keep the original ")
        append("intact, use \u201CSave as copy\u201D instead, or close without saving.")
    }

    /**
     * One short line for the banner shown as soon as the file opens. It states the
     * consequence and the count, not the whole list: on a 360 dp phone a full
     * enumeration ran to several lines of unreadable text and buried the one fact
     * the user needs. The list itself is one tap away in the dialog.
     */
    fun banner(features: List<DroppedFeature>): String {
        val count = features.size
        return if (count == 1) {
            "Saving here will remove 1 unsupported item"
        } else {
            "Saving here will remove $count kinds of unsupported content"
        }
    }

    /**
     * Whether a write must stop and ask first.
     *
     * [accepted] is per editor session: once the user has seen the loss and
     * chosen to save, re-asking on every later save would train them to dismiss
     * it. [writesWholeFormat] is false for CSV, where only the active sheet's
     * cell values are written and the .xlsx package is not the destination —
     * there is nothing in that file for the writer to drop.
     */
    fun requiresConfirmation(
        dropped: List<DroppedFeature>,
        accepted: Boolean,
        writesWholeFormat: Boolean,
    ): Boolean = writesWholeFormat && !accepted && dropped.isNotEmpty()
}
