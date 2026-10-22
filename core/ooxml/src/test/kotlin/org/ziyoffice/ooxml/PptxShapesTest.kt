package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.ziyoffice.model.Slide
import org.ziyoffice.model.SlideDeck
import org.ziyoffice.model.SlideImage
import org.ziyoffice.model.SlideShape
import org.ziyoffice.model.SlideShapeKind

/**
 * The pictures and the shapes on a slide.
 */
class PptxShapesTest {

    /** A one-pixel PNG, so the picture has real bytes. */
    private fun pictureBytes(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    )

    private fun deckWith(
        images: List<SlideImage> = emptyList(),
        shapes: List<SlideShape> = emptyList(),
    ): SlideDeck = SlideDeck(
        slides = listOf(
            Slide(
                number = 1,
                lines = listOf("A slide", "with a picture"),
                images = images,
                shapes = shapes,
            ),
        ),
    )

    private fun partOf(bytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().decodeToString()
            }
        }
        return null
    }

    @Test
    fun aPictureSurvivesASave() {
        val original = PptxWriter().create(
            deckWith(
                images = listOf(
                    SlideImage("ppt/media/image1.png", pictureBytes(), 100, 200, 300, 400),
                ),
            ),
        )
        val read = PptxReader().read(ByteArrayInputStream(original))
        val image = read.slides.first().images.single()
        assertEquals(100, image.xEmu)
        assertEquals(200, image.yEmu)
        assertEquals(300, image.widthEmu)
        assertEquals(400, image.heightEmu)
        assertTrue(image.bytes.contentEquals(pictureBytes()), "the bytes should come back")
    }

    @Test
    fun thePictureIsInAPartOfItsOwn() {
        val original = PptxWriter().create(
            deckWith(
                images = listOf(
                    SlideImage("ppt/media/image1.png", pictureBytes(), 0, 0, 100, 100),
                ),
            ),
        )
        val read = PptxReader().read(ByteArrayInputStream(original))
        assertEquals("ppt/media/image1_1.png", read.slides.first().images.single().partName)
    }

    @Test
    fun aShapeSurvivesASave() {
        val original = PptxWriter().create(
            deckWith(
                shapes = listOf(
                    SlideShape(
                        kind = SlideShapeKind.ELLIPSE,
                        xEmu = 10,
                        yEmu = 20,
                        widthEmu = 30,
                        heightEmu = 40,
                        fillRgb = 0xFF0000FF.toInt(),
                    ),
                ),
            ),
        )
        val read = PptxReader().read(ByteArrayInputStream(original))
        val shape = read.slides.first().shapes.single()
        assertEquals(SlideShapeKind.ELLIPSE, shape.kind)
        assertEquals(10, shape.xEmu)
        assertEquals(40, shape.heightEmu)
        assertEquals(0xFF0000FF.toInt(), shape.fillRgb)
    }

    @Test
    fun theShapeIsInTheSlide() {
        val original = PptxWriter().create(
            deckWith(shapes = listOf(SlideShape(SlideShapeKind.RECTANGLE, 0, 0, 100, 100))),
        )
        val slide = assertNotNull(partOf(original, "ppt/slides/slide1.xml"))
        assertTrue(slide.contains("<p:sp>"), "the slide should hold the shape")
        assertTrue(slide.contains("prst=\"rect\""))
    }

    @Test
    fun theTextOfTheSlideIsStillThere() {
        val original = PptxWriter().create(
            deckWith(
                images = listOf(SlideImage("", pictureBytes(), 0, 0, 10, 10)),
                shapes = listOf(SlideShape(SlideShapeKind.RECTANGLE, 0, 0, 10, 10)),
            ),
        )
        val read = PptxReader().read(ByteArrayInputStream(original))
        assertEquals(listOf("A slide", "with a picture"), read.slides.first().lines)
    }

    @Test
    fun aSlideWithNoPicturesHasNone() {
        val original = PptxWriter().create(deckWith())
        val read = PptxReader().read(ByteArrayInputStream(original))
        assertTrue(read.slides.first().images.isEmpty())
        assertTrue(read.slides.first().shapes.isEmpty())
    }

    @Test
    fun aPictureAddedToADeckIsWrittenIntoIt() {
        val original = PptxWriter().create(deckWith())
        val deck = PptxReader().read(ByteArrayInputStream(original))
        val withPicture = deck.copy(
            slides = listOf(
                deck.slides.first().copy(
                    images = listOf(SlideImage("", pictureBytes(), 5, 6, 7, 8)),
                ),
            ),
        )
        val edited = PptxWriter().edit(original, withPicture)
        val read = PptxReader().read(ByteArrayInputStream(edited))
        val image = read.slides.first().images.single()
        assertEquals(5, image.xEmu)
        assertEquals(8, image.heightEmu)
    }
}
