package org.ziyoffice.model

fun List<Run>.textLength(): Int =
    sumOf { it.text.length }

/**
 * Replaces the characters in [start, end) with [replacement].
 *
 * Text outside the range keeps its own formatting. The replacement takes the
 * formatting of the run the range starts in, which is what an editor does when
 * you type over a selection.
 */
fun Paragraph.replaceRange(start: Int, end: Int, replacement: String): Paragraph {
    val total = runs.textLength()
    if (total == 0) {
        return if (replacement.isEmpty()) this else copy(runs = listOf(Run(replacement)))
    }
    val from = start.coerceIn(0, total)
    val to = end.coerceIn(from, total)
    if (from == to && replacement.isEmpty()) return this

    val head = mutableListOf<Run>()
    val tail = mutableListOf<Run>()
    var before: Run? = null
    var after: Run? = null
    var overlap: Run? = null
    var offset = 0

    for (run in runs) {
        if (run.text.isEmpty()) continue
        val runStart = offset
        val runEnd = offset + run.text.length
        offset = runEnd
        when {
            runEnd <= from -> head += run
            runStart >= to -> tail += run
            else -> {
                if (overlap == null) overlap = run
                val keepBefore = from - runStart
                if (keepBefore > 0) before = run.copy(text = run.text.substring(0, keepBefore))
                val keepAfter = runEnd - to
                if (keepAfter > 0) {
                    after = run.copy(text = run.text.substring(run.text.length - keepAfter))
                }
            }
        }
    }

    val template = overlap ?: head.lastOrNull() ?: runs.firstOrNull() ?: Run("")
    val result = mutableListOf<Run>()
    result += head
    if (before != null) result += before
    if (replacement.isNotEmpty()) result += template.copy(text = replacement)
    if (after != null) result += after
    result += tail
    return copy(runs = mergeRuns(result))
}

/**
 * Replaces the paragraph's text with [text], leaving the formatting of
 * everything the change did not touch alone.
 *
 * The edit is worked out by comparing the old text with the new one: whatever
 * they share at the start and at the end stays, and only the middle is
 * replaced. Typing at the end of a bold word therefore leaves the word bold
 * instead of flattening the paragraph onto its first run.
 */
fun Paragraph.applyText(text: String): Paragraph {
    val old = runs.joinToString("") { it.text }
    if (old == text) return this
    val limit = minOf(old.length, text.length)
    var prefix = 0
    while (prefix < limit && old[prefix] == text[prefix]) prefix++
    var suffix = 0
    while (suffix < limit - prefix &&
        old[old.length - 1 - suffix] == text[text.length - 1 - suffix]
    ) {
        suffix++
    }
    return replaceRange(prefix, old.length - suffix, text.substring(prefix, text.length - suffix))
}

/** Joins runs that ended up with the same formatting. */
private fun mergeRuns(runs: List<Run>): List<Run> {
    val result = mutableListOf<Run>()
    for (run in runs) {
        if (run.text.isEmpty()) continue
        val last = result.lastOrNull()
        if (last != null && last.hasSameFormattingAs(run)) {
            result[result.lastIndex] = last.copy(text = last.text + run.text)
        } else {
            result += run
        }
    }
    return result
}

fun List<Run>.formatRange(range: IntRange, transform: (Run) -> Run): List<Run> {
    val start = range.first
    val end = range.last
    if (start >= end || isEmpty()) return this

    val result = mutableListOf<Run>()
    var offset = 0
    for (run in this) {
        val runStart = offset
        val runEnd = offset + run.text.length
        offset = runEnd

        if (run.text.isEmpty()) continue

        if (runEnd <= start || runStart >= end) {
            result.add(run)
            continue
        }

        if (runStart >= start && runEnd <= end) {
            val transformed = transform(run)
            if (transformed.text.isNotEmpty()) {
                result.add(transformed)
            }
            continue
        }

        val overlapStart = maxOf(runStart, start)
        val overlapEnd = minOf(runEnd, end)

        if (overlapStart > runStart) {
            val prefix = run.text.substring(0, overlapStart - runStart)
            if (prefix.isNotEmpty()) {
                result.add(run.copy(text = prefix))
            }
        }
        if (overlapEnd > overlapStart) {
            val overlap = run.text.substring(overlapStart - runStart, overlapEnd - runStart)
            if (overlap.isNotEmpty()) {
                val transformed = transform(run.copy(text = overlap))
                if (transformed.text.isNotEmpty()) {
                    result.add(transformed)
                }
            }
        }
        if (runEnd > overlapEnd) {
            val suffix = run.text.substring(overlapEnd - runStart)
            if (suffix.isNotEmpty()) {
                result.add(run.copy(text = suffix))
            }
        }
    }
    return result
}

