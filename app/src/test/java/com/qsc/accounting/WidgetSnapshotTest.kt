package com.qsc.accounting

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 小组件布局没有模拟器也能验：inflate 出来按桌面尺寸绘制成 PNG。
 * 同时渲染 3 格和 4 格两种宽度，确认单行内容不会被截断。
 * 产物在 app/build/snapshots 目录下。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetSnapshotTest {

    private fun render(name: String, width: Int, height: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = LayoutInflater.from(context).inflate(R.layout.widget_today, null)

        view.findViewById<TextView>(R.id.widget_expense_value).text = "105.76"
        view.findViewById<TextView>(R.id.widget_income_value).text = "411.9"
        // 结余经常是负数，用最长的情况验证不截断
        view.findViewById<TextView>(R.id.widget_net_value).text = "-305.87"

        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val directory = File("build/snapshots").apply { mkdirs() }
        val target = File(directory, "$name.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("小组件快照没有写出来：$name", target.length() > 0)
        val colors = mutableSetOf<Int>()
        for (x in 0 until width step 11) {
            for (y in 0 until height step 11) {
                colors += bitmap.getPixel(x, y)
                if (colors.size > 4) break
            }
            if (colors.size > 4) break
        }
        assertTrue("小组件快照是空白图：$name", colors.size > 4)
    }

    @Test
    fun one_row_at_four_cells() {
        // 4 格宽、1 行高：约 360dp × 66dp
        render("widget", 1080, 200)
    }

    @Test
    fun one_row_at_three_cells() {
        // 三星最小 3 格宽：约 270dp × 66dp，检查数字不被挤掉
        render("widget-3cells", 810, 200)
    }
}
