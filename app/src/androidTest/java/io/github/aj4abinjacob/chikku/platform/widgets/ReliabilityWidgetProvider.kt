package io.github.aj4abinjacob.chikku.platform.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

internal const val RELIABILITY_WIDGET_REFRESH = "io.github.aj4abinjacob.chikku.test.REFRESH_WIDGET"

/**
 * Test APK only. Android starts this receiver outside instrumentation, where dependencies shared
 * with the target APK (including Kotlin's runtime) are unavailable. Nullable Java overrides and
 * direct Java operations deliberately avoid generated runtime assertions/helpers here.
 */
class ReliabilityWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (RELIABILITY_WIDGET_REFRESH.equals(intent.action)) {
            val manager: AppWidgetManager? = AppWidgetManager.getInstance(context)
            if (manager == null) return
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context,
                "io.github.aj4abinjacob.chikku.platform.widgets.ReliabilityWidgetProvider")))
        } else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context?, manager: AppWidgetManager?, ids: IntArray?) {
        if (context == null || manager == null || ids == null) return
        val view = RemoteViews("android", android.R.layout.simple_list_item_1)
        view.setTextViewText(android.R.id.text1, "Reliable widget")
        manager.updateAppWidget(ids, view)
    }
}
