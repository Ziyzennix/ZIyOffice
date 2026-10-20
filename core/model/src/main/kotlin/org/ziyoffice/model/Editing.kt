package org.ziyoffice.model

fun List<Run>.textLength(): Int =
    sumOf { it.text.length }

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

private fun Run.hasSameFormattingAs(other: Run): Boolean =
    bold == other.bold &&
        italic == other.italic &&
        underline == other.underline &&
        strike == other.strike &&
        sizePt == other.sizePt &&
        colorRgb == other.colorRgb

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
