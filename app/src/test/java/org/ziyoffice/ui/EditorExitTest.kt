package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the two data-loss transitions the UX review flagged:
 * leaving an editor before unsaved work is resolved, and clearing the dirty flag
 * for a write that finished after newer edits landed.
 */
class EditorExitTest {

    @Test
    fun cleanEditorLeavesImmediately() {
        assertEquals(
            ExitAction.LEAVE,
            exitAction(dirty = false, saving = false, needsConfirmation = true),
        )
        assertEquals(
            ExitAction.LEAVE,
            exitAction(dirty = false, saving = false, needsConfirmation = false),
        )
    }

    @Test
    fun dirtyDocsAndSlidesAskInsteadOfLeaving() {
        // The old behaviour left the screen and started a save in the background,
        // so a failed write lost the work with no way back.
        assertEquals(
            ExitAction.ASK,
            exitAction(dirty = true, saving = false, needsConfirmation = true),
        )
    }

    @Test
    fun dirtySheetsSaveThenLeave() {
        assertEquals(
            ExitAction.SAVE_AND_LEAVE,
            exitAction(dirty = true, saving = false, needsConfirmation = false),
        )
    }

    @Test
    fun aWriteInFlightBlocksExit() {
        // Two overlapping writes could land out of order; Back must not start one.
        assertEquals(
            ExitAction.BLOCK,
            exitAction(dirty = true, saving = true, needsConfirmation = true),
        )
        assertEquals(
            ExitAction.BLOCK,
            exitAction(dirty = true, saving = true, needsConfirmation = false),
        )
        // A clean document that is mid-write is still mid-write.
        assertEquals(
            ExitAction.BLOCK,
            exitAction(dirty = false, saving = true, needsConfirmation = false),
        )
    }

    @Test
    fun aWriteWithNoNewerEditsClearsDirty() {
        assertTrue(writeSettlesDirty(writingVersion = 4, editedVersion = 4))
    }

    @Test
    fun aWriteFollowedByNewerEditsKeepsThemDirty() {
        assertFalse(writeSettlesDirty(writingVersion = 4, editedVersion = 5))
        assertFalse(writeSettlesDirty(writingVersion = 0, editedVersion = 1))
    }
}
