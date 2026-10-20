package org.ziyoffice.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CellFillContrastTest {

    @Test
    fun `light fills are the ones that need dark text`() {
        assertTrue(isLightColour(0xF2CC))       // the pale yellow of the palette
        assertTrue(isLightColour(0xFFFFFF))
        assertTrue(isLightColour(0xE8F0FE))
    }

    @Test
    fun `dark fills keep the light theme text`() {
        assertFalse(isLightColour(0x000000))
        assertFalse(isLightColour(0x0E5A4E))    // the deep teal of the brand
        assertFalse(isLightColour(0x1B1B1B))
    }
}
