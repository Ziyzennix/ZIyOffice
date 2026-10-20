package org.ziyoffice.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * One user mark on a PDF page. Coordinates are a fraction of the page (0..1), so
 * a mark made while zoomed out still lands in the right place at print
 * resolution.
 *
 * [doc] says which opened document the mark belongs to, so marks stay on their
 * page when pages are reordered or a second PDF is merged in.
 */
sealed interface PdfMark {
    val doc: Int
    val page: Int

    /** Translucent highlight over a rectangle. */
    data class Highlight(
        override val doc: Int,
        override val page: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : PdfMark

    /** Freehand ink from a drag gesture; [points] are x,y pairs. */
    data class Ink(
        override val doc: Int,
        override val page: Int,
        val points: List<Pair<Float, Float>>,
        val isSignature: Boolean = false,
    ) : PdfMark

    /** A note anchored at a point. Notes are listed, not drawn as text on export. */
    data class Note(
        override val doc: Int,
        override val page: Int,
        val x: Float,
        val y: Float,
        val text: String,
    ) : PdfMark

    /** Text stamped onto the page at a point, like a label or a signature block. */
    data class Text(
        override val doc: Int,
        override val page: Int,
        val x: Float,
        val y: Float,
        val text: String,
    ) : PdfMark

    /** An opaque black box; on export it paints over and destroys what is underneath. */
    data class Redaction(
        override val doc: Int,
        override val page: Int,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : PdfMark
}

/** Which mark the next gesture produces. */
enum class MarkMode(val label: String) {
    HIGHLIGHT("Highlight"),
    INK("Ink"),
    NOTE("Note"),
    TEXT("Text"),
    REDACTION("Redact"),
    SIGNATURE("Sign"),
}

/**
 * Interactive layer over a rendered page. Draws every mark for the page and turns
 * drags and taps into new ones. The same surface handles highlighting, inking,
 * notes, signatures and redaction because they differ only in how they are
 * painted, not in how they are captured.
 */
@Composable
fun MarkLayer(
    doc: Int,
    page: Int,
    marks: List<PdfMark>,
    mode: MarkMode,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onAdd: (PdfMark) -> Unit,
    onNoteAt: (Float, Float) -> Unit,
    onTextAt: (Float, Float) -> Unit,
) {
    var drag = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    val inkColor = if (mode == MarkMode.SIGNATURE) ComposeColor(0xFF1A1C1B) else ComposeColor(0xFF1A73E8)
    val strokeWidth = if (mode == MarkMode.SIGNATURE) 3f else 2f

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier.pointerInput(mode, doc, page) {
                        // One tap is the whole gesture: the mark lands where the
                        // finger is. Double-tap was tried first and abandoned — it
                        // needs a sub-300 ms second tap, which is a coin flip on a
                        // phone and invisible when it misses.
                        detectTapGestures { offset ->
                            val x = offset.x / size.width
                            val y = offset.y / size.height
                            when (mode) {
                                MarkMode.NOTE -> onNoteAt(x, y)

                                MarkMode.TEXT -> onTextAt(x, y)

                                MarkMode.HIGHLIGHT -> onAdd(
                                    PdfMark.Highlight(
                                        doc = doc,
                                        page = page,
                                        left = (x - 0.3f).coerceAtLeast(0f),
                                        top = (y - 0.02f).coerceAtLeast(0f),
                                        right = (x + 0.3f).coerceAtMost(1f),
                                        bottom = (y + 0.02f).coerceAtMost(1f),
                                    ),
                                )

                                MarkMode.REDACTION -> onAdd(
                                    PdfMark.Redaction(
                                        doc = doc,
                                        page = page,
                                        left = (x - 0.35f).coerceAtLeast(0f),
                                        top = (y - 0.02f).coerceAtLeast(0f),
                                        right = (x + 0.35f).coerceAtMost(1f),
                                        bottom = (y + 0.02f).coerceAtMost(1f),
                                    ),
                                )

                                else -> Unit
                            }
                        }
                    }.pointerInput(mode, doc, page) {
                        if (mode == MarkMode.INK || mode == MarkMode.SIGNATURE) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    drag.value = listOf(
                                        offset.x / size.width to offset.y / size.height,
                                    )
                                },
                                onDragEnd = {
                                    if (drag.value.size > 1) {
                                        onAdd(
                                            PdfMark.Ink(
                                                doc = doc,
                                                page = page,
                                                points = drag.value,
                                                isSignature = mode == MarkMode.SIGNATURE,
                                            ),
                                        )
                                    }
                                    drag.value = emptyList()
                                },
                                onDrag = { change, _ ->
                                    drag.value = drag.value + (
                                        change.position.x / size.width to
                                            change.position.y / size.height
                                        )
                                },
                            )
                        }
                    }
                },
            ),
    ) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            marks.filter { it.doc == doc && it.page == page }.forEach { mark ->
                when (mark) {
                    is PdfMark.Highlight -> drawRect(
                        color = ComposeColor(0x66FFD54F),
                        topLeft = Offset(mark.left * size.width, mark.top * size.height),
                        size = androidx.compose.ui.geometry.Size(
                            (mark.right - mark.left) * size.width,
                            (mark.bottom - mark.top) * size.height,
                        ),
                    )

                    is PdfMark.Redaction -> drawRect(
                        color = ComposeColor.Black,
                        topLeft = Offset(mark.left * size.width, mark.top * size.height),
                        size = androidx.compose.ui.geometry.Size(
                            (mark.right - mark.left) * size.width,
                            (mark.bottom - mark.top) * size.height,
                        ),
                    )

                    is PdfMark.Ink -> {
                        val path = androidx.compose.ui.graphics.Path()
                        mark.points.forEachIndexed { index, (x, y) ->
                            if (index == 0) {
                                path.moveTo(x * size.width, y * size.height)
                            } else {
                                path.lineTo(x * size.width, y * size.height)
                            }
                        }
                        drawPath(
                            path = path,
                            color = if (mark.isSignature) inkColor else ComposeColor(0xFF1A73E8),
                            style = Stroke(width = strokeWidth),
                        )
                    }

                    is PdfMark.Note -> {
                        drawCircle(
                            color = ComposeColor(0xFFF9A825),
                            radius = 8f,
                            center = Offset(mark.x * size.width, mark.y * size.height),
                        )
                        drawCircle(
                            color = ComposeColor(0xFF6D4C00),
                            radius = 8f,
                            center = Offset(mark.x * size.width, mark.y * size.height),
                            style = Stroke(width = 2f),
                        )
                    }

                    is PdfMark.Text -> {
                        drawContext.canvas.nativeCanvas.drawText(                            mark.text,
                            mark.x * size.width,
                            mark.y * size.height,
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.rgb(0x1A, 0x1C, 0x1B)
                                textSize = 12f * density
                            },
                        )
                    }
                }
            }

            // The stroke in progress follows the finger before it is committed.
            if (drag.value.size > 1) {
                val path = androidx.compose.ui.graphics.Path()
                drag.value.forEachIndexed { index, (x, y) ->
                    if (index == 0) {
                        path.moveTo(x * size.width, y * size.height)
                    } else {
                        path.lineTo(x * size.width, y * size.height)
                    }
                }
                drawPath(
                    path = path,
                    color = if (mode == MarkMode.SIGNATURE) inkColor else ComposeColor(0xFF1A73E8),
                    style = Stroke(width = strokeWidth),
                )
            }

            // A rectangle preview tells the user where a redaction will land.
            if (mode == MarkMode.REDACTION) {
                drawRect(
                    color = ComposeColor(0x33000000),
                    topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.08f),
                    style = Stroke(width = 2f),
                )
            }
        }
    }
}

