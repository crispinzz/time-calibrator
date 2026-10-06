package br.com.timecalibrator

import android.content.Context
import android.graphics.Color

/**
 * Aparência de um relógio nos widgets. Cor null significa "automático": segue o claro/escuro do sistema.
 */
data class WidgetStyle(
    val background: Int? = null,
    val text: Int? = null,
    val opacity: Int = 100,
    val showLegend: Boolean = true,
    val legendText: String = "",
    val showDelay: Boolean = true
) {

    fun backgroundFor(night: Boolean): Int = background ?: if (night) AUTO_DARK_BG else AUTO_LIGHT_BG

    fun textFor(night: Boolean): Int = text ?: if (night) AUTO_DARK_TEXT else AUTO_LIGHT_TEXT

    /** Legenda: texto livre e/ou o atraso. Vazio quando não há nada para mostrar. */
    fun legendFor(status: String): String {
        val parts = listOfNotNull(legendText.trim().ifEmpty { null }, status.takeIf { showDelay })
        return parts.joinToString(" · ")
    }

    fun encode(): String = listOf(
        VERSION,
        background?.let { Integer.toHexString(it) } ?: AUTO,
        text?.let { Integer.toHexString(it) } ?: AUTO,
        opacity.toString(),
        if (showLegend) "1" else "0",
        if (showDelay) "1" else "0",
        legendText
    ).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "2"
        private const val AUTO = "auto"
        private const val SEPARATOR = "|"

        const val AUTO_LIGHT_BG = Color.WHITE
        const val AUTO_DARK_BG = 0xFF0A0A0A.toInt()
        const val AUTO_LIGHT_TEXT = 0xFF0A0A0A.toInt()
        const val AUTO_DARK_TEXT = Color.WHITE
        const val LEGEND_MAX = 28

        /** Paleta rápida; null é o automático. */
        val SWATCHES: List<Int?> = listOf(
            null,
            0xFFFFFFFF.toInt(),
            0xFF0A0A0A.toInt(),
            0xFFE60023.toInt(),
            0xFFF3EEE6.toInt(),
            0xFF8E8E93.toInt(),
            0xFF1D3557.toInt(),
            0xFF2D6A4F.toInt(),
            0xFFFFC300.toInt(),
            0xFFFF8FA3.toInt()
        )

        private fun color(raw: String): Int? = raw.takeIf { it != AUTO }?.let { it.toLong(16).toInt() }

        fun decode(encoded: String?): WidgetStyle? {
            if (encoded == null) return null
            return try {
                when {
                    encoded.startsWith("2$SEPARATOR") -> {
                        val p = encoded.split(SEPARATOR, limit = 7)
                        if (p.size < 7) return null
                        WidgetStyle(color(p[1]), color(p[2]), p[3].toInt().coerceIn(0, 100), p[4] == "1", p[6].take(LEGEND_MAX), p[5] == "1")
                    }
                    encoded.startsWith("1$SEPARATOR") -> {
                        val p = encoded.split(SEPARATOR, limit = 6)
                        if (p.size < 6) return null
                        WidgetStyle(color(p[1]), color(p[2]), p[3].toInt().coerceIn(0, 100), p[4] == "1", p[5].take(LEGEND_MAX))
                    }
                    else -> null
                }
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

/** Estilo efetivo de cada widget da tela inicial. */
object WidgetStyles {

    private fun legacyKey(appWidgetId: Int) = "widget_style_$appWidgetId"

    fun legacyDefault(context: Context): WidgetStyle =
        when (Prefs.of(context).getInt(Prefs.LEGACY_WIDGET_THEME, ThemeMode.SYSTEM)) {
            ThemeMode.LIGHT -> WidgetStyle(WidgetStyle.AUTO_LIGHT_BG, WidgetStyle.AUTO_LIGHT_TEXT)
            ThemeMode.DARK -> WidgetStyle(WidgetStyle.AUTO_DARK_BG, WidgetStyle.AUTO_DARK_TEXT)
            else -> WidgetStyle()
        }

    /** Widgets antigos guardavam estilo próprio; os novos seguem o do relógio. */
    fun load(context: Context, appWidgetId: Int): WidgetStyle =
        WidgetStyle.decode(Prefs.of(context).getString(legacyKey(appWidgetId), null))
            ?: Clocks.style(context, Clocks.forWidget(context, appWidgetId))

    fun clearLegacy(context: Context, appWidgetId: Int) {
        Prefs.of(context).edit().remove(legacyKey(appWidgetId)).apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        clearLegacy(context, appWidgetId)
        Clocks.unbindWidget(context, appWidgetId)
    }

    fun move(context: Context, oldId: Int, newId: Int) {
        Clocks.bindWidget(context, newId, Clocks.forWidget(context, oldId))
        val prefs = Prefs.of(context)
        prefs.getString(legacyKey(oldId), null)?.let {
            prefs.edit().putString(legacyKey(newId), it).apply()
        }
        delete(context, oldId)
    }
}
