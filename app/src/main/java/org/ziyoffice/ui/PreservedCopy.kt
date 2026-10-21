package org.ziyoffice.ui

import org.ziyoffice.ooxml.PreservedFeature

/**
 * Copy for the notice about content ZiyOffice keeps but cannot edit.
 *
 * Saving writes the edited parts back into the original package, so nothing
 * the app reads is thrown away. What the notice has to say is therefore
 * narrower and more useful: which parts of the document are out of reach, so
 * the user does not look for an editing option that is not there.
 *
 * Shared by Docs and Sheets and unit-tested, so the wording cannot drift into
 * vagueness or start claiming damage that does not happen.
 */
internal object PreservedCopy {

    /** "Comments, Footnotes and 2 more" — the first few, counted honestly. */
    fun summary(features: List<PreservedFeature>, limit: Int = 3): String {
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
     * The full list. [formatName] names the format, e.g. ".docx", so the user
     * understands the notice is about the file, not just the screen.
     */
    fun body(features: List<PreservedFeature>, formatName: String): String = buildString {
        append("This $formatName also contains content ZiyOffice keeps but cannot edit:\n")
        // One item per line: a phone dialog that wraps a comma-separated list
        // into a paragraph is not read, and this list is the point.
        features.forEach { append("\n\u2022  ").append(it.label) }
        append("\n\nSaving keeps all of it as it is. Only the text and formatting you ")
        append("change are written back.")
    }

    /**
     * One short line for the banner shown as soon as the file opens. It states
     * what the situation is and the count, not the whole list: on a 360 dp
     * phone a full enumeration ran to several lines of unreadable text and
     * buried the one fact the user needs. The list itself is one tap away.
     */
    fun banner(features: List<PreservedFeature>): String {
        val count = features.size
        return if (count == 1) {
            "1 kind of content is kept as it is, but cannot be edited here"
        } else {
            "$count kinds of content are kept as they are, but cannot be edited here"
        }
    }
}
