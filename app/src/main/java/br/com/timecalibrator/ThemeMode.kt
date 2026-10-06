package br.com.timecalibrator

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object ThemeMode {

    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    fun applyAppTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (ClockStore(context).appTheme) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun label(context: Context, mode: Int): String = context.getString(
        when (mode) {
            LIGHT -> R.string.theme_light
            DARK -> R.string.theme_dark
            else -> R.string.theme_system
        }
    )

    /** Modo escuro do sistema, ignorando o tema escolhido para o app. */
    fun isSystemNight(context: Context): Boolean {
        val uiMode = context.applicationContext.resources.configuration.uiMode
        return uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
