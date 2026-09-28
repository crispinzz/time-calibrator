package br.com.clockschool

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object ThemeMode {

    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    fun applyAppTheme(context: Context) {
        val mode = context.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
            .getInt(Prefs.APP_THEME, SYSTEM)
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    /** Contexto com o modo claro/escuro forçado, para resolver as cores do widget. */
    fun contextFor(context: Context, mode: Int): Context {
        if (mode == SYSTEM) return context
        val config = Configuration(context.resources.configuration)
        val night = if (mode == DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return context.createConfigurationContext(config)
    }
}
