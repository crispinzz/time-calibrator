package br.com.timecalibrator.ui

import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import br.com.timecalibrator.R

/**
 * Controle segmentado no estilo do seletor de tema: pílula cinza com a opção ativa em
 * cartão. [onPick] recebe o índice tocado depois que a seleção já foi pintada.
 */
class Segmented(
    private val container: LinearLayout,
    labels: List<String>,
    selected: Int,
    private val onPick: (Int) -> Unit
) {
    private val buttons = labels.mapIndexed { i, label ->
        TextView(container.context, null, 0, R.style.Text_Label).apply {
            text = label
            gravity = Gravity.CENTER
            maxLines = 1
            isClickable = true
            setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            setPadding(4.dp, 0, 4.dp, 0)
            setOnClickListener {
                it.tick()
                paint(i)
                onPick(i)
            }
            container.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    init {
        paint(selected)
    }

    private fun paint(selected: Int) {
        buttons.forEachIndexed { i, b ->
            val on = i == selected
            if (on) b.setBackgroundResource(R.drawable.bg_card) else b.background = null
            b.setTextColor(container.context.color(if (on) R.color.text_primary else R.color.text_secondary))
        }
    }
}
