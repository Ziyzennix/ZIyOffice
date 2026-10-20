package org.ziyoffice.ui

/**
 * What Back should do in an editor. Extracted from the three editor screens so
 * the decision can be unit-tested instead of only inspected by hand: losing a
 * user's edits is the one bug class this app must not have.
 */
internal enum class ExitAction {
    /** Nothing unsaved: leave immediately. */
    LEAVE,

    /** Unsaved work and no write in flight: write, then leave only on success. */
    SAVE_AND_LEAVE,

    /** Unsaved work that needs an explicit Save / Discard / Stay answer. */
    ASK,

    /** A write is already running; exiting now would race it. */
    BLOCK,
}

/**
 * The single Back decision shared by Docs, Sheets and Slides.
 *
 * [needsConfirmation] is true for editors that ask rather than auto-save (Docs
 * and Slides); Sheets saves and leaves, because a spreadsheet has no unsaved
 * formatting that a silent save could misrepresent.
 */
internal fun exitAction(
    dirty: Boolean,
    saving: Boolean,
    needsConfirmation: Boolean,
): ExitAction = when {
    saving -> ExitAction.BLOCK
    !dirty -> ExitAction.LEAVE
    needsConfirmation -> ExitAction.ASK
    else -> ExitAction.SAVE_AND_LEAVE
}

/**
 * Whether a finished write may clear the dirty flag. If the document was edited
 * again while the bytes were being written, [editedVersion] has moved past
 * [writingVersion] and the newer work must stay dirty — otherwise the next
 * keystroke would be silently unsaved.
 */
internal fun writeSettlesDirty(writingVersion: Long, editedVersion: Long): Boolean =
    writingVersion == editedVersion