fun List<Run>.replaceAll(
    query: String,
    replacement: String,
    ignoreCase: Boolean = true,
): Pair<List<Run>, Int> {
    if (query.isEmpty() || isEmpty()) return this to 0

    val fullTextBuilder = StringBuilder()
    val charRuns = mutableListOf<Run>()
    for (run in this) {
        fullTextBuilder.append(run.text)
        for (i in run.text.indices) {
            charRuns.add(run)
        }
    }
    val fullText = fullTextBuilder.toString()
    if (fullText.isEmpty()) return this to 0

    val matchStarts = mutableListOf<Int>()
    var searchIndex = 0
    while (searchIndex <= fullText.length - query.length) {
        val idx = fullText.indexOf(query, searchIndex, ignoreCase = ignoreCase)
        if (idx < 0) break
        matchStarts.add(idx)
        searchIndex = idx + query.length
    }
    if (matchStarts.isEmpty()) return this to 0

    val newChars = mutableListOf<Char>()
    val newCharRuns = mutableListOf<Run>()

    var origIdx = 0
    for (matchStart in matchStarts) {
        while (origIdx < matchStart) {
            newChars.add(fullText[origIdx])
            newCharRuns.add(charRuns[origIdx])
            origIdx++
        }
        val matchStartRun = charRuns[matchStart]
        for (ch in replacement) {
            newChars.add(ch)
            newCharRuns.add(matchStartRun)
        }
        origIdx += query.length
    }
    while (origIdx < fullText.length) {
        newChars.add(fullText[origIdx])
        newCharRuns.add(charRuns[origIdx])
        origIdx++
    }

    if (newChars.isEmpty()) return emptyList<Run>() to matchStarts.size

    val resultRuns = mutableListOf<Run>()
    var currentRunTemplate = newCharRuns[0]
    val currentText = StringBuilder().append(newChars[0])

    for (i in 1 until newChars.size) {
        val runTemplate = newCharRuns[i]
        if (runTemplate.hasSameFormattingAs(currentRunTemplate)) {
            currentText.append(newChars[i])
        } else {
            resultRuns.add(currentRunTemplate.copy(text = currentText.toString()))
            currentRunTemplate = runTemplate
            currentText.clear().append(newChars[i])
        }
    }
    resultRuns.add(currentRunTemplate.copy(text = currentText.toString()))

    return resultRuns to matchStarts.size
}

data class TextMatch(val path: FocusPath, val start: Int, val end: Int)

fun Document.findMatches(query: String, ignoreCase: Boolean = true): List<TextMatch> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<TextMatch>()
    for (path in allFocusPaths()) {
        val p = paragraphAt(path) ?: continue
        val text = p.runs.joinToString("") { it.text }
        if (text.isEmpty()) continue
        var searchIndex = 0
        while (searchIndex <= text.length - query.length) {
            val idx = text.indexOf(query, searchIndex, ignoreCase = ignoreCase)
            if (idx < 0) break
            matches.add(TextMatch(path, idx, idx + query.length))
            searchIndex = idx + query.length
        }
    }
    return matches
}

fun Document.replaceAll(
    query: String,
    replacement: String,
    ignoreCase: Boolean = true,
): Pair<Document, Int> {
    if (query.isEmpty()) return this to 0
    var totalMatches = 0
    var currentDoc = this
    for (path in allFocusPaths()) {
        val p = currentDoc.paragraphAt(path) ?: continue
        val (newRuns, count) = p.runs.replaceAll(query, replacement, ignoreCase)
        if (count > 0) {
            totalMatches += count
            currentDoc = currentDoc.mapParagraphAt(path) { it.copy(runs = newRuns) }
        }
    }
    return currentDoc to totalMatches
}
