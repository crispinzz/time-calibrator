package br.com.clockschool

import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

object ClockOffset {

    /**
     * Fuso artificial que faz um TextClock exibir "hora atual menos o atraso calibrado",
     * deixando a atualização por conta do sistema em vez de alarmes do app.
     */
    fun shiftedTimeZoneId(offsetMillis: Long): String {
        val deviceMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        val offsetMinutes = (offsetMillis / 60000.0).roundToLong().toInt()
        val shifted = deviceMinutes - offsetMinutes
        val sign = if (shifted < 0) "-" else "+"
        val magnitude = abs(shifted)
        return String.format(Locale.US, "GMT%s%02d:%02d", sign, magnitude / 60, magnitude % 60)
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
}
