package io.pedalcheck.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Static launch affordance. No frozen accelerator/brake status is exposed as current data. */
class PedalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { id ->
            val view = RemoteViews(context.packageName, R.layout.pedal_widget)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            view.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(id, view)
        }
    }
}
