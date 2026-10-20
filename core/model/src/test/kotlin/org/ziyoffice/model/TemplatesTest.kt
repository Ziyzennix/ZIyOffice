package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplatesTest {
    @Test
    fun everyTemplateHasTextAndUniqueId() {
        assertTrue(DOCUMENT_TEMPLATES.size >= 4)
        assertEquals(DOCUMENT_TEMPLATES.size, DOCUMENT_TEMPLATES.map { it.id }.toSet().size)
        DOCUMENT_TEMPLATES.filterNot { it.id == "blank" }.forEach { template ->
            assertTrue(
                template.document.blocks.any { block ->
                    block is Paragraph && block.runs.any { it.text.isNotBlank() }
                },
                "${template.id} has no text",
            )
        }
    }

    @Test
    fun unknownIdFallsBackToBlank() {
        assertEquals("blank", documentTemplate("does-not-exist").id)
        assertEquals("letter", documentTemplate("letter").id)
    }
}
