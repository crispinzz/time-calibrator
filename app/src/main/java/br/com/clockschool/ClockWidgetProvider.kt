package br.com.clockschool

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            updateAllWidgets(context)
        }
    }

    companion object {

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ClockWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            val offsetMillis = prefs.getLong(Prefs.OFFSET_MILLIS, 0L)
            val lastCalibration = prefs.getLong(Prefs.LAST_CALIBRATION_AT, -1L)
            val pillText = when {
                lastCalibration < 0 -> "não calibrado"
                offsetMillis == 0L -> "sincronizado"
                offsetMillis > 0 -> "atrasada " + ClockOffset.formatDuration(offsetMillis)
                else -> "adiantada " + ClockOffset.formatDuration(offsetMillis)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setString(R.id.widgetTime, "setTimeZone", ClockOffset.shiftedTimeZoneId(offsetMillis))
            views.setTextViewText(R.id.widgetSubtitle, pillText)

            // No modo Sistema o próprio layout resolve as cores com a configuração do launcher.
            val themeMode = prefs.getInt(Prefs.WIDGET_THEME, ThemeMode.SYSTEM)
            if (themeMode != ThemeMode.SYSTEM) {
                val themed = ThemeMode.contextFor(context, themeMode)
                val dark = themeMode == ThemeMode.DARK
                views.setInt(
                    R.id.widgetRoot, "setBackgroundResource",
                    if (dark) R.drawable.bg_widget_card_dark else R.drawable.bg_widget_card_light
                )
                views.setInt(
                    R.id.widgetSubtitle, "setBackgroundResource",
                    if (dark) R.drawable.bg_widget_pill_dark else R.drawable.bg_widget_pill_light
                )
                views.setTextColor(R.id.widgetTime, ContextCompat.getColor(themed, R.color.widget_text))
                views.setTextColor(R.id.widgetSubtitle, ContextCompat.getColor(themed, R.color.widget_pill_text))
            }

            val openAppIntent = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent, flags)
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
