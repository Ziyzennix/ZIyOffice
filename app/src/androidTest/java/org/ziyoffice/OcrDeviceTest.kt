package org.ziyoffice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ziyoffice.ui.PictureText

@RunWith(AndroidJUnit4::class)
class OcrDeviceTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeOcrEngineLoadsAndProcessesBitmapOnDevice() {
        val bitmap = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 80f
                isAntiAlias = true
            }
            canvas.drawText("HELLO ZIYOFFICE", 100f, 200f, paint)
        }

        val extracted = runBlocking {
            PictureText.read(context, bitmap)
        }

        bitmap.recycle()
        assertNotNull(extracted)
    }
}
