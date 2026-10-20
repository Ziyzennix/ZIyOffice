package org.ziyoffice.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ziyoffice.model.ChartPoint
import org.ziyoffice.model.formatNumber

/**
 * A minimal bar chart drawn with Canvas — no charting dependency, which is the
 * whole point of this app. Bars scale to the largest absolute value, so negative
 * numbers plot below the baseline instead of being clipped to zero.
 */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    numberFormat: String?,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Text(
            "No numbers to chart. Put labels in one column and numbers in the next.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp),
        )
        return
    }

    val barColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            points.joinToString("  ") { "${it.label}: ${formatNumber(it.value, numberFormat)}" },
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val maxMagnitude = points.maxOf { kotlin.math.abs(it.value) }.takeIf { it > 0 } ?: 1.0
            val hasNegative = points.any { it.value < 0 }
            val baseline = if (hasNegative) size.height / 2f else size.height
            val scale = (if (hasNegative) size.height / 2f else size.height) / maxMagnitude

            drawLine(
                color = axisColor,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx(),
            )

            val slot = size.width / points.size
            val barWidth = slot * 0.6f
            points.forEachIndexed { index, point ->
                val height = (kotlin.math.abs(point.value) * scale).toFloat()
                val left = index * slot + (slot - barWidth) / 2f
                val top = if (point.value < 0) baseline else baseline - height
                drawRect(
                    color = if (point.value < 0) negativeColor else barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, height.coerceAtLeast(1f)),
                )
            }
        }
    }
}
