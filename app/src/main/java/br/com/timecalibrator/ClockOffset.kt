package br.com.timecalibrator

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

object ClockOffset {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val HALF_DAY_MILLIS = DAY_MILLIS / 2

    /**
     * Traz a diferença para o intervalo de ±12 h. Sem isso, calibrar o sinal das 00:00
     * quando o celular ainda marca 23:59:50 viraria um atraso de quase 24 horas.
     */
    fun normalize(offsetMillis: Long): Long {
        var value = offsetMillis % DAY_MILLIS
        if (value > HALF_DAY_MILLIS) value -= DAY_MILLIS
        if (value < -HALF_DAY_MILLIS) value += DAY_MILLIS
        return value
    }

    /** Hora corrigida agora, em milissegundos de época. */
    fun calibratedNow(offsetMillis: Long, now: Long = System.currentTimeMillis()): Long = now - offsetMillis

    /** Milissegundos desde a meia-noite local da hora corrigida. */
    fun millisOfDay(calibratedMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = calibratedMillis }
        return cal.get(Calendar.HOUR_OF_DAY) * 3_600_000L +
            cal.get(Calendar.MINUTE) * 60_000L +
            cal.get(Calendar.SECOND) * 1_000L +
            cal.get(Calendar.MILLISECOND)
    }

    fun formatClock(calibratedMillis: Long, withSeconds: Boolean = true): String {
        val cal = Calendar.getInstance().apply { timeInMillis = calibratedMillis }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return if (withSeconds) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, cal.get(Calendar.SECOND))
        } else {
            String.format(Locale.US, "%02d:%02d", h, m)
        }
    }

    fun formatDuration(offsetMillis: Long): String {
        val seconds = abs(offsetMillis) / 1000
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            seconds / 3600,
            (seconds % 3600) / 60,
            seconds % 60
        )
    }

    /** "3 min 30 s", "1 h 05 min", "12 s". */
    fun formatHuman(offsetMillis: Long): String {
        val total = abs(offsetMillis) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 && m > 0 -> String.format(Locale.US, "%d h %02d min", h, m)
            h > 0 -> "$h h"
            m > 0 && s > 0 -> "$m min $s s"
            m > 0 -> "$m min"
            else -> "$s s"
        }
    }

    /** Frase de estado usada no app e na legenda automática dos widgets. */
    fun status(offsetMillis: Long, calibrated: Boolean): String = when {
        !calibrated -> "não calibrado"
        offsetMillis / 1000 == 0L -> "sincronizado"
        offsetMillis > 0 -> "${formatHuman(offsetMillis)} atrasado"
        else -> "${formatHuman(offsetMillis)} adiantado"
    }
}
