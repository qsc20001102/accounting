package com.qsc.accounting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.qsc.accounting.domain.TrendPoint
import com.qsc.accounting.ui.charts.TrendLineChart
import com.qsc.accounting.ui.charts.TrendSeries
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 图表是纯 Canvas 画的，编译通过不代表画对。
 * 这里把界面真的画到 Bitmap 上存成 PNG，人工看一眼即可。
 * 产物在 app/build/snapshots 目录下。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartSnapshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun points(): List<TrendPoint> = (1..14).map { day ->
        val income = if (day % 3 == 0) 320_000L else 0L
        val expense = 60_000L + (day % 5) * 18_500L
        TrendPoint(
            label = "2026-11-%02d".format(day),
            incomeCents = income,
            expenseCents = expense,
        )
    }

    private fun snapshot(name: String, visible: Set<TrendSeries>) {
        compose.setContent {
            Surface(color = Color(0xFFF6F4EF)) {
                Column(Modifier.padding(16.dp).background(Color(0xFFF6F4EF))) {
                    TrendLineChart(
                        points = points(),
                        visible = visible,
                        seriesColor = { series -> seriesColor(series) },
                        baselineColor = Color(0xFFE2DCCD),
                        labelColor = Color(0xFF8A857A),
                        labelFormatter = { it.takeLast(2) },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idle()

        val decor = compose.activity.window.decorView
        val width = 900
        val height = 600
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))

        val directory = File("build/snapshots").apply { mkdirs() }
        val target = File(directory, "$name.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("快照没有写出来", target.length() > 0)
        // 画布非空白：至少有不止一种颜色。
        val colors = mutableSetOf<Int>()
        for (x in 0 until width step 17) {
            for (y in 0 until height step 17) {
                colors += bitmap.getPixel(x, y)
                if (colors.size > 3) break
            }
            if (colors.size > 3) break
        }
        assertTrue("快照是空白图", colors.size > 3)
    }

    private fun seriesColor(series: TrendSeries): Color = when (series) {
        TrendSeries.NET -> Color(0xFF0F4C46)
        TrendSeries.INCOME -> Color(0xFF2F7A5B)
        TrendSeries.EXPENSE -> Color(0xFFB54B33)
    }

    @Test
    fun net_series_only() {
        snapshot("trend-net", setOf(TrendSeries.NET))
    }

    @Test
    fun all_series() {
        snapshot("trend-all", setOf(TrendSeries.NET, TrendSeries.INCOME, TrendSeries.EXPENSE))
    }
}
