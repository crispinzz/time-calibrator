package br.com.clockschool

import android.content.Context
import android.graphics.Color
import android.os.Bundle

/**
 * Aparência de um widget. Cor null significa "automático": segue o claro/escuro do sistema.
 */
data class WidgetStyle(
    val background: Int? = null,
    val text: Int? = null,
    val opacity: Int = 100,
    val showLegend: Boolean = true,
    val legendText: String = ""
) {

    fun backgroundFor(night: Boolean): Int = background ?: if (night) AUTO_DARK_BG else AUTO_LIGHT_BG

    fun textFor(night: Boolean): Int = text ?: if (night) AUTO_DARK_TEXT else AUTO_LIGHT_TEXT

    fun legendFor(status: String): String = legendText.trim().ifEmpty { status }

    fun toBundle() = Bundle().apply { putString(KEY_ENCODED, encode()) }

    fun encode(): String = listOf(
        VERSION,
        background?.let { Integer.toHexString(it) } ?: AUTO,
        text?.let { Integer.toHexString(it) } ?: AUTO,
        opacity.toString(),
        if (showLegend) "1" else "0",
        legendText
    ).joinToString(SEPARATOR)

    companion object {
        private const val VERSION = "1"
        private const val AUTO = "auto"
        private const val SEPARATOR = "|"
        private const val KEY_ENCODED = "widget_style"

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

        fun decode(encoded: String?): WidgetStyle? {
            val parts = encoded?.split(SEPARATOR, limit = 6) ?: return null
            if (parts.size < 6 || parts[0] != VERSION) return null
            return try {
                WidgetStyle(
                    background = parts[1].takeIf { it != AUTO }?.let { it.toLong(16).toInt() },
                    text = parts[2].takeIf { it != AUTO }?.let { it.toLong(16).toInt() },
                    opacity = parts[3].toInt().coerceIn(0, 100),
                    showLegend = parts[4] == "1",
                    legendText = parts[5].take(LEGEND_MAX)
                )
            } catch (e: NumberFormatException) {
                null
            }
        }

        fun fromBundle(bundle: Bundle?): WidgetStyle? = decode(bundle?.getString(KEY_ENCODED))
    }
}

/** Estilos guardados por appWidgetId. */
object WidgetStyles {

    private fun key(appWidgetId: Int) = "widget_style_$appWidgetId"

    fun load(context: Context, appWidgetId: Int): WidgetStyle {
        val prefs = Prefs.of(context)
        WidgetStyle.decode(prefs.getString(key(appWidgetId), null))?.let { return it }
        return when (prefs.getInt(Prefs.LEGACY_WIDGET_THEME, ThemeMode.SYSTEM)) {
            ThemeMode.LIGHT -> WidgetStyle(WidgetStyle.AUTO_LIGHT_BG, WidgetStyle.AUTO_LIGHT_TEXT)
            ThemeMode.DARK -> WidgetStyle(WidgetStyle.AUTO_DARK_BG, WidgetStyle.AUTO_DARK_TEXT)
            else -> WidgetStyle()
        }
    }

    fun save(context: Context, appWidgetId: Int, style: WidgetStyle) {
        Prefs.of(context).edit().putString(key(appWidgetId), style.encode()).apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        Prefs.of(context).edit().remove(key(appWidgetId)).apply()
    }

    fun move(context: Context, oldId: Int, newId: Int) {
        val prefs = Prefs.of(context)
        val encoded = prefs.getString(key(oldId), null) ?: return
        prefs.edit().remove(key(oldId)).putString(key(newId), encoded).apply()
    }
}
