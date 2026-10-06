package br.com.timecalibrator

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Idioma do app, independente do idioma do celular. "" segue o sistema.
 *
 * O AppCompat aplica o idioma às activities; o widget roda fora delas, então a escolha
 * também fica nas preferências e [wrap] monta um contexto no idioma certo para ele.
 */
object Lang {

    val TAGS = listOf("", "en", "pt-BR", "es")

    fun current(context: Context): String {
        // No Android 13+ o idioma também muda pelas configurações do sistema; o sistema é a fonte.
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return ""
            return when (locales[0]?.language) {
                "en" -> "en"
                "pt" -> "pt-BR"
                "es" -> "es"
                else -> ""
            }
        }
        return Prefs.of(context).getString(Prefs.APP_LANGUAGE, "").orEmpty()
    }

    fun set(context: Context, tag: String) {
        Prefs.of(context).edit().putString(Prefs.APP_LANGUAGE, tag).commit()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        ClockWidgetProvider.updateAllWidgets(context.applicationContext)
    }

    /** Nome de cada idioma nele mesmo, como nos seletores do sistema. */
    fun label(context: Context, tag: String): String = when (tag) {
        "en" -> "English"
        "pt-BR" -> "Português"
        "es" -> "Español"
        else -> context.getString(R.string.lang_system)
    }

    fun wrap(context: Context): Context {
        val tag = current(context)
        if (tag.isEmpty()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }
}
