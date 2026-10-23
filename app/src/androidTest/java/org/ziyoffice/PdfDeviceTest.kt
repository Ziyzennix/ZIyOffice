package org.ziyoffice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ziyoffice.export.ImageToPdfExporter
import org.ziyoffice.pdf.PdfDocument

@RunWith(AndroidJUnit4::class)
class PdfDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun scratchFile(name: String): File =
        File(context.filesDir, name).also { it.delete() }

    @Test
    fun imageToPdfCompilationAndRenderingOnDevice() {
        val pdfFile = scratchFile("compiled-images.pdf")
        val cacheFile = scratchFile("cache-renderer.pdf")

        val b1 = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.BLUE }
            canvas.drawRect(50f, 50f, 200f, 200f, paint)
        }
        val b2 = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply { color = Color.RED }
            canvas.drawRect(50f, 50f, 200f, 200f, paint)
        }

        pdfFile.outputStream().use { out ->
            ImageToPdfExporter.export(listOf(b1, b2), out)
        }
        b1.recycle()
        b2.recycle()

        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)

        // Verify PDF magic bytes: %PDF-
        val magic = pdfFile.readBytes().take(5).toByteArray().toString(Charsets.ISO_8859_1)
        assertTrue("File should start with %PDF-, got $magic", magic.startsWith("%PDF-"))

        // Render with platform PdfDocument
        val opened = PdfDocument.open(pdfFile.inputStream(), cacheFile)
        assertNotNull(opened)
        assertEquals(2, opened!!.pageCount)

        val rendered = opened.renderPage(0, 400)
        assertNotNull(rendered)
        assertEquals(400, rendered!!.width)
        assertTrue(rendered.height > 0)
        rendered.recycle()
        opened.close()
    }
}
