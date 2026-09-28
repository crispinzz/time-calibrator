package br.com.clockschool

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    const val NAME = "clock_school_prefs"
    const val OFFSET_MILLIS = "offset_millis"
    const val LAST_CALIBRATION_AT = "last_calibration_at"
    const val CALIBRATED_VIA_CODE = "calibrated_via_code"
    const val APP_THEME = "app_theme"
    const val SCHEDULED_HOUR = "scheduled_hour"
    const val SCHEDULED_MINUTE = "scheduled_minute"

    /** Tema único que os widgets usavam antes de cada um ter o próprio estilo. */
    const val LEGACY_WIDGET_THEME = "widget_theme"

    fun of(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}

/**
 * Relógios independentes: cada um tem nome, calibração e aparência próprios.
 * O relógio 0 usa as chaves antigas, então quem já tinha calibrado não perde nada.
 */
object Clocks {
    private const val IDS = "clock_ids"
    private const val ACTIVE = "clock_active"
    private const val NEXT = "clock_next_id"

    fun ids(context: Context): List<Int> {
        val raw = Prefs.of(context).getString(IDS, null) ?: return listOf(0)
        return raw.split(',').mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
    }

    fun active(context: Context): Int {
        val id = Prefs.of(context).getInt(ACTIVE, 0)
        return if (id in ids(context)) id else ids(context).first()
    }

    fun setActive(context: Context, id: Int) {
        Prefs.of(context).edit().putInt(ACTIVE, id).apply()
    }

    fun create(context: Context, name: String, style: WidgetStyle): Int {
        val prefs = Prefs.of(context)
        val id = maxOf(prefs.getInt(NEXT, 1), (ids(context).maxOrNull() ?: 0) + 1)
        prefs.edit()
            .putInt(NEXT, id + 1)
            .putString(IDS, (ids(context) + id).joinToString(","))
            .putString(nameKey(id), name)
            .putString(styleKey(id), style.encode())
            .commit()
        return id
    }

    fun delete(context: Context, id: Int) {
        val remaining = ids(context) - id
        if (remaining.isEmpty()) return
        val prefs = Prefs.of(context)
        val edit = prefs.edit().putString(IDS, remaining.joinToString(","))
        prefs.all.keys.filter { it.startsWith("c${id}_") || it == nameKey(id) || it == styleKey(id) }
            .forEach { edit.remove(it) }
        edit.commit()
    }

    fun name(context: Context, id: Int): String =
        Prefs.of(context).getString(nameKey(id), null)?.takeIf { it.isNotBlank() } ?: "Relógio ${ids(context).indexOf(id) + 1}"

    fun rename(context: Context, id: Int, name: String) {
        Prefs.of(context).edit().putString(nameKey(id), name.trim()).apply()
    }

    fun style(context: Context, id: Int): WidgetStyle =
        WidgetStyle.decode(Prefs.of(context).getString(styleKey(id), null)) ?: WidgetStyles.legacyDefault(context)

    fun saveStyle(context: Context, id: Int, style: WidgetStyle) {
        Prefs.of(context).edit().putString(styleKey(id), style.encode()).commit()
    }

    /** Relógio mostrado por um widget da tela inicial. */
    fun forWidget(context: Context, appWidgetId: Int): Int {
        val id = Prefs.of(context).getInt(widgetKey(appWidgetId), 0)
        return if (id in ids(context)) id else 0
    }

    fun bindWidget(context: Context, appWidgetId: Int, clockId: Int) {
        Prefs.of(context).edit().putInt(widgetKey(appWidgetId), clockId).commit()
    }

    fun unbindWidget(context: Context, appWidgetId: Int) {
        Prefs.of(context).edit().remove(widgetKey(appWidgetId)).apply()
    }

    private fun nameKey(id: Int) = "clock_name_$id"
    private fun styleKey(id: Int) = "clock_style_$id"
    private fun widgetKey(appWidgetId: Int) = "widget_clock_$appWidgetId"
}

/** Calibração de um relógio, lida e gravada num só lugar. */
class ClockStore(context: Context, val clockId: Int = Clocks.active(context)) {

    private val prefs = Prefs.of(context)

    private fun key(base: String) = if (clockId == 0) base else "c${clockId}_$base"

    val offsetMillis: Long
        get() = ClockOffset.normalize(prefs.getLong(key(Prefs.OFFSET_MILLIS), 0L))

    val lastCalibrationAt: Long
        get() = prefs.getLong(key(Prefs.LAST_CALIBRATION_AT), -1L)

    val isCalibrated: Boolean
        get() = lastCalibrationAt >= 0

    val calibratedViaCode: Boolean
        get() = prefs.getBoolean(key(Prefs.CALIBRATED_VIA_CODE), false)

    var scheduledHour: Int
        get() = prefs.getInt(key(Prefs.SCHEDULED_HOUR), 7)
        set(value) = prefs.edit().putInt(key(Prefs.SCHEDULED_HOUR), value).apply()

    var scheduledMinute: Int
        get() = prefs.getInt(key(Prefs.SCHEDULED_MINUTE), 0)
        set(value) = prefs.edit().putInt(key(Prefs.SCHEDULED_MINUTE), value).apply()

    var appTheme: Int
        get() = prefs.getInt(Prefs.APP_THEME, ThemeMode.SYSTEM)
        set(value) = prefs.edit().putInt(Prefs.APP_THEME, value).apply()

    fun snapshot() = Snapshot(
        prefs.getLong(key(Prefs.OFFSET_MILLIS), 0L),
        lastCalibrationAt,
        calibratedViaCode
    )

    fun restore(snapshot: Snapshot) {
        prefs.edit()
            .putLong(key(Prefs.OFFSET_MILLIS), snapshot.offsetMillis)
            .putLong(key(Prefs.LAST_CALIBRATION_AT), snapshot.lastCalibrationAt)
            .putBoolean(key(Prefs.CALIBRATED_VIA_CODE), snapshot.viaCode)
            .commit()
    }

    fun saveCalibration(offsetMillis: Long, viaCode: Boolean) {
        prefs.edit()
            .putLong(key(Prefs.OFFSET_MILLIS), ClockOffset.normalize(offsetMillis))
            .putLong(key(Prefs.LAST_CALIBRATION_AT), System.currentTimeMillis())
            .putBoolean(key(Prefs.CALIBRATED_VIA_CODE), viaCode)
            .commit()
    }

    data class Snapshot(val offsetMillis: Long, val lastCalibrationAt: Long, val viaCode: Boolean)
}
