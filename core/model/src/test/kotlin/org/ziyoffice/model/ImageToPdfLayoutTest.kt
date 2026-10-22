package org.ziyoffice.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageToPdfLayoutTest {

    @Test
    fun autoOrientationSelectsPortraitForTallImage() {
        val placement = ImageToPdfLayout.computePlacement(
            imageWidthPx = 1000,
            imageHeightPx = 2000,
            config = ImageToPdfConfig(pageSize = PdfPageSize.A4, orientation = PdfPageOrientation.AUTO, marginPt = 36f),
        )
        assertEquals(595f, placement.pageWidthPt)
        assertEquals(842f, placement.pageHeightPt)
        assertTrue(placement.imageWidthPt > 0f)
        assertTrue(placement.imageHeightPt > 0f)
        assertTrue(placement.imageHeightPt <= 842f - 72f)
        // Aspect ratio preserved: height / width = 2
        assertEquals(2.0, (placement.imageHeightPt / placement.imageWidthPt).toDouble(), 0.001)
    }

    @Test
    fun autoOrientationSelectsLandscapeForWideImage() {
        val placement = ImageToPdfLayout.computePlacement(
            imageWidthPx = 2000,
            imageHeightPx = 1000,
            config = ImageToPdfConfig(pageSize = PdfPageSize.A4, orientation = PdfPageOrientation.AUTO, marginPt = 36f),
        )
        assertEquals(842f, placement.pageWidthPt)
        assertEquals(595f, placement.pageHeightPt)
        assertEquals(2.0, (placement.imageWidthPt / placement.imageHeightPt).toDouble(), 0.001)
    }

    @Test
    fun forcedOrientationHonoursSetting() {
        val placement = ImageToPdfLayout.computePlacement(
            imageWidthPx = 2000,
            imageHeightPx = 1000,
            config = ImageToPdfConfig(pageSize = PdfPageSize.A4, orientation = PdfPageOrientation.PORTRAIT, marginPt = 20f),
        )
        assertEquals(595f, placement.pageWidthPt)
        assertEquals(842f, placement.pageHeightPt)
        assertTrue(placement.imageWidthPt <= 595f - 40f)
    }

    @Test
    fun fitImageMatchesExactPixelDimensions() {
        val placement = ImageToPdfLayout.computePlacement(
            imageWidthPx = 800,
            imageHeightPx = 600,
            config = ImageToPdfConfig(pageSize = PdfPageSize.FIT_IMAGE),
        )
        assertEquals(800f, placement.pageWidthPt)
        assertEquals(600f, placement.pageHeightPt)
        assertEquals(0f, placement.imageLeftPt)
        assertEquals(0f, placement.imageTopPt)
        assertEquals(800f, placement.imageWidthPt)
        assertEquals(600f, placement.imageHeightPt)
    }

    @Test
    fun zeroMarginProvidesFullBleed() {
        val placement = ImageToPdfLayout.computePlacement(
            imageWidthPx = 595,
            imageHeightPx = 842,
            config = ImageToPdfConfig(pageSize = PdfPageSize.A4, orientation = PdfPageOrientation.PORTRAIT, marginPt = 0f),
        )
        assertEquals(595f, placement.pageWidthPt)
        assertEquals(842f, placement.pageHeightPt)
        assertEquals(0f, placement.imageLeftPt, 0.001f)
        assertEquals(0f, placement.imageTopPt, 0.001f)
        assertEquals(595f, placement.imageWidthPt, 0.001f)
        assertEquals(842f, placement.imageHeightPt, 0.001f)
    }

    @Test
    fun zeroOrNegativeImageDimensionsProduceFallback() {
        val placement = ImageToPdfLayout.computePlacement(
            imageWidthPx = 0,
            imageHeightPx = -5,
            config = ImageToPdfConfig(pageSize = PdfPageSize.A4),
        )
        assertEquals(595f, placement.pageWidthPt)
        assertEquals(842f, placement.pageHeightPt)
        assertEquals(0f, placement.imageWidthPt)
        assertEquals(0f, placement.imageHeightPt)
    }
}