/**
 * Re-expresses marks for a page that has just been rotated clockwise by
 * [degrees]. Marks are stored as fractions of the page, so rotating the page
 * without rotating them would slide every highlight, stroke and redaction off
 * the content it was placed on.
 *
 * Only marks belonging to [doc]/[page] move; everything else is returned as-is.
 */
fun rotateMarks(
    marks: List<PdfMark>,
    doc: Int,
    page: Int,
    degrees: Int,
): List<PdfMark> {
    val turns = ((degrees % 360) + 360) % 360
    if (turns == 0) return marks

    // Clockwise rotation in unit coordinates: (x, y) -> (1 - y, x) per 90°.
    fun point(x: Float, y: Float): Pair<Float, Float> = when (turns) {
        90 -> 1f - y to x
        180 -> 1f - x to 1f - y
        270 -> y to 1f - x
        else -> x to y
    }

    fun box(left: Float, top: Float, right: Float, bottom: Float):
        List<Float> {
        val corners = listOf(
            point(left, top),
            point(right, top),
            point(right, bottom),
            point(left, bottom),
        )
        return listOf(
            corners.minOf { it.first },
            corners.minOf { it.second },
            corners.maxOf { it.first },
            corners.maxOf { it.second },
        )
    }

    return marks.map { mark ->
        if (mark.doc != doc || mark.page != page) return@map mark
        when (mark) {
            is PdfMark.Highlight -> {
                val (left, top, right, bottom) = box(mark.left, mark.top, mark.right, mark.bottom)
                mark.copy(left = left, top = top, right = right, bottom = bottom)
            }
            is PdfMark.Redaction -> {
                val (left, top, right, bottom) = box(mark.left, mark.top, mark.right, mark.bottom)
                mark.copy(left = left, top = top, right = right, bottom = bottom)
            }
            is PdfMark.Ink -> mark.copy(points = mark.points.map { (x, y) -> point(x, y) })
            is PdfMark.Note -> {
                val (x, y) = point(mark.x, mark.y)
                mark.copy(x = x, y = y)
            }
            is PdfMark.Text -> {
                val (x, y) = point(mark.x, mark.y)
                mark.copy(x = x, y = y)
            }
        }
    }
}

