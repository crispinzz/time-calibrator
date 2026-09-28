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

/** Estado da calibração, lido e gravado num só lugar. */
class ClockStore(context: Context) {

    private val prefs = Prefs.of(context)

    val offsetMillis: Long
        get() = ClockOffset.normalize(prefs.getLong(Prefs.OFFSET_MILLIS, 0L))

    val lastCalibrationAt: Long
        get() = prefs.getLong(Prefs.LAST_CALIBRATION_AT, -1L)

    val isCalibrated: Boolean
        get() = lastCalibrationAt >= 0

    val calibratedViaCode: Boolean
        get() = prefs.getBoolean(Prefs.CALIBRATED_VIA_CODE, false)

    var scheduledHour: Int
        get() = prefs.getInt(Prefs.SCHEDULED_HOUR, 7)
        set(value) = prefs.edit().putInt(Prefs.SCHEDULED_HOUR, value).apply()

    var scheduledMinute: Int
        get() = prefs.getInt(Prefs.SCHEDULED_MINUTE, 0)
        set(value) = prefs.edit().putInt(Prefs.SCHEDULED_MINUTE, value).apply()

    var appTheme: Int
        get() = prefs.getInt(Prefs.APP_THEME, ThemeMode.SYSTEM)
        set(value) = prefs.edit().putInt(Prefs.APP_THEME, value).apply()

    fun snapshot() = Snapshot(
        prefs.getLong(Prefs.OFFSET_MILLIS, 0L),
        lastCalibrationAt,
        calibratedViaCode
    )

    fun restore(snapshot: Snapshot) {
        prefs.edit()
            .putLong(Prefs.OFFSET_MILLIS, snapshot.offsetMillis)
            .putLong(Prefs.LAST_CALIBRATION_AT, snapshot.lastCalibrationAt)
            .putBoolean(Prefs.CALIBRATED_VIA_CODE, snapshot.viaCode)
            .commit()
    }

    fun saveCalibration(offsetMillis: Long, viaCode: Boolean) {
        prefs.edit()
            .putLong(Prefs.OFFSET_MILLIS, ClockOffset.normalize(offsetMillis))
            .putLong(Prefs.LAST_CALIBRATION_AT, System.currentTimeMillis())
            .putBoolean(Prefs.CALIBRATED_VIA_CODE, viaCode)
            .commit()
    }

    data class Snapshot(val offsetMillis: Long, val lastCalibrationAt: Long, val viaCode: Boolean)
}
