package com.qsc.accounting.ui.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qsc.accounting.domain.TrendPoint
import kotlin.math.max
import kotlin.math.min

data class ChartSlice(val label: String, val value: Long, val color: Color)

/** 趋势里的三条曲线。默认只看结余，收入/支出按需打开。 */
enum class TrendSeries(val label: String) {
    NET("结余"),
    INCOME("收入"),
    EXPENSE("支出"),
}

/** 环形占比图：不依赖第三方图表库，两三行 Canvas 就能画准。 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    stroke: Dp = 18.dp,
    trackColor: Color = Color(0x22000000),
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val diameter = min(size.width, size.height) - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val visible = slices.filter { it.value > 0 }
            val total = visible.sumOf { it.value }

            if (total <= 0L) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                )
                return@Canvas
            }

            var startAngle = -90f
            visible.forEach { slice ->
                val sweep = slice.value.toFloat() / total * 360f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + 1.2f,
                    sweepAngle = max(0f, sweep - 2.4f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        content()
    }
}

/**
 * 趋势折线：每条曲线独立着色，[visible] 里的才画。
 * 纵轴按当前可见曲线的最小/最大值自适应，结余为负时零轴会落在中间。
 */
@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    visible: Set<TrendSeries>,
    seriesColor: (TrendSeries) -> Color,
    baselineColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    labelFormatter: (String) -> String = { it },
) {
    if (points.isEmpty() || visible.isEmpty()) return
    val density = LocalDensity.current

    Canvas(modifier = modifier) {
        val labelHeight = with(density) { 18.dp.toPx() }
        val plotHeight = size.height - labelHeight
        val slot = size.width / points.size

        val values = points.flatMap { point ->
            visible.map { series ->
                when (series) {
                    TrendSeries.INCOME -> point.incomeCents
                    TrendSeries.EXPENSE -> point.expenseCents
                    TrendSeries.NET -> point.netCents
                }
            }
        }
        val maxValue = max(values.maxOrNull() ?: 0L, 0L)
        val minValue = min(values.minOrNull() ?: 0L, 0L)
        val span = (maxValue - minValue).coerceAtLeast(1L)

        fun yFor(value: Long): Float {
            val ratio = (value - minValue).toFloat() / span
            return plotHeight - ratio * (plotHeight * 0.9f) - plotHeight * 0.05f
        }

        val zeroY = yFor(0)
        drawLine(
            color = baselineColor,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 1f,
        )

        val every = when {
            points.size <= 8 -> 1
            points.size <= 16 -> 2
            points.size <= 31 -> 5
            else -> 11
        }

        val strokeWidth = with(density) { 2.dp.toPx() }
        val dotRadius = with(density) { 2.5.dp.toPx() }

        visible.sortedBy { it.ordinal }.forEach { series ->
            val path = Path()
            points.forEachIndexed { index, point ->
                val value = when (series) {
                    TrendSeries.INCOME -> point.incomeCents
                    TrendSeries.EXPENSE -> point.expenseCents
                    TrendSeries.NET -> point.netCents
                }
                val x = slot * index + slot / 2f
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val color = seriesColor(series)
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // 点太多时不画点，避免糊成一片。
            if (points.size <= 16) {
                points.forEachIndexed { index, point ->
                    val value = when (series) {
                        TrendSeries.INCOME -> point.incomeCents
                        TrendSeries.EXPENSE -> point.expenseCents
                        TrendSeries.NET -> point.netCents
                    }
                    drawCircle(
                        color = color,
                        radius = dotRadius,
                        center = Offset(slot * index + slot / 2f, yFor(value)),
                    )
                }
            }
        }

        val paint = Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            textAlign = Paint.Align.CENTER
        }
        points.forEachIndexed { index, point ->
            if (index % every != 0 && index != points.lastIndex) return@forEachIndexed
            val text = labelFormatter(point.label)
            val x = (slot * index + slot / 2f).coerceIn(text.length * 3.2f, size.width - text.length * 3.2f)
            drawContext.canvas.nativeCanvas.drawText(text, x, size.height - 4.dp.toPx(), paint)
        }
    }
}

/** 分类占比条：横向长条比饼图更容易比较大小，列表里也能直接读。 */
@Composable
fun CategoryBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val height = size.height
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            size = Size(size.width, height),
            cornerRadius = CornerRadius(height / 2f),
        )
        drawRoundRect(
            color = color,
            size = Size(
                (size.width * fraction.coerceIn(0f, 1f)).coerceAtLeast(if (fraction > 0f) height else 0f),
                height,
            ),
            cornerRadius = CornerRadius(height / 2f),
        )
    }
}

@Composable
fun LegendDot(color: Color, modifier: Modifier = Modifier.size(8.dp)) {
    Canvas(modifier) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}
