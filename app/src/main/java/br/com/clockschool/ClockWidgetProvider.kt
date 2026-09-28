package br.com.clockschool

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * O horário do widget é um Chronometer cuja base é "meia-noite corrigida" no relógio
 * monotônico. O launcher faz o tique a cada segundo sozinho, com precisão de segundo
 * (o antigo TextClock só aceitava ajustes em minutos inteiros).
 *
 * O Chronometer formata H:MM:SS sem zero à esquerda e MM:SS antes de 1 h, então o formato
 * muda às 01:00 e às 10:00, e a base volta a zero à meia-noite. Um alarme atualiza o
 * widget exatamente nessas viradas.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
        scheduleNextTransition(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PINNED -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val clockId = intent.getIntExtra(EXTRA_CLOCK, 0)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) Clocks.bindWidget(context, id, clockId)
                updateAllWidgets(context)
            }
            ACTION_TRANSITION,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> updateAllWidgets(context)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) WidgetStyles.delete(context, id)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        for (i in oldWidgetIds.indices) WidgetStyles.move(context, oldWidgetIds[i], newWidgetIds[i])
    }

    override fun onDisabled(context: Context) {
        alarmManager(context).cancel(transitionIntent(context))
    }

    companion object {

        const val ACTION_PINNED = "br.com.clockschool.action.WIDGET_PINNED"
        private const val ACTION_TRANSITION = "br.com.clockschool.action.CLOCK_TRANSITION"
        private const val EXTRA_CLOCK = "br.com.clockschool.extra.CLOCK"

        private const val HOUR = 3_600_000L
        private val TRANSITIONS = longArrayOf(1 * HOUR, 10 * HOUR, 24 * HOUR)

        fun widgetIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in widgetIds(context)) render(context, manager, id)
            scheduleNextTransition(context)
        }

        fun widgetsOf(context: Context, clockId: Int): List<Int> =
            widgetIds(context).filter { Clocks.forWidget(context, it) == clockId }

        fun updateWidget(context: Context, appWidgetId: Int) {
            render(context, AppWidgetManager.getInstance(context), appWidgetId)
        }

        /** Pede ao launcher para fixar um widget novo já com o estilo escolhido no app. */
        fun requestPin(context: Context, clockId: Int): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            val callback = Intent(context, ClockWidgetProvider::class.java)
                .setAction(ACTION_PINNED)
                .putExtra(EXTRA_CLOCK, clockId)
            // Mutável para o sistema preencher o EXTRA_APPWIDGET_ID do widget criado.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, 100 + clockId, callback, flags)
            return manager.requestPinAppWidget(
                ComponentName(context, ClockWidgetProvider::class.java), null, pending
            )
        }

        private fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val store = ClockStore(context, Clocks.forWidget(context, appWidgetId))
            val style = WidgetStyles.load(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_clock)

            val msOfDay = ClockOffset.millisOfDay(ClockOffset.schoolNow(store.offsetMillis))
            views.setChronometer(
                R.id.widgetTime,
                SystemClock.elapsedRealtime() - msOfDay,
                chronometerFormat(msOfDay),
                true
            )

            applyColors(context, views, style)

            val options = manager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val legend = style.legendFor(ClockOffset.status(store.offsetMillis, store.isCalibrated))
            val legendVisible = style.showLegend && legend.isNotEmpty() && minHeight >= 72
            views.setViewVisibility(R.id.widgetLegend, if (legendVisible) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.widgetLegendText, legend)

            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                android.R.id.background,
                PendingIntent.getActivity(
                    context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            manager.updateAppWidget(appWidgetId, views)
        }

        private fun applyColors(context: Context, views: RemoteViews, style: WidgetStyle) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // O launcher escolhe a variante clara/escura na hora, sem precisar de nova atualização.
                val bgDay = style.backgroundFor(false)
                val bgNight = style.backgroundFor(true)
                val textDay = style.textFor(false)
                val textNight = style.textFor(true)
                views.setColorInt(R.id.widgetBg, "setColorFilter", bgDay, bgNight)
                views.setColorInt(R.id.widgetTime, "setTextColor", textDay, textNight)
                views.setColorInt(R.id.widgetLegendText, "setTextColor", textDay, textNight)
                views.setColorInt(R.id.widgetLegendBg, "setColorFilter", textDay, textNight)
            } else {
                val night = ThemeMode.isSystemNight(context)
                views.setInt(R.id.widgetBg, "setColorFilter", style.backgroundFor(night))
                views.setTextColor(R.id.widgetTime, style.textFor(night))
                views.setTextColor(R.id.widgetLegendText, style.textFor(night))
                views.setInt(R.id.widgetLegendBg, "setColorFilter", style.textFor(night))
            }
            views.setInt(R.id.widgetBg, "setImageAlpha", style.opacity * 255 / 100)
        }

        private fun chronometerFormat(msOfDay: Long): String = when {
            msOfDay < 1 * HOUR -> "00:%s"
            msOfDay < 10 * HOUR -> "0%s"
            else -> "%s"
        }

        private fun scheduleNextTransition(context: Context) {
            val alarms = alarmManager(context)
            val pending = transitionIntent(context)
            if (widgetIds(context).isEmpty()) {
                alarms.cancel(pending)
                return
            }
            val now = System.currentTimeMillis()
            // A próxima virada entre todos os relógios em uso.
            val triggerAt = widgetIds(context).map { Clocks.forWidget(context, it) }.distinct().minOf { clockId ->
                val msOfDay = ClockOffset.millisOfDay(ClockOffset.schoolNow(ClockStore(context, clockId).offsetMillis, now))
                now + (TRANSITIONS.first { it > msOfDay } - msOfDay) + 20
            }
            // Folga mínima para já estarmos do outro lado da virada quando o alarme chegar.
            

            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            try {
                if (canExact) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                }
            } catch (e: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }

        private fun transitionIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ClockWidgetProvider::class.java).setAction(ACTION_TRANSITION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun alarmManager(context: Context) =
            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
}
