package com.qsc.accounting.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetUpdater.requestUpdate(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.requestUpdate(context)
    }
}
