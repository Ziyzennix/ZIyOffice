package org.ziyoffice.ooxml

import org.ziyoffice.model.Run

/**
 * Lines up the runs the user ended up with and the runs the file had.
 *
 * A paragraph gets written back by patching the runs it started with, but the
 * model may have split one of those in two or put text at either end. So the
 * two sides get matched by character position: the start goes together, the
 * end goes together, and whatever is left in the middle is what changed.
 */
internal class RunAlignment(
    /** What each source run turns into. The index matches the source run's. */
    val pieces: List<List<Piece>>,
    /** Text that belongs after every source run. */
    val trailing: List<Piece>,
) {
    companion object {
        /** Matches the runs the file had against the runs the user made. */
        fun of(original: List<Run>, updated: List<Run>): RunAlignment {
            val old = Runs(original)
            val new = Runs(updated)
            val oldText = old.text()
            val newText = new.text()

            var prefix = 0
            val limit = minOf(oldText.length, newText.length)
            while (prefix < limit && oldText[prefix] == newText[prefix]) prefix++
            var suffix = 0
            while (suffix < limit - prefix &&
                oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
            ) {
                suffix++
            }
            val oldChangedEnd = oldText.length - suffix
            val newChangedEnd = newText.length - suffix

            val buckets = original.indices.map { mutableListOf<Piece>() }.toMutableList()
            val trailing = mutableListOf<Piece>()

            // The characters that did not move, at the start.
            alignSame(old, new, oldStart = 0, newStart = 0, length = prefix, buckets = buckets)

            // The characters the user changed.
            val changed = mutableListOf<Piece>()
            new.forEachRun { index, run, runStart, runEnd ->
                if (runEnd <= prefix || runStart >= newChangedEnd) return@forEachRun
                val from = maxOf(runStart, prefix)
                val to = minOf(runEnd, newChangedEnd)
                changed += Piece(run.text.substring(from - runStart, to - runStart), run, from)
            }
            if (changed.isNotEmpty()) {
                val target = old.runAt(prefix)
                if (target == null) trailing += changed else buckets[target] += changed
            }

            // The characters that did not move, at the end.
            alignSame(
                old,
                new,
                oldStart = oldChangedEnd,
                newStart = newChangedEnd,
                length = suffix,
                buckets = buckets,
            )

            buckets.forEach { bucket -> bucket.sortBy { it.offset } }
            trailing.sortBy { it.offset }
            return RunAlignment(buckets, trailing)
        }

        /**
         * Lines up two stretches of text that hold the same characters, cutting
         * runs wherever the two sides disagree about where a run ends.
         */
        private fun alignSame(
            old: Runs,
            new: Runs,
            oldStart: Int,
            newStart: Int,
            length: Int,
            buckets: MutableList<MutableList<Piece>>,
        ) {
            if (length <= 0) return
            var oldOffset = oldStart
            var newOffset = newStart
            var remaining = length
            var oldIndex = 0
            var newIndex = 0
            while (remaining > 0) {
                while (oldIndex < old.size && oldOffset >= old.end(oldIndex)) oldIndex++
                while (newIndex < new.size && newOffset >= new.end(newIndex)) newIndex++
                if (oldIndex >= old.size || newIndex >= new.size) return
                val chunk = minOf(old.end(oldIndex) - oldOffset, new.end(newIndex) - newOffset, remaining)
                val from = oldOffset - old.start(oldIndex)
                buckets[oldIndex] += Piece(
                    old.runs[oldIndex].text.substring(from, from + chunk),
                    new.runs[newIndex],
                    newOffset,
                )
                oldOffset += chunk
                newOffset += chunk
                remaining -= chunk
            }
        }
    }
}

/** One piece of a run: some text and the formatting it is written with. */
internal class Piece(
    val text: String,
    /** The model run whose formatting this piece takes. */
    val model: Run,
    /** Where the piece sits in the paragraph's new text. */
    val offset: Int,
)

/** A run list with the offsets of its runs worked out. */
private class Runs(val runs: List<Run>) {
    private val starts = IntArray(runs.size + 1)

    init {
        var offset = 0
        runs.forEachIndexed { index, run ->
            starts[index] = offset
            offset += run.text.length
        }
        starts[runs.size] = offset
    }

    val size: Int get() = runs.size

    fun start(index: Int): Int = starts[index]

    fun end(index: Int): Int = starts[index + 1]

    fun text(): String = runs.joinToString("") { it.text }

    /** The run holding [offset]; the last run when the offset is at the end. */
    fun runAt(offset: Int): Int? {
        if (runs.isEmpty()) return null
        var index = 0
        while (index < runs.size && end(index) <= offset) index++
        return if (index >= runs.size) runs.lastIndex else index
    }

    inline fun forEachRun(action: (index: Int, run: Run, start: Int, end: Int) -> Unit) {
        runs.forEachIndexed { index, run ->
            action(index, run, start(index), end(index))
        }
    }
}
