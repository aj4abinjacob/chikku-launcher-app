package io.github.aj4abinjacob.chikku.home.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import io.github.aj4abinjacob.chikku.R

/** Offline clock. Framework TextClocks own visible ticking and time/zone/format changes. */
class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.widget_clock))
        }
    }
}