/** Bakes marks into a page bitmap at export time. Redactions paint last, so they cover everything. */
fun bakeMarks(canvas: Canvas, doc: Int, page: Int, marks: List<PdfMark>) {
    val width = canvas.width.toFloat()
    val height = canvas.height.toFloat()

    fun paint(color: Int, stroke: Float, style: Paint.Style = Paint.Style.STROKE) = Paint().apply {
        this.color = color
        this.strokeWidth = stroke
        this.style = style
    }

    val highlightPaint = paint(Color.argb(0x66, 0xFF, 0xD5, 0x4F), 0f, Paint.Style.FILL)
    val inkPaint = paint(Color.rgb(0x1A, 0x73, 0xE8), 4f).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val signaturePaint = paint(Color.rgb(0x1A, 0x1C, 0x1B), 6f).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val redactionPaint = paint(Color.BLACK, 0f, Paint.Style.FILL)
    val textPaint = Paint().apply {
        color = Color.rgb(0x1A, 0x1C, 0x1B)
        textSize = 36f
    }

    val pageMarks = marks.filter { it.doc == doc && it.page == page }

    pageMarks.forEach { mark ->
        when (mark) {
            is PdfMark.Highlight -> canvas.drawRect(
                mark.left * width,
                mark.top * height,
                mark.right * width,
                mark.bottom * height,
                highlightPaint,
            )

            is PdfMark.Ink -> {
                if (mark.points.size > 1) {
                    val path = Path()
                    mark.points.forEachIndexed { index, (x, y) ->
                        if (index == 0) path.moveTo(x * width, y * height)
                        else path.lineTo(x * width, y * height)
                    }
                    canvas.drawPath(path, if (mark.isSignature) signaturePaint else inkPaint)
                }
            }

            // Notes are not drawn: a note is a comment for the reader's list, and
            // stamping its text onto the page would change the document's content.
            is PdfMark.Note -> Unit

            is PdfMark.Text -> canvas.drawText(
                mark.text,
                mark.x * width,
                mark.y * height,
                textPaint,
            )

            is PdfMark.Redaction -> Unit
        }
    }

    // Redactions last, so nothing drawn above can be peeled back to reveal them.
    pageMarks.filterIsInstance<PdfMark.Redaction>().forEach { mark ->
        canvas.drawRect(
            mark.left * width,
            mark.top * height,
            mark.right * width,
            mark.bottom * height,
            redactionPaint,
        )
    }
}