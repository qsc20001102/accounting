package com.qsc.accounting.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.qsc.accounting.AccountingApp
import com.qsc.accounting.MainActivity
import com.qsc.accounting.R
import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.Money
import com.qsc.accounting.domain.Period
import com.qsc.accounting.domain.StatsQuery
import com.qsc.accounting.domain.Summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面小组件的数据源：读今天的收支汇总，塞进 RemoteViews。
 * 触发点有三处：桌面刷新周期、App 启动、以及每笔记账/删除之后。
 */
object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { updateAll(appContext) }
        }
    }

    suspend fun updateAll(context: Context) {
        val app = context.applicationContext as? AccountingApp ?: return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TodayWidgetProvider::class.java),
        )
        if (ids.isEmpty()) return

        val today = Dates.today()
        val summary = app.container.repository.summary(
            StatsQuery(period = Period.DAY, date = today.toString()),
            app.container.prefs.statisticsMode,
            app.container.prefs.includePending,
            today,
        )
        val views = buildViews(context, summary)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun buildViews(context: Context, summary: Summary): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        // 去掉无意义的 .00，一行里能多放一位数字。
        views.setTextViewText(R.id.widget_expense_value, Money.formatPlain(summary.expenseCents))
        views.setTextViewText(R.id.widget_income_value, Money.formatPlain(summary.incomeCents))
        views.setTextViewText(R.id.widget_net_value, Money.formatPlain(summary.netCents))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
