package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.ziyoffice.model.Document
import org.ziyoffice.model.FocusPath
import org.ziyoffice.model.Paragraph
import org.ziyoffice.model.Run

class EditorViewModelTest {

    private fun testViewModelWithDocument(): EditorViewModel {
        val vm = EditorViewModel()
        val doc = Document(
            blocks = listOf(
                Paragraph(runs = listOf(Run("First"))),
                Paragraph(runs = listOf(Run("Second"))),
            ),
        )
        vm.setDocumentForTesting(doc)
        return vm
    }

    @Test
    fun updateDocumentMarksDirtyAndAllowsUndo() {
        val vm = testViewModelWithDocument()
        val ready = vm.uiState.value.preview
        assertIs<DocxPreview.Ready>(ready)
        assertEquals("First", (ready.document.blocks[0] as Paragraph).runs[0].text)
        assertFalse(vm.uiState.value.canUndo)

        vm.updateDocument { doc ->
            doc.copy(blocks = listOf(Paragraph(runs = listOf(Run("Updated")))))
        }

        val updated = vm.uiState.value.preview
        assertIs<DocxPreview.Ready>(updated)
        assertEquals("Updated", (updated.document.blocks[0] as Paragraph).runs[0].text)
        assertTrue(vm.uiState.value.dirty)
        assertTrue(vm.uiState.value.canUndo)

        vm.undo()
        val undone = vm.uiState.value.preview
        assertIs<DocxPreview.Ready>(undone)
        assertEquals("First", (undone.document.blocks[0] as Paragraph).runs[0].text)
        assertTrue(vm.uiState.value.canRedo)

        vm.redo()
        val redone = vm.uiState.value.preview
        assertIs<DocxPreview.Ready>(redone)
        assertEquals("Updated", (redone.document.blocks[0] as Paragraph).runs[0].text)
    }

    @Test
    fun updateTargetParagraphUpdatesSpecificBlock() {
        val vm = testViewModelWithDocument()
        vm.setFocusedTarget(FocusPath(1))
        vm.updateTargetParagraph {
            it.copy(runs = listOf(Run("Modified Second")))
        }

        val ready = vm.uiState.value.preview
        assertIs<DocxPreview.Ready>(ready)
        assertEquals("First", (ready.document.blocks[0] as Paragraph).runs[0].text)
        assertEquals("Modified Second", (ready.document.blocks[1] as Paragraph).runs[0].text)
    }
}
